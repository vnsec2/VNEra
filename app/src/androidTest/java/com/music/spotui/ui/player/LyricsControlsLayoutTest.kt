package com.music.spotui.ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.spotui.data.entity.LyricLine
import com.music.spotui.data.entity.Lyrics
import com.music.spotui.ui.immersive.lyrics.ImmersiveLyricsPanel
import com.music.spotui.ui.viewmodel.LyricsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LyricsControlsLayoutTest {
    @Test fun longLyricsDoNotPassBehindTranslationControls() = assertProtectedLyricsArea(false, 1f)

    @Test fun largeWrappedLyricsDoNotPassBehindControls() = assertProtectedLyricsArea(false, 1.5f)

    @Test fun translatedLyricsStayInsideTheReadingArea() = assertProtectedLyricsArea(false, 1.5f, short = true)

    @Test fun rightToLeftTranslationsStayInsideTheReadingArea() = assertProtectedLyricsArea(true, 1.5f, short = true)

    @Test fun rightToLeftLyricsDoNotPassBehindControls() = assertProtectedLyricsArea(true, 1.5f)

    private fun assertProtectedLyricsArea(rtl: Boolean, fontScale: Float, short: Boolean = false) {
        val text = if (short) "短い歌詞" else "長い歌詞が何行にも折り返されても翻訳ボタンに隠れないように表示領域を確保します".repeat(3)
        val translated = "A translated lyric that also remains inside the reading area"
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[LyricsViewModel::class.java]
                val field = LyricsViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST")
                val state = field.get(vm) as MutableStateFlow<LyricsViewModel.State>
                state.value = LyricsViewModel.State.Loaded(
                    lyrics = Lyrics(lines = listOf(LyricLine(0, text, romanization = "long lyric")), synced = false),
                    sourceLanguage = "ja", translationChecked = true,
                    translations = listOf(translated),
                    showTranslation = true,
                )
                activity.setContent {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f, fontScale),
                        androidx.compose.ui.platform.LocalLayoutDirection provides if (rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
                    ) {
                        Box(Modifier.size(320.dp, 500.dp)) { ImmersiveLyricsPanel(Modifier.size(320.dp, 500.dp)) }
                    }
                }
            }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            val labels = setOf(
                instrumentation.targetContext.getString(app.myspoti.engine.R.string.lyrics_romanize),
                instrumentation.targetContext.getString(app.myspoti.engine.R.string.lyrics_hide_translation),
            )
            var lyric: android.graphics.Rect? = null
            val controls = mutableListOf<android.graphics.Rect>()
            val deadline = System.currentTimeMillis() + 10000
            while (System.currentTimeMillis() < deadline) {
                controls.clear()
                fun visit(node: android.view.accessibility.AccessibilityNodeInfo) {
                    val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
                    if (node.text?.toString() == if (short) translated else text) lyric = bounds
                    if (node.contentDescription?.toString() in labels) controls.add(bounds)
                    for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
                }
                instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
                if (lyric != null && controls.size == 2) break
                Thread.sleep(100)
            }
            assertTrue("Long lyric must be rendered", lyric != null && !lyric!!.isEmpty)
            assertEquals("Both controls must be rendered", 2, controls.size)
            controls.forEach { control ->
                assertTrue("Lyric $lyric must not intersect control $control", !android.graphics.Rect.intersects(lyric!!, control))
            }
        }
    }

    @Test fun romanizationAndTranslationShareOneVerticalAxis() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[LyricsViewModel::class.java]
                val field = LyricsViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST")
                val state = field.get(vm) as MutableStateFlow<LyricsViewModel.State>
                state.value = LyricsViewModel.State.Loaded(
                    lyrics = Lyrics(lines = listOf(LyricLine(0, "短い歌詞", romanization = "mijikai kashi")), synced = true),
                    sourceLanguage = "ja",
                    translationChecked = true,
                )
                activity.setContent {
                    Box(Modifier.size(320.dp, 500.dp)) { ImmersiveLyricsPanel(Modifier.size(320.dp, 500.dp)) }
                }
            }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            val device = instrumentation.uiAutomation
            val deadline = System.currentTimeMillis() + 10000
            var rectangles: List<android.graphics.Rect> = emptyList()
            while (System.currentTimeMillis() < deadline) {
                val root = device.rootInActiveWindow
                val labels = setOf(instrumentation.targetContext.getString(app.myspoti.engine.R.string.lyrics_romanize), instrumentation.targetContext.getString(app.myspoti.engine.R.string.lyrics_fullscreen_chip_label_translate))
                val nodes = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
                fun visit(node: android.view.accessibility.AccessibilityNodeInfo) {
                    if (node.contentDescription?.toString() in labels) nodes.add(node)
                    for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
                }
                root?.let(::visit)
                rectangles = nodes.map { android.graphics.Rect().also(it::getBoundsInScreen) }.distinct()
                if (rectangles.size == 2) break
                Thread.sleep(100)
            }
            assertEquals("Both lyric toggles must be visible", 2, rectangles.size)
            assertEquals("Toggles must have the same horizontal center", rectangles[0].centerX(), rectangles[1].centerX())
            assertTrue("Toggles must not overlap", !android.graphics.Rect.intersects(rectangles[0], rectangles[1]))
        }
    }
}
