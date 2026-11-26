package com.music.spotui.data.api

import com.music.spotui.data.entity.showContextUri
import org.junit.Assert.assertEquals
import org.junit.Test

/** A book's chapters must play in the book, not in a podcast show (the audiobook bug). */
class ShowContextUriTest {
    @Test fun aPodcastUsesItsShow() = assertEquals("spotify:show:s1", showContextUri("s1", null, false))

    @Test fun aBookUsesItsOwnUri() = assertEquals("spotify:audiobook:b1", showContextUri("b1", "spotify:audiobook:b1", true))

    @Test fun aBookWithoutAHeaderUsesItsId() = assertEquals("spotify:audiobook:b1", showContextUri("b1", null, true))

    @Test fun aShowUriFromTheHeaderIsNotUsedAsTheBook() = assertEquals("spotify:audiobook:b1", showContextUri("b1", "spotify:show:b1", true))
}
