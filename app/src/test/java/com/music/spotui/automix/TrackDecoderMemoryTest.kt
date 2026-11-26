package com.music.spotui.automix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reported: an OutOfMemoryError on the analysis thread closed the app (256 MB heap). */
class TrackDecoderMemoryTest {
    private val mb = 1L shl 20

    @Test fun `the signals of the longest song analysed fit in a third of the heap`() {
        for (heap in listOf(192 * mb, 256 * mb, 512 * mb, 1024 * mb)) {
            val maxSec = TrackDecoder.maxSeconds(heap)
            val bytes = TrackDecoder.signalBytes(maxSec, heap)
            assertTrue("heap ${heap / mb} MB: $bytes bytes", bytes <= heap / 3)
            val stereoSec = TrackDecoder.stereoMaxSeconds(heap)
            val stereoBytes = TrackDecoder.signalBytes(stereoSec, heap)
            assertTrue("stereo heap ${heap / mb} MB: $stereoBytes bytes", stereoBytes <= heap / 3)
        }
        assertTrue(TrackDecoder.maxSeconds(256 * mb) >= 16 * 60)
        assertTrue(TrackDecoder.stereoMaxSeconds(512 * mb) >= 16 * 60)
        assertEquals(TrackDecoder.MAX_SECONDS, TrackDecoder.maxSeconds(512 * mb))
        assertEquals(TrackDecoder.MAX_SECONDS, TrackDecoder.maxSeconds(1024 * mb))
    }
}
