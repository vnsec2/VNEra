package com.music.spotui.data.entity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsWordCoverageTest {
    @Test fun emptyAndWhitespaceTimingsKeepLineMode() {
        for (words in listOf(emptyList(), listOf(LyricWord(200, 400, " \t")))) {
            assertFalse(Lyrics(listOf(LyricLine(200, "Keep the original line", words)), synced = true).wordSynced)
        }
    }

    @Test fun mixedSongKeepsWordModeForItsTimedLine() {
        val timed = LyricLine(500, "One", listOf(LyricWord(500, 900, "One")))
        assertTrue(Lyrics(listOf(LyricLine(100, "Intro"), timed), synced = true).wordSynced)
    }
}
