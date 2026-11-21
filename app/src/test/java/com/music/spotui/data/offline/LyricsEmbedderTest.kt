package com.music.spotui.data.offline

import com.music.spotui.data.entity.LyricLine
import com.music.spotui.data.entity.Lyrics
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The embedded-lyrics writers, checked with independent readers written here
 * (they only read — a bug in a writer cannot hide behind its own reader).
 * The same layouts were already checked against real files with mutagen and
 * Musync's own reader, see `~/workspace/orpheon-review/embed-prototype/`.
 */
class LyricsEmbedderTest {
    private val lines = listOf(
        LyricLine(0, "Première ligne"),
        LyricLine(1500, "Deuxième ligne, avec accents : café — naïve"),
        LyricLine(3200, "Dernière ligne"),
    )
    private val synced = Lyrics(lines, synced = true)
    private val plain = Lyrics(lines, synced = false)
    private val lrc = "[00:00.00]Première ligne\n[00:01.50]Deuxième ligne, avec accents : café — naïve\n[00:03.20]Dernière ligne"
    private val plainText = "Première ligne\nDeuxième ligne, avec accents : café — naïve\nDernière ligne"

    // ── LRC text ──

    @Test fun lrcTextIsTimestampedWhenSyncedAndPlainOtherwise() {
        assertEquals(lrc, LyricsEmbedder.lrcText(synced))
        assertEquals(plainText, LyricsEmbedder.lrcText(plain))
    }

    @Test fun lrcTimestampsAreCentiseconds() {
        assertEquals("00:00.00", LyricsEmbedder.lrcTimestamp(0))
        assertEquals("00:01.50", LyricsEmbedder.lrcTimestamp(1500))
        assertEquals("00:01.99", LyricsEmbedder.lrcTimestamp(1999))
        assertEquals("01:00.00", LyricsEmbedder.lrcTimestamp(60_000))
        assertEquals("100:00.00", LyricsEmbedder.lrcTimestamp(6_000_000))
    }

    // ── FLAC (independent Vorbis reader below) ──

    private fun flacHead(withComments: Boolean = false, withLyrics: String? = null): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray(Charsets.US_ASCII))
        val streamInfo = ByteArray(34)
        out.write(0x80) // last block (for now), type STREAMINFO
        out.write(byteArrayOf(0, 0, 34))
        out.write(streamInfo)
        if (withComments) {
            val comments = ByteArrayOutputStream()
            val vendor = "TestVendor".toByteArray(Charsets.UTF_8)
            val entries = mutableListOf("ARTIST=Test Artist", "TITLE=Test Song")
            if (withLyrics != null) entries += "LYRICS=$withLyrics"
            fun le32(v: Int) = byteArrayOf(
                (v and 0xFF).toByte(), ((v ushr 8) and 0xFF).toByte(),
                ((v ushr 16) and 0xFF).toByte(), ((v ushr 24) and 0xFF).toByte(),
            )
            comments.write(le32(vendor.size)); comments.write(vendor)
            comments.write(le32(entries.size))
            for (e in entries) {
                val b = e.toByteArray(Charsets.UTF_8)
                comments.write(le32(b.size)); comments.write(b)
            }
            // STREAMINFO is no longer last: rewrite its header without the flag.
            val head = out.toByteArray()
            head[4] = 0
            val block = comments.toByteArray()
            val withBlock = ByteArrayOutputStream()
            withBlock.write(head)
            withBlock.write(0x80 or 4)
            withBlock.write(byteArrayOf(((block.size ushr 16) and 0xFF).toByte(), ((block.size ushr 8) and 0xFF).toByte(), (block.size and 0xFF).toByte()))
            withBlock.write(block)
            return withBlock.toByteArray()
        }
        return out.toByteArray()
    }

    /** (vendor, comments) of a FLAC head's Vorbis block, or null. Independent of the writer. */
    private fun readVorbis(head: ByteArray): Pair<String, Map<String, String>>? {
        var pos = 4
        while (pos + 4 <= head.size) {
            val last = (head[pos].toInt() and 0x80) != 0
            val type = head[pos].toInt() and 0x7F
            val size = ((head[pos + 1].toInt() and 0xFF) shl 16) or ((head[pos + 2].toInt() and 0xFF) shl 8) or (head[pos + 3].toInt() and 0xFF)
            if (type == 4) {
                val p = head.copyOfRange(pos + 4, pos + 4 + size)
                fun le32(o: Int) = (p[o].toInt() and 0xFF) or ((p[o + 1].toInt() and 0xFF) shl 8) or
                    ((p[o + 2].toInt() and 0xFF) shl 16) or ((p[o + 3].toInt() and 0xFF) shl 24)
                var o = 0
                val vLen = le32(o); o += 4
                val vendor = p.copyOfRange(o, o + vLen).toString(Charsets.UTF_8); o += vLen
                val n = le32(o); o += 4
                val map = LinkedHashMap<String, String>()
                repeat(n) {
                    val len = le32(o); o += 4
                    val entry = p.copyOfRange(o, o + len).toString(Charsets.UTF_8); o += len
                    val eq = entry.indexOf('=')
                    if (eq > 0) map[entry.substring(0, eq).uppercase()] = entry.substring(eq + 1)
                }
                return vendor to map
            }
            pos += 4 + size
            if (last) break
        }
        return null
    }

    @Test fun flacLyricsAreEmbeddedAndStreamInfoUntouched() {
        val head = flacHead()
        val embedded = LyricsEmbedder.rebuildFlacHead(head, lrc)
        val (vendor, map) = readVorbis(embedded)!!
        assertEquals("Revera", vendor)
        assertEquals(lrc, map["LYRICS"])
        // STREAMINFO (34 bytes after the first header) is byte-identical.
        assertTrue(embedded.copyOfRange(8, 42).contentEquals(head.copyOfRange(8, 42)))
        // Audio behind the head is not part of this call; the head still parses.
        assertTrue(LyricsEmbedder.flacAudioOffset(embedded) == embedded.size)
    }

    @Test fun flacExistingCommentsAndVendorSurvive() {
        val embedded = LyricsEmbedder.rebuildFlacHead(flacHead(withComments = true), lrc)
        val (vendor, map) = readVorbis(embedded)!!
        assertEquals("TestVendor", vendor)
        assertEquals("Test Artist", map["ARTIST"])
        assertEquals("Test Song", map["TITLE"])
        assertEquals(lrc, map["LYRICS"])
    }

    @Test fun flacExistingLyricsAreReplacedNotDuplicated() {
        val embedded = LyricsEmbedder.rebuildFlacHead(flacHead(withComments = true, withLyrics = "old"), lrc)
        val (_, map) = readVorbis(embedded)!!
        assertEquals(lrc, map["LYRICS"])
        assertEquals(1, map.keys.count { it == "LYRICS" })
    }

    @Test fun flacNonFlacHeadIsRefused() {
        assertEquals(-1, LyricsEmbedder.flacAudioOffset("ID3".toByteArray(Charsets.US_ASCII)))
    }

    /** A FLAC head with a hand-built Vorbis comment payload (possibly corrupt). */
    private fun flacHeadWithRawComment(payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray(Charsets.US_ASCII))
        out.write(0) // STREAMINFO, not last
        out.write(byteArrayOf(0, 0, 34))
        out.write(ByteArray(34))
        out.write(0x80 or 4) // Vorbis comment, last
        out.write(byteArrayOf(((payload.size ushr 16) and 0xFF).toByte(), ((payload.size ushr 8) and 0xFF).toByte(), (payload.size and 0xFF).toByte()))
        out.write(payload)
        return out.toByteArray()
    }

    @Test fun flacNegativeVendorLengthIsRefused() {
        // le32 is signed: 0xFFFFFFFF reads as -1, and must not walk the parser backwards.
        val payload = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        try {
            LyricsEmbedder.rebuildFlacHead(flacHeadWithRawComment(payload), lrc)
            assertTrue("negative vendor length must be refused", false)
        } catch (_: IllegalArgumentException) {
            // expected: corrupt input, embed gives up
        }
        assertNull(LyricsEmbedder.readFlacLyrics(flacHeadWithRawComment(payload)))
    }

    @Test fun flacNegativeCommentCountReturnsNull() {
        val payload = ByteArrayOutputStream().apply {
            write(byteArrayOf(1, 0, 0, 0)) // vendor length 1
            write("V".toByteArray(Charsets.US_ASCII))
            write(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())) // count -1
        }.toByteArray()
        assertNull(LyricsEmbedder.readFlacLyrics(flacHeadWithRawComment(payload)))
    }

    // ── MP3 (independent ID3 reader below) ──

    private fun be32(v: Int) = byteArrayOf(
        ((v ushr 24) and 0xFF).toByte(), ((v ushr 16) and 0xFF).toByte(),
        ((v ushr 8) and 0xFF).toByte(), (v and 0xFF).toByte(),
    )
    private fun synch(v: Int) = byteArrayOf(
        ((v ushr 21) and 0x7F).toByte(), ((v ushr 14) and 0x7F).toByte(),
        ((v ushr 7) and 0x7F).toByte(), (v and 0x7F).toByte(),
    )

    private fun textFrame(id: String, text: String, major: Int): ByteArray {
        val body = ByteArrayOutputStream()
        if (major >= 4) {
            body.write(3)
            body.write(text.toByteArray(Charsets.UTF_8))
        } else {
            body.write(1)
            body.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
            body.write(text.toByteArray(Charsets.UTF_16LE))
        }
        val payload = body.toByteArray()
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.US_ASCII))
        out.write(if (major == 4) synch(payload.size) else be32(payload.size))
        out.write(0); out.write(0)
        out.write(payload)
        return out.toByteArray()
    }

    private fun id3Tag(major: Int, frames: List<ByteArray>, padding: Int = 0): ByteArray {
        val framesData = frames.fold(ByteArrayOutputStream()) { acc, f -> acc.apply { write(f) } }.toByteArray()
        val total = framesData.size + padding
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray(Charsets.US_ASCII))
        out.write(major); out.write(0); out.write(0)
        out.write(synch(total))
        out.write(framesData)
        repeat(padding) { out.write(0) }
        return out.toByteArray()
    }

    /** (id, body) frames of a tag, version aside. Independent of the writer. */
    private fun readId3Frames(tag: ByteArray): List<Pair<String, ByteArray>> {
        if (tag.size < 10 || !tag.copyOfRange(0, 3).contentEquals("ID3".toByteArray(Charsets.US_ASCII))) return emptyList()
        val major = tag[3].toInt() and 0xFF
        fun synchRead(o: Int) = ((tag[o].toInt() and 0x7F) shl 21) or ((tag[o + 1].toInt() and 0x7F) shl 14) or
            ((tag[o + 2].toInt() and 0x7F) shl 7) or (tag[o + 3].toInt() and 0x7F)
        fun beRead(o: Int) = ((tag[o].toInt() and 0xFF) shl 24) or ((tag[o + 1].toInt() and 0xFF) shl 16) or
            ((tag[o + 2].toInt() and 0xFF) shl 8) or (tag[o + 3].toInt() and 0xFF)
        val declared = synchRead(6)
        val frames = ArrayList<Pair<String, ByteArray>>()
        var off = 10
        val end = minOf(10 + declared, tag.size)
        while (off + 10 <= end) {
            val id = String(tag.copyOfRange(off, off + 4), Charsets.US_ASCII)
            if (!id.all { it in '0'..'9' || it in 'A'..'Z' }) break
            val size = if (major == 4) synchRead(off + 4) else beRead(off + 4)
            if (size < 0 || off + 10 + size > end) break
            frames += id to tag.copyOfRange(off + 10, off + 10 + size)
            off += 10 + size
        }
        return frames
    }

    private fun decodeFrameText(body: ByteArray): String {
        if (body.isEmpty()) return ""
        return when (body[0].toInt() and 0xFF) {
            1 -> body.copyOfRange(3, body.size).toString(Charsets.UTF_16LE)
            else -> body.copyOfRange(1, body.size).toString(Charsets.UTF_8)
        }
    }

    /** (text, timestamp) pairs of a SYLT body, leading newlines stripped. */
    private fun decodeSylt(body: ByteArray): List<Pair<String, Long>> {
        val enc = body[0].toInt() and 0xFF
        val termLen = if (enc == 1 || enc == 2) 2 else 1
        var i = 1 + 3 + 1 + 1 + termLen // encoding, language, format, type, descriptor
        val out = ArrayList<Pair<String, Long>>()
        fun textAt(from: Int, to: Int): String {
            var raw = body.copyOfRange(from, to)
            if (enc == 1) {
                // Each SYLT text starts with its own UTF-16 BOM.
                if (raw.size >= 2 && raw[0] == 0xFF.toByte() && raw[1] == 0xFE.toByte()) raw = raw.copyOfRange(2, raw.size)
                return raw.toString(Charsets.UTF_16LE)
            }
            return raw.toString(Charsets.UTF_8)
        }
        while (i < body.size) {
            val end = if (termLen == 2) {
                var j = i
                while (j + 1 < body.size && !(body[j] == 0.toByte() && body[j + 1] == 0.toByte())) j += 2
                j
            } else {
                var j = i
                while (j < body.size && body[j] != 0.toByte()) j++
                j
            }
            val text = textAt(i, end).trimStart('\n')
            val tsOff = end + termLen
            if (tsOff + 4 > body.size) break
            val ts = ((body[tsOff].toLong() and 0xFF) shl 24) or ((body[tsOff + 1].toLong() and 0xFF) shl 16) or
                ((body[tsOff + 2].toLong() and 0xFF) shl 8) or (body[tsOff + 3].toLong() and 0xFF)
            out += text to ts
            i = tsOff + 4
        }
        return out
    }

    private fun usltText(frames: List<Pair<String, ByteArray>>): String? {
        val body = frames.firstOrNull { it.first == "USLT" }?.second ?: return null
        val enc = body[0].toInt() and 0xFF
        val termLen = if (enc == 1 || enc == 2) 2 else 1
        var i = 1 + 3 // encoding + language, then the descriptor up to its terminator
        if (termLen == 2) {
            while (i + 1 < body.size && !(body[i] == 0.toByte() && body[i + 1] == 0.toByte())) i += 2
            i += 2
        } else {
            while (i < body.size && body[i] != 0.toByte()) i++
            i += 1
        }
        val raw = body.copyOfRange(i, body.size)
        return if (enc == 1) {
            val stripped = if (raw.size >= 2 && raw[0] == 0xFF.toByte() && raw[1] == 0xFE.toByte()) raw.copyOfRange(2, raw.size) else raw
            stripped.toString(Charsets.UTF_16LE).trimEnd('\u0000')
        } else {
            raw.toString(Charsets.UTF_8)
        }
    }

    @Test fun mp3NoTagGetsAV24TagWithUsltAndSylt() {
        val rebuilt = LyricsEmbedder.rebuildMp3Tag(ByteArray(0), synced)
        assertFalse(rebuilt.fitsInPlace)
        assertEquals(0, rebuilt.audioOffset)
        val frames = readId3Frames(rebuilt.tagBytes)
        assertEquals(lrc, usltText(frames))
        val sylt = frames.firstOrNull { it.first == "SYLT" }
        assertNotNull(sylt)
        assertEquals(listOf("Première ligne" to 0L, "Deuxième ligne, avec accents : café — naïve" to 1500L, "Dernière ligne" to 3200L), decodeSylt(sylt!!.second))
        assertEquals(lrc, LyricsEmbedder.readMp3Uslt(rebuilt.tagBytes))
        // The new tag declares itself v2.4.
        assertEquals(4, rebuilt.tagBytes[3].toInt() and 0xFF)
    }

    @Test fun mp3V23TagKeepsItsVersionAndFrames() {
        val tag = id3Tag(3, listOf(textFrame("TIT2", "Test Song", 3), textFrame("TPE1", "Test Artist", 3)))
        val rebuilt = LyricsEmbedder.rebuildMp3Tag(tag, synced)
        assertFalse(rebuilt.fitsInPlace) // no padding to grow into
        assertEquals(tag.size, rebuilt.audioOffset)
        val frames = readId3Frames(rebuilt.tagBytes)
        assertEquals(3, rebuilt.tagBytes[3].toInt() and 0xFF)
        assertEquals("Test Song", decodeFrameText(frames.first { it.first == "TIT2" }.second))
        assertEquals("Test Artist", decodeFrameText(frames.first { it.first == "TPE1" }.second))
        assertEquals(lrc, usltText(frames))
        assertNotNull(frames.firstOrNull { it.first == "SYLT" })
    }

    @Test fun mp3CorruptExtendedHeaderSizeDoesNotCrash() {
        // v2.3 extended-header size is a signed be32: 0x80000000 reads as -2^31,
        // and an unchecked offset would crash the frame walk.
        val body = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x80.toByte(), 0, 0, 0)) // extended header size -2^31
            write(textFrame("TIT2", "Test Song", 3))
        }.toByteArray()
        val tag = ByteArrayOutputStream().apply {
            write("ID3".toByteArray(Charsets.US_ASCII))
            write(3); write(0); write(0x40) // v2.3, extended header flag
            write(synch(body.size))
            write(body)
        }.toByteArray()
        val rebuilt = LyricsEmbedder.rebuildMp3Tag(tag, synced)
        assertEquals(lrc, LyricsEmbedder.readMp3Uslt(rebuilt.tagBytes))
    }

    @Test fun mp3TagWithRoomIsRewrittenInPlace() {
        val tag = id3Tag(3, listOf(textFrame("TIT2", "Test Song", 3)), padding = 4096)
        val rebuilt = LyricsEmbedder.rebuildMp3Tag(tag, plain)
        assertTrue(rebuilt.fitsInPlace)
        assertEquals(tag.size, rebuilt.tagBytes.size)
        val frames = readId3Frames(rebuilt.tagBytes)
        assertEquals("Test Song", decodeFrameText(frames.first { it.first == "TIT2" }.second))
        assertEquals(plainText, usltText(frames))
        assertNull(frames.firstOrNull { it.first == "SYLT" })
    }

    @Test fun mp3UnsyncedLyricsHaveNoSylt() {
        val rebuilt = LyricsEmbedder.rebuildMp3Tag(ByteArray(0), plain)
        val frames = readId3Frames(rebuilt.tagBytes)
        assertEquals(plainText, usltText(frames))
        assertNull(frames.firstOrNull { it.first == "SYLT" })
    }

    @Test fun mp3OldLyricsAreReplacedNotDuplicated() {
        val first = LyricsEmbedder.rebuildMp3Tag(ByteArray(0), synced)
        val second = LyricsEmbedder.rebuildMp3Tag(first.tagBytes, plain)
        val frames = readId3Frames(second.tagBytes)
        assertEquals(1, frames.count { it.first == "USLT" })
        assertEquals(0, frames.count { it.first == "SYLT" })
        assertEquals(plainText, usltText(frames))
    }

    @Test fun mp3V22TagIsRefused() {
        val tag = id3Tag(2, listOf(textFrame("TIT2", "Test Song", 2)))
        try {
            LyricsEmbedder.rebuildMp3Tag(tag, synced)
            assertTrue("v2.2 must be refused", false)
        } catch (_: IllegalArgumentException) {
            // Expected: a v2.2 tag is left alone rather than rewritten from a misparse.
        }
    }

    // ── M4A (independent atom reader below) ──

    private fun atom(type: ByteArray, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(be32(8 + payload.size))
        out.write(type)
        out.write(payload)
        return out.toByteArray()
    }

    private fun atom(type: String, payload: ByteArray): ByteArray =
        atom(type.toByteArray(Charsets.US_ASCII), payload)

    private fun textDataAtom(type: ByteArray, text: String): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val data = ByteArrayOutputStream()
        data.write(be32(16 + textBytes.size))
        data.write("data".toByteArray(Charsets.US_ASCII))
        data.write(be32(1))
        data.write(be32(0))
        data.write(textBytes)
        return atom(type, data.toByteArray())
    }

    private val LYR = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
    private val TOO = byteArrayOf(0xA9.toByte(), 't'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte())

    /** A minimal MP4: ftyp + moov (mvhd, trak with stco, optional udta) + mdat. */
    private fun m4aBytes(withUdta: Boolean = true): ByteArray {
        val ftyp = atom("ftyp", "isom".toByteArray(Charsets.US_ASCII) + be32(0) +
            "isom".toByteArray(Charsets.US_ASCII) + "iso2".toByteArray(Charsets.US_ASCII))
        val mvhd = atom("mvhd", ByteArray(100))
        val ilst = atom("ilst", textDataAtom(TOO, "Lavf"))
        val metaPayload = ByteArray(4) + atom("hdlr", ByteArray(4) + ByteArray(4) + "mdir".toByteArray(Charsets.US_ASCII) + ByteArray(12) + byteArrayOf(0)) + ilst
        val udta = atom("udta", atom("meta", metaPayload))
        // Build moov once with a placeholder chunk offset to measure it, then again
        // with the real one (same size, so nothing else moves).
        fun moovWithStco(chunkOffset: Int): ByteArray {
            val stco = atom("stco", be32(0) + be32(1) + be32(chunkOffset))
            val trak = atom("trak", atom("mdia", atom("minf", atom("stbl", stco))))
            return atom("moov", mvhd + trak + if (withUdta) udta else ByteArray(0))
        }
        val moovSize = moovWithStco(0).size
        val moov = moovWithStco(ftyp.size + moovSize + 8)
        return ftyp + moov + atom("mdat", ByteArray(100))
    }

    /** Direct children of [bytes] from [base] to [end]: (offset, size, type bytes). */
    private fun readAtoms(bytes: ByteArray, base: Int, end: Int): List<Triple<Int, Int, ByteArray>> {
        val out = ArrayList<Triple<Int, Int, ByteArray>>()
        var off = base
        while (off + 8 <= end) {
            val size = ((bytes[off].toInt() and 0xFF) shl 24) or ((bytes[off + 1].toInt() and 0xFF) shl 16) or
                ((bytes[off + 2].toInt() and 0xFF) shl 8) or (bytes[off + 3].toInt() and 0xFF)
            if (size < 8 || off + size > end) break
            out += Triple(off, size, bytes.copyOfRange(off + 4, off + 8))
            off += size
        }
        return out
    }

    private fun findAtom(bytes: ByteArray, base: Int, end: Int, type: ByteArray): Triple<Int, Int, ByteArray>? =
        readAtoms(bytes, base, end).firstOrNull { it.third.contentEquals(type) }

    private fun typeBytes(s: String): ByteArray = s.toByteArray(Charsets.US_ASCII)

    private fun m4aLyr(bytes: ByteArray): String? {
        val moov = findAtom(bytes, 0, bytes.size, typeBytes("moov")) ?: return null
        var current = bytes.copyOfRange(moov.first + 8, moov.first + moov.second)
        var start = 0
        for (step in listOf("udta", "meta", "ilst")) {
            val found = findAtom(current, start, current.size, typeBytes(step)) ?: return null
            current = current.copyOfRange(found.first + 8, found.first + found.second)
            start = if (step == "meta") 4 else 0
        }
        val lyr = findAtom(current, 0, current.size, LYR) ?: return null
        val payload = current.copyOfRange(lyr.first + 8, lyr.first + lyr.second)
        val data = findAtom(payload, 0, payload.size, typeBytes("data")) ?: return null
        return payload.copyOfRange(data.first + 16, data.first + data.second).toString(Charsets.UTF_8)
    }

    private fun m4aStco(bytes: ByteArray): Long? {
        val moov = findAtom(bytes, 0, bytes.size, typeBytes("moov")) ?: return null
        var current = bytes.copyOfRange(moov.first + 8, moov.first + moov.second)
        for (step in listOf("trak", "mdia", "minf", "stbl")) {
            val found = findAtom(current, 0, current.size, typeBytes(step)) ?: return null
            current = current.copyOfRange(found.first + 8, found.first + found.second)
        }
        val stco = findAtom(current, 0, current.size, typeBytes("stco")) ?: return null
        val base = stco.first + 8
        val count = ((current[base + 4].toInt() and 0xFF) shl 24) or ((current[base + 5].toInt() and 0xFF) shl 16) or
            ((current[base + 6].toInt() and 0xFF) shl 8) or (current[base + 7].toInt() and 0xFF)
        if (count != 1) return null
        val o = base + 8
        return ((current[o].toLong() and 0xFF) shl 24) or ((current[o + 1].toLong() and 0xFF) shl 16) or
            ((current[o + 2].toLong() and 0xFF) shl 8) or (current[o + 3].toLong() and 0xFF)
    }

    @Test fun m4aLyricsAreEmbeddedAndTooPreserved() {
        val bytes = m4aBytes()
        val before = m4aStco(bytes)!!
        val embedded = LyricsEmbedder.embedM4a(bytes, lrc)
        assertEquals(lrc, m4aLyr(embedded))
        assertEquals(lrc, LyricsEmbedder.readM4aLyrics(embedded))
        // ©too survives in the same ilst.
        val moov = findAtom(embedded, 0, embedded.size, typeBytes("moov"))!!
        var current = embedded.copyOfRange(moov.first + 8, moov.first + moov.second)
        for (step in listOf("udta", "meta", "ilst")) {
            // meta's children start after its 4 version/flags bytes.
            val found = findAtom(current, if (step == "ilst") 4 else 0, current.size, typeBytes(step))!!
            current = current.copyOfRange(found.first + 8, found.first + found.second)
        }
        assertNotNull(findAtom(current, 0, current.size, TOO))
        // moov grew in front of mdat: the chunk offset moved by exactly the growth.
        assertEquals(before + (embedded.size - bytes.size), m4aStco(embedded))
        // The audio behind moov is byte-identical (mdat payload).
        val mdatBefore = findAtom(bytes, 0, bytes.size, typeBytes("mdat"))!!
        val mdatAfter = findAtom(embedded, 0, embedded.size, typeBytes("mdat"))!!
        assertTrue(embedded.copyOfRange(mdatAfter.first + 8, mdatAfter.first + mdatAfter.second)
            .contentEquals(bytes.copyOfRange(mdatBefore.first + 8, mdatBefore.first + mdatBefore.second)))
    }

    @Test fun m4aChainIsCreatedWhenMissing() {
        val embedded = LyricsEmbedder.embedM4a(m4aBytes(withUdta = false), lrc)
        assertEquals(lrc, m4aLyr(embedded))
    }

    @Test fun m4aLyricsAreReplacedNotDuplicated() {
        val first = LyricsEmbedder.embedM4a(m4aBytes(), lrc)
        val second = LyricsEmbedder.embedM4a(first, plainText)
        assertEquals(plainText, m4aLyr(second))
        val moov = findAtom(second, 0, second.size, typeBytes("moov"))!!
        var current = second.copyOfRange(moov.first + 8, moov.first + moov.second)
        for (step in listOf("udta", "meta", "ilst")) {
            // meta's children start after its 4 version/flags bytes.
            val found = findAtom(current, if (step == "ilst") 4 else 0, current.size, typeBytes(step))!!
            current = current.copyOfRange(found.first + 8, found.first + found.second)
        }
        assertEquals(1, readAtoms(current, 0, current.size).count { it.third.contentEquals(LYR) })
    }

    @Test fun m4aWithoutMoovIsRefused() {
        try {
            LyricsEmbedder.embedM4a("ftyp".toByteArray(Charsets.US_ASCII), lrc)
            assertTrue("no moov must be refused", false)
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    // ── Cover (independent readers below) ──

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 1, 2, 3, 4, 5, 6, 7)
    private val jpeg2 = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 9, 8, 7, 6, 5, 4, 3, 2)
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)

    /** Image bytes of a FLAC head's PICTURE block, or null. Independent of the writer. */
    private fun readPicture(head: ByteArray): ByteArray? {
        var pos = 4
        while (pos + 4 <= head.size) {
            val last = (head[pos].toInt() and 0x80) != 0
            val type = head[pos].toInt() and 0x7F
            val size = ((head[pos + 1].toInt() and 0xFF) shl 16) or ((head[pos + 2].toInt() and 0xFF) shl 8) or (head[pos + 3].toInt() and 0xFF)
            if (type == 6) {
                val p = head.copyOfRange(pos + 4, pos + 4 + size)
                // A PICTURE payload is big-endian throughout (unlike Vorbis comments).
                fun be32(o: Int) = ((p[o].toInt() and 0xFF) shl 24) or ((p[o + 1].toInt() and 0xFF) shl 16) or
                    ((p[o + 2].toInt() and 0xFF) shl 8) or (p[o + 3].toInt() and 0xFF)
                var o = 4 // picture type
                val mimeLen = be32(o); o += 4 + mimeLen
                val descLen = be32(o); o += 4 + descLen
                o += 16 // width, height, depth, colours
                val dataLen = be32(o); o += 4
                return p.copyOfRange(o, o + dataLen)
            }
            pos += 4 + size
            if (last) break
        }
        return null
    }

    private fun countBlocks(head: ByteArray, type: Int): Int {
        var pos = 4
        var n = 0
        while (pos + 4 <= head.size) {
            val last = (head[pos].toInt() and 0x80) != 0
            val t = head[pos].toInt() and 0x7F
            val size = ((head[pos + 1].toInt() and 0xFF) shl 16) or ((head[pos + 2].toInt() and 0xFF) shl 8) or (head[pos + 3].toInt() and 0xFF)
            if (t == type) n++
            pos += 4 + size
            if (last) break
        }
        return n
    }

    /** (mime, type, image bytes) of an APIC body, or null. Independent of the writer. */
    private fun apicImage(body: ByteArray): Triple<String, Int, ByteArray>? {
        if (body.size < 4) return null
        val enc = body[0].toInt() and 0xFF
        val termLen = if (enc == 1 || enc == 2) 2 else 1
        var i = 1
        while (i < body.size && body[i] != 0.toByte()) i++
        val mime = body.copyOfRange(1, i).toString(Charsets.US_ASCII)
        i++ // MIME terminator
        if (i >= body.size) return null
        val type = body[i].toInt() and 0xFF
        i++ // picture type: the description starts here
        if (termLen == 2) {
            while (i + 1 < body.size && !(body[i] == 0.toByte() && body[i + 1] == 0.toByte())) i += 2
            i += 2
        } else {
            while (i < body.size && body[i] != 0.toByte()) i++
            i += 1
        }
        if (i > body.size) return null
        return Triple(mime, type, body.copyOfRange(i, body.size))
    }

    private fun apicFrame(img: ByteArray, mime: String, major: Int): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(0) // ISO-8859-1
        body.write(mime.toByteArray(Charsets.US_ASCII))
        body.write(0)
        body.write(3) // front cover
        body.write(0) // empty description
        body.write(img)
        val payload = body.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("APIC".toByteArray(Charsets.US_ASCII))
        out.write(if (major == 4) synch(payload.size) else be32(payload.size))
        out.write(0); out.write(0)
        out.write(payload)
        return out.toByteArray()
    }

    private val COVR = typeBytes("covr")

    /** (data type, image bytes) of an M4A's covr atom, or null. Independent of the writer. */
    private fun m4aCovr(bytes: ByteArray): Pair<Int, ByteArray>? {
        val moov = findAtom(bytes, 0, bytes.size, typeBytes("moov")) ?: return null
        var current = bytes.copyOfRange(moov.first + 8, moov.first + moov.second)
        var start = 0
        for (step in listOf("udta", "meta", "ilst")) {
            val found = findAtom(current, start, current.size, typeBytes(step)) ?: return null
            current = current.copyOfRange(found.first + 8, found.first + found.second)
            start = if (step == "meta") 4 else 0
        }
        val covr = findAtom(current, 0, current.size, COVR) ?: return null
        val payload = current.copyOfRange(covr.first + 8, covr.first + covr.second)
        val data = findAtom(payload, 0, payload.size, typeBytes("data")) ?: return null
        val type = ((payload[data.first + 8].toInt() and 0xFF) shl 24) or ((payload[data.first + 9].toInt() and 0xFF) shl 16) or
            ((payload[data.first + 10].toInt() and 0xFF) shl 8) or (payload[data.first + 11].toInt() and 0xFF)
        return type to payload.copyOfRange(data.first + 16, data.first + data.second)
    }

    /** How many atoms of [type] the ilst of [bytes] holds. */
    private fun countIlstAtoms(bytes: ByteArray, type: ByteArray): Int {
        val moov = findAtom(bytes, 0, bytes.size, typeBytes("moov")) ?: return 0
        var current = bytes.copyOfRange(moov.first + 8, moov.first + moov.second)
        var start = 0
        for (step in listOf("udta", "meta", "ilst")) {
            val found = findAtom(current, start, current.size, typeBytes(step)) ?: return 0
            current = current.copyOfRange(found.first + 8, found.first + found.second)
            start = if (step == "meta") 4 else 0
        }
        return readAtoms(current, 0, current.size).count { it.third.contentEquals(type) }
    }

    @Test fun coverImageDetectsJpegAndPngByMagic() {
        assertEquals("image/jpeg", LyricsEmbedder.coverImage(jpeg)?.mime)
        assertEquals("image/png", LyricsEmbedder.coverImage(png)?.mime)
        assertNull(LyricsEmbedder.coverImage(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)))
        assertNull(LyricsEmbedder.coverImage(null))
        assertNull(LyricsEmbedder.coverImage(ByteArray(9) { 0xFF.toByte() }))
        assertNull(LyricsEmbedder.coverImage(ByteArray(8 * 1024 * 1024 + 1)))
    }

    @Test fun flacCoverIsEmbeddedAndReplaces() {
        val cover = LyricsEmbedder.coverImage(jpeg)!!
        val once = LyricsEmbedder.rebuildFlacHead(flacHead(withComments = true), lrc, cover)
        assertTrue(readPicture(once)!!.contentEquals(jpeg))
        assertEquals(lrc, readVorbis(once)!!.second["LYRICS"])
        val twice = LyricsEmbedder.rebuildFlacHead(once, lrc, LyricsEmbedder.coverImage(jpeg2)!!)
        assertTrue(readPicture(twice)!!.contentEquals(jpeg2))
        assertEquals(1, countBlocks(twice, 6))
        assertTrue(LyricsEmbedder.readFlacPicture(twice)!!.contentEquals(jpeg2))
    }

    @Test fun flacCoverWithoutLyricsLeavesCommentsAlone() {
        val head = flacHead(withComments = true, withLyrics = "old")
        val out = LyricsEmbedder.rebuildFlacHead(head, "", LyricsEmbedder.coverImage(jpeg)!!)
        assertTrue(readPicture(out)!!.contentEquals(jpeg))
        val (_, map) = readVorbis(out)!!
        assertEquals("old", map["LYRICS"])
        assertEquals("Test Artist", map["ARTIST"])
    }

    @Test fun mp3CoverIsEmbeddedInV23AndV24() {
        val cover = LyricsEmbedder.coverImage(jpeg)!!
        val v23 = LyricsEmbedder.rebuildMp3Tag(id3Tag(3, listOf(textFrame("TIT2", "Test Song", 3))), synced, cover)
        val (mime, type, img) = apicImage(readId3Frames(v23.tagBytes).first { it.first == "APIC" }.second)!!
        assertEquals("image/jpeg", mime)
        assertEquals(3, type)
        assertTrue(img.contentEquals(jpeg))
        assertEquals(3, v23.tagBytes[3].toInt() and 0xFF)
        val v24 = LyricsEmbedder.rebuildMp3Tag(ByteArray(0), synced, LyricsEmbedder.coverImage(png)!!)
        val (mime24, _, img24) = apicImage(readId3Frames(v24.tagBytes).first { it.first == "APIC" }.second)!!
        assertEquals("image/png", mime24)
        assertTrue(img24.contentEquals(png))
        assertTrue(LyricsEmbedder.readMp3Apic(v24.tagBytes)!!.contentEquals(png))
    }

    @Test fun mp3CoverReplacesExistingApic() {
        val tag = id3Tag(3, listOf(textFrame("TIT2", "Test Song", 3), apicFrame(jpeg, "image/jpeg", 3)))
        val rebuilt = LyricsEmbedder.rebuildMp3Tag(tag, synced, LyricsEmbedder.coverImage(jpeg2)!!)
        val frames = readId3Frames(rebuilt.tagBytes)
        assertEquals(1, frames.count { it.first == "APIC" })
        assertTrue(apicImage(frames.first { it.first == "APIC" }.second)!!.third.contentEquals(jpeg2))
        assertEquals("Test Song", decodeFrameText(frames.first { it.first == "TIT2" }.second))
    }

    @Test fun mp3CoverWithoutLyricsKeepsFramesAndAddsNoUslt() {
        val tag = id3Tag(3, listOf(textFrame("TIT2", "Test Song", 3)))
        val rebuilt = LyricsEmbedder.rebuildMp3Tag(tag, null, LyricsEmbedder.coverImage(jpeg)!!)
        val frames = readId3Frames(rebuilt.tagBytes)
        assertEquals("Test Song", decodeFrameText(frames.first { it.first == "TIT2" }.second))
        assertNull(frames.firstOrNull { it.first == "USLT" })
        assertNull(frames.firstOrNull { it.first == "SYLT" })
        assertEquals(1, frames.count { it.first == "APIC" })
    }

    @Test fun mp3LyricsWithoutCoverKeepExistingApic() {
        val tag = id3Tag(3, listOf(textFrame("TIT2", "Test Song", 3), apicFrame(jpeg, "image/jpeg", 3)))
        val rebuilt = LyricsEmbedder.rebuildMp3Tag(tag, synced, null)
        val frames = readId3Frames(rebuilt.tagBytes)
        assertEquals(1, frames.count { it.first == "APIC" })
        assertTrue(apicImage(frames.first { it.first == "APIC" }.second)!!.third.contentEquals(jpeg))
    }

    @Test fun m4aCoverIsEmbeddedAndTooPreserved() {
        val bytes = m4aBytes()
        val embedded = LyricsEmbedder.embedM4a(bytes, lrc, LyricsEmbedder.coverImage(jpeg)!!)
        val (type, img) = m4aCovr(embedded)!!
        assertEquals(13, type)
        assertTrue(img.contentEquals(jpeg))
        assertTrue(LyricsEmbedder.readM4aCover(embedded)!!.contentEquals(jpeg))
        assertEquals(lrc, m4aLyr(embedded))
    }

    @Test fun m4aCoverReplacesExisting() {
        val first = LyricsEmbedder.embedM4a(m4aBytes(), lrc, LyricsEmbedder.coverImage(jpeg)!!)
        val second = LyricsEmbedder.embedM4a(first, lrc, LyricsEmbedder.coverImage(png)!!)
        val (type, img) = m4aCovr(second)!!
        assertEquals(14, type)
        assertTrue(img.contentEquals(png))
        assertEquals(1, countIlstAtoms(second, COVR))
    }

    @Test fun m4aCoverWithoutLyricsKeepsLyr() {
        val withLyrics = LyricsEmbedder.embedM4a(m4aBytes(), lrc)
        val withCover = LyricsEmbedder.embedM4a(withLyrics, "", LyricsEmbedder.coverImage(jpeg)!!)
        assertEquals(lrc, m4aLyr(withCover))
        assertTrue(m4aCovr(withCover)!!.second.contentEquals(jpeg))
    }

    // ── File level (temp files, atomic rename) ──

    private fun tempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "lyrics-embedder-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    @Test fun fileLevelFlacEmbedsAndWritesSidecarOnlyWhenPublic() = runBlocking {
        val dir = tempDir()
        val flac = File(dir, "Artist - Title [abc].flac")
        flac.writeBytes(flacHead() + "AUDIO".toByteArray(Charsets.US_ASCII))
        assertTrue(LyricsEmbedder.embed(flac, "flac", synced, cover = null, public = false))
        assertEquals(lrc, readVorbis(flac.readBytes())!!.second["LYRICS"])
        assertFalse(File(dir, "Artist - Title [abc].lrc").exists())
        assertTrue(LyricsEmbedder.embed(flac, "flac", synced, cover = null, public = true))
        val sidecar = File(dir, "Artist - Title [abc].lrc")
        assertTrue(sidecar.exists())
        assertEquals(lrc, sidecar.readText(Charsets.UTF_8))
    }

    @Test fun fileLevelMp3WithoutTagEmbeds() = runBlocking {
        val dir = tempDir()
        val mp3 = File(dir, "song.mp3")
        mp3.writeBytes(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte()) + ByteArray(100))
        assertTrue(LyricsEmbedder.embed(mp3, "mp3", synced, cover = null, public = false))
        val frames = readId3Frames(mp3.readBytes())
        assertEquals(lrc, usltText(frames))
    }

    @Test fun fileLevelUnknownExtensionLeavesTheFileAlone() = runBlocking {
        val dir = tempDir()
        val ogg = File(dir, "song.ogg")
        val before = byteArrayOf(1, 2, 3, 4)
        ogg.writeBytes(before)
        assertFalse(LyricsEmbedder.embed(ogg, "ogg", synced, cover = null, public = false))
        assertTrue(ogg.readBytes().contentEquals(before))
    }

    @Test fun fileLevelEmptyLyricsDoNothing() = runBlocking {
        val dir = tempDir()
        val flac = File(dir, "song.flac")
        val before = flacHead() + "AUDIO".toByteArray(Charsets.US_ASCII)
        flac.writeBytes(before)
        assertFalse(LyricsEmbedder.embed(flac, "flac", Lyrics(emptyList(), synced = true), cover = null, public = false))
        assertTrue(flac.readBytes().contentEquals(before))
    }

    @Test fun fileLevelCoverOnlyEmbeds() = runBlocking {
        val dir = tempDir()
        val mp3 = File(dir, "song.mp3")
        mp3.writeBytes(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte()) + ByteArray(100))
        assertTrue(LyricsEmbedder.embed(mp3, "mp3", null, jpeg, public = false))
        val frames = readId3Frames(mp3.readBytes())
        assertEquals(1, frames.count { it.first == "APIC" })
        assertTrue(apicImage(frames.first { it.first == "APIC" }.second)!!.third.contentEquals(jpeg))
        assertNull(frames.firstOrNull { it.first == "USLT" })
    }

    @Test fun fileLevelLyricsAndCoverTogether() = runBlocking {
        val dir = tempDir()
        val flac = File(dir, "Artist - Title [abc].flac")
        flac.writeBytes(flacHead(withComments = true) + "AUDIO".toByteArray(Charsets.US_ASCII))
        assertTrue(LyricsEmbedder.embed(flac, "flac", synced, jpeg, public = true))
        val bytes = flac.readBytes()
        assertEquals(lrc, readVorbis(bytes)!!.second["LYRICS"])
        assertTrue(readPicture(bytes)!!.contentEquals(jpeg))
        assertTrue(File(dir, "Artist - Title [abc].lrc").exists())
    }

    @Test fun fileLevelNeitherLyricsNorCoverDoesNothing() = runBlocking {
        val dir = tempDir()
        val flac = File(dir, "song.flac")
        val before = flacHead() + "AUDIO".toByteArray(Charsets.US_ASCII)
        flac.writeBytes(before)
        assertFalse(LyricsEmbedder.embed(flac, "flac", null, null, public = false))
        assertTrue(flac.readBytes().contentEquals(before))
    }
}
