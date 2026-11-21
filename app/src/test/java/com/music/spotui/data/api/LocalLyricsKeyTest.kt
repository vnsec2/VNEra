package com.music.spotui.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalLyricsKeyTest {
    private val studio = LyricsApi.Track("", "Song", "Artist", "Studio", 180000)

    @Test fun versionsDoNotShareLyrics() {
        assertNotEquals(studio.key, studio.copy(album = "Live").key)
        assertNotEquals(studio.key, studio.copy(durationMs = 240000).key)
        assertNotEquals(studio.copy(artist = "Artist, B").key, studio.copy(artist = "Artist, C").key)
    }

    @Test fun knownCatalogIdRemainsStable() {
        assertEquals("catalog-id", studio.copy(trackId = "catalog-id").key)
    }
}
