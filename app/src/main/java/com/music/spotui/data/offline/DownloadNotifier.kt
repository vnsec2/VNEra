package com.music.spotui.data.offline

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.myspoti.engine.R
import app.myspoti.MainActivity
import com.music.spotui.di.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One quiet notification for the entire music and podcast download queue. */
internal object DownloadNotifier {
    private const val CHANNEL = "downloads_progress"
    private const val ID = 0x444f574e
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var update: Job? = null
    private var shown: State? = null

    private data class State(val remaining: Int, val current: List<String>)

    fun changed() {
        scope.launch {
            update?.cancel()
            update = launch {
                delay(300) // Coalesce a whole playlist being enqueued and near-simultaneous finishes.
                refresh()
            }
        }
    }

    private fun refresh() {
        val context = Graph.app
        val all = OfflineDownloader.requests.values.map { it.trackId to it.title } +
            PodcastDownloader.requests.values.map { it.trackId to it.title }
        val active = all.filter { (id, _) ->
            val status = OfflineStore.status[id]
            status == OfflineStatus.Queued || status is OfflineStatus.Downloading
        }
        val state = State(active.size, active.mapNotNull { (id, title) ->
            title.takeIf { OfflineStore.status[id] is OfflineStatus.Downloading && it.isNotBlank() }
        })
        val manager = NotificationManagerCompat.from(context)
        if (state.remaining == 0) {
            manager.cancel(ID)
            shown = null
            return
        }
        if (state == shown) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val systemManager = context.getSystemService(NotificationManager::class.java) ?: return
        if (systemManager.getNotificationChannel(CHANNEL) == null) {
            systemManager.createNotificationChannel(NotificationChannel(
                CHANNEL, context.getString(R.string.download_notify_channel), NotificationManager.IMPORTANCE_LOW,
            ))
        }
        val open = PendingIntent.getActivity(context, ID, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val names = state.current.take(4)
        val title = context.getString(R.string.download_notify_remaining, state.remaining)
        val detail = if (names.isEmpty()) context.getString(R.string.download_notify_waiting)
            else names.joinToString("\n")
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.msp_ic_notification)
            .setContentTitle(title)
            .setContentText(names.firstOrNull() ?: detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        runCatching { manager.notify(ID, notification) }.onSuccess { shown = state }
    }
}
