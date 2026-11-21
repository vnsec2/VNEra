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
    fun dominantArtistsRanksByFrequencyAcrossQueue() {
        val singers = listOf(
            "Artist A, Artist B",
            "Artist C",
            "Artist B",
            "Artist B, Artist D",
            "Artist A",
        )
        assertEquals(listOf("Artist B", "Artist A"), QueueDiscovery.dominantArtists(singers, limit = 2))
    }

    @Test
    fun buildQueryUsesQueueSeedArtistsForDiscoverSimilarAndFamiliar() {
        assertEquals(
            "Daft Punk Justice mix",
            QueueDiscovery.buildQuery(QueueMood.DISCOVER, listOf("Daft Punk", "Justice")),
        )
        assertEquals(
            "Daft Punk essentials",
            QueueDiscovery.buildQuery(QueueMood.FAMILIAR, listOf("Daft Punk")),
        )
        assertEquals(QueueMood.CHILL.query, QueueDiscovery.buildQuery(QueueMood.CHILL, listOf("Daft Punk")))
        assertEquals(QueueMood.DISCOVER.query, QueueDiscovery.buildQuery(QueueMood.DISCOVER, ""))
    }

    @Test
    fun buildSaveTrackIdsRespectsManualOnlyAndCurrentFlags() {
        assertEquals(
            listOf("q1", "q2"),
            QueueDiscovery.buildSaveTrackIds("c1", listOf("q1", "q2", "q1"), listOf("n1", "n2"), includeCurrent = false, includeNextUp = false),
        )
        assertEquals(
            listOf("c1", "q1", "n1"),
            QueueDiscovery.buildSaveTrackIds("c1", listOf("q1"), listOf("n1"), includeCurrent = true, includeNextUp = true),
        )
    }
}
