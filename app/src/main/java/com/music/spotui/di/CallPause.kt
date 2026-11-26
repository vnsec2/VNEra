/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.di

/**
 * Fallback for playback that needs an explicit call pause. Resume only what we paused, unless the
 * user acted meanwhile, enabled playback during calls, or the pause is too old.
 */
internal class CallPause(private val longestMs: Long = 30 * 60_000L) {
    enum class Action { PAUSE, RESUME, NONE }

    private var heldAt: Long? = null

    fun modeChanged(busy: Boolean, playing: Boolean, nowMs: Long, allowMusic: Boolean = false): Action {
        if (allowMusic) {
            heldAt = null
            return Action.NONE
        }
        val held = heldAt
        return when {
            busy && held == null && playing -> { heldAt = nowMs; Action.PAUSE }
            busy -> Action.NONE
            held == null -> Action.NONE
            else -> { heldAt = null; if (nowMs - held <= longestMs) Action.RESUME else Action.NONE }
        }
    }

    fun userActed() { heldAt = null }
}

/** Never change normal media-focus handling merely because the preference is on. */
internal fun shouldRelaxCallFocus(optedIn: Boolean, busy: Boolean): Boolean = optedIn && busy
