/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.automix

import app.myspoti.automix.Grid
import app.myspoti.automix.TrackAnalysis
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.math.abs

/** Provider-asserted identity of a recording AND its playback timeline, not a media cache key. */
internal data class RecordingIdentity(val track: String, val provider: String, val recording: String, val durationMs: Long) {
    private val usable get() = track.isNotBlank() && provider.isNotBlank() && recording.isNotBlank() && validDuration(durationMs)
    val fileName: String? get() = if (usable) "recording-v1-" + AnalysisIdentity.digest(
        AnalysisIdentity.fields(track, provider, recording),
    ) else null

    fun accepts(producerDurationMs: Long, analysis: TrackAnalysis): Boolean {
        if (!usable || !validDuration(producerDurationMs) || abs(durationMs - producerDurationMs) > TOLERANCE_MS) return false
        if (abs(analysis.duration * 1000 - durationMs) > TOLERANCE_MS ||
            abs(analysis.duration * 1000 - producerDurationMs) > TOLERANCE_MS) return false
        return analysis.timelineFits(minOf(analysis.duration, durationMs / 1000.0, producerDurationMs / 1000.0))
    }

    private fun validDuration(ms: Long) = ms in 5_000..1_080_000

    companion object {
        // Allow minor container rounding, not a different cut or a timebase to scale cues onto.
        private const val TOLERANCE_MS = 50L
    }
}

/** Stores only derived analysis. Exact-source entries remain available when sharing is unproven. */
internal class AnalysisDiskCache(private val dir: File) {
    fun load(exact: String, recording: RecordingIdentity?): TrackAnalysis? {
        read(exact) { TrackAnalysis.read(it) }?.let { return it }
        val shared = recording?.fileName ?: return null
        return read(shared) { input ->
            val producerDurationMs = input.readLong()
            TrackAnalysis.read(input)?.takeIf { recording.accepts(producerDurationMs, it) }
        }
    }

    fun save(exact: String, analysis: TrackAnalysis, recording: RecordingIdentity?) {
        write(exact) { analysis.write(it) }
        val shared = recording?.fileName
        if (shared != null && recording.accepts(recording.durationMs, analysis)) {
            write(shared) { it.writeLong(recording.durationMs); analysis.write(it) }
        }
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        AnalysisCache.toTrim(files.map { AnalysisCache.Entry(it.name, it.length(), it.lastModified()) })
            .forEach { File(dir, it).delete() }
    }

    fun clear() { dir.listFiles()?.forEach { it.delete() } }
    fun bytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun read(name: String, read: (DataInputStream) -> TrackAnalysis?): TrackAnalysis? = runCatching {
        val file = File(dir, name)
        if (!file.isFile || file.length() !in 1L..MAX_ENTRY_BYTES) return null
        file.inputStream().buffered().use { read(DataInputStream(it)) }?.also { file.setLastModified(System.currentTimeMillis()) }
    }.getOrNull()

    private fun write(name: String, write: (DataOutputStream) -> Unit) {
        runCatching {
            dir.mkdirs()
            val tmp = File.createTempFile("analysis-", ".tmp", dir)
            try {
                DataOutputStream(tmp.outputStream().buffered()).use(write)
                Files.move(tmp.toPath(), File(dir, name).toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } finally { tmp.delete() }
        }
    }

    companion object {
        // A normal entry is tens of KB. Refuse unexpectedly large or empty cache files.
        private const val MAX_ENTRY_BYTES = 2L * 1024 * 1024
    }
}

private fun TrackAnalysis.timelineFits(end: Double): Boolean {
    fun time(t: Double) = t.isFinite() && t in 0.0..end
    fun times(ts: DoubleArray) = ts.indices.all { i -> time(ts[i]) && (i == 0 || ts[i] > ts[i - 1]) }
    fun grid(g: Grid?, down: Double): Boolean = if (g == null) down.isNaN() else
        g.period.isFinite() && g.period > 0 && g.count > 0 && time(g.first) && time(g.last) &&
            (down.isNaN() || time(down))
    return duration.isFinite() && time(contentStart) && time(contentEnd) && contentStart < contentEnd &&
        times(beats) && times(modelBeats) && times(barStarts) && barStarts.all { it < contentEnd } &&
        grid(head, headDown) && grid(tail, tailDown)
}

/** The analyzer concatenates PCM. Do not share its cues if media timestamps disagree with that clock. */
internal class DecodedTimeline {
    private var previousUs = Long.MIN_VALUE
    private var elapsedUs = 0.0
    private var valid = true
    val shareable get() = valid && elapsedUs > 0

    fun add(timestampUs: Long, frames: Int, sampleRate: Int) {
        if (!valid || frames == 0) return
        // Permit timestamp rounding, but reject leading offsets, backwards timestamps, gaps and drift.
        if (sampleRate <= 0 || timestampUs < 0 || timestampUs <= previousUs || abs(timestampUs - elapsedUs) > 5_000) {
            valid = false
            return
        }
        previousUs = timestampUs
        elapsedUs += frames * 1_000_000.0 / sampleRate
    }
}
