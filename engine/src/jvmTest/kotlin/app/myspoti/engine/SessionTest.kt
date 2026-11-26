package app.myspoti.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The playback session state machine against the sequences measured in the captures: which
 * state PUTs (debug_source) and melody messages go out, in which order, and only when the
 * player reported real playback.
 */
class SessionTest {
    private fun kotlinx.serialization.json.JsonObject.str(vararg path: String): String {
        var e: kotlinx.serialization.json.JsonElement = this
        path.forEach { e = e.jsonObject[it]!! }
        return e.jsonPrimitive.content
    }

    @Test
    fun play_to_the_end_then_advance() = runTest {
        val rig = Rig(backgroundScope)
        rig.start()
        assertEquals(listOf("apresolve", "devices", "hobs"), rig.http.requests.map {
            when { "apresolve" in it.url -> "apresolve"; it.url.endsWith("/devices") -> "devices"; else -> "hobs" }
        })

        // Server push after our play command: state 2, playing, from 0.
        rig.engine.handleDealerFrame(rig.replaceState(2, paused = false, seekTo = 0))
        assertEquals(listOf("before_track_load"), rig.http.debugSources())
        assertEquals(listOf("load ${rig.trackUriOf(2)} @0 play=true"), rig.player.calls)
        val btl = rig.http.statePuts()[0]
        assertEquals("2", btl.str("seq_num"))
        assertEquals("ChTdealer2false0", btl.str("state_ref", "state_machine_id"))
        assertEquals(rig.stateId(2), btl.str("state_ref", "state_id"))
        assertEquals("0", btl.str("sub_state", "playback_speed"))
        assertEquals(listOf(listOf("jssdk_playback_start")), rig.http.melodyTypes())

        // Nothing more is reported until the player says audio plays.
        rig.env.now += 5_000
        assertEquals(1, rig.http.statePuts().size)

        rig.player.licence!!.licence(Fixtures.bytes("licence-challenge.bin"))
        rig.engine.handlePlayerEvent(PlayerEvent.Loaded(153_889))
        rig.engine.handlePlayerEvent(PlayerEvent.Playing(0))
        rig.playFor(0, 1_000)
        assertEquals(listOf("before_track_load", "started_playing"), rig.http.debugSources())
        val started = rig.http.statePuts()[1]
        assertEquals("1", started.str("sub_state", "playback_speed"))
        assertEquals("1000", started.str("previous_position"))
        assertEquals("153889", started.str("sub_state", "duration"))
        // The answer of the first PUT is the machine the next one refers to.
        assertEquals("ChTresponse1", started.str("state_ref", "state_machine_id"))
        // Next track preloaded once playing.
        assertEquals("preload ${rig.trackUriOf(rig.advanceOf(2))}", rig.player.calls[1])

        rig.playFor(1_000, 29_500)
        assertEquals(2, rig.http.statePuts().size)
        rig.playFor(29_500, 30_000)
        assertEquals(listOf("before_track_load", "started_playing", "played_threshold_reached"), rig.http.debugSources())

        rig.playFor(30_000, 153_500)
        val resolvesBefore = rig.http.count("/storage-resolve/")
        rig.engine.handlePlayerEvent(PlayerEvent.Ended(153_889))
        assertEquals(
            listOf("before_track_load", "started_playing", "played_threshold_reached", "track_data_finalized", "before_track_load"),
            rig.http.debugSources(),
        )
        val puts = rig.http.statePuts()
        assertEquals((2..6).map { it.toString() }, puts.map { it.str("seq_num") })
        val fin = puts[3]
        assertEquals(rig.stateId(2), fin.str("state_ref", "state_id"))
        assertEquals("153889", fin.str("playback_stats", "ms_played_per_audio_format", "mp4a.40.2;128000"))
        val next = puts[4]
        assertEquals(rig.stateId(rig.advanceOf(2)), next.str("state_ref", "state_id"))
        assertEquals("false", next.str("state_ref", "paused"))
        assertEquals("0", next.str("sub_state", "position"))
        // previous_position is the last position the player reported, not the end (real1: 153699 of 153889).
        assertEquals("153500", next.str("previous_position"))
        assertEquals(fin.str("state_ref", "state_machine_id"), next.str("state_ref", "state_machine_id"))
        // Melody at the end: verification + stats in one batch (real1 order), then the next start.
        val types = rig.http.melodyTypes()
        assertTrue(types.contains(listOf("track_stream_verification", "jssdk_playback_stats")))
        assertEquals(listOf("jssdk_playback_start"), types.last())
        // 120 s of play flushed one media_playback_event_v2 timeline.
        assertTrue(types.contains(listOf("media_playback_event_v2")))
        // The preloaded file is loaded without resolving it again.
        assertEquals(resolvesBefore, rig.http.count("/storage-resolve/"))
        assertSame(rig.player.preloaded.single(), rig.player.loaded)
    }

    @Test
    fun natural_end_matches_the_firefox_end_of_track_capture() = runTest {
        // end-of-track (Firefox 159, 2026-11-10): paused session restored at 120752, resumed, played
        // to the natural end of a 150.2266 s file (the fixture seektable), then the next state.
        val rig = Rig(backgroundScope)
        rig.start()
        rig.engine.transfer()
        rig.engine.handleDealerFrame(rig.replaceState(2, paused = true, seekTo = 120_752))
        rig.engine.resume()
        rig.engine.handleDealerFrame(rig.replaceState(2, paused = false))
        rig.engine.handlePlayerEvent(PlayerEvent.Loaded(150_274))
        rig.engine.handlePlayerEvent(PlayerEvent.Playing(120_752))
        rig.playFor(120_752, 150_062)
        val before = rig.http.requests.size
        rig.engine.handlePlayerEvent(PlayerEvent.Ended(150_274))

        val fxFin = Fixtures.json("state-track-data-finalized-firefox.json")
        val fxNext = Fixtures.json("state-before-track-load-after-end.json")
        val fxMelody = Fixtures.json("melody-end-of-track-firefox.json")["messages"]!!.jsonArray.map { it.jsonObject }

        // Order as captured: melody verification + stats, finalized PUT, next before_track_load.
        val after = rig.http.requests.drop(before)
        assertTrue("/melody/v1/msg/batch" in after[0].url)
        assertEquals(listOf("track_stream_verification", "jssdk_playback_stats"),
            rig.http.json(after[0])["messages"]!!.jsonArray.map { it.jsonObject.str("type") })
        val fin = rig.http.json(after[1])
        val next = rig.http.json(after[2])
        assertEquals("track_data_finalized", fin.str("debug_source"))
        assertEquals("before_track_load", next.str("debug_source"))
        assertEquals(keyPaths(fxFin), keyPaths(fin))
        assertEquals(keyPaths(fxNext), keyPaths(next))

        // The capture's numbers: last reported position in the PUT, the file's duration, played time to the end.
        for (path in listOf(arrayOf("sub_state", "position"), arrayOf("previous_position"), arrayOf("sub_state", "duration"),
            arrayOf("sub_state", "playback_speed"), arrayOf("state_ref", "paused"),
            arrayOf("playback_stats", "ms_total_est"), arrayOf("playback_stats", "start_offset_ms"),
            arrayOf("playback_stats", "ms_played_per_audio_format", "mp4a.40.2;128000"))) {
            assertEquals(fxFin.str(*path), fin.str(*path), path.joinToString("."))
        }
        // Sent together: same machine as the finalized PUT, not the machine of its answer.
        assertEquals(fin.str("state_ref", "state_machine_id"), next.str("state_ref", "state_machine_id"))
        assertEquals(rig.stateId(rig.advanceOf(2)), next.str("state_ref", "state_id"))
        for (path in listOf(arrayOf("sub_state", "position"), arrayOf("sub_state", "playback_speed"), arrayOf("previous_position"), arrayOf("state_ref", "paused"))) {
            assertEquals(fxNext.str(*path), next.str(*path), path.joinToString("."))
        }

        val melody = rig.http.json(after[0])["messages"]!!.jsonArray.map { it.jsonObject }
        for ((i, keys) in listOf(
            0 to listOf("ms_played", "ms_nominal_played", "sequence_id", "playback_service"),
            1 to listOf("ms_played", "ms_file_duration", "ms_actual_duration", "ms_start_position", "ms_end_position",
                "reason_start", "reason_end", "initially_paused", "audio_format", "key_system"),
        )) {
            assertEquals(keyPaths(fxMelody[i]), keyPaths(melody[i]))
            keys.forEach { k -> assertEquals(fxMelody[i].str("message", k), melody[i].str("message", k), k) }
        }
        assertEquals(rig.stateId(rig.advanceOf(2)), melody[0].str("message", "next_playback_id"))

        // The next track's first audio flushes the timeline (+0.45 s in the capture) ...
        assertFalse(rig.http.melodyTypes().contains(listOf("media_playback_event_v2")))
        rig.engine.handlePlayerEvent(PlayerEvent.Playing(0))
        assertEquals(listOf("media_playback_event_v2"), rig.http.melodyTypes().last())
        // ... and the next report refers to the answer of before_track_load.
        rig.playFor(0, 1_000)
        val started = rig.http.statePuts().last()
        assertEquals("started_playing", started.str("debug_source"))
        assertTrue(started.str("state_ref", "state_machine_id") != next.str("state_ref", "state_machine_id"))
    }

    @Test
    fun loaded_paused_then_resumed_reports_started_playing_not_resume() = runTest {
        // The play-bodies sequence: transfer -> paused load at 60271 -> resume -> started_playing.
        val rig = Rig(backgroundScope)
        rig.start()
        rig.engine.transfer()
        rig.engine.handleDealerFrame(rig.replaceState(2, paused = true, seekTo = 60_271))
        val btl = rig.http.statePuts().single()
        assertEquals("true", btl.str("state_ref", "paused"))
        assertEquals("60271", btl.str("sub_state", "position"))
        assertTrue(rig.http.melodyTypes().contains(listOf("jssdk_connect_command")))
        assertEquals("load ${rig.trackUriOf(2)} @60271 play=false", rig.player.calls.single())

        rig.engine.resume()
        rig.engine.handleDealerFrame(rig.replaceState(2, paused = false))
        assertEquals("resume", rig.player.calls.last())
        rig.engine.handlePlayerEvent(PlayerEvent.Playing(60_271))
        rig.playFor(60_271, 61_337)
        assertEquals(listOf("before_track_load", "started_playing"), rig.http.debugSources())
        rig.playFor(61_337, 90_270)
        assertEquals(listOf("before_track_load", "started_playing"), rig.http.debugSources())
        rig.playFor(90_270, 90_271)
        // 30 s of audio from the start position (90514 in the capture, reported on a 200 ms tick).
        assertEquals(listOf("before_track_load", "started_playing", "played_threshold_reached"), rig.http.debugSources())
        assertEquals("90271", rig.http.statePuts().last().str("sub_state", "position"))
    }

    @Test
    fun pause_and_resume_are_reported_when_the_player_did_them() = runTest {
        val rig = playing(this)
        rig.engine.pause()
        assertTrue(rig.http.requests.last().url.contains("/player/command/from/${rig.engine.deviceId}/to/${rig.engine.deviceId}"))
        rig.engine.handleDealerFrame(rig.replaceState(2, paused = true))
        assertEquals("pause", rig.player.calls.last())
        assertFalse(rig.http.debugSources().contains("pause")) // not before the player confirms
        rig.engine.handlePlayerEvent(PlayerEvent.Paused(10_000))
        val pause = rig.http.statePuts().last()
        assertEquals("pause", pause.str("debug_source"))
        assertEquals("true", pause.str("state_ref", "paused"))
        assertEquals("0", pause.str("sub_state", "playback_speed"))
        assertEquals("10000", pause.str("sub_state", "position"))

        // Time paused is not played time.
        rig.env.now += 60_000
        rig.engine.handleDealerFrame(rig.replaceState(2, paused = false))
        assertEquals("resume", rig.player.calls.last())
        rig.engine.handlePlayerEvent(PlayerEvent.Playing(10_000))
        val resume = rig.http.statePuts().last()
        assertEquals("resume", resume.str("debug_source"))
        assertEquals("1", resume.str("sub_state", "playback_speed"))
        rig.playFor(10_000, 29_500)
        assertFalse(rig.http.debugSources().contains("played_threshold_reached"))
        rig.playFor(29_500, 30_000)
        assertTrue(rig.http.debugSources().contains("played_threshold_reached"))
    }

    @Test
    fun pause_confirmed_before_dealer_echo_is_reported_at_the_echo() = runTest {
        val rig = playing(this)
        rig.engine.pause()
        rig.engine.handlePlayerEvent(PlayerEvent.Paused(10_000))
        assertFalse(rig.http.debugSources().contains("pause"))

        rig.engine.handleDealerFrame(rig.replaceState(2, paused = true))

        assertEquals(listOf("before_track_load", "started_playing", "pause"), rig.http.debugSources())
        val pause = rig.http.statePuts().last()
        assertEquals("true", pause.str("state_ref", "paused"))
        assertEquals("0", pause.str("sub_state", "playback_speed"))
        assertEquals("10000", pause.str("sub_state", "position"))
    }

    @Test
    fun paused_confirmation_is_not_reused_after_playing_resumes() = runTest {
        val rig = playing(this)
        rig.engine.pause()
        rig.engine.handlePlayerEvent(PlayerEvent.Paused(10_000))
        rig.engine.handlePlayerEvent(PlayerEvent.Playing(10_000))

        rig.engine.handleDealerFrame(rig.replaceState(2, paused = true))
        assertFalse(rig.http.debugSources().contains("pause"))

        rig.engine.handlePlayerEvent(PlayerEvent.Paused(10_000))
        assertEquals("pause", rig.http.debugSources().last())
    }

    @Test
    fun pause_reaches_the_player_before_the_command_is_answered() = runTest {
        val rig = playing(this)
        val gate = CompletableDeferred<Unit>()
        rig.http.requestGates["/player/command/"] = gate
        val command = launch { rig.engine.pause() }
        runCurrent()
        try {
            assertEquals("pause", rig.player.calls.last())
            assertFalse(rig.http.debugSources().contains("pause"))
        } finally {
            gate.complete(Unit)
            command.join()
        }
        rig.engine.handleDealerFrame(rig.replaceState(2, paused = true))
        rig.engine.handlePlayerEvent(PlayerEvent.Paused(10_000))
        assertEquals("pause", rig.http.debugSources().last())
        assertFalse(rig.http.debugSources().contains("modify_current_state"))
    }

    @Test
    fun seek_reports_old_and_new_position() = runTest {
        val rig = playing(this)
        rig.engine.seekTo(121_190)
        val cmd = rig.http.json(rig.http.requests.last())["command"]!!.jsonObject
        assertEquals("seek_to", cmd.str("endpoint"))
        assertEquals("121190.0", cmd.str("value"))
        rig.engine.handleDealerFrame(rig.replaceState(2, paused = false, seekTo = 121_190))
        assertEquals("seek 121190", rig.player.calls.last())
        rig.engine.handlePlayerEvent(PlayerEvent.Seeked(121_190))
        val seek = rig.http.statePuts().last()
        assertEquals("seek", seek.str("debug_source"))
        assertEquals("121190", seek.str("sub_state", "position"))
        assertEquals("10000", seek.str("previous_position"))
        // A jump is not listening time: still under the 30 s threshold after the seek.
        rig.playFor(121_190, 125_000)
        assertFalse(rig.http.debugSources().contains("played_threshold_reached"))
    }

    @Test
    fun previous_seeks_to_zero_past_three_seconds() = runTest {
        val rig = playing(this)
        rig.engine.previous()
        val cmd = rig.http.json(rig.http.requests.last())["command"]!!.jsonObject
        assertEquals("seek_to", cmd.str("endpoint"))
        assertEquals("0.0", cmd.str("value"))
    }

    @Test
    fun skip_to_the_preloaded_next_track() = runTest {
        val rig = playing(this)
        val resolves = rig.http.count("/storage-resolve/")
        rig.engine.skipNext()
        val skipTo = rig.states[2].jsonObject["transitions"]!!.jsonObject["skip_next"]!!.jsonObject["state_index"]!!.jsonPrimitive.content.toInt()
        rig.engine.handleDealerFrame(rig.replaceState(skipTo, paused = false))
        assertEquals(listOf("before_track_load", "started_playing", "track_data_finalized", "before_track_load"), rig.http.debugSources())
        val fin = rig.http.statePuts()[2]
        assertEquals(rig.stateId(2), fin.str("state_ref", "state_id"))
        val stats = rig.http.requests.filter { "/melody/v1/msg/batch" in it.url }.map { rig.http.json(it) }
            .flatMap { it["messages"]!!.let { m -> (m as kotlinx.serialization.json.JsonArray).map { x -> x.jsonObject } } }
            .first { it.str("type") == "jssdk_playback_stats" }["message"]!!.jsonObject
        assertEquals("remote", stats.str("reason_end"))
        assertEquals("10000", stats.str("ms_played"))
        val verification = rig.http.requests.filter { "/melody/v1/msg/batch" in it.url }.map { rig.http.json(it) }
            .flatMap { (it["messages"] as kotlinx.serialization.json.JsonArray).map { x -> x.jsonObject } }
            .first { it.str("type") == "track_stream_verification" }["message"]!!.jsonObject
        assertEquals(rig.stateId(skipTo), verification.str("next_playback_id"))
        assertEquals(rig.stateId(skipTo), rig.http.statePuts()[3].str("state_ref", "state_id"))
        // No network round trip for the new track's media: it was preloaded.
        assertEquals(resolves, rig.http.count("/storage-resolve/"))
        assertSame(rig.player.preloaded.single(), rig.player.loaded)
        assertTrue(rig.player.calls.last().startsWith("load ${rig.trackUriOf(skipTo)} @0"))
    }

    @Test
    fun skip_loads_the_preloaded_track_before_telemetry_is_answered() = runTest {
        val rig = playing(this)
        val skipTo = rig.states[2].jsonObject["transitions"]!!.jsonObject["skip_next"]!!.jsonObject["state_index"]!!.jsonPrimitive.content.toInt()
        rig.engine.skipNext()
        val gates = listOf(CompletableDeferred<Unit>(), CompletableDeferred<Unit>(), CompletableDeferred<Unit>())
        rig.http.requestGates["/melody/v1/msg/batch"] = gates[0]
        rig.http.gates["track_data_finalized"] = gates[1]
        rig.http.gates["before_track_load"] = gates[2]
        val push = launch { rig.engine.handleDealerFrame(rig.replaceState(skipTo, paused = false)) }
        runCurrent()
        try {
            assertSame(rig.player.preloaded.single(), rig.player.loaded)
            assertTrue(rig.player.calls.last().startsWith("load ${rig.trackUriOf(skipTo)} @0"))
        } finally {
            gates.forEach { it.complete(Unit) }
            push.join()
        }
        assertEquals(listOf("before_track_load", "started_playing", "track_data_finalized", "before_track_load"), rig.http.debugSources())
        val stats = rig.http.requests.filter { "/melody/v1/msg/batch" in it.url }.map { rig.http.json(it) }
            .flatMap { it["messages"]!!.let { m -> (m as kotlinx.serialization.json.JsonArray).map { x -> x.jsonObject } } }
            .first { it.str("type") == "jssdk_playback_stats" }["message"]!!.jsonObject
        assertEquals("10000", stats.str("ms_played"))
    }

    @Test
    fun pause_after_skip_http_waits_for_its_dealer_echo() = runTest {
        val rig = playing(this)
        val skipTo = rig.states[2].jsonObject["transitions"]!!.jsonObject["skip_next"]!!.jsonObject["state_index"]!!.jsonPrimitive.content.toInt()
        rig.engine.skipNext()
        val command = launch { rig.engine.pause() }
        runCurrent()
        fun endpoints() = rig.http.requests.filter { "/player/command/" in it.url }
            .map { rig.http.json(it).str("command", "endpoint") }
        try {
            assertEquals("pause", rig.player.calls.last())
            assertEquals(listOf("skip_next"), endpoints())
        } finally {
            rig.engine.handleDealerFrame(rig.replaceState(skipTo, paused = false))
            command.join()
        }
        assertEquals(listOf("skip_next", "pause"), endpoints())
        assertEquals(rig.trackUriOf(skipTo), rig.player.loaded?.trackUri)
    }

    @Test
    fun transport_commands_wait_for_the_new_track_report_without_delaying_local_pause() = runTest {
        val actions = listOf<Pair<String, suspend (NativeEngine) -> Unit>>(
            "pause" to { it.pause() },
            "resume" to { it.resume() },
            "skip_next" to { it.skipNext() },
        )
        for ((endpoint, act) in actions) {
            val rig = playing(this)
            val skipTo = rig.states[2].jsonObject["transitions"]!!.jsonObject["skip_next"]!!.jsonObject["state_index"]!!.jsonPrimitive.content.toInt()
            rig.engine.skipNext()
            val gate = CompletableDeferred<Unit>()
            rig.http.gates["before_track_load"] = gate
            val push = launch { rig.engine.handleDealerFrame(rig.replaceState(skipTo, paused = false)) }
            runCurrent()
            val command = launch { act(rig.engine) }
            runCurrent()
            fun endpoints() = rig.http.requests.filter { "/player/command/" in it.url }
                .map { rig.http.json(it).str("command", "endpoint") }
            try {
                if (endpoint == "pause") assertEquals("pause", rig.player.calls.last())
                assertEquals(listOf("skip_next"), endpoints(), endpoint)
            } finally {
                gate.complete(Unit)
                push.join()
                command.join()
            }
            assertEquals(listOf("skip_next", endpoint), endpoints(), endpoint)
            assertEquals(rig.trackUriOf(skipTo), rig.player.loaded?.trackUri, endpoint)
        }
    }

    @Test
    fun local_playing_state_is_visible_while_load_reporting_is_blocked() = runTest {
        val rig = playing(this)
        val skipTo = rig.states[2].jsonObject["transitions"]!!.jsonObject["skip_next"]!!.jsonObject["state_index"]!!.jsonPrimitive.content.toInt()
        rig.engine.skipNext()
        val gate = CompletableDeferred<Unit>()
        rig.http.gates["before_track_load"] = gate
        val push = launch { rig.engine.handleDealerFrame(rig.replaceState(skipTo, paused = false)) }
        runCurrent()
        val event = launch { rig.engine.handlePlayerEvent(PlayerEvent.Playing(250)) }
        runCurrent()
        try {
            assertEquals(rig.trackUriOf(skipTo), rig.engine.now.value?.uri)
            assertEquals(true, rig.engine.now.value?.playing)
            assertEquals(false, rig.engine.now.value?.buffering)
            assertEquals(250L, rig.engine.now.value?.positionMs)
        } finally {
            gate.complete(Unit)
            push.join()
            event.join()
        }
    }

    @Test
    fun a_401_refreshes_the_token_once_then_surfaces() = runTest {
        val rig = Rig(backgroundScope)
        rig.start()
        rig.http.failNext("/storage-resolve/", 401)
        rig.engine.handleDealerFrame(rig.replaceState(2, paused = false, seekTo = 0))
        assertEquals(1, rig.tokens.refreshes)
        val resolves = rig.http.requests.filter { "/storage-resolve/" in it.url }
        assertEquals(listOf("Bearer test-token-1", "Bearer test-token-2"), resolves.map { it.headers["authorization"] })
        assertEquals(listOf("before_track_load"), rig.http.debugSources())

        // Twice in a row: the session is gone, the caller is told.
        rig.http.failNext("/state", 401, times = 2)
        rig.engine.handlePlayerEvent(PlayerEvent.Playing(0))
        val e = runCatching { rig.playFor(0, 1_000) }.exceptionOrNull()
        assertTrue(e is EngineException.Unauthorized, "$e")
        assertEquals(2, rig.tokens.refreshes) // refreshed once more before giving up
    }

    @Test
    fun a_429_waits_retry_after_then_surfaces() = runTest {
        val rig = Rig(backgroundScope)
        val events = ArrayList<EngineEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { rig.engine.events.collect { events += it } }
        rig.start()
        rig.http.failNext("/storage-resolve/", 429, mapOf("retry-after" to "2"))
        val job = launch { rig.engine.handleDealerFrame(rig.replaceState(2, paused = false, seekTo = 0)) }
        runCurrent()
        assertEquals(1, rig.http.count("/storage-resolve/"))
        assertTrue(rig.http.debugSources().isEmpty())
        advanceTimeBy(1_999); runCurrent()
        assertEquals(1, rig.http.count("/storage-resolve/"))
        advanceTimeBy(2); runCurrent()
        job.join()
        assertEquals(2, rig.http.count("/storage-resolve/"))
        assertEquals(listOf("before_track_load"), rig.http.debugSources())
        assertTrue(events.any { it is EngineEvent.RateLimited && it.retryAfterMs == 2_000L && it.attempt == 1 })

        // Without Retry-After: 1 s, 2 s, 4 s, then the error reaches the caller.
        rig.http.failNext("/melody/v1/msg/batch", 429, times = 4)
        var failure: Throwable? = null
        val failing = launch { failure = runCatching { rig.engine.handlePlayerEvent(PlayerEvent.Ended(0)) }.exceptionOrNull() }
        advanceTimeBy(7_001); runCurrent()
        failing.join()
        assertTrue(failure is EngineException.RateLimited, "$failure")
        assertEquals(listOf(1_000L, 2_000L, 4_000L), events.filterIsInstance<EngineEvent.RateLimited>().drop(1).map { it.retryAfterMs })
    }

    @Test
    fun repeated_ad_plays_the_next_music_track_once() = runTest {
        val rig = Rig(backgroundScope)
        rig.start()
        val raw = EngineJson.parseToJsonElement(rig.replaceState(2, paused = false)).jsonObject
        val payload = raw["payloads"]!!.jsonArray[0].jsonObject
        val sm = payload["state_machine"]!!.jsonObject
        val index = rig.states[2].jsonObject["track"]!!.jsonPrimitive.content.toInt()
        val tracks = sm["tracks"]!!.jsonArray.mapIndexed { i, t ->
            if (i == index) kotlinx.serialization.json.JsonObject(t.jsonObject + ("content_type" to kotlinx.serialization.json.JsonPrimitive("AD"))) else t
        }
        val patched = kotlinx.serialization.json.JsonObject(sm + ("tracks" to kotlinx.serialization.json.JsonArray(tracks)))
        val frame = kotlinx.serialization.json.JsonObject(raw + ("payloads" to kotlinx.serialization.json.JsonArray(listOf(
            kotlinx.serialization.json.JsonObject(payload + ("state_machine" to patched))
        )))).toString()
        val adOnly = rig.replaceState(2, paused = false).replace("\"content_type\":\"TRACK\"", "\"content_type\":\"AD\"")
        repeat(4) { rig.engine.handleDealerFrame(adOnly); runCurrent() }
        assertEquals(null, rig.player.loaded)
        repeat(4) { rig.engine.handleDealerFrame(frame); runCurrent() }
        val commands = rig.http.requests.filter { "/player/command/" in it.url }.map { rig.http.json(it).str("command", "endpoint") }
        assertEquals(listOf("skip_next"), commands)
        assertTrue(rig.player.loaded?.trackUri != null)
        assertTrue(rig.player.loaded?.trackUri != rig.trackUriOf(2))
    }

    @Test
    fun rotating_ad_ids_do_not_restart_the_recovery_budget() = runTest {
        val rig = Rig(backgroundScope)
        rig.start()
        val raw = rig.replaceState(2, paused = false, seekTo = 0)
            .replace("\"content_type\":\"TRACK\"", "\"content_type\":\"AD\"")
        repeat(12) { i ->
            val frame = raw.replace(rig.trackUriOf(2), "spotify:ad:rotating-$i")
            rig.engine.handleDealerFrame(frame)
            runCurrent()
        }
        assertEquals(1, rig.http.count("/player/command/"))
    }

    @Test
    fun repeated_ad_state_does_not_flood_skip_commands() = runTest {
        val rig = Rig(backgroundScope)
        rig.start()
        val frame = rig.replaceState(2, paused = false, seekTo = 0)
            .replace("\"content_type\":\"TRACK\"", "\"content_type\":\"AD\"")
        repeat(5) {
            rig.engine.handleDealerFrame(frame)
            runCurrent()
        }
        assertEquals(1, rig.http.count("/player/command/"))
    }

    @Test
    fun an_ad_is_neither_fetched_nor_reported() = runTest {
        val rig = Rig(backgroundScope)
        val events = ArrayList<EngineEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { rig.engine.events.collect { events += it } }
        rig.start()
        val before = rig.http.requests.size
        val frame = rig.replaceState(2, paused = false, seekTo = 0).replace("\"content_type\":\"TRACK\"", "\"content_type\":\"AD\"")
        rig.engine.handleDealerFrame(frame)
        runCurrent()
        // Nothing is asked for the ad and nothing reported of it: the one request is the command that passes it.
        assertEquals(before + 1, rig.http.requests.size)
        val sent = rig.http.requests.last()
        assertTrue("/connect-state/v1/player/command/" in sent.url)
        assertTrue("skip_next" in sent.body!!.decodeToString())
        assertEquals(listOf("stop"), rig.player.calls)
        assertTrue(events.any { it is EngineEvent.AdBreak })
    }

    @Test
    fun clear_state_finalizes_and_reports_state_clear() = runTest {
        val rig = playing(this)
        rig.engine.handleDealerFrame(rig.clearState())
        assertEquals(listOf("before_track_load", "started_playing", "track_data_finalized", "state_clear"), rig.http.debugSources())
        assertEquals("stop", rig.player.calls.last())
    }

    @Test
    fun close_without_a_next_track_omits_the_next_playback_link() = runTest {
        val rig = playing(this)
        rig.engine.close()

        val verification = rig.http.requests.filter { "/melody/v1/msg/batch" in it.url }.map { rig.http.json(it) }
            .flatMap { it["messages"]!!.jsonArray.map { m -> m.jsonObject } }
            .first { it.str("type") == "track_stream_verification" }["message"]!!.jsonObject
        assertFalse("next_playback_id" in verification, "terminal verification must not contain JSON null: $verification")
        assertEquals("endplay", rig.http.requests.filter { "/melody/v1/msg/batch" in it.url }.map { rig.http.json(it) }
            .flatMap { it["messages"]!!.jsonArray.map { m -> m.jsonObject } }
            .first { it.str("type") == "jssdk_playback_stats" }.str("message", "reason_end"))
    }

    @Test
    fun state_put_400_during_player_event_does_not_crash_background_loop() = runTest {
        val rig = Rig(backgroundScope)
        val events = ArrayList<EngineEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { rig.engine.events.collect { events += it } }
        rig.start()
        rig.engine.handleDealerFrame(rig.replaceState(2, paused = false, seekTo = 0))
        rig.http.failNext("/track-playback/v1/devices/", 400)
        rig.player.onEvent!!(PlayerEvent.Playing(0))
        rig.env.now += 1_100
        rig.player.onEvent!!(PlayerEvent.Position(1_100))
        runCurrent()
        assertTrue(events.any { it is EngineEvent.Failure && "400" in it.message })
        assertTrue(events.any { it is EngineEvent.Preloaded })
    }

    @Test
    fun skip_to_sends_skip_next_with_target_track() = runTest {
        val rig = playing(this)
        val push = launch {
            kotlinx.coroutines.yield()
            rig.engine.handleDealerFrame(rig.replaceState(3, paused = false, seekTo = 0))
        }
        rig.engine.skipTo("spotify:track:target123", "uid456")
        push.join()
        val cmd = rig.http.requests.last { "/player/command/" in it.url }
        val body = rig.http.json(cmd)["command"]!!.jsonObject
        assertEquals("skip_next", body.str("endpoint"))
        assertEquals("spotify:track:target123", body.str("track", "uri"))
        assertEquals("uid456", body.str("track", "uid"))
    }

    @Test
    fun skip_next_automatically_hops_over_adjacent_duplicate_release() = runTest {
        val rig = playing(this)
        val firstUri = rig.trackUriOf(2)
        val firstTrackObj = rig.http.machine["tracks"]!!.jsonArray[rig.states[2].jsonObject["track"]!!.jsonPrimitive.content.toInt()].jsonObject
        val firstName = firstTrackObj.str("metadata", "name")
        val firstDur = firstTrackObj["metadata"]!!.jsonObject["duration"]!!
        val nextStateIdx = rig.states[2].jsonObject["transitions"]!!.jsonObject["skip_next"]!!.jsonObject["state_index"]!!.jsonPrimitive.content.toInt()
        val nextTrackIdx = rig.states[nextStateIdx].jsonObject["track"]!!.jsonPrimitive.content.toInt()

        val dupRaw = EngineJson.parseToJsonElement(rig.replaceState(nextStateIdx, paused = false, seekTo = 0)).jsonObject
        val dupPayload = dupRaw["payloads"]!!.jsonArray[0].jsonObject
        val dupSm = dupPayload["state_machine"]!!.jsonObject
        val dupTracks = dupSm["tracks"]!!.jsonArray.mapIndexed { i, t ->
            if (i != nextTrackIdx) t else {
                val meta = t.jsonObject["metadata"]!!.jsonObject
                val patchedMeta = kotlinx.serialization.json.JsonObject(
                    meta + ("uri" to kotlinx.serialization.json.JsonPrimitive("spotify:track:dupRelease999")) +
                        ("name" to kotlinx.serialization.json.JsonPrimitive(firstName)) +
                        ("duration" to firstDur),
                )
                kotlinx.serialization.json.JsonObject(t.jsonObject + ("metadata" to patchedMeta))
            }
        }
        val dupFrame = kotlinx.serialization.json.JsonObject(
            dupRaw + ("payloads" to kotlinx.serialization.json.JsonArray(listOf(
                kotlinx.serialization.json.JsonObject(
                    dupPayload + ("state_machine" to kotlinx.serialization.json.JsonObject(dupSm + ("tracks" to kotlinx.serialization.json.JsonArray(dupTracks)))),
                ),
            ))),
        ).toString()

        val thirdIdx = rig.advanceOf(2)
        val push = launch {
            kotlinx.coroutines.yield()
            rig.engine.handleDealerFrame(dupFrame)
            kotlinx.coroutines.yield()
            rig.engine.handleDealerFrame(rig.replaceState(thirdIdx, paused = false, seekTo = 0))
        }
        rig.engine.skipNext()
        push.join()
        runCurrent()

        assertFalse(rig.player.calls.any { "dupRelease999" in it }, "duplicate release must not load: ${rig.player.calls}")
        assertEquals(rig.trackUriOf(thirdIdx), rig.player.loaded?.trackUri)
        assertTrue(rig.engine.now.value?.uri != firstUri && rig.engine.now.value?.uri != "spotify:track:dupRelease999")
    }

    @Test
    fun set_speed_applies_to_episodes_and_resets_to_one_on_music_tracks() = runTest {
        val rig = Rig(backgroundScope)
        rig.start()
        val raw = Fixtures.json("dealer-replace-state.json")
        val payload = raw["payloads"]!!.jsonArray[0].jsonObject
        val sm = payload["state_machine"]!!.jsonObject
        val epTrackIdx = rig.states[2].jsonObject["track"]!!.jsonPrimitive.content.toInt()
        val tracks = sm["tracks"]!!.jsonArray.mapIndexed { idx, t ->
            if (idx != epTrackIdx) t else {
                val meta = t.jsonObject["metadata"]!!.jsonObject
                val epMeta = kotlinx.serialization.json.JsonObject(
                    meta + ("uri" to kotlinx.serialization.json.JsonPrimitive("spotify:episode:ep123")),
                )
                kotlinx.serialization.json.JsonObject(
                    t.jsonObject + ("content_type" to kotlinx.serialization.json.JsonPrimitive("EPISODE")) + ("metadata" to epMeta),
                )
            }
        }
        val epFrame = kotlinx.serialization.json.JsonObject(
            raw + ("payloads" to kotlinx.serialization.json.JsonArray(listOf(
                kotlinx.serialization.json.JsonObject(
                    payload + mapOf(
                        "state_machine" to kotlinx.serialization.json.JsonObject(
                            sm + ("state_machine_id" to kotlinx.serialization.json.JsonPrimitive("ChTep1")) + ("tracks" to kotlinx.serialization.json.JsonArray(tracks)),
                        ),
                        "state_ref" to kotlinx.serialization.json.JsonObject(mapOf(
                            "state_index" to kotlinx.serialization.json.JsonPrimitive(2),
                            "paused" to kotlinx.serialization.json.JsonPrimitive(false),
                            "active_alias" to kotlinx.serialization.json.JsonNull,
                        )),
                    ),
                ),
            ))),
        ).toString()
        rig.engine.handleDealerFrame(epFrame)
        rig.engine.handlePlayerEvent(PlayerEvent.Playing(0))
        rig.engine.setSpeed(1.5)
        assertEquals(1.5, rig.player.lastSpeed, 0.001)
        assertEquals(1.5, rig.engine.now.value?.speed ?: 0.0, 0.001)

        rig.engine.handleDealerFrame(rig.replaceState(rig.advanceOf(2), paused = false, seekTo = 0))
        assertEquals(1.0, rig.player.lastSpeed, 0.001)
        assertEquals(1.0, rig.engine.now.value?.speed ?: 0.0, 0.001)
    }

    @Test
    fun unacknowledged_skip_command_unlocks_after_timeout() = runTest {
        val rig = playing(this)
        rig.engine.skipNext()
        advanceTimeBy(3_000)
        runCurrent()
        rig.engine.skipNext()
        val skipPosts = rig.http.requests.count { "/player/command/" in it.url }
        assertEquals(2, skipPosts)
    }

    /** A rig playing state 2 from 0, 10 s in (started_playing sent, next track preloaded). */
    private suspend fun playing(scope: kotlinx.coroutines.test.TestScope): Rig {
        val rig = Rig(scope.backgroundScope)
        rig.start()
        rig.engine.handleDealerFrame(rig.replaceState(2, paused = false, seekTo = 0))
        rig.engine.handlePlayerEvent(PlayerEvent.Playing(0))
        rig.playFor(0, 10_000)
        return rig
    }
}
