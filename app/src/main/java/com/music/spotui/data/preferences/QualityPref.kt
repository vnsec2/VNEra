package com.music.spotui.data.preferences

/*
 * Sound quality: what the Web Player streams, and how downloads are saved.
 */

import android.content.Context
import kotlinx.coroutines.launch

private const val PREF = "settings_prefs"
private const val KEY_STREAMING = "streaming_quality"
private const val KEY_CHOSEN = "quality_chosen"
private const val KEY_STREAMING_CELL = "streaming_quality_cellular"
private const val KEY_STREAMING_OUTPUT_PREFIX = "streaming_quality_output_"
private const val KEY_CANVAS_WIFI = "canvas_wifi_only"

/**
 * The Web Player's own streaming levels (its "Streaming quality" setting): 24, 96 and
 * 160 kbit/s. Only the levels a free account gets; "very_high" (320) is Premium's.
 */
enum class StreamingQuality(val level: String) {
    AUTO("auto"), LOW("low"), NORMAL("normal"), HIGH("high"),
    /** 256 kbit/s: the Web Player gives it to Premium accounts only (offered then, see StreamingQualityPicker). */
    VERY_HIGH("very_high");

    companion object {
        fun of(level: String?): StreamingQuality = entries.firstOrNull { it.level == level } ?: AUTO
    }
}

/**
 * How a downloaded song is saved: not at all, small (AAC re-encoded from the checked FLAC),
 * FLAC 16-bit (CD), or FLAC 24-bit when it exists.
 */
enum class DownloadQuality {
    OFF, SMALL, CD, HI_RES,
    /** Asked when a download starts (once for a whole playlist or album). */
    ASK;

    companion object {
        /** A quality a file can be saved in ([ASK] and [OFF] are not); null when unknown. */
        fun saved(name: String?): DownloadQuality? = entries.firstOrNull { it.name == name }?.takeIf { it != ASK && it != OFF }
    }
}

/**
 * The level for Wi-Fi, or for mobile data ([cellular]), as the official app's two settings.
 * Mobile data follows the Wi-Fi level until it is set on its own.
 */
fun getStreamingQuality(c: Context, cellular: Boolean = false): StreamingQuality {
    // The account's own levels (see ProfilePrefs).
    val wifi = ProfilePrefs.getString(prefs(c), KEY_STREAMING, null)
    return StreamingQuality.of(if (cellular) ProfilePrefs.getString(prefs(c), KEY_STREAMING_CELL, null) ?: wifi else wifi)
}

/** Saves the level for that network and hands the one for the current network to the player. */
fun setStreamingQuality(c: Context, q: StreamingQuality, cellular: Boolean = false) {
    prefs(c).edit().putString(ProfilePrefs.key(if (cellular) KEY_STREAMING_CELL else KEY_STREAMING), q.level).apply()
    applyStreamingQuality(c)
}

fun getOutputStreamingQuality(
    c: Context,
    route: com.music.spotui.ui.components.AudioRoute.Kind,
    cellular: Boolean = false,
): StreamingQuality? {
    val net = if (cellular) "cell" else "wifi"
    val raw = ProfilePrefs.getString(prefs(c), "$KEY_STREAMING_OUTPUT_PREFIX${route.name.lowercase()}_$net", null)
        ?: return null
    return StreamingQuality.entries.firstOrNull { it.level == raw }
}

fun setOutputStreamingQuality(
    c: Context,
    route: com.music.spotui.ui.components.AudioRoute.Kind,
    q: StreamingQuality?,
    cellular: Boolean = false,
) {
    val net = if (cellular) "cell" else "wifi"
    val key = ProfilePrefs.key("$KEY_STREAMING_OUTPUT_PREFIX${route.name.lowercase()}_$net")
    prefs(c).edit().apply {
        if (q == null) remove(key) else putString(key, q.level)
    }.apply()
    applyStreamingQuality(c)
}

fun effectiveStreamingQuality(
    c: Context,
    cellular: Boolean = com.music.spotui.data.DataSaver.metered,
    route: com.music.spotui.ui.components.AudioRoute.Kind = com.music.spotui.ui.components.AudioRoute.current(c).kind,
): StreamingQuality =
    getOutputStreamingQuality(c, route, cellular) ?: getStreamingQuality(c, cellular)

fun applyStreamingQuality(c: Context) {
    val runtime = app.myspoti.host.android.MyspotiRuntime
    if (!runtime.isStarted) return
    val q = effectiveStreamingQuality(c)
    runtime.scope.launch { runtime.spotify.player.setAudioQuality(q.level) }
}

object AudioOutputProfileWatcher {
    @Volatile private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        runCatching {
            val am = app.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            am.registerAudioDeviceCallback(object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>?) {
                    applyStreamingQuality(app)
                }

                override fun onAudioDevicesRemoved(removed: Array<out android.media.AudioDeviceInfo>?) {
                    applyStreamingQuality(app)
                }
            }, null)
        }
    }
}

/** Canvas (the looping videos) only on Wi-Fi: they cost data on every song. */
fun isCanvasWifiOnly(c: Context): Boolean = prefs(c).getBoolean(KEY_CANVAS_WIFI, false)
fun setCanvasWifiOnly(c: Context, on: Boolean) = prefs(c).edit().putBoolean(KEY_CANVAS_WIFI, on).apply()

fun getDownloadQuality(c: Context): DownloadQuality = when {
    !isDownloadsEnabled(c) -> DownloadQuality.OFF
    isAskDownloadQuality(c) -> DownloadQuality.ASK
    isSmallDownloads(c) -> DownloadQuality.SMALL
    isHiRes(c) -> DownloadQuality.HI_RES
    else -> DownloadQuality.CD
}

fun setDownloadQuality(c: Context, q: DownloadQuality) {
    setDownloadsEnabled(c, q != DownloadQuality.OFF)
    setHiRes(c, q == DownloadQuality.HI_RES)
    setSmallDownloads(c, q == DownloadQuality.SMALL)
    setAskDownloadQuality(c, q == DownloadQuality.ASK)
}

/** The quality step of the onboarding was seen (new installs and updates alike). */
fun isQualityChosen(c: Context): Boolean = prefs(c).getBoolean(KEY_CHOSEN, false)
fun setQualityChosen(c: Context) = prefs(c).edit().putBoolean(KEY_CHOSEN, true).apply()

private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
