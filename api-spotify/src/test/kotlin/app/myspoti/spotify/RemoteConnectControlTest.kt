package app.myspoti.spotify

import app.myspoti.spotify.bridge.AdSchedule
import app.myspoti.spotify.bridge.Bridge
import app.myspoti.spotify.bridge.BridgeHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class RemoteConnectControlTest {
    private class FakeHost : BridgeHost {
        override val messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
        val calls = CopyOnWriteArrayList<String>()

        override suspend fun evaluate(script: String) {
            val m = Regex("""Myspoti\.call\((\d+), "([^"]+)", (.*)\)\.catch""", RegexOption.DOT_MATCHES_ALL).find(script) ?: return
            val method = m.groupValues[2]
            calls += method
            val value = if (method == "media.state") {
                """{"paused":true,"muted":false,"level":1,"own":1,"time":0,"active":false}"""
            } else "null"
            messages.emit("""{"type":"result","id":${m.groupValues[1]},"ok":true,"value":$value}""")
        }

        suspend fun post(json: String) = messages.emit(json)
    }

    private class FakeAccountAudio : AccountAudio {
        override val on: Boolean = true
        override val deviceId: String = "local-native-device"
        override var holdsPlayback: Boolean = false
        val localCalls = CopyOnWriteArrayList<String>()

        override suspend fun play(contextUri: String, trackUri: String?, index: Int?, positionMs: Long?, order: List<String>?, shuffle: Boolean?) {
            localCalls += "play"
        }

        override suspend fun pause() { localCalls += "pause" }
        override suspend fun resume() { localCalls += "resume" }
        override suspend fun seek(positionMs: Long) { localCalls += "seek" }
        override suspend fun next() { localCalls += "next" }
        override suspend fun previous() { localCalls += "previous" }
    }

    private val time = VirtualTime()
    private lateinit var scope: CoroutineScope
    private lateinit var host: FakeHost
    private lateinit var bridge: Bridge
    private lateinit var audio: FakeAccountAudio
    private lateinit var player: SpotifyPlayer

    @BeforeEach
    fun up() {
        AdSchedule.clear()
        scope = time.scope
        host = FakeHost()
        bridge = Bridge(host, scope, time::now)
        audio = FakeAccountAudio()
        player = SpotifyPlayer(bridge, scope)
        player.audio = audio
    }

    @AfterEach
    fun down() {
        AdSchedule.clear()
        scope.cancel()
    }

    private suspend fun readyWithTrack(paused: Boolean = false) {
        host.post("""{"type":"ready"}""")
        time.waitFor("page ready", 1_000) { bridge.ready.value }
        val track = """{"kind":"track","uri":"spotify:track:A","uid":"uA","name":"A","durationMs":200000}"""
        val next = """{"kind":"track","uri":"spotify:track:B","uid":"uB","name":"B","durationMs":200000}"""
        host.post("""{"type":"state","state":{"track":$track,"contextUri":"spotify:playlist:C","next":$next,"isPaused":$paused,"isBuffering":false,"positionMs":10000,"timestamp":${time.now()},"restrictions":{}}}""")
        time.waitFor("song state", 1_000) { player.state.value?.item?.uri == "spotify:track:A" }
        delay(20)
    }

    @Test
    fun `remote Connect control routes playback commands to page instead of local native engine`() = time.test {
        readyWithTrack(paused = false)
        player.remoteControl = true

        player.next()
        player.previous()
        player.seek(25_000L)
        player.pause()
        player.resume()

        assertEquals(emptyList<String>(), audio.localCalls)
        assertEquals(1, host.calls.count { it == "player.next" })
        assertEquals(1, host.calls.count { it == "player.previous" })
        assertEquals(1, host.calls.count { it == "player.seek" })
        assertEquals(1, host.calls.count { it == "player.pause" })
        assertEquals(1, host.calls.count { it == "player.resume" })
    }

    @Test
    fun `local playback still routes to native engine when not controlling remote device`() = time.test {
        readyWithTrack(paused = false)
        audio.holdsPlayback = true
        player.remoteControl = false

        player.next()
        player.pause()
        player.resume()
        player.seek(12_000L)
        player.previous()

        assertEquals(listOf("next", "pause", "resume", "seek", "previous"), audio.localCalls)
        assertEquals(0, host.calls.count { it.startsWith("player.") })
    }
}
