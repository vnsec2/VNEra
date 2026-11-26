package com.music.spotui.ui.components

import org.junit.Assert.*
import org.junit.Test

class ArtworkLoopTest {
    @Test fun coverGentlyZoomsAndReturnsWithoutASeam() {
        assertEquals(1f, artworkLoopScale(0L), 0.00001f)
        assertTrue(artworkLoopScale(6000L) in 1.03f..1.05f)
        assertEquals(artworkLoopScale(0L), artworkLoopScale(12000L), 0.00001f)
        assertEquals(artworkLoopScale(1000L), artworkLoopScale(11000L), 0.00001f)
    }
}
