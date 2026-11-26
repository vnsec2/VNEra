/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.di

/**
 * The Spotify page in our Web Player plays a song unheard next to our local player (the listen
 * sync's mirror). Chromium asks Android for the audio focus for that page, and the system answers by
 * pausing the active media session — ours — although the sound comes from the local player. The
 * report we got has that pause 37 ms after the page moved to the mirrored song.
 *
 * Only a pause that lands shortly after such a page play is the echo; a pause asked later (a
 * notification button, a headset, the car, another app) is real and is still obeyed. A state read
 * again while the same song plays does not start a new window.
 */
internal class MirrorPauseGuard(private val windowMs: Long = WINDOW_MS) {
    @Volatile private var mirroredAtMs = 0L
    private var lastKey: String? = null

    /** The mirrored page's state: note only the moment it starts a song, not every refresh. */
    fun pageState(uri: String?, playing: Boolean, nowMs: Long) {
        val key = "${uri.orEmpty()}|$playing"
        if (key == lastKey) return
        lastKey = key
        if (playing) mirroredAtMs = nowMs
    }

    /** The mirror is over: no later pause can be its echo. */
    fun cleared() { lastKey = null; mirroredAtMs = 0L }

    fun echoes(nowMs: Long): Boolean {
        val at = mirroredAtMs
        return at != 0L && nowMs >= at && nowMs - at <= windowMs
    }

    companion object {
        /** Long enough for the focus handover, short enough not to swallow a user's pause. */
        const val WINDOW_MS = 1_000L
    }
}
