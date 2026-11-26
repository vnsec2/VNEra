package com.music.spotui.extensions
import app.myspoti.host.android.AppLog

import app.myspoti.extensions.ExtensionMediaHttp

/** The app's one door for fetching an address an extension gave (see [ExtensionMediaHttp]); a refused hop goes to the log. */
internal object MediaHttp {
    val http = ExtensionMediaHttp(log = { AppLog.d(it) })

    fun open(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        fixed: Map<String, String> = emptyMap(),
        timeoutMs: Int = 15_000,
    ): ExtensionMediaHttp.Opened = http.open(url, method, headers, fixed, timeoutMs)
}
