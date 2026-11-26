/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.automix

import app.myspoti.extensions.AudioStream
import java.security.MessageDigest

/** Cache the recording, not the expiring access token or just its catalogue track id. */
internal object AnalysisIdentity {
    fun digest(id: String): String = MessageDigest.getInstance("SHA-1").digest(id.toByteArray())
        .joinToString("") { "%02x".format(it) }.take(24)

    fun stream(track: String, provider: String?, durationMs: Long?, codec: String?, source: String?, url: String, manifest: String?): String =
        fields(track, provider, durationMs, codec, source, manifest != null, manifest?.let(::manifestIdentity) ?: stableAddress(url))

    fun fields(vararg values: Any?): String = values.joinToString("") { v -> v?.toString()?.let { "${it.length}:$it" } ?: "-:" }

    fun encoding(s: AudioStream): String = digest(fields(
        "encoding-v2", stream("", s.providerId, s.durationMs, s.codec, s.source, s.url, s.manifest),
        s.bitDepth, s.sampleRate, s.mimeType, s.transform, s.recordingId,
        s.headers.toSortedMap().entries.joinToString("") { fields(it.key, it.value) },
    ))

    fun exact(track: String, stream: AudioStream): String = digest(fields("stream-v2", track, encoding(stream)))

    fun recording(track: String, stream: AudioStream?): RecordingIdentity? {
        val provider = stream?.providerId ?: return null
        val id = stream.recordingId ?: return null
        val duration = stream.durationMs ?: return null
        return RecordingIdentity(track, provider, id, duration)
    }

    fun file(uri: String, size: Long, modifiedAt: Long): String = "$uri|$size|$modifiedAt"

    private val signedParameter = Regex("([?&](?:amp;)?(?:token|sig|signature|expires|policy|key-pair-id|hdnts|X-Amz-[^=\\s]+)=)[^&\\s\"<>]*", RegexOption.IGNORE_CASE)

    private fun stableAddress(value: String): String = signedParameter.replace(value) { it.groupValues[1] }

    /** Read once on the worker, never for every player tick. Preserve media paths and non-auth parameters. */
    private fun manifestIdentity(encoded: String): String = runCatching {
        stableAddress(String(java.util.Base64.getMimeDecoder().decode(encoded), Charsets.UTF_8))
    }.getOrElse { stableAddress(encoded) }
}
