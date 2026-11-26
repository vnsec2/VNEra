package com.music.spotui.extensions

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpotifyFirstRouteTest {
    @Test fun spotifyDispatchDoesNotWaitForResolutionAndLocalWaitsForSilence() = runTest {
        val events = mutableListOf<Pair<String, Long>>()
        val spotifyPlaying = CompletableDeferred<Unit>()
        val spotifySilent = CompletableDeferred<Long>()
        backgroundScope.launch {
            spotifyFirstRoute(
                startSpotify = { events += "spotify" to testScheduler.currentTime },
                resolve = { delay(5_000); "preferred" },
                awaitPlaying = { spotifyPlaying.await(); true },
                handoff = { source -> events += source to testScheduler.currentTime; spotifySilent.await() },
                isCurrent = { true },
                startLocal = { _, position -> events += "local@$position" to testScheduler.currentTime },
            )
        }
        runCurrent()
        assertEquals(listOf("spotify" to 0L), events)
        testScheduler.advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf("spotify" to 0L), events)
        spotifyPlaying.complete(Unit)
        runCurrent()
        assertEquals("preferred" to 5_000L, events.last())
        assertEquals(2, events.size)
        testScheduler.advanceTimeBy(200)
        spotifySilent.complete(1_234L)
        runCurrent()
        assertEquals("local@1234" to 5_200L, events.last())
        assertEquals(3, events.size)
    }

    @Test fun supersededSameTrackRequestCannotPauseOrStartLocal() = runTest {
        val answer = CompletableDeferred<String>()
        var generation = 1
        var spotify = 0
        val job = launch {
            val request = generation
            spotifyFirstRoute(
                startSpotify = { spotify++ }, resolve = { answer.await() }, awaitPlaying = { true },
                handoff = { error("stale request paused Spotify") }, isCurrent = { generation == request },
                startLocal = { _, _ -> error("stale request started local playback") },
            )
        }
        runCurrent()
        generation++
        answer.complete("preferred")
        job.join()
        assertEquals(1, spotify)
    }

    @Test fun staleWhilePausingOrMissingPauseConfirmationNeverStartsLocal() = runTest {
        for (confirmed in listOf(null, 1_000L)) {
            var current = true
            var local = false
            spotifyFirstRoute(
                startSpotify = {}, resolve = { "preferred" }, awaitPlaying = { true },
                handoff = { if (confirmed != null) current = false; confirmed }, isCurrent = { current },
                startLocal = { _, _ -> local = true },
            )
            assertTrue(!local)
        }
    }

    @Test fun extensionMissKeepsSpotifyWithoutPausingIt() = runTest {
        var started = 0
        spotifyFirstRoute<String>(
            startSpotify = { started++ }, resolve = { delay(100); null }, awaitPlaying = { delay(8_000); false },
            handoff = { error("miss paused Spotify") }, isCurrent = { true },
            startLocal = { _, _ -> error("miss started local") },
        )
        assertEquals(1, started)
        assertEquals(100L, testScheduler.currentTime)
    }

    @Test fun missingStartupConfirmationNeverPausesSpotify() = runTest {
        spotifyFirstRoute(
            startSpotify = {}, resolve = { delay(2_000); "preferred" },
            awaitPlaying = { delay(500); false },
            handoff = { error("unconfirmed startup paused Spotify") }, isCurrent = { true },
            startLocal = { _, _ -> error("unconfirmed startup started local") },
        )
        assertEquals(2_000L, testScheduler.currentTime)
    }

    @Test fun fastLowerPriorityAnswerDoesNotTakeOverWhilePreferredSourceIsPending() = runTest {
        val handoffs = mutableListOf<Pair<String, Long>>()
        spotifyFirstRoute(
            startSpotify = { handoffs += "Spotify" to testScheduler.currentTime },
            resolve = {
                HookDispatch.audioSource(listOf("preferred", "second")) { source ->
                    delay(if (source == "preferred") 3_000 else 100)
                    source
                }?.second
            },
            awaitPlaying = { true }, handoff = { 1_000L }, isCurrent = { true },
            startLocal = { source, _ -> handoffs += source to testScheduler.currentTime },
        )
        assertEquals(listOf("Spotify" to 0L, "preferred" to 3_000L), handoffs)
    }
}
