package com.music.spotui.data.offline

import android.content.ContextWrapper
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.spotui.data.MediaCacheStartup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class MediaCacheStartupTest {
    @Test fun sessionPreferenceIsCapturedBeforePlaybackStarts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("myspoti_prefs", 0)
        val previous = prefs.getBoolean("clear_media_on_new_session", false)
        try {
            prefs.edit().putBoolean("clear_media_on_new_session", false).commit()
            MediaCacheStartup.prepareSession(context)
            prefs.edit().putBoolean("clear_media_on_new_session", true).commit()
            assertEquals(false, MediaCacheStartup.clearPreviousSession)
            MediaCacheStartup.prepareSession(context)
            assertEquals(true, MediaCacheStartup.clearPreviousSession)
        } finally {
            prefs.edit().putBoolean("clear_media_on_new_session", previous).commit()
            MediaCacheStartup.prepareSession(context)
        }
    }

    @Test fun newCacheSessionHonorsOptInWithoutTouchingOtherFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "startup-cache-test").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getCacheDir() = root
        }
        val download = File(root, "downloads/song.flac").apply { parentFile!!.mkdirs(); writeText("download") }
        val image = File(root, "images/cover.jpg").apply { parentFile!!.mkdirs(); writeText("cover") }
        fun open(clear: Boolean) = MediaCacheStartup.open(isolated, clear, 1024 * 1024L)
        try {
            open(false).let { cache ->
                try {
                    val source = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory { ByteArrayDataSource(ByteArray(1024) { 7 }) }.createDataSource()
                    CacheWriter(source, DataSpec.Builder().setUri("https://example.test/song").setKey("song").setLength(1024).build(), null, null).cache()
                    assertTrue(cache.keys.contains("song"))
                } finally { cache.release() }
            }
            open(false).let { cache ->
                try { assertTrue("Default must retain temporary media", cache.keys.contains("song")) }
                finally { cache.release() }
            }
            open(true).let { cache ->
                try { assertTrue("Opt-in must remove media from the previous session", cache.keys.isEmpty()) }
                finally { cache.release() }
            }
            assertEquals("download", download.readText())
            assertEquals("cover", image.readText())
        } finally {
            root.deleteRecursively()
        }
    }
}
