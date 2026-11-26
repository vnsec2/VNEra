package com.music.spotui.di

import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.local.LocalFileMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MixedPlaylistQueueTest {
    private fun spotifySong(id: Int, trackId: String, playable: Boolean = true) = SongsModel(
        id = id,
        title = "Spotify $id",
        album = "Album",
        singer = "Artist",
        coverUri = "",
        url = "spotify:track:$trackId",
        spotifyTrackId = trackId,
        playable = playable,
    )

    private fun localSong(id: Int) = SongsModel(
        id = id,
        title = "Local $id",
        album = "Local",
        singer = "Local Artist",
        coverUri = "",
        url = "${LocalFileMatch.DEVICE_PREFIX}content://media/external/audio/media/$id",
        spotifyTrackId = "",
        playable = true,
    )

    private fun unmatchedLocal(id: Int) = SongsModel(
        id = id,
        title = "Missing $id",
        album = "",
        singer = "",
        coverUri = "",
        url = "${LocalFileMatch.SPOTIFY_PREFIX}Artist:Album:Title:180",
        spotifyTrackId = "",
        playable = false,
    )

    @Test
    fun detectsMixedPlaylistsAccurately() {
        val sp1 = spotifySong(1, "4cOdK2wGLETKBW3PvgPWqT")
        val spUnplayable = spotifySong(10, "3n3Ppam7vgaVa1iaRUc9Lp", playable = false)
        val loc1 = localSong(2)
        val missing = unmatchedLocal(3)

        assertTrue(MixedQueuePlan.isMixed(listOf(sp1, loc1)))
        assertTrue(MixedQueuePlan.isMixed(listOf(loc1, sp1, missing)))
        assertFalse(MixedQueuePlan.isMixed(emptyList()))
        assertFalse(MixedQueuePlan.isMixed(listOf(sp1)))
        assertFalse(MixedQueuePlan.isMixed(listOf(loc1)))
        assertFalse(MixedQueuePlan.isMixed(listOf(sp1, missing)))
        assertFalse(MixedQueuePlan.isMixed(listOf(loc1, spUnplayable)))
        assertFalse(MixedQueuePlan.isMixed(listOf(loc1, spUnplayable, missing)))
    }

    @Test
    fun buildsContiguousSpotifyRunsAndKeepsLocalFilesInUpcomingQueue() {
        val sp1 = spotifySong(1, "4cOdK2wGLETKBW3PvgPWqT")
        val sp2 = spotifySong(2, "3n3Ppam7vgaVa1iaRUc9Lp")
        val loc1 = localSong(3)
        val sp3 = spotifySong(4, "7ouMYWpwJ422jRcDASZB7P")
        val missing = unmatchedLocal(5)
        val unplayable = spotifySong(6, "0VjIjW4GlUZAMYd2vXMi3b", playable = false)

        val entries = MixedQueuePlan.playableEntries(listOf(sp1, sp2, loc1, sp3, missing, unplayable))
        assertEquals(listOf(sp1, sp2, loc1, sp3), entries)

        val plan = MixedQueuePlan("spotify:playlist:mix", "Mix", entries, 0)
        assertEquals(listOf(sp1, sp2), plan.spotifyRunFrom(0))
        assertEquals(listOf(sp2, loc1, sp3), plan.upcoming())
        assertFalse(plan.isLastInSpotifyRun())

        assertTrue(plan.syncToSpotifyTrackId("3n3Ppam7vgaVa1iaRUc9Lp"))
        assertEquals(1, plan.index)
        assertTrue(plan.isLastInSpotifyRun())
        assertEquals(listOf(loc1, sp3), plan.upcoming())

        val next = plan.stepForward(repeatMode = 0)
        assertEquals(loc1, next)
        assertEquals(listOf(sp3), plan.upcoming())

        val afterLocal = plan.stepForward(repeatMode = 0)
        assertEquals(sp3, afterLocal)
        assertEquals(listOf(sp3), plan.spotifyRunFrom())
        assertTrue(plan.isLastInSpotifyRun())
        assertNull(plan.stepForward(repeatMode = 0))
    }

    @Test
    fun doesNotJumpAcrossLocalBoundaryWhenDuplicateSpotifyTrackAppearsLater() {
        val sp1 = spotifySong(1, "4cOdK2wGLETKBW3PvgPWqT")
        val loc1 = localSong(2)
        val sp1Again = spotifySong(3, "4cOdK2wGLETKBW3PvgPWqT")
        val sp2 = spotifySong(4, "3n3Ppam7vgaVa1iaRUc9Lp")

        val plan = MixedQueuePlan("spotify:playlist:dup", "Dup", listOf(sp1, loc1, sp1Again, sp2), 0)
        assertEquals(listOf(sp1), plan.spotifyRunFrom(0))
        assertTrue(plan.isLastInSpotifyRun())
        assertFalse(plan.syncToSpotifyTrackId("3n3Ppam7vgaVa1iaRUc9Lp"))
        assertEquals(0, plan.index)
        assertFalse(plan.syncToSpotifyTrackId(""))
        assertEquals(0, plan.index)
        assertEquals(loc1, plan.stepForward(repeatMode = 0))
        assertFalse(plan.isLastInSpotifyRun())
        assertEquals(emptyList<SongsModel>(), plan.spotifyRunFrom())
        assertEquals(sp1Again, plan.stepForward(repeatMode = 0))
        assertEquals(listOf(sp1Again, sp2), plan.spotifyRunFrom())
    }

    @Test
    fun handlesConsecutiveLocalsAndMiddleStartIndex() {
        val loc1 = localSong(1)
        val loc2 = localSong(2)
        val sp1 = spotifySong(3, "4cOdK2wGLETKBW3PvgPWqT")
        val sp2 = spotifySong(4, "3n3Ppam7vgaVa1iaRUc9Lp")
        val sp3 = spotifySong(5, "7ouMYWpwJ422jRcDASZB7P")

        val plan = MixedQueuePlan("spotify:playlist:mid", "Mid", listOf(loc1, loc2, sp1, sp2, sp3), 3)
        assertEquals(sp2, plan.current())
        assertEquals(listOf(sp2, sp3), plan.spotifyRunFrom())
        assertFalse(plan.isLastInSpotifyRun())
        assertEquals(listOf(sp3), plan.upcoming())

        assertEquals(sp1, plan.stepBackward(repeatMode = 0))
        assertEquals(loc2, plan.stepBackward(repeatMode = 0))
        assertEquals(loc1, plan.stepBackward(repeatMode = 0))
        assertNull(plan.stepBackward(repeatMode = 0))
        assertEquals(0, plan.index)
    }

    @Test
    fun handlesJumpToBoundsAndEmptyPlanSafely() {
        val sp1 = spotifySong(1, "4cOdK2wGLETKBW3PvgPWqT")
        val loc1 = localSong(2)
        val plan = MixedQueuePlan("spotify:playlist:jmp", "Jmp", listOf(sp1, loc1), 99)
        assertEquals(1, plan.index)
        assertEquals(loc1, plan.current())

        assertNull(plan.jumpTo(-1))
        assertNull(plan.jumpTo(2))
        assertEquals(1, plan.index)
        assertEquals(sp1, plan.jumpTo(0))
        assertEquals(0, plan.index)

        val empty = MixedQueuePlan("spotify:playlist:empty", "Empty", emptyList(), 0)
        assertNull(empty.current())
        assertEquals(emptyList<SongsModel>(), empty.upcoming())
        assertEquals(emptyList<SongsModel>(), empty.spotifyRunFrom())
        assertFalse(empty.isLastInSpotifyRun())
        assertNull(empty.stepForward(repeatMode = 1))
        assertNull(empty.stepBackward(repeatMode = 1))
        assertNull(empty.jumpTo(0))
    }

    @Test
    fun wrapsOnRepeatAllAndStepsBackward() {
        val sp1 = spotifySong(1, "4cOdK2wGLETKBW3PvgPWqT")
        val loc1 = localSong(2)
        val plan = MixedQueuePlan("spotify:playlist:mix", "Mix", listOf(sp1, loc1), 1)

        assertEquals(sp1, plan.stepForward(repeatMode = 1))
        assertEquals(0, plan.index)
        assertEquals(loc1, plan.stepBackward(repeatMode = 1))
        assertEquals(1, plan.index)
        assertEquals(sp1, plan.stepBackward(repeatMode = 0))
        assertNull(plan.stepBackward(repeatMode = 0))
    }
}
