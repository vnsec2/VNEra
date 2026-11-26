package com.music.spotui.extensions

import app.myspoti.extensions.PlaybackEvent
import app.myspoti.extensions.TrackInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** What episodes look like to the extensions (rich presence and the `track` event). */
class TrackShapeTest {
    @Test fun aSongKeepsItsTrackUri() {
        val t = TrackInfo("t1", "spotify:track:t1", "Title", "Artist", listOf("Artist"), "Album", 100_000, kind = "track")
        assertEquals("track", t.kind)
        assertEquals("t1", t.id)
    }

    @Test fun anEpisodeIsNotASong() {
        val t = TrackInfo("e1", "spotify:episode:e1", "Chapter", "The Show", listOf("The Show"), "The Book", 100_000, kind = "episode")
        assertEquals("episode", t.kind)
        assertEquals("spotify:episode:e1", t.uri)
        assertEquals("e1", t.id)
    }

    @Test fun aPlaybackEventCarriesTheKind() {
        val track = TrackInfo("e1", "spotify:episode:e1", "Chapter", "The Show", listOf("The Show"), "The Book", 100_000, kind = "episode")
        val json = PlaybackEvent.trackEventJson(track, PlaybackEvent.ORIGIN_SPOTIFY, null)
        assert(json.contains("\"kind\":\"episode\""))
        assert(json.contains("\"uri\":\"spotify:episode:e1\""))
    }
}
