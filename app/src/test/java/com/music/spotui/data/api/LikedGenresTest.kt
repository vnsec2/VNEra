package com.music.spotui.data.api

import com.music.spotui.data.entity.SongsModel
import org.junit.Assert.assertEquals
import org.junit.Test

class LikedGenresTest {
    private fun song(id: String, artists: List<String>) = SongsModel(
        id = id.hashCode(), title = id, album = "", singer = "", coverUri = "", url = "spotify:track:$id",
        spotifyTrackId = id, artistIds = artists,
    )

    @Test fun artistCanAppearInMoreThanOneGenre() {
        assertEquals(setOf(LikedGenre.ROCK, LikedGenre.INDIE), LikedGenre.fromNames(listOf("indie rock")))
        assertEquals(setOf(LikedGenre.HIP_HOP, LikedGenre.R_AND_B), LikedGenre.fromNames(listOf("hip hop", "neo soul")))
    }

    @Test fun filterUsesAnyArtistAndLeavesAllOrderUntouched() {
        val tracks = listOf(
            song("first", listOf("spotify:artist:main", "spotify:artist:guest")),
            song("second", listOf("other")),
            song("third", emptyList()),
        )
        val genres = mapOf("guest" to setOf(LikedGenre.JAZZ), "other" to setOf(LikedGenre.POP))
        assertEquals(listOf("first"), LikedGenres.filter(tracks, LikedGenre.JAZZ, genres).map { it.title })
        assertEquals(tracks, LikedGenres.filter(tracks, null, genres))
    }
}
