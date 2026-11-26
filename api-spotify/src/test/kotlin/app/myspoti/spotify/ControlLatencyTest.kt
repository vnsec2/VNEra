package app.myspoti.spotify

import app.myspoti.spotify.bridge.AdSchedule
import app.myspoti.spotify.bridge.Bridge
import app.myspoti.spotify.bridge.BridgeHost
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class ControlLatencyTest {
    private class FakeHost : BridgeHost {
        override val messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
        val calls = CopyOnWriteArrayList<Triple<String, String, Long>>()
        var now = { 0L }
        var mediaPaused = false
        private val gates = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

        fun block(method: String) {
            gates[method] = CompletableDeferred()
        }

        fun release(method: String) {
            gates.remove(method)?.complete(Unit)
        }

        override suspend fun evaluate(script: String) {
            val m = Regex("""Myspoti\.call\((\d+), "([^"]+)", (.*)\)\.catch""", RegexOption.DOT_MATCHES_ALL).find(script) ?: return
            val method = m.groupValues[2]
            calls += Triple(method, m.groupValues[3], now())
            gates[method]?.await()
            val value = if (method == "media.state") {
                """{"paused":$mediaPaused,"muted":false,"level":1,"own":1,"time":10,"active":true}"""
            } else "null"
            messages.emit("""{"type":"result","id":${m.groupValues[1]},"ok":true,"value":$value}""")
        }

        suspend fun post(json: String) = messages.emit(json)
    }

    private val time = VirtualTime()
    private lateinit var scope: CoroutineScope
    private lateinit var host: FakeHost
    private lateinit var bridge: Bridge
    private lateinit var player: SpotifyPlayer

    @BeforeEach fun up() {
        AdSchedule.clear()
        scope = time.scope
        host = FakeHost()
        host.now = time::now
        bridge = Bridge(host, scope, time::now)
        player = SpotifyPlayer(bridge, scope)
    }

    @AfterEach fun down() {
        AdSchedule.clear()
        scope.cancel()
    }

    private fun track(name: String) =
        """{"kind":"track","uri":"spotify:track:$name","uid":"u$name","name":"$name","durationMs":200000}"""

    private suspend fun playing(positionMs: Long = 10_000) {
        host.post("""{"type":"ready"}""")
        time.waitFor("page ready", 1_000) { bridge.ready.value }
        host.post("""{"type":"state","state":{"track":${track("A")},"contextUri":"spotify:playlist:C","next":${track("B")},"isPaused":false,"isBuffering":false,"positionMs":$positionMs,"timestamp":${time.now()},"restrictions":{}}}""")
        time.waitFor("song state", 1_000) { player.state.value?.item?.uri == "spotify:track:A" }
        time.waitFor("media state", 2_000) { host.calls.any { it.first == "media.state" } }
        delay(20)
    }

    private fun called(method: String) = host.calls.count { it.first == method }

    @Test fun `pause is sent after its fade while the ramp answer is still pending`() = time.test {
        host.block("media.ramp")
        playing()
        val pausing = async { player.pause() }
        delay(SpotifyPlayer.PAUSE_MS + 20)
        assertEquals(1, called("media.ramp"))
        assertEquals(1, called("player.pause"), "pause waited for the ramp answer: ${host.calls.map { it.first }}")
        pausing.await()
        assertEquals(1, called("media.fade"))
        host.release("media.ramp")
    }

    @Test fun `next is sent after its fade hold while the fade answer is still pending`() = time.test {
        host.block("media.fade")
        playing()
        val skipping = async { player.next() }
        delay(SpotifyPlayer.SKIP_HOLD_MS + 20)
        assertEquals(1, called("media.fade"))
        assertEquals(1, called("player.next"), "next waited for the fade answer: ${host.calls.map { it.first }}")
        skipping.await()
        host.release("media.fade")
    }

    @Test fun `next during a crossfade is sent after its hold while the ramp answer is still pending`() = time.test {
        player.crossfadeSeconds = 6
        playing(positionMs = 195_000)
        time.waitFor("crossfade", 2_000) { called("media.fade") == 1 }
        host.block("media.ramp")
        val skipping = async { player.next() }
        delay(SpotifyPlayer.SKIP_HOLD_MS + 20)
        assertEquals(
            listOf("media.ramp", "media.rise", "player.next"),
            host.calls.filter { it.first in setOf("media.ramp", "media.rise", "player.next") }.map { it.first },
            "next waited for the crossfade ramp answer: ${host.calls.map { it.first }}",
        )
        skipping.await()
        host.release("media.ramp")
    }

    @Test fun `next keeps its fade hold when the page answers immediately`() = time.test {
        playing()
        val skipping = async { player.next() }
        delay(SpotifyPlayer.SKIP_HOLD_MS - 20)
        assertEquals(0, called("player.next"))
        delay(40)
        assertEquals(1, called("player.next"))
        skipping.await()
    }

    @Test fun `pause keeps its fade when the page answers immediately`() = time.test {
        playing()
        val pausing = async { player.pause() }
        delay(SpotifyPlayer.PAUSE_MS - 20)
        assertEquals(0, called("player.pause"))
        delay(40)
        assertEquals(1, called("player.pause"))
        pausing.await()
    }

    @Test fun `handoff requires paused state and a fresh paused element reading`() = time.test {
        playing()
        player.pause()
        assertEquals(1, called("player.pause"))
        assertEquals(false, player.outputPausedForHandoff(), "pause reply is not a paused state")
        host.post("""{"type":"state","state":{"track":${track("A")},"contextUri":"spotify:playlist:C","isPaused":true,"isBuffering":false,"positionMs":10160,"timestamp":${time.now()},"restrictions":{}}}""")
        time.waitFor("paused state", 1_000) { player.state.value?.isPaused == true }
        assertEquals(false, player.outputPausedForHandoff(), "the element is still playing")
        host.mediaPaused = true
        assertEquals(true, player.outputPausedForHandoff())
    }

    @Test fun `fallback rise keeps its grace after the next song is reported`() = time.test {
        playing()
        player.next()
        host.post("""{"type":"state","state":{"track":${track("B")},"contextUri":"spotify:playlist:C","next":null,"isPaused":false,"isBuffering":false,"positionMs":0,"timestamp":${time.now()},"restrictions":{}}}""")
        time.waitFor("next song", 1_000) { player.state.value?.item?.uri == "spotify:track:B" }
        delay(SpotifyPlayer.SWITCH_GRACE_MS - 20)
        assertEquals(0, called("media.rise"))
        delay(40)
        assertEquals(1, called("media.rise"))
    }
}
