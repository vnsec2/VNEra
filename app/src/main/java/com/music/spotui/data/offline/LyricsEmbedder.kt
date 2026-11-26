package com.music.spotui.data.offline

import com.music.spotui.data.entity.Lyrics
import com.music.spotui.data.writeTextSafely
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Embeds a downloaded song's lyrics and cover into its audio file, at download time.
 *
 * Orpheon already keeps the lyrics beside the download as JSON ([LyricsApi.keepForOffline]),
 * which is what its own player reads offline. This is the other half: the file itself
 * carries its lyrics and its cover, so other players show them — Musync reads USLT/SYLT
 * and APIC out of an MP3, `©lyr` and `covr` out of an M4A, and a `<basename>.lrc` sidecar
 * next to a FLAC (it does not read FLAC's Vorbis LYRICS field, hence the sidecar for FLAC).
 *
 * One format per file, written the way the players that read it expect:
 * - FLAC: the `LYRICS` field of the Vorbis comment block (LRC text when the lyrics
 *   are synchronised, plain text otherwise); the other comments and the vendor
 *   string are carried across untouched. Plus a `.lrc` sidecar next to a *visible*
 *   (public) file, for Musync. The cover goes in a PICTURE block (front cover).
 * - MP3: USLT (plain text — carrying the LRC timings too when synchronised, since
 *   several players ignore SYLT and read the timings out of the plain frame) and
 *   SYLT (line text + millisecond timestamps); APIC (front cover). Every other frame
 *   is kept verbatim and the tag keeps its major version (v2.3 sizes are big-endian,
 *   v2.4 sizes are synchsafe — re-stamping would corrupt every frame longer than 127 bytes).
 * - M4A: the `©lyr` atom at `moov > udta > meta > ilst`, UTF-8 text, LRC text when
 *   synchronised, and the `covr` atom in the same `ilst` (JPEG = type 13, PNG = 14);
 *   `stco`/`co64` chunk offsets are fixed up when `moov` moves in front of `mdat`.
 *
 * The layout rules are the ones Musync's own writers use (`lib/core/id3/`), checked
 * here against real files with an independent reader (mutagen) before this code was
 * written — see `~/workspace/orpheon-review/embed-prototype/`.
 *
 * Safety, same contract as Musync's writers: the original is never opened for
 * writing on the slow path (temp file + verify + atomic rename); an in-place
 * overwrite only happens for an MP3 tag that fits where the old one sat, and the
 * head is re-read and compared afterwards. Any failure leaves the downloaded file
 * untouched and returns false — the download itself never fails for lyrics or cover.
 *
 * No `android.*` import on purpose: the byte writers are pure JVM and unit-tested.
 */
object LyricsEmbedder {
    /** The MP3 fast path's slack: a later edit grows into the tag instead of moving the audio. */
    private const val MP3_PADDING = 2048
    /** A tag bigger than this is refused rather than trusted (a truncated file can declare one). */
    private const val MAX_TAG_BYTES = 64 * 1024 * 1024
    /** Vorbis comment blocks live at the head; beyond this the file is not what it claims to be. */
    private const val MAX_FLAC_HEAD_BYTES = 16 * 1024 * 1024
    /** Whole-file reads (M4A only) stop here: a song is never this big. */
    private const val MAX_FILE_BYTES = 256L * 1024 * 1024
    /** A cover bigger than this is skipped: a phone cover is a few hundred kB. */
    private const val MAX_COVER_BYTES = 8 * 1024 * 1024
    private const val COPY_CHUNK = 256 * 1024

    /** One file rewritten at a time: a whole file in memory is fine once, not three times. */
    private val embedMutex = Mutex()

    /** A cover image to embed, as downloaded (JPEG or PNG by magic bytes). */
    internal data class CoverImage(val bytes: ByteArray, val mime: String) {
        val isJpeg: Boolean get() = mime == "image/jpeg"
    }

    /** JPEG/PNG by magic bytes, or null when the bytes are not a cover image. */
    internal fun coverImage(bytes: ByteArray?): CoverImage? {
        if (bytes == null || bytes.size < 10 || bytes.size > MAX_COVER_BYTES) return null
        return when {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() ->
                CoverImage(bytes, "image/jpeg")
            bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() &&
                bytes[3] == 'G'.code.toByte() && bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
                bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte() -> CoverImage(bytes, "image/png")
            else -> null
        }
    }

    /** `[mm:ss.cc]`, the form LRC and the sync editor use. Minutes are not capped at 99. */
    internal fun lrcTimestamp(ms: Long): String {
        val m = maxOf(0L, ms)
        return "${(m / 60_000).toString().padStart(2, '0')}:${((m / 1000) % 60).toString().padStart(2, '0')}.${((m % 1000) / 10).toString().padStart(2, '0')}"
    }

    /**
     * The lyrics as text for a tag: `[mm:ss.cc]line` per line when synchronised,
     * plain lines otherwise. Word timings are line-level only here — the words live
     * in the JSON kept beside the download, not in the file.
     */
    fun lrcText(lyrics: Lyrics): String =
        if (lyrics.synced) lyrics.lines.joinToString("\n") { "[${lrcTimestamp(it.timeMs)}]${it.text}" }
        else lyrics.lines.joinToString("\n") { it.text }

    // ── Little helpers ──

    private fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF
    private fun ByteArray.be32(offset: Int): Int =
        (u8(offset) shl 24) or (u8(offset + 1) shl 16) or (u8(offset + 2) shl 8) or u8(offset + 3)
    private fun ByteArray.le32(offset: Int): Int =
        u8(offset) or (u8(offset + 1) shl 8) or (u8(offset + 2) shl 16) or (u8(offset + 3) shl 24)
    private fun ByteArray.synch(offset: Int): Int =
        ((u8(offset) and 0x7F) shl 21) or ((u8(offset + 1) and 0x7F) shl 14) or
            ((u8(offset + 2) and 0x7F) shl 7) or (u8(offset + 3) and 0x7F)

    private fun be32Bytes(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(), ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(), (value and 0xFF).toByte(),
    )
    private fun le32Bytes(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(), ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(), ((value ushr 24) and 0xFF).toByte(),
    )
    private fun synchBytes(value: Int): ByteArray = byteArrayOf(
        ((value ushr 21) and 0x7F).toByte(), ((value ushr 14) and 0x7F).toByte(),
        ((value ushr 7) and 0x7F).toByte(), (value and 0x7F).toByte(),
    )

    private fun ByteArray.startsWithAscii(text: String, offset: Int = 0): Boolean {
        if (size < offset + text.length) return false
        return text.indices.all { this[offset + it] == text[it].code.toByte() }
    }

    // ── FLAC ──

    /** Where the audio starts in a FLAC file (end of the last metadata block), or -1. */
    internal fun flacAudioOffset(bytes: ByteArray): Int {
        if (bytes.size < 4 || !bytes.startsWithAscii("fLaC")) return -1
        var pos = 4
        while (true) {
            if (pos + 4 > bytes.size || pos + 4 > MAX_FLAC_HEAD_BYTES) return -1
            val last = (bytes[pos].toInt() and 0x80) != 0
            val size = (bytes.u8(pos + 1) shl 16) or (bytes.u8(pos + 2) shl 8) or bytes.u8(pos + 3)
            if (pos + 4 + size > bytes.size) return -1
            if (pos + 4 + size > MAX_FLAC_HEAD_BYTES) return -1
            pos += 4 + size
            if (last) return pos
        }
    }

    /**
     * [head] (from `fLaC` to the audio) with the `LYRICS` field set: the existing
     * Vorbis comment block is rebuilt (vendor and other fields untouched), or a new
     * one is inserted right after STREAMINFO. STREAMINFO itself is never touched.
     */
    internal fun rebuildFlacHead(head: ByteArray, lrc: String, cover: CoverImage? = null): ByteArray {
        val audioOffset = flacAudioOffset(head)
        require(audioOffset in 4..head.size) { "not a FLAC head" }
        require(head.size >= 42 && (head[4].toInt() and 0x7F) == 0) { "no STREAMINFO" }
        data class Block(val type: Int, val last: Boolean, val payload: ByteArray)
        val blocks = ArrayList<Block>()
        var pos = 4
        while (pos < audioOffset) {
            val last = (head[pos].toInt() and 0x80) != 0
            val type = head[pos].toInt() and 0x7F
            val size = (head.u8(pos + 1) shl 16) or (head.u8(pos + 2) shl 8) or head.u8(pos + 3)
            blocks += Block(type, last, head.copyOfRange(pos + 4, pos + 4 + size))
            pos += 4 + size
        }
        // Existing comments, minus LYRICS; the vendor string is kept as read.
        var vendor = "Revera".toByteArray(Charsets.UTF_8)
        val kept = ArrayList<Pair<String, String>>()
        val commentIndex = blocks.indexOfFirst { it.type == 4 }
        if (commentIndex >= 0) {
            val p = blocks[commentIndex].payload
            var o = 0
            fun need(n: Int) = require(o + n <= p.size) { "corrupt Vorbis comment block" }
            need(4)
            // le32 is signed: a corrupt length with the high bit set is negative,
            // and need() alone would accept it (o + negative <= size).
            val vLen = p.le32(o); o += 4
            require(vLen >= 0) { "corrupt Vorbis comment block" }
            need(vLen)
            vendor = p.copyOfRange(o, o + vLen); o += vLen
            need(4)
            val n = p.le32(o); o += 4
            require(n >= 0) { "corrupt Vorbis comment block" }
            repeat(n) {
                need(4)
                val len = p.le32(o); o += 4
                require(len >= 0) { "corrupt Vorbis comment block" }
                need(len)
                val entry = p.copyOfRange(o, o + len).toString(Charsets.UTF_8); o += len
                val eq = entry.indexOf('=')
                if (eq > 0 && !entry.substring(0, eq).equals("LYRICS", ignoreCase = true)) {
                    kept += entry.substring(0, eq) to entry.substring(eq + 1)
                }
            }
        }
        // A blank LRC leaves the comments as they are: no lyrics found must not
        // fabricate an empty LYRICS field, nor delete one the source file carries.
        var out = ArrayList<Block>(blocks)
        if (lrc.isNotBlank()) {
            kept += "LYRICS" to lrc
            val comment = java.io.ByteArrayOutputStream().apply {
                write(le32Bytes(vendor.size)); write(vendor)
                write(le32Bytes(kept.size))
                for ((k, v) in kept) {
                    val e = "$k=$v".toByteArray(Charsets.UTF_8)
                    write(le32Bytes(e.size)); write(e)
                }
            }.toByteArray()
            if (commentIndex >= 0) out[commentIndex] = Block(4, blocks[commentIndex].last, comment)
            else {
                // Inserted after STREAMINFO: STREAMINFO loses its "last" flag, the new
                // block takes it when STREAMINFO was the only block.
                out[0] = Block(0, false, blocks[0].payload)
                out.add(1, Block(4, blocks[0].last, comment))
            }
        }
        if (cover != null) {
            // Any picture the source file carries is replaced; the new one sits
            // after the comment block, or after STREAMINFO when there is none.
            val without = out.filter { it.type != 6 }
            val at = without.indexOfFirst { it.type == 4 }.let { if (it < 0) 1 else it + 1 }
            out = ArrayList<Block>(without).apply { add(at, Block(6, false, buildPictureBlock(cover))) }
        }
        val res = java.io.ByteArrayOutputStream()
        res.write("fLaC".toByteArray(Charsets.US_ASCII))
        out.forEachIndexed { i, b ->
            val isLast = i == out.lastIndex
            res.write((if (isLast) 0x80 else 0) or b.type)
            res.write(byteArrayOf(((b.payload.size ushr 16) and 0xFF).toByte(), ((b.payload.size ushr 8) and 0xFF).toByte(), (b.payload.size and 0xFF).toByte()))
            res.write(b.payload)
        }
        return res.toByteArray()
    }

    /** The `LYRICS` value of [head]'s Vorbis comment block, or null. Read-only, for verification. */
    internal fun readFlacLyrics(head: ByteArray): String? {
        val audioOffset = flacAudioOffset(head)
        if (audioOffset < 0) return null
        var pos = 4
        while (pos < audioOffset) {
            val type = head[pos].toInt() and 0x7F
            val last = (head[pos].toInt() and 0x80) != 0
            val size = (head.u8(pos + 1) shl 16) or (head.u8(pos + 2) shl 8) or head.u8(pos + 3)
            if (type == 4) {
                val p = head.copyOfRange(pos + 4, pos + 4 + size)
                var o = 0
                fun need(n: Int): Boolean = o + n <= p.size
                if (!need(4)) return null
                val vLen = p.le32(o); o += 4
                // le32 is signed: a corrupt length with the high bit set is negative.
                if (vLen < 0 || !need(vLen)) return null
                o += vLen
                if (!need(4)) return null
                val n = p.le32(o); o += 4
                if (n < 0) return null
                repeat(n) {
                    if (!need(4)) return null
                    val len = p.le32(o); o += 4
                    if (len < 0 || !need(len)) return null
                    val entry = p.copyOfRange(o, o + len).toString(Charsets.UTF_8); o += len
                    val eq = entry.indexOf('=')
                    if (eq > 0 && entry.substring(0, eq).equals("LYRICS", ignoreCase = true)) return entry.substring(eq + 1)
                }
                return null
            }
            pos += 4 + size
            if (last) break
        }
        return null
    }

    /** A FLAC PICTURE block payload: type 3 (front cover), MIME, empty description, unknown dimensions, image data. */
    private fun buildPictureBlock(cover: CoverImage): ByteArray {
        val mime = cover.mime.toByteArray(Charsets.US_ASCII)
        return java.io.ByteArrayOutputStream().apply {
            write(be32Bytes(3))
            write(be32Bytes(mime.size)); write(mime)
            write(be32Bytes(0)) // empty description
            write(be32Bytes(0)); write(be32Bytes(0)); write(be32Bytes(0)); write(be32Bytes(0)) // width, height, depth, colours
            write(be32Bytes(cover.bytes.size)); write(cover.bytes)
        }.toByteArray()
    }

    /** The image bytes of [head]'s PICTURE block, or null. Read-only, for verification. */
    internal fun readFlacPicture(head: ByteArray): ByteArray? {
        val audioOffset = flacAudioOffset(head)
        if (audioOffset < 0 || audioOffset > head.size) return null
        var pos = 4
        while (pos < audioOffset) {
            val type = head[pos].toInt() and 0x7F
            val size = (head.u8(pos + 1) shl 16) or (head.u8(pos + 2) shl 8) or head.u8(pos + 3)
            if (type == 6) {
                val p = head.copyOfRange(pos + 4, pos + 4 + size)
                var o = 0
                fun need(n: Int): Boolean = o + n <= p.size
                if (!need(4)) return null
                o += 4 // picture type
                if (!need(4)) return null
                // A PICTURE payload is big-endian throughout (unlike the Vorbis
                // comment block, which is little-endian).
                val mimeLen = p.be32(o); o += 4
                if (mimeLen < 0 || !need(mimeLen)) return null
                o += mimeLen
                if (!need(4)) return null
                val descLen = p.be32(o); o += 4
                if (descLen < 0 || !need(descLen)) return null
                o += descLen
                if (!need(16)) return null
                o += 16 // width, height, depth, colours
                if (!need(4)) return null
                val dataLen = p.be32(o); o += 4
                if (dataLen < 0 || !need(dataLen)) return null
                return p.copyOfRange(o, o + dataLen)
            }
            pos += 4 + size
        }
        return null
    }

    // ── MP3 (ID3v2) ──

    private data class Id3Frame(val id: String, val flagsHi: Int, val flagsLo: Int, val body: ByteArray)
    private data class Id3Tag(
        val majorVersion: Int, val revision: Int, val frames: List<Id3Frame>,
        val audioOffset: Int, val declaredSize: Int, val hadFooter: Boolean, val isSupported: Boolean,
    )

    /** Collapses the $FF $00 pairs an unsynchronisation pass inserted. */
    private fun removeUnsynchronisation(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < data.size) {
            out.write(data[i].toInt())
            if (data[i] == 0xFF.toByte() && i + 1 < data.size && data[i + 1] == 0.toByte()) i++
            i++
        }
        return out.toByteArray()
    }

    private fun isFrameId(id: ByteArray): Boolean =
        id.size == 4 && id.all { (it.toInt() and 0xFF) in 0x30..0x39 || (it.toInt() and 0xFF) in 0x41..0x5A }

    private fun walkFrames(
        body: ByteArray, major: Int, skipExtendedHeader: Boolean, unsyncBodies: Boolean = false,
    ): List<Id3Frame> {
        var offset = 0
        if (skipExtendedHeader && body.size >= 4) {
            // v2.3 states the size of what *follows* the size field; v2.4 states the
            // size of the whole extended header, itself included.
            // be32 is signed: a corrupt size with the high bit set goes negative,
            // and a negative offset would crash the frame walk below.
            val extSize = if (major == 3) body.be32(0) + 4 else body.synch(0)
            offset += extSize.coerceIn(0, body.size)
        }
        val frames = ArrayList<Id3Frame>()
        while (offset + 10 <= body.size) {
            val idBytes = body.copyOfRange(offset, offset + 4)
            if (!isFrameId(idBytes)) break // padding, or the tag is corrupt from here
            val size = if (major == 4) body.synch(offset + 4) else body.be32(offset + 4)
            val start = offset + 10
            // A frame of size zero is empty, not the end of the tag.
            if (size < 0 || start + size > body.size) break
            val raw = body.copyOfRange(start, start + size)
            frames += Id3Frame(
                String(idBytes, Charsets.US_ASCII), body.u8(offset + 8), body.u8(offset + 9),
                if (unsyncBodies) removeUnsynchronisation(raw) else raw,
            )
            offset = start + size
        }
        return frames
    }

    /**
     * Reads the tag at the head of [bytes], or null when there is none. Never throws
     * on malformed input: a truncated or nonsensical tag stops the frame walk and
     * yields whatever was read cleanly up to that point.
     */
    private fun parseId3(bytes: ByteArray): Id3Tag? {
        if (bytes.size < 10 || !bytes.startsWithAscii("ID3")) return null
        val major = bytes.u8(3)
        val revision = bytes.u8(4)
        val flags = bytes.u8(5)
        val declared = bytes.synch(6)
        val hadFooter = major >= 4 && (flags and 0x10) != 0
        val audioOffset = 10 + declared + if (hadFooter) 10 else 0
        if (major != 3 && major != 4) {
            return Id3Tag(major, revision, emptyList(), audioOffset, declared, hadFooter, false)
        }
        val bodyEnd = minOf(10 + declared, bytes.size)
        val body = bytes.copyOfRange(10, bodyEnd)
        val unsync = (flags and 0x80) != 0
        val frames = if (!unsync) {
            walkFrames(body, major, (flags and 0x40) != 0)
        } else {
            // v2.3 never settled whether a frame's declared size counts the stuffed
            // bytes: both readings are tried, the one yielding more frames wins.
            // Either way the bodies come back de-unsynchronised, so a v2.4 frame must
            // stop claiming to be unsynchronised — or it would be stripped twice.
            val wholeTag = walkFrames(removeUnsynchronisation(body), major, (flags and 0x40) != 0)
            val perFrame = walkFrames(body, major, (flags and 0x40) != 0, unsyncBodies = true)
            val chosen = if (perFrame.size > wholeTag.size) perFrame else wholeTag
            chosen.map { it.copy(flagsLo = it.flagsLo and 0x02.inv()) }
        }
        return Id3Tag(major, revision, frames, audioOffset, declared, hadFooter, true)
    }

    /** (encoding byte, encoded text): v2.3 predates UTF-8, so UTF-16 with BOM there. */
    private fun encodeText(text: String, major: Int): Pair<Int, ByteArray> =
        if (major >= 4) 3 to text.toByteArray(Charsets.UTF_8)
        else 1 to (byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE))

    private fun terminatorLength(encoding: Int): Int = if (encoding == 1 || encoding == 2) 2 else 1

    private fun decodeId3Text(bytes: ByteArray, encoding: Int): String {
        if (bytes.isEmpty()) return ""
        return when (encoding) {
            1 -> { // UTF-16 with BOM: little-endian unless the BOM says otherwise.
                val be = bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()
                val le = bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()
                val raw = if (be || le) bytes.copyOfRange(2, bytes.size) else bytes
                raw.toString(if (be) Charsets.UTF_16BE else Charsets.UTF_16LE).trimEnd('\u0000')
            }
            else -> bytes.toString(Charsets.UTF_8)
        }
    }

    private fun buildFrame(frame: Id3Frame, major: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(frame.id.toByteArray(Charsets.US_ASCII))
        out.write(if (major == 4) synchBytes(frame.body.size) else be32Bytes(frame.body.size))
        out.write(frame.flagsHi)
        out.write(frame.flagsLo)
        out.write(frame.body)
        return out.toByteArray()
    }

    /**
     * A complete tag, header included. [bodySize] pins the body to an exact length
     * (in-place rewrite); left null, the body gets [padding] bytes of slack.
     */
    private fun buildTag(
        frames: List<Id3Frame>, major: Int, revision: Int,
        padding: Int = MP3_PADDING, bodySize: Int? = null,
    ): ByteArray {
        val framesData = java.io.ByteArrayOutputStream().apply {
            frames.forEach { write(buildFrame(it, major)) }
        }.toByteArray()
        val total = bodySize ?: (framesData.size + padding)
        require(bodySize == null || bodySize >= framesData.size) { "tag too small for frames" }
        val out = java.io.ByteArrayOutputStream()
        out.write("ID3".toByteArray(Charsets.US_ASCII))
        out.write(major)
        out.write(revision)
        out.write(0) // no unsynchronisation, no extended header, no footer
        out.write(synchBytes(total))
        out.write(framesData)
        repeat(total - framesData.size) { out.write(0) }
        return out.toByteArray()
    }

    /** SYLT: encoding, language, timestamp format (ms), content type (lyrics), empty descriptor, then `<text><terminator><timestamp>` per line. */
    private fun buildSyltFrame(lyrics: Lyrics, major: Int): Id3Frame {
        val (encoding, _) = encodeText("", major)
        val term = ByteArray(terminatorLength(encoding))
        val body = java.io.ByteArrayOutputStream().apply {
            write(encoding)
            write("eng".toByteArray(Charsets.US_ASCII))
            write(2) // timestamps in milliseconds
            write(1) // content type: lyrics
            write(term) // empty content descriptor
            for (line in lyrics.lines) {
                // The leading newline marks a syllable as starting a new line: SYLT
                // carries no other notion of line breaks.
                val (_, textBytes) = encodeText("\n" + line.text, major)
                write(textBytes)
                write(term)
                write(be32Bytes(line.timeMs.coerceIn(0, 0x7FFFFFFF).toInt()))
            }
        }.toByteArray()
        return Id3Frame("SYLT", 0, 0, body)
    }

    /** USLT: encoding, language, empty descriptor, then the whole text. */
    private fun buildUsltFrame(text: String, major: Int): Id3Frame {
        val (encoding, textBytes) = encodeText(text, major)
        val term = ByteArray(terminatorLength(encoding))
        val body = java.io.ByteArrayOutputStream().apply {
            write(encoding)
            write("eng".toByteArray(Charsets.US_ASCII))
            write(term)
            write(textBytes)
        }.toByteArray()
        return Id3Frame("USLT", 0, 0, body)
    }

    /** APIC: encoding, MIME, picture type 3 (front cover), empty description, image data. */
    private fun buildApicFrame(cover: CoverImage, major: Int): Id3Frame {
        val (encoding, _) = encodeText("", major)
        val term = ByteArray(terminatorLength(encoding))
        val body = java.io.ByteArrayOutputStream().apply {
            write(encoding)
            write(cover.mime.toByteArray(Charsets.US_ASCII))
            write(0)
            write(3) // front cover
            write(term) // empty description
            write(cover.bytes)
        }.toByteArray()
        return Id3Frame("APIC", 0, 0, body)
    }

    internal data class RebuiltMp3Tag(val tagBytes: ByteArray, val audioOffset: Int, val fitsInPlace: Boolean)

    /**
     * [tagBytes] is the file's head up to the audio (empty when there is no tag).
     * Returns the new tag, where the audio starts behind it, and whether the new tag
     * fits where the old one sat (in-place overwrite, else temp + copy).
     */
    internal fun rebuildMp3Tag(tagBytes: ByteArray, lyrics: Lyrics?, cover: CoverImage? = null): RebuiltMp3Tag {
        val tag = if (tagBytes.isEmpty()) null else parseId3(tagBytes)
        require(tagBytes.isEmpty() || tag != null) { "not an ID3 tag" }
        val major: Int
        val revision: Int
        val frames: List<Id3Frame>
        val audioOffset: Int
        val declared: Int
        val hadFooter: Boolean
        if (tag == null) {
            major = 4; revision = 0; frames = emptyList(); audioOffset = 0; declared = 0; hadFooter = false
        } else {
            require(tag.isSupported) { "ID3v2.${tag.majorVersion} not supported" }
            require(tag.audioOffset <= tagBytes.size) { "truncated ID3 tag" }
            major = tag.majorVersion; revision = tag.revision; frames = tag.frames
            audioOffset = tag.audioOffset; declared = tag.declaredSize; hadFooter = tag.hadFooter
        }
        val newFrames = ArrayList<Id3Frame>()
        // A new cover replaces the one the source file carries; without one,
        // the existing cover stays (it was never ours to drop).
        val kept = frames.filter { it.id != "APIC" || cover == null }
        if (lyrics != null) {
            // The plain frame carries the timings too, as LRC text: SYLT is the frame
            // the spec intends, but support for it is patchy, and several players read
            // `[mm:ss.xx]` prefixes out of the plain frame instead.
            val lrc = lrcText(lyrics)
            newFrames += kept.filter { it.id != "SYLT" && it.id != "USLT" }
            if (lyrics.synced && lyrics.lines.isNotEmpty()) newFrames += buildSyltFrame(lyrics, major)
            newFrames += buildUsltFrame(lrc, major)
        } else {
            // No lyrics found: the frames the source file carries stay as they are.
            newFrames += kept
        }
        if (cover != null) newFrames += buildApicFrame(cover, major)
        val framesSize = newFrames.sumOf { 10 + it.body.size }
        val fitsInPlace = audioOffset > 0 && !hadFooter && framesSize <= declared
        val newTag = if (fitsInPlace) buildTag(newFrames, major, revision, bodySize = declared)
            else buildTag(newFrames, major, revision)
        return RebuiltMp3Tag(newTag, audioOffset, fitsInPlace)
    }

    /** The USLT text of [tagBytes], or null. Read-only, for verification. */
    internal fun readMp3Uslt(tagBytes: ByteArray): String? {
        val tag = parseId3(tagBytes) ?: return null
        if (!tag.isSupported) return null
        val frame = tag.frames.firstOrNull { it.id == "USLT" } ?: return null
        val body = frame.body
        if (body.size < 4) return null
        val encoding = body.u8(0)
        val termLen = terminatorLength(encoding)
        var i = 4 // past encoding + language: the descriptor, up to its terminator.
        if (termLen == 2) {
            while (i + 1 < body.size && !(body[i] == 0.toByte() && body[i + 1] == 0.toByte())) i += 2
            i += 2
        } else {
            while (i < body.size && body[i] != 0.toByte()) i++
            i += 1
        }
        if (i > body.size) return null
        return decodeId3Text(body.copyOfRange(i, body.size), encoding)
    }

    /** The image bytes of [tagBytes]'s APIC frame, or null. Read-only, for verification. */
    internal fun readMp3Apic(tagBytes: ByteArray): ByteArray? {
        val tag = parseId3(tagBytes) ?: return null
        if (!tag.isSupported) return null
        val frame = tag.frames.firstOrNull { it.id == "APIC" } ?: return null
        val body = frame.body
        if (body.size < 4) return null
        val encoding = body.u8(0)
        val termLen = terminatorLength(encoding)
        var i = 1 // past the encoding: the MIME type, up to its null byte.
        while (i < body.size && body[i] != 0.toByte()) i++
        i += 2 // the MIME terminator, then the picture-type byte.
        if (i > body.size) return null
        // The description, up to its terminator for this encoding.
        if (termLen == 2) {
            while (i + 1 < body.size && !(body[i] == 0.toByte() && body[i + 1] == 0.toByte())) i += 2
            i += 2
        } else {
            while (i < body.size && body[i] != 0.toByte()) i++
            i += 1
        }
        if (i > body.size) return null
        return body.copyOfRange(i, body.size)
    }

    // ── M4A (MP4) ──

    private val LYR_TYPE = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())

    private data class AtomHeader(val offset: Int, val size: Long, val headerSize: Int, val type: ByteArray)

    private fun ByteArray.u32(offset: Int): Long =
        ((u8(offset).toLong() shl 24) or (u8(offset + 1).toLong() shl 16) or
            (u8(offset + 2).toLong() shl 8) or u8(offset + 3).toLong())

    private fun ByteArray.u64(offset: Int): Long = (u32(offset) shl 32) or u32(offset + 4)

    private fun u32Bytes(value: Long): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(), ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(), (value and 0xFF).toByte(),
    )

    private fun u64Bytes(value: Long): ByteArray = u32Bytes(value ushr 32) + u32Bytes(value and 0xFFFFFFFFL)

    private fun typeBytes(type: String): ByteArray = type.toByteArray(Charsets.US_ASCII)

    /** A parsed atom header, bounded by [end] — the end of the enclosing atom, not the file. */
    private fun readHeader(bytes: ByteArray, offset: Int, end: Int): AtomHeader? {
        if (offset + 8 > end) return null
        var size = bytes.u32(offset)
        var headerSize = 8
        if (size == 1L) {
            if (offset + 16 > end) return null
            size = bytes.u64(offset + 8)
            headerSize = 16
        } else if (size == 0L) {
            size = (end - offset).toLong() // extends to the end of the enclosing atom
        }
        if (size < headerSize || offset + size > end) return null
        return AtomHeader(offset, size, headerSize, bytes.copyOfRange(offset + 4, offset + 8))
    }

    private fun looksLikeType(type: ByteArray): Boolean {
        // Atom types are 4 printable ASCII bytes, or 0xA9 + 3 ASCII (©xyz).
        var i = if (type[0] == 0xA9.toByte()) 1 else 0
        while (i < 4) {
            val c = type[i].toInt() and 0xFF
            if (c < 0x20 || c > 0x7E) return false
            i++
        }
        return true
    }

    private fun parseChildren(bytes: ByteArray, base: Int, end: Int, childrenStart: Int = 0): List<AtomHeader> {
        val atoms = ArrayList<AtomHeader>()
        var offset = base + childrenStart
        while (offset + 8 <= end) {
            val header = readHeader(bytes, offset, end) ?: break
            if (!looksLikeType(header.type)) {
                // Custom atom with a non-ASCII type: skip it by its (sane) size instead
                // of stopping, so a later udta/meta/ilst isn't missed and duplicated.
                if (header.size >= 8 && offset + header.size <= end) {
                    offset += header.size.toInt()
                    continue
                }
                break
            }
            atoms += header
            offset += header.size.toInt()
        }
        return atoms
    }

    private fun findByType(atoms: List<AtomHeader>, type: ByteArray): AtomHeader? =
        atoms.firstOrNull { it.type.contentEquals(type) }

    private fun makeAtom(type: ByteArray, payload: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(u32Bytes((8 + payload.size).toLong()))
        out.write(type)
        out.write(payload)
        return out.toByteArray()
    }

    /** A complete `©lyr` atom with a `data` child holding UTF-8 [text]. */
    private fun buildLyrAtom(text: String): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val data = java.io.ByteArrayOutputStream().apply {
            write(u32Bytes((16 + textBytes.size).toLong()))
            write(typeBytes("data"))
            write(u32Bytes(1)) // UTF-8
            write(u32Bytes(0)) // locale
            write(textBytes)
        }.toByteArray()
        return makeAtom(LYR_TYPE, data)
    }

    private val COVR_TYPE = typeBytes("covr")

    /** A `covr` atom: the image in a `data` child (JPEG = 13, PNG = 14), locale 0. */
    private fun buildCovrAtom(cover: CoverImage): ByteArray {
        val data = java.io.ByteArrayOutputStream().apply {
            write(u32Bytes((16 + cover.bytes.size).toLong()))
            write(typeBytes("data"))
            write(u32Bytes(if (cover.isJpeg) 13L else 14L))
            write(u32Bytes(0)) // locale
            write(cover.bytes)
        }.toByteArray()
        return makeAtom(COVR_TYPE, data)
    }

    /** A `meta` payload with the `hdlr` the iTunes spec requires (handler `mdir`). */
    private fun buildMetaPayload(ilst: ByteArray): ByteArray {
        val hdlr = makeAtom(
            typeBytes("hdlr"),
            java.io.ByteArrayOutputStream().apply {
                write(ByteArray(4)) // version + flags
                write(ByteArray(4)) // pre_defined
                write(typeBytes("mdir")) // handler_type: metadata
                write(ByteArray(12)) // reserved
                write(0) // empty name, null-terminated
            }.toByteArray(),
        )
        val out = java.io.ByteArrayOutputStream()
        out.write(ByteArray(4)) // meta version/flags
        out.write(hdlr)
        out.write(ilst)
        return out.toByteArray()
    }

    /** Rebuilds [payload], replacing the direct child [childType] with [newChild] (appended if absent). */
    private fun rebuildWithChild(
        payload: ByteArray, childType: ByteArray, newChild: ByteArray?, childrenStart: Int = 0,
    ): ByteArray {
        val children = parseChildren(payload, 0, payload.size, childrenStart)
        val existing = findByType(children, childType)
        val out = java.io.ByteArrayOutputStream()
        out.write(payload, 0, childrenStart)
        if (existing == null) {
            out.write(payload, childrenStart, payload.size - childrenStart)
            if (newChild != null) out.write(newChild)
        } else {
            out.write(payload, childrenStart, existing.offset - childrenStart)
            if (newChild != null) out.write(newChild)
            val after = existing.offset + existing.size.toInt()
            out.write(payload, after, payload.size - after)
        }
        return out.toByteArray()
    }

    /** Rebuilds the moov payload with [updateIlst] applied to the `udta > meta > ilst` chain, creating any missing parents. */
    private fun rebuildMoovWithIlst(moovPayload: ByteArray, updateIlst: (ByteArray) -> ByteArray): ByteArray {
        val udta = findByType(parseChildren(moovPayload, 0, moovPayload.size), typeBytes("udta"))
        if (udta == null) {
            val newIlst = updateIlst(ByteArray(0))
            if (newIlst.isEmpty()) return moovPayload // nothing to remove
            val meta = makeAtom(typeBytes("meta"), buildMetaPayload(makeAtom(typeBytes("ilst"), newIlst)))
            return rebuildWithChild(moovPayload, typeBytes("udta"), makeAtom(typeBytes("udta"), meta))
        }
        val udtaPayload = moovPayload.copyOfRange(udta.offset + udta.headerSize, (udta.offset + udta.size).toInt())
        val meta = findByType(parseChildren(udtaPayload, 0, udtaPayload.size), typeBytes("meta"))
        if (meta == null) {
            val newIlst = updateIlst(ByteArray(0))
            if (newIlst.isEmpty()) return moovPayload
            val ilst = makeAtom(typeBytes("ilst"), newIlst)
            val newMeta = makeAtom(typeBytes("meta"), buildMetaPayload(ilst))
            val newUdta = rebuildWithChild(udtaPayload, typeBytes("meta"), newMeta)
            return rebuildWithChild(moovPayload, typeBytes("udta"), makeAtom(typeBytes("udta"), newUdta))
        }
        val metaPayload = udtaPayload.copyOfRange(meta.offset + meta.headerSize, (meta.offset + meta.size).toInt())
        val ilst = findByType(parseChildren(metaPayload, 0, metaPayload.size, 4), typeBytes("ilst"))
        if (ilst == null) {
            val newIlst = updateIlst(ByteArray(0))
            if (newIlst.isEmpty()) return moovPayload
            val newMeta = rebuildWithChild(metaPayload, typeBytes("ilst"), makeAtom(typeBytes("ilst"), newIlst), 4)
            val newUdta = rebuildWithChild(udtaPayload, typeBytes("meta"), makeAtom(typeBytes("meta"), newMeta))
            return rebuildWithChild(moovPayload, typeBytes("udta"), makeAtom(typeBytes("udta"), newUdta))
        }
        val ilstPayload = metaPayload.copyOfRange(ilst.offset + ilst.headerSize, (ilst.offset + ilst.size).toInt())
        val newIlstPayload = updateIlst(ilstPayload)
        val newMeta = rebuildWithChild(metaPayload, typeBytes("ilst"), makeAtom(typeBytes("ilst"), newIlstPayload), 4)
        val newUdta = rebuildWithChild(udtaPayload, typeBytes("meta"), makeAtom(typeBytes("meta"), newMeta))
        return rebuildWithChild(moovPayload, typeBytes("udta"), makeAtom(typeBytes("udta"), newUdta))
    }

    /** Adds [delta] to every `stco`/`co64` entry at or past [shiftFrom] under [moovBytes]. */
    private fun shiftChunkOffsets(moovBytes: ByteArray, delta: Long, shiftFrom: Long): ByteArray {
        val bytes = moovBytes.copyOf()
        val headerSize = readHeader(bytes, 0, bytes.size)?.headerSize ?: 8
        for (trak in parseChildren(bytes, headerSize, bytes.size).filter { it.type.contentEquals(typeBytes("trak")) }) {
            fun single(parent: AtomHeader, type: String, childrenStart: Int = 0): AtomHeader? {
                val start = parent.offset + parent.headerSize + childrenStart
                val end = (parent.offset + parent.size).toInt()
                return findByType(parseChildren(bytes, start, end), typeBytes(type))
            }
            val mdia = single(trak, "mdia") ?: continue
            val minf = single(mdia, "minf") ?: continue
            val stbl = single(minf, "stbl") ?: continue
            single(stbl, "stco")?.let { shiftEntries(bytes, it, delta, 4, shiftFrom) }
            single(stbl, "co64")?.let { shiftEntries(bytes, it, delta, 8, shiftFrom) }
        }
        return bytes
    }

    /** Shifts the chunk-offset entries of one `stco` (4-byte) or `co64` (8-byte) atom, in place. */
    private fun shiftEntries(bytes: ByteArray, atom: AtomHeader, delta: Long, entrySize: Int, shiftFrom: Long) {
        // version/flags(4) + entry_count(4), then entries.
        val base = atom.offset + atom.headerSize
        if (base + 8 > atom.offset + atom.size) return
        val count = bytes.u32(base + 4)
        var pos = base + 8
        var remaining = count
        while (remaining > 0 && pos + entrySize <= atom.offset + atom.size) {
            val value = if (entrySize == 4) bytes.u32(pos) else bytes.u64(pos)
            if (value >= shiftFrom) {
                if (entrySize == 4) u32Bytes(value + delta).copyInto(bytes, pos)
                else u64Bytes(value + delta).copyInto(bytes, pos)
            }
            remaining--
            pos += entrySize
        }
    }

    /**
     * The whole file with the `©lyr` atom replaced/added. Throws when the structure
     * can't be navigated safely.
     */
    internal fun embedM4a(bytes: ByteArray, lrc: String, cover: CoverImage? = null): ByteArray {
        val top = parseChildren(bytes, 0, bytes.size)
        val moov = findByType(top, typeBytes("moov")) ?: throw IllegalArgumentException("no moov atom")
        val moovPayload = bytes.copyOfRange(moov.offset + moov.headerSize, (moov.offset + moov.size).toInt())
        val newMoovPayload = rebuildMoovWithIlst(moovPayload) { ilstPayload ->
            // A blank LRC leaves ©lyr as it is: no lyrics found must not fabricate
            // an empty frame, nor delete one the source file carries.
            var p = ilstPayload
            if (lrc.isNotBlank()) p = rebuildWithChild(p, LYR_TYPE, buildLyrAtom(lrc))
            if (cover != null) p = rebuildWithChild(p, COVR_TYPE, buildCovrAtom(cover))
            p
        }
        val newMoov = makeAtom(typeBytes("moov"), newMoovPayload)
        val delta = (newMoov.size - moov.size).toLong()
        // Fix chunk offsets for the chunks that sit after moov: growing moov pushes
        // them down by `delta`. The threshold is the end of the old moov: every chunk
        // offset at or past it moved by exactly `delta`, whichever `mdat` it lives in.
        val fixedMoov = if (delta != 0L) shiftChunkOffsets(newMoov, delta, moov.offset + moov.size) else newMoov
        val out = java.io.ByteArrayOutputStream()
        out.write(bytes, 0, moov.offset)
        out.write(fixedMoov)
        val after = (moov.offset + moov.size).toInt()
        out.write(bytes, after, bytes.size - after)
        return out.toByteArray()
    }

    /** The `©lyr` text of [bytes], or null. Read-only, for verification. */
    internal fun readM4aLyrics(bytes: ByteArray): String? {
        val top = parseChildren(bytes, 0, bytes.size)
        val moov = findByType(top, typeBytes("moov")) ?: return null
        var current = bytes.copyOfRange(moov.offset + moov.headerSize, (moov.offset + moov.size).toInt())
        var childrenStart = 0
        for (step in listOf("udta", "meta", "ilst")) {
            val found = findByType(parseChildren(current, 0, current.size, childrenStart), typeBytes(step)) ?: return null
            current = current.copyOfRange(found.offset + found.headerSize, (found.offset + found.size).toInt())
            childrenStart = if (step == "meta") 4 else 0
        }
        val lyr = findByType(parseChildren(current, 0, current.size), LYR_TYPE) ?: return null
        val payload = current.copyOfRange(lyr.offset + lyr.headerSize, (lyr.offset + lyr.size).toInt())
        val data = findByType(parseChildren(payload, 0, payload.size), typeBytes("data")) ?: return null
        val start = data.offset + data.headerSize + 8 // type(4) + locale(4), then text
        val end = (data.offset + data.size).toInt()
        if (start > end) return null
        val type = payload.u32(data.offset + data.headerSize)
        val textBytes = payload.copyOfRange(start, end)
        return when (type) {
            2L -> decodeUtf16(textBytes)
            0L, 1L -> textBytes.toString(Charsets.UTF_8)
            else -> null
        }
    }

    /** The image bytes of [bytes]'s `covr` atom, or null. Read-only, for verification. */
    internal fun readM4aCover(bytes: ByteArray): ByteArray? {
        val top = parseChildren(bytes, 0, bytes.size)
        val moov = findByType(top, typeBytes("moov")) ?: return null
        var current = bytes.copyOfRange(moov.offset + moov.headerSize, (moov.offset + moov.size).toInt())
        var childrenStart = 0
        for (step in listOf("udta", "meta", "ilst")) {
            val found = findByType(parseChildren(current, 0, current.size, childrenStart), typeBytes(step)) ?: return null
            current = current.copyOfRange(found.offset + found.headerSize, (found.offset + found.size).toInt())
            childrenStart = if (step == "meta") 4 else 0
        }
        val covr = findByType(parseChildren(current, 0, current.size), COVR_TYPE) ?: return null
        val payload = current.copyOfRange(covr.offset + covr.headerSize, (covr.offset + covr.size).toInt())
        val data = findByType(parseChildren(payload, 0, payload.size), typeBytes("data")) ?: return null
        val start = data.offset + data.headerSize + 8 // type(4) + locale(4), then image
        val end = (data.offset + data.size).toInt()
        if (start > end) return null
        return payload.copyOfRange(start, end)
    }

    /** UTF-16 text, big-endian unless a little-endian BOM says otherwise. */
    private fun decodeUtf16(bytes: ByteArray): String {
        if (bytes.size < 2) return ""
        var littleEndian = false
        var offset = 0
        if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) offset = 2
        else if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) { littleEndian = true; offset = 2 }
        val units = CharArray((bytes.size - offset) / 2) { i ->
            val a = bytes[offset + i * 2].toInt() and 0xFF
            val b = bytes[offset + i * 2 + 1].toInt() and 0xFF
            if (littleEndian) (a or (b shl 8)).toChar() else ((a shl 8) or b).toChar()
        }
        return String(units)
    }

    // ── File level ──

    /**
     * Embeds [lyrics] and [cover] into [file] (format by [ext]: flac, mp3, m4a).
     * Either may be absent: no lyrics leaves the file's own lyrics frames alone,
     * no cover leaves its cover alone. Returns true when the file was rewritten
     * with lyrics and/or cover; false leaves the file untouched.
     * A visible FLAC also gets a `.lrc` sidecar next to it, which is what Musync
     * reads for FLAC (it does not read Vorbis LYRICS).
     */
    suspend fun embed(file: File, ext: String, lyrics: Lyrics?, cover: ByteArray?, public: Boolean): Boolean {
        val lrc = lyrics?.let { lrcText(it) } ?: ""
        val image = coverImage(cover)
        if (lrc.isBlank() && image == null) return false
        return embedMutex.withLock {
            val ok = runCatching {
                when (ext.lowercase()) {
                    "flac" -> embedFlacFile(file, lrc, image)
                    "mp3" -> embedMp3File(file, lyrics, lrc, image)
                    "m4a" -> embedM4aFile(file, lrc, image)
                    else -> false
                }
            }.getOrDefault(false)
            if (ok && public && ext.equals("flac", ignoreCase = true) && lrc.isNotBlank()) {
                runCatching { writeSidecarLrc(file, lrc) }
            }
            ok
        }
    }

    private fun readFully(input: java.io.InputStream, size: Int): ByteArray? {
        val buf = ByteArray(size)
        var off = 0
        while (off < size) {
            val n = input.read(buf, off, size - off)
            if (n < 0) return null
            off += n
        }
        return buf
    }

    private fun skipFully(input: java.io.InputStream, n: Long): Boolean {
        var remaining = n
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) return false
            remaining -= skipped
        }
        return true
    }

    private fun uniqueTemp(file: File): File {
        val stamp = System.currentTimeMillis()
        val rand = Random.nextInt().toString(16)
        val parent = file.parentFile ?: File(".")
        return File(parent, "${file.name}.$stamp-$rand.orpheon.tmp")
    }

    /** Deletes temp files of earlier writes on this track that a kill or crash left behind (older than ten minutes). */
    private fun deleteStaleTemps(file: File) {
        try {
            val parent = file.parentFile ?: return
            val prefix = "${file.name}."
            val suffix = ".orpheon.tmp"
            val cutoff = System.currentTimeMillis() - 10 * 60 * 1000
            parent.listFiles()?.forEach { f ->
                if (f.name.startsWith(prefix) && f.name.endsWith(suffix) && f.lastModified() < cutoff) {
                    runCatching { f.delete() }
                }
            }
        } catch (_: Exception) {
            // Best effort — a leftover temp is harmless, and a cleanup failure must
            // not fail the write itself.
        }
    }

    /** `<basename>.lrc` next to the audio file — what Musync reads for a FLAC. */
    private fun writeSidecarLrc(file: File, lrc: String) {
        val base = file.name.substringBeforeLast('.')
        val sidecar = File(file.parentFile, "$base.lrc")
        writeTextSafely(sidecar, lrc, sync = false)
    }

    /** The metadata head of a FLAC stream (magic + blocks up to the last one), or null. */
    private fun readFlacHead(input: java.io.InputStream): ByteArray? {
        val buf = java.io.ByteArrayOutputStream()
        val magic = readFully(input, 4) ?: return null
        if (!magic.startsWithAscii("fLaC")) return null
        buf.write(magic)
        while (true) {
            val header = readFully(input, 4) ?: return null
            buf.write(header)
            val last = (header[0].toInt() and 0x80) != 0
            val size = (header.u8(1) shl 16) or (header.u8(2) shl 8) or header.u8(3)
            if (buf.size() + size > MAX_FLAC_HEAD_BYTES) return null
            val payload = readFully(input, size) ?: return null
            buf.write(payload)
            if (last) break
        }
        return buf.toByteArray()
    }

    private fun embedFlacFile(file: File, lrc: String, cover: CoverImage?): Boolean {
        val temp = uniqueTemp(file)
        try {
            deleteStaleTemps(file)
            val newHead: ByteArray
            val audioOffset: Int
            val audioLength: Long
            file.inputStream().use { input ->
                val head = readFlacHead(input) ?: return false
                audioOffset = flacAudioOffset(head)
                if (audioOffset < 0 || audioOffset != head.size) return false
                newHead = rebuildFlacHead(head, lrc, cover)
                audioLength = file.length() - audioOffset
                temp.outputStream().use { output ->
                    output.write(newHead)
                    val chunk = ByteArray(COPY_CHUNK)
                    while (true) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        output.write(chunk, 0, n)
                    }
                    output.flush()
                }
            }
            // Verify the temp: the LYRICS and PICTURE it carries are the ones written,
            // and the audio behind it is the same length.
            if (temp.length() != newHead.size.toLong() + audioLength) return false
            val tempHead = temp.inputStream().use { readFully(it, newHead.size) } ?: return false
            if (lrc.isNotBlank() && readFlacLyrics(tempHead) != lrc) return false
            if (cover != null && readFlacPicture(tempHead)?.contentEquals(cover.bytes) != true) return false
            java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            return true
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun readMp3TagHead(file: File): ByteArray? {
        file.inputStream().use { input ->
            val header = readFully(input, 10) ?: return null
            if (!header.startsWithAscii("ID3")) return ByteArray(0) // no tag: audio starts at 0
            val declared = header.synch(6)
            if (10 + declared > MAX_TAG_BYTES) return null
            val hadFooter = header.u8(3) >= 4 && (header.u8(5) and 0x10) != 0
            val total = 10 + declared + if (hadFooter) 10 else 0
            if (file.length() < total) return null
            val rest = readFully(input, total - 10) ?: return null
            return header + rest
        }
    }

    private fun embedMp3File(file: File, lyrics: Lyrics?, lrc: String, cover: CoverImage?): Boolean {
        val tagHead = readMp3TagHead(file) ?: return false
        val rebuilt = runCatching { rebuildMp3Tag(tagHead, lyrics, cover) }.getOrNull() ?: return false
        if (rebuilt.fitsInPlace) return overwriteMp3Head(file, rebuilt.tagBytes)
        return embedMp3Slow(file, rebuilt.tagBytes, rebuilt.audioOffset, lrc, cover)
    }

    /**
     * Overwrites the tag region without touching the audio behind it, then verifies
     * the write by re-reading the head and comparing it byte for byte. The fast path
     * has no copy to fall back on, so a short or torn write must surface as a failure
     * here, not as a success the caller believes.
     */
    private fun overwriteMp3Head(file: File, newTag: ByteArray): Boolean {
        return try {
            RandomAccessFile(file, "rw").use { handle ->
                handle.seek(0)
                handle.write(newTag)
            }
            val written = file.inputStream().use { readFully(it, newTag.size) }
            written != null && written.contentEquals(newTag)
        } catch (_: Exception) {
            false
        }
    }

    private fun embedMp3Slow(file: File, newTag: ByteArray, audioOffset: Int, lrc: String, cover: CoverImage?): Boolean {
        val temp = uniqueTemp(file)
        try {
            deleteStaleTemps(file)
            file.inputStream().use { input ->
                if (!skipFully(input, audioOffset.toLong())) return false
                temp.outputStream().use { output ->
                    output.write(newTag)
                    val chunk = ByteArray(COPY_CHUNK)
                    while (true) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        output.write(chunk, 0, n)
                    }
                    output.flush()
                }
            }
            if (temp.length() != newTag.size.toLong() + (file.length() - audioOffset)) return false
            val tempTag = temp.inputStream().use { readFully(it, newTag.size) } ?: return false
            if (!tempTag.contentEquals(newTag)) return false
            if (lrc.isNotBlank() && readMp3Uslt(tempTag) != lrc) return false
            if (cover != null && readMp3Apic(tempTag)?.contentEquals(cover.bytes) != true) return false
            java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            return true
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun embedM4aFile(file: File, lrc: String, cover: CoverImage?): Boolean {
        if (file.length() > MAX_FILE_BYTES) return false
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return false
        val newBytes = runCatching { embedM4a(bytes, lrc, cover) }.getOrNull() ?: return false
        val temp = uniqueTemp(file)
        try {
            deleteStaleTemps(file)
            temp.writeBytes(newBytes)
            // Verify the temp: a sane MP4 whose ©lyr and covr are the ones written.
            val written = runCatching { temp.readBytes() }.getOrNull() ?: return false
            if (lrc.isNotBlank() && readM4aLyrics(written) != lrc) return false
            if (cover != null && readM4aCover(written)?.contentEquals(cover.bytes) != true) return false
            java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            return true
        } finally {
            if (temp.exists()) temp.delete()
        }
    }
}
