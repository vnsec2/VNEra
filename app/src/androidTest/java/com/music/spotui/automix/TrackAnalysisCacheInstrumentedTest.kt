/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.automix

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.myspoti.automix.TrackAnalysis
import app.myspoti.extensions.AudioStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackAnalysisCacheInstrumentedTest {
    @get:Rule val folder = TemporaryFolder(InstrumentationRegistry.getInstrumentation().targetContext.filesDir)

    private val track = "track-a"
    private val source = AudioStream("https://example.invalid/studio.flac", emptyMap(), "audio/flac", 60_000,
        providerId = "provider-a", codec = "flac", bitDepth = 24, sampleRate = 96_000, recordingId = "studio-master")
    private val alternate = source.copy(url = "https://example.invalid/studio.m4a", mimeType = "audio/mp4",
        durationMs = 59_980, codec = "aac", bitDepth = null, sampleRate = 44_100)
    private val flac = AnalysisIdentity.recording(track, source)!!
    private val aac = AnalysisIdentity.recording(track, alternate)!!
    private val flacKey = AnalysisIdentity.exact(track, source)
    private val aacKey = AnalysisIdentity.exact(track, alternate)

    @Test fun anotherEncodingUsesTheTrackAnalysisWithoutAnExactEntry() {
        val cache = AnalysisDiskCache(folder.root)
        assertNotEquals(AnalysisIdentity.encoding(source), AnalysisIdentity.encoding(alternate))
        assertNotEquals(flacKey, aacKey)
        cache.save(flacKey, analysis(), flac)
        val hit = cache.load(aacKey, aac)
        assertNotNull(hit)
        assertEquals("flac-analysis", hit!!.audioKey)
        assertArrayEquals(doubleArrayOf(0.5, 2.5), hit.barStarts, 0.0)
        assertNull(cache.load(aacKey, null)) // Nothing was written under the other media identity.
    }

    @Test fun wrongRecordingOrTimelineDoesNotBorrowAnalysis() {
        val cache = AnalysisDiskCache(folder.root)
        cache.save(flacKey, analysis(), flac)
        for (mismatch in listOf(
            aac.copy(track = "track-b"), aac.copy(provider = "provider-b"),
            aac.copy(recording = "live-version"), aac.copy(durationMs = 59_500),
            aac.copy(durationMs = 0), null,
        )) assertNull(cache.load(aacKey, mismatch))
        assertNotNull(cache.load(flacKey, null))
    }

    @Test fun recordingIdentityMustBeExplicitlySuppliedByAnExtension() {
        fun answer(recording: String) = AudioStream.parse(
            """{"manifest":"PE1QRD48L01QRD4=","durationMs":60000,$recording"source":"FLAC"}""",
        )!!.copy(providerId = "provider-a")
        assertEquals(flac, AnalysisIdentity.recording(track, answer("\"recordingId\":\"studio-master\",")))
        assertNull(AnalysisIdentity.recording(track, answer("")))
        assertNull(AnalysisIdentity.recording(track, answer("\"recordingId\":\"studio master\",")))
    }

    @Test fun invalidTimesAreNotShared() {
        val cache = AnalysisDiskCache(folder.root)
        for (bars in listOf(doubleArrayOf(2.5, 0.5), doubleArrayOf(Double.NaN), doubleArrayOf(60.001))) {
            cache.clear()
            cache.save(flacKey, analysis(bars), flac)
            assertNull(cache.load(aacKey, aac))
        }
        cache.clear()
        cache.save(flacKey, analysis(doubleArrayOf(59.99), contentEnd = 60.0), flac)
        assertNull(cache.load(aacKey, aac)) // Within duration tolerance, but a cue is beyond the target.
    }

    @Test fun decoderTimestampsMustFollowTheDecodedSampleClock() {
        fun timeline(vararg pts: Long) = DecodedTimeline().apply { pts.forEach { add(it, 960, 48_000) } }
        assertTrue(timeline(0, 20_001, 39_999).shareable)
        assertFalse(timeline(12_000, 32_000).shareable)
        assertFalse(timeline(0, 20_000, 49_000).shareable)
        assertFalse(timeline(0, 20_000, 20_000).shareable)
    }

    @Test fun compatibleHitSurvivesReopeningTheCacheAndClearRemovesIt() {
        AnalysisDiskCache(folder.root).save(flacKey, analysis(), flac)
        val reopened = AnalysisDiskCache(folder.root)
        assertNotNull(reopened.load(aacKey, aac))
        assertNull(reopened.load(aacKey, aac.copy(recording = "other")))
        reopened.clear()
        assertNull(AnalysisDiskCache(folder.root).load(flacKey, flac))
    }

    @Test fun damagedPersistedAnalysisIsAMiss() {
        val cache = AnalysisDiskCache(folder.root)
        cache.save(flacKey, analysis(), flac)
        val shared = java.io.File(folder.root, flac.fileName!!)
        // After the producer duration and the no-grid header comes the first array size.
        java.io.RandomAccessFile(shared, "rw").use {
            it.seek(8L + 4 + 4 * 8 + 4 + 2 * 8 + 3)
            it.writeInt(Int.MAX_VALUE)
        }
        assertNull(AnalysisDiskCache(folder.root).load(aacKey, aac))
        assertNotNull(cache.load(flacKey, flac))
    }

    private fun analysis(bars: DoubleArray = doubleArrayOf(0.5, 2.5), contentEnd: Double = 59.8) = TrackAnalysis(
        duration = 60.0, contentStart = 0.1, contentEnd = contentEnd, loudnessDb = -12.0,
        key = 0, keyStrength = 1.0, bpm = 120.0, steady = false, head = null, tail = null,
        beats = doubleArrayOf(0.5, 1.0, 1.5, 2.0, 2.5), barStarts = bars,
        barDb = FloatArray(bars.size), barLowDb = FloatArray(bars.size), barHighDb = FloatArray(bars.size),
        barCentre = FloatArray(bars.size), barNovelty = FloatArray(bars.size), phraseOffset = 0,
        stereo = true, headDown = Double.NaN, tailDown = Double.NaN, audioKey = "flac-analysis",
    )
}
