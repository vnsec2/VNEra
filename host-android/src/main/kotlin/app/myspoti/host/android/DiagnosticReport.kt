package app.myspoti.host.android

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Builds a shareable diagnostic report from the current process's Logcat history. */
object DiagnosticReport {
    private val fileStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    /**
     * What the report's header says of this session, kept apart from the log (which may have cut
     * the line long ago): the account, the network, the devices, the registration... Set from
     * where it is known, the latest value wins.
     */
    private val facts = java.util.concurrent.ConcurrentHashMap<String, String>()
    fun fact(key: String, value: String) { facts[key] = app.myspoti.spotify.bridge.LogMask.redact(value) }

    /** More header lines the app knows of (its extensions), asked when the report is written. */
    @Volatile var headerExtras: (() -> List<Pair<String, String>>)? = null

    fun export(context: Context): File {
        // The external dir is what a FileProvider can expose; the internal one is the
        // fallback for a device with no external storage.
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "diagnostic-${fileStamp.format(Date())}.txt")
        file.writeText(text(context))
        return file
    }

    fun text(context: Context): String = report(context, logcat(context))

    fun recent(context: Context): String = logcat(context).joinToString("\n")

    private fun logcat(context: Context): List<String> = runCatching {
        val file = File.createTempFile("revera-logcat-", ".txt", context.cacheDir)
        try {
            val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "--pid=${android.os.Process.myPid()}")
                .redirectErrorStream(true).redirectOutput(file).start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return emptyList()
            }
            if (process.exitValue() != 0) return emptyList()
            file.readText().lineSequence().filter { it.isNotBlank() }
                .map(app.myspoti.spotify.report.DiagnosticDigest::mask).toList()
        } finally {
            file.delete()
        }
    }.getOrDefault(emptyList())

    /**
     * What the device's Widevine is (security level, HDCP, version): a 403 on the audio licence
     * (seen on a new sign-in, Android 11) or a silent stream cannot be told from the log alone.
     * Read once; a MediaDrm failure is itself the answer.
     */
    private val drmFact: String? by lazy {
        runCatching {
            val drm = android.media.MediaDrm(java.util.UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"))
            try {
                fun p(k: String) = runCatching { drm.getPropertyString(k) }.getOrNull() ?: "?"
                "widevine ${p("version")} level=${p("securityLevel")} hdcp=${p("hdcpLevel")}/${p("maxHdcpLevel")} vendor=${p("vendor")} algorithms=${p("algorithms")}"
            } finally { if (android.os.Build.VERSION.SDK_INT >= 28) drm.close() else @Suppress("DEPRECATION") drm.release() }
        }.getOrElse { "MediaDrm failed: ${it.javaClass.simpleName} ${it.message?.take(80)}" }
    }

    /** Where the sound goes: a silent music volume or a route to a sink that makes none reads as "plays, no sound". */
    private fun audioDetails(context: Context): String? = runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val outs = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).joinToString(",") { it.type.toString() }
        val v = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        "music volume $v/$max muted=${am.isStreamMute(android.media.AudioManager.STREAM_MUSIC)} mode=${am.mode} output types=$outs"
    }.getOrNull()

    /**
     * What decides whether Android lets the app run with the screen off: the battery exemption, the
     * standby bucket, the power saver, the process importance, the maker's name (Samsung and others
     * add their own sleeping lists). Music that stops a few seconds after the lock cannot be told
     * apart without them.
     */
    private fun powerLines(context: Context): List<Pair<String, String>> = buildList {
        runCatching {
            val pm = context.getSystemService(android.os.PowerManager::class.java)
            add("exempt" to "ignoring battery optimizations=${pm.isIgnoringBatteryOptimizations(context.packageName)}")
            add("saver" to "power save mode=${pm.isPowerSaveMode} device idle=${pm.isDeviceIdleMode}")
        }
        if (android.os.Build.VERSION.SDK_INT >= 28) runCatching {
            val bucket = context.getSystemService(android.app.usage.UsageStatsManager::class.java).appStandbyBucket
            val name = when (bucket) {
                android.app.usage.UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
                android.app.usage.UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working set"
                android.app.usage.UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
                android.app.usage.UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
                android.app.usage.UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "restricted"
                else -> "other"
            }
            val restricted = context.getSystemService(android.app.ActivityManager::class.java).isBackgroundRestricted
            add("standby" to "bucket $bucket ($name) background restricted=$restricted")
        }
        runCatching {
            val me = android.app.ActivityManager.RunningAppProcessInfo().also { android.app.ActivityManager.getMyMemoryState(it) }
            val importance = when (me.importance) {
                android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "foreground"
                android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "foreground service"
                android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
                android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "perceptible"
                android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "service"
                android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "cached"
                else -> "other"
            }
            add("process" to "importance ${me.importance} ($importance)")
        }
        add("maker" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
    }

    /** The lines the 402 analyses read: refusals, the matrix, registration, clusters, licences, routes, ads. */
    private val RELEVANT_402 = Regex("""(?i)refus|\b402\b|registration|cluster:|licence|command headers|commands from the page|relay route|relay: |audio after|ad break|page ready|connect/transfer|previous player device""")
    private const val RELEVANT_LINES = 50

    /** An age in coarse buckets: never a date. */
    internal fun ageBucket(ms: Long): String {
        val days = ms / 86_400_000L
        return when {
            days < 1 -> "<1 d ago"
            days < 7 -> "1-7 d ago"
            days < 30 -> "7-30 d ago"
            days < 180 -> "1-6 months ago"
            else -> ">6 months ago"
        }
    }

    private val FACT_ORDER = listOf("account", "network", "registration", "devices", "relay route")

    private fun report(context: Context, tail: List<String>): String {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val versionName = info?.versionName ?: "?"
        val versionCode = info?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it) } ?: 0L
        // The player's page URL tells which Spotify screen the app was on; read only when the
        // runtime started, or the not-yet-initialized host would crash the export.
        val pageUrl = if (MyspotiRuntime.isStarted) runCatching { MyspotiRuntime.host.url.value }.getOrNull() else null
        val webView = runCatching { android.webkit.WebView.getCurrentWebViewPackage() }.getOrNull()
        val widevine = if (MyspotiRuntime.isStarted) runCatching {
            if (MyspotiRuntime.spotify.player.drmMissing.value) "missing (nothing Spotify streams can play)" else null
        }.getOrNull() else null
        val header = buildList {
            add("app" to "$versionName ($versionCode)")
            add("android" to "${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
            add("device" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            add("webview" to (webView?.let { "${it.packageName} ${it.versionName}" } ?: "?"))
            add("locale" to Locale.getDefault().toLanguageTag())
            if (!pageUrl.isNullOrEmpty()) add("page" to pageUrl)
            for (key in FACT_ORDER) facts[key]?.let { add(key to it) }
            facts.keys.filter { it !in FACT_ORDER }.sorted().forEach { add(it to facts.getValue(it)) }
            add("widevine" to (widevine ?: facts["widevine"] ?: "not reported missing"))
            drmFact?.let { add("drm" to it) }
            audioDetails(context)?.let { add("audio" to it) }
            runCatching { headerExtras?.invoke() }.getOrNull()?.let { addAll(it) }
        }
        // With how long ago this WebView was signed in (a new sign-in had its licences refused, 30/10).
        val signedIn = if (MyspotiRuntime.isStarted) runCatching { MyspotiRuntime.host.signedInSince }.getOrNull() else null
        val signedInAge = when {
            signedIn == null || signedIn == 0L -> null
            signedIn < 0 -> "signed in here before this was noted"
            else -> "signed in here ${ageBucket(System.currentTimeMillis() - signedIn)}"
        }
        val installAge = info?.firstInstallTime?.let { ageBucket(System.currentTimeMillis() - it) }
            ?.let { age -> listOfNotNull(age, signedInAge).joinToString(", ") }
        val summary = app.myspoti.spotify.report.Refusal402.summary(facts["account"], installAge)
        // Masked and cut down (DiagnosticDigest): small enough to paste, nothing that opens an account.
        return buildString {
            appendLine("Myspoti diagnostic report")
            // The 402 questions answered in one screen (Refusal402), before anything else.
            appendLine("--- 402 summary ---")
            summary.forEach { (k, v) -> appendLine("${k.padEnd(12)}: ${app.myspoti.spotify.report.DiagnosticDigest.mask(v)}") }
            appendLine()
            header.forEach { (k, v) -> appendLine("${k.padEnd(12)}: ${app.myspoti.spotify.report.DiagnosticDigest.mask(v)}") }
            appendLine()
            appendLine("--- power ---")
            powerLines(context).forEach { (k, v) -> appendLine("${k.padEnd(12)}: ${app.myspoti.spotify.report.DiagnosticDigest.mask(v)}") }
            val relevant = tail.filter { RELEVANT_402.containsMatchIn(it.drop(13)) }.takeLast(RELEVANT_LINES)
            if (relevant.isNotEmpty()) {
                appendLine()
                appendLine("--- 402 lines (last $RELEVANT_LINES) ---")
                relevant.forEach { appendLine(app.myspoti.spotify.report.DiagnosticDigest.mask(it).take(400)) }
            }
            appendLine()
            appendLine("--- log ---")
            append(app.myspoti.spotify.report.DiagnosticDigest.digest(tail))
        }
    }
}
