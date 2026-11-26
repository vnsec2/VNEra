package com.music.spotui.data

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object MediaCacheStartup {
    var clearPreviousSession: Boolean = false
        private set

    fun prepareSession(context: Context) {
        clearPreviousSession = context.getSharedPreferences("myspoti_prefs", 0).getBoolean("clear_media_on_new_session", false)
    }

    fun open(context: Context, clearPreviousSession: Boolean, limit: Long): SimpleCache =
        SimpleCache(File(context.cacheDir, "media"), LeastRecentlyUsedCacheEvictor(limit), StandaloneDatabaseProvider(context)).also { cache ->
            if (clearPreviousSession) {
                try { cache.keys.toList().forEach(cache::removeResource) }
                catch (error: Exception) {
                    cache.release()
                    throw error
                }
            }
        }
}
