/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.di

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pause the system sends when our own mirrored Web Player page takes the audio focus must not stop the local player,
 * a pause asked later must still work.
 */
class MirrorPauseGuardTest {
    private val uri = "spotify:track:mirrored"

    @Test fun noPagePlayNoEcho() {
        assertFalse(MirrorPauseGuard().echoes(1_000))
    }

    @Test fun thePauseRightAfterThePagePlaysIsAnEcho() {
        val g = MirrorPauseGuard(windowMs = 1_000)
        g.pageState(uri, playing = true, nowMs = 10_000)
        assertTrue(g.echoes(10_000))
        assertTrue(g.echoes(10_037)) // the report: 37 ms after the page moved to the mirrored song
        assertTrue(g.echoes(10_999))
    }

    @Test fun aPauseLongAfterThePagePlayIsNotAnEcho() {
        val g = MirrorPauseGuard(windowMs = 1_000)
        g.pageState(uri, playing = true, nowMs = 10_000)
        assertFalse(g.echoes(11_001))
    }

    @Test fun aClockThatWentBackIsNotAnEcho() {
        val g = MirrorPauseGuard(windowMs = 1_000)
        g.pageState(uri, playing = true, nowMs = 10_000)
        assertFalse(g.echoes(9_999))
    }

    @Test fun readingTheSamePlayingStateAgainDoesNotRearmTheGuard() {
        val g = MirrorPauseGuard(windowMs = 1_000)
        g.pageState(uri, playing = true, nowMs = 10_000)
        // The app reads the page state every few seconds; that is not a new focus handover.
        g.pageState(uri, playing = true, nowMs = 13_000)
        assertFalse(g.echoes(13_500))
    }

    @Test fun aNewSongOrAResumeStartsANewWindow() {
        val g = MirrorPauseGuard(windowMs = 1_000)
        g.pageState(uri, playing = true, nowMs = 10_000)
        g.pageState("spotify:track:next", playing = true, nowMs = 20_000)
        assertFalse(g.echoes(21_001)) // the old song's window is over
        assertTrue(g.echoes(20_100))
        g.pageState("spotify:track:next", playing = false, nowMs = 22_000)
        assertFalse(g.echoes(22_100))
        g.pageState("spotify:track:next", playing = true, nowMs = 23_000)
        assertTrue(g.echoes(23_100))
    }

    @Test fun theMirrorEndClearsTheGuard() {
        val g = MirrorPauseGuard(windowMs = 1_000)
        g.pageState(uri, playing = true, nowMs = 10_000)
        g.cleared()
        assertFalse(g.echoes(10_050))
    }
}
