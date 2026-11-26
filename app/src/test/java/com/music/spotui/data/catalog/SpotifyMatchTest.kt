/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.data.catalog

import app.myspoti.api.Track
import app.myspoti.api.Album
import app.myspoti.api.Artist
import app.myspoti.api.ArtistRef
import app.myspoti.api.Page
import app.myspoti.catalog.Hit
import app.myspoti.catalog.MetadataProvider
import app.myspoti.catalog.ProviderChain
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reported: a search's song of another provider said "Not found on Spotify" when tapped, and
 * played when tapped again. A lookup that got no answer is tried again, and never said absent.
 */
class SpotifyMatchTest {
    private val found = SpotifyMatch.Found("S1", Track("spotify:track:S1", "Song", 200_000, emptyList(), null))

    @Test fun aLookupWithoutAnswerIsTriedAgainUntilItAnswers() = runBlocking {
        val answers = ArrayDeque(listOf<SpotifyMatch>(SpotifyMatch.Unavailable("timed out"), SpotifyMatch.Unavailable("resting"), found))
        var asked = 0
        val r = SpotifyMatch.withRetries(longArrayOf(1, 1)) { asked++; answers.removeFirst() }
        assertEquals(found, r)
        assertEquals(3, asked)
    }

    @Test fun anAnswerIsNotAskedAgain() = runBlocking {
        var asked = 0
        assertEquals(SpotifyMatch.Absent, SpotifyMatch.withRetries(longArrayOf(1, 1)) { asked++; SpotifyMatch.Absent })
        assertEquals(1, asked)
    }

    @Test fun noAnswerAtAllSaysSpotifyDidNotAnswerNotThatTheSongIsMissing() = runBlocking {
        val r = SpotifyMatch.withRetries(longArrayOf(1, 1)) { SpotifyMatch.Unavailable("network") }
        assertTrue(r is SpotifyMatch.Unavailable)
        assertEquals(app.myspoti.engine.R.string.metadata_spotify_no_answer, SpotifyMatch.message(r))
        assertEquals(app.myspoti.engine.R.string.metadata_not_on_spotify, SpotifyMatch.message(SpotifyMatch.Absent))
        assertNull(SpotifyMatch.message(found))
    }

    private val sourceTrack = Track("deezer:track:1", "Song", 200_000, listOf(ArtistRef("deezer:artist:1", "Band")), null)
    private val spotifyTrack = sourceTrack.copy(uri = "spotify:track:S1")

    private class Provider(override val id: String, val hit: Hit) : MetadataProvider {
        var reads = 0
        var searches = 0
        var failure: Exception? = null
        var answers = listOf(hit)
        override suspend fun track(id: String): Hit { reads++; failure?.let { throw it }; return hit }
        override suspend fun byIsrc(isrc: String): List<Hit> { searches++; failure?.let { throw it }; return answers.filter { it.isrc == isrc } }
        override suspend fun find(title: String, artist: String, limit: Int): List<Hit> { searches++; failure?.let { throw it }; return answers }
        override suspend fun searchTracks(query: String, offset: Int, limit: Int) = Page(answers, offset, answers.size, null)
        override suspend fun searchAlbums(query: String, offset: Int, limit: Int) = Page<Album>(emptyList(), offset, 0, null)
        override suspend fun searchArtists(query: String, offset: Int, limit: Int) = Page<Artist>(emptyList(), offset, 0, null)
        override suspend fun album(id: String): Album = error("unused")
        override suspend fun albumTracks(id: String, offset: Int, limit: Int) = Page<Hit>(emptyList(), offset, 0, null)
        override suspend fun artist(id: String): Artist = error("unused")
        override suspend fun topTracks(id: String) = emptyList<Hit>()
        override suspend fun discography(id: String, offset: Int, limit: Int) = Page<Album>(emptyList(), offset, 0, null)
        override suspend fun related(id: String) = emptyList<Artist>()
    }

    @Test fun spotifyDisabledForBrowsingStillResolvesDeezerForPlayback() = runBlocking {
        val deezer = Provider("deezer", Hit(sourceTrack, "GBDUW0000053"))
        val spotify = Provider("spotify", Hit(spotifyTrack, "GBDUW0000053"))
        val browsing = ProviderChain(listOf(deezer))
        val result = SpotifyMatch.lookup(sourceTrack.uri, browsing, ProviderChain(listOf(spotify)))
        assertEquals(SpotifyMatch.Found("S1", sourceTrack), result)
        assertEquals(listOf("deezer"), browsing.ids)
        assertEquals(1, spotify.searches)
    }

    @Test fun recordingWithoutIsrcUsesTitleArtistAndDuration() = runBlocking {
        val deezer = Provider("deezer", Hit(sourceTrack))
        val spotify = Provider("spotify", Hit(spotifyTrack))
        assertEquals(SpotifyMatch.Found("S1", sourceTrack), SpotifyMatch.lookup(sourceTrack.uri, ProviderChain(listOf(deezer)), ProviderChain(listOf(spotify))))
    }

    @Test fun signedOutAccountIsNotTreatedAsAMissingRecording() = runBlocking {
        val deezer = Provider("deezer", Hit(sourceTrack))
        assertEquals(SpotifyMatch.NotAsked, SpotifyMatch.lookup(sourceTrack.uri, ProviderChain(listOf(deezer)), null))
        assertEquals(0, deezer.reads)
        assertEquals(app.myspoti.engine.R.string.not_signed_in, SpotifyMatch.message(SpotifyMatch.NotAsked))
    }

    @Test fun spotifyFailureIsUnavailableButAnAnsweredMissIsAbsent() = runBlocking {
        val browsing = ProviderChain(listOf(Provider("deezer", Hit(sourceTrack))))
        val spotify = Provider("spotify", Hit(spotifyTrack))
        spotify.failure = IOException("offline")
        assertTrue(SpotifyMatch.lookup(sourceTrack.uri, browsing, ProviderChain(listOf(spotify))) is SpotifyMatch.Unavailable)
        spotify.failure = null
        spotify.answers = emptyList()
        assertEquals(SpotifyMatch.Absent, SpotifyMatch.lookup(sourceTrack.uri, browsing, ProviderChain(listOf(spotify))))
        spotify.answers = listOf(Hit(spotifyTrack.copy(name = "A completely different song", artists = listOf(ArtistRef("spotify:artist:other", "Someone else")))) )
        assertEquals(SpotifyMatch.Absent, SpotifyMatch.lookup(sourceTrack.uri, browsing, ProviderChain(listOf(spotify))))
    }

    @Test(expected = CancellationException::class) fun cancelledCatalogLookupDoesNotBecomeAMissingRecording(): Unit = runBlocking {
        val deezer = Provider("deezer", Hit(sourceTrack)).apply { failure = CancellationException("new selection") }
        SpotifyMatch.lookup(sourceTrack.uri, ProviderChain(listOf(deezer)), ProviderChain(listOf(Provider("spotify", Hit(spotifyTrack)))))
        Unit
    }
}
