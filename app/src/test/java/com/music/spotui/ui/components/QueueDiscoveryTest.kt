package com.music.spotui.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueDiscoveryTest {
    @Test
    fun selectFreshUrisDeduplicatesAndExcludesExistingTracks() {
        val candidates = listOf(
            "spotify:track:1",
            "spotify:track:2",
            "spotify:track:2",
            "spotify:episode:9",
            "spotify:track:3",
            "spotify:track:4",
        )
        val existing = setOf("spotify:track:1", "spotify:track:3")
        assertEquals(
            listOf("spotify:track:2", "spotify:track:4"),
            QueueDiscovery.selectFreshUris(candidates, existing, limit = 5),
        )
    }

    @Test
    fun buildQueryUsesSeedArtistOnlyForDiscoverMood() {
        assertEquals("Daft Punk mix", QueueDiscovery.buildQuery(QueueMood.DISCOVER, "Daft Punk, Pharrell"))
        assertEquals(QueueMood.CHILL.query, QueueDiscovery.buildQuery(QueueMood.CHILL, "Daft Punk"))
        assertEquals(QueueMood.DISCOVER.query, QueueDiscovery.buildQuery(QueueMood.DISCOVER, ""))
    }
}
