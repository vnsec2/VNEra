package com.music.spotui.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/** The player's "open show" link works for a book's chapters too. */
class EpisodeShowIdTest {
    @Test fun aShowContext() = assertEquals("s1", episodeShowId("spotify:show:s1"))

    @Test fun anAudiobookContext() = assertEquals("b1", episodeShowId("spotify:audiobook:b1"))

    @Test fun noContext() = assertEquals("", episodeShowId("spotify:track:t1"))
}
