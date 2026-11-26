package app.myspoti.host.android

import android.util.Log
import app.myspoti.spotify.bridge.LogMask

/** Sanitized app diagnostics written to Android Logcat. */
object AppLog {
    fun d(message: String) {
        Log.d("Myspoti", LogMask.shown(message, MAX_MESSAGE_LENGTH))
    }

    private const val MAX_MESSAGE_LENGTH = 2_000
}
