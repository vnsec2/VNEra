package com.music.spotui.ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.music.spotui.data.entity.LyricLine
import com.music.spotui.data.entity.Lyrics
import com.music.spotui.ui.immersive.lyrics.ImmersiveLyricsPanel
import com.music.spotui.ui.viewmodel.LyricsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsAppearanceTest {
    @Test fun classicLyricsSettingsExposeAppearance() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                com.music.spotui.ui.screens.SettingsScreen(androidx.navigation.compose.rememberNavController())
            } }
            fun find(label: String): android.view.accessibility.AccessibilityNodeInfo? {
                fun visit(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
                    if (node.text?.toString() == label) return node
                    for (i in 0 until node.childCount) node.getChild(i)?.let { child -> visit(child)?.let { return it } }
                    return null
                }
                return instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
            }
            val label = instrumentation.targetContext.getString(app.myspoti.engine.R.string.settings_section_lyrics)
            var clicked = false
            repeat(50) {
                var node = find(label)
                while (node != null && !node.isClickable) node = node.parent
                if (!clicked) clicked = node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true
                if (!clicked) Thread.sleep(100)
            }
            assertTrue("Lyrics tab must be reachable", clicked)
            var found = false
            repeat(40) {
                if (!found) found = find("140%") != null
                if (!found) {
                    fun scroll(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
                        if (node.isScrollable && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
                        for (i in 0 until node.childCount) if (node.getChild(i)?.let(::scroll) == true) return true
                        return false
                    }
                    instrumentation.uiAutomation.rootInActiveWindow?.let(::scroll)
                    Thread.sleep(150)
                }
            }
            assertTrue("Appearance must be reachable in Classic Lyrics settings", found)
        }
    }

    @Test fun immersiveLyricsSettingsExposeAppearance() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                com.music.spotui.ui.immersive.pages.LyricsPage({}, {})
            } }
            var found = false
            repeat(50) {
                fun visit(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
                    if (node.text?.toString() == "140%") return true
                    for (i in 0 until node.childCount) if (node.getChild(i)?.let(::visit) == true) return true
                    return false
                }
                if (!found) found = instrumentation.uiAutomation.rootInActiveWindow?.let(::visit) == true
                if (!found) Thread.sleep(100)
            }
            assertTrue("Appearance must be reachable in Immersive Lyrics settings", found)
        }
    }

    @Test fun controlsPersistSizeColorAndAutomaticReset() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("settings_prefs", 0)
        val oldSize = prefs.getInt("lyrics_text_percent", 100)
        val oldColor = prefs.getInt("lyrics_text_color", 0)
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    com.music.spotui.ui.components.LyricsAppearanceControls()
                } }
                fun click(label: String) {
                    val deadline = System.currentTimeMillis() + 5000
                    var clicked = false
                    while (!clicked && System.currentTimeMillis() < deadline) {
                        fun find(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
                            if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
                            for (i in 0 until node.childCount) node.getChild(i)?.let { child -> find(child)?.let { return it } }
                            return null
                        }
                        var target = instrumentation.uiAutomation.rootInActiveWindow?.let(::find)
                        while (target != null && !target.isClickable) target = target.parent
                        clicked = target?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true
                        if (!clicked) Thread.sleep(100)
                    }
                    assertTrue("Reachable control: $label", clicked)
                    instrumentation.waitForIdleSync()
                }
                click("140%")
                org.junit.Assert.assertEquals(140, prefs.getInt("lyrics_text_percent", 0))
                click(context.getString(app.myspoti.engine.R.string.color_blue))
                org.junit.Assert.assertEquals(0xFF90CAF9.toInt(), prefs.getInt("lyrics_text_color", 0))
                scenario.recreate()
                org.junit.Assert.assertEquals(140, prefs.getInt("lyrics_text_percent", 0))
            }
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent { com.music.spotui.ui.components.LyricsAppearanceControls() } }
                instrumentation.waitForIdleSync()
                val label = context.getString(app.myspoti.engine.R.string.language_auto)
                fun reset(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
                    if (node.text?.toString() == label) {
                        var target = node
                        while (!target.isClickable) target = target.parent ?: return false
                        return target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    for (i in 0 until node.childCount) if (node.getChild(i)?.let(::reset) == true) return true
                    return false
                }
                var clicked = false
                repeat(30) {
                    if (!clicked) clicked = instrumentation.uiAutomation.rootInActiveWindow?.let(::reset) == true
                    if (!clicked) Thread.sleep(100)
                }
                assertTrue("Automatic color must be reachable", clicked)
                instrumentation.waitForIdleSync()
                org.junit.Assert.assertEquals(0, prefs.getInt("lyrics_text_color", -1))
            }
        } finally {
            prefs.edit().putInt("lyrics_text_percent", oldSize).putInt("lyrics_text_color", oldColor).commit()
        }
    }

    @Test fun savedColorChangesRenderedLyricPixels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences("settings_prefs", 0)
        val previous = prefs.getInt("lyrics_text_color", 0)
        try {
            prefs.edit().putInt("lyrics_text_color", 0xFF90CAF9.toInt()).commit()
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val vm = ViewModelProvider(activity)[LyricsViewModel::class.java]
                    val field = LyricsViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }
                    @Suppress("UNCHECKED_CAST")
                    val state = field.get(vm) as MutableStateFlow<LyricsViewModel.State>
                    state.value = LyricsViewModel.State.Loaded(
                        lyrics = Lyrics(lines = listOf(LyricLine(0, "Appearance fixture")), synced = false), translationChecked = true)
                    activity.setContent {
                        Box(Modifier.size(320.dp, 500.dp)) { ImmersiveLyricsPanel(Modifier.size(320.dp, 500.dp)) }
                    }
                }
                instrumentation.waitForIdleSync()
                Thread.sleep(1500)
                val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                var bluePixels = 0
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    if (android.graphics.Color.blue(pixel) > 190 && android.graphics.Color.green(pixel) > 140 &&
                        android.graphics.Color.blue(pixel) - android.graphics.Color.red(pixel) > 65) bluePixels++
                }
                bitmap.recycle()
                assertTrue("Saved blue color must produce colored lyric pixels, found $bluePixels", bluePixels > 50)
            }
        } finally {
            prefs.edit().putInt("lyrics_text_color", previous).commit()
        }
    }

    @Test fun savedSizeChangesTheRenderedLyricHeight() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences("settings_prefs", 0)
        val previous = prefs.getInt("lyrics_text_percent", 100)
        fun height(percent: Int): Int {
            prefs.edit().putInt("lyrics_text_percent", percent).commit()
            var result = 0
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val vm = ViewModelProvider(activity)[LyricsViewModel::class.java]
                    val field = LyricsViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }
                    @Suppress("UNCHECKED_CAST")
                    val state = field.get(vm) as MutableStateFlow<LyricsViewModel.State>
                    state.value = LyricsViewModel.State.Loaded(
                        lyrics = Lyrics(lines = listOf(LyricLine(0, "Appearance fixture")), synced = false),
                        translationChecked = true,
                    )
                    activity.setContent {
                        Box(Modifier.size(320.dp, 500.dp)) { ImmersiveLyricsPanel(Modifier.size(320.dp, 500.dp)) }
                    }
                }
                val deadline = System.currentTimeMillis() + 10000
                while (System.currentTimeMillis() < deadline && result == 0) {
                    instrumentation.waitForIdleSync()
                    fun visit(node: android.view.accessibility.AccessibilityNodeInfo) {
                        if (node.text?.toString() == "Appearance fixture") {
                            result = android.graphics.Rect().also(node::getBoundsInScreen).height()
                        }
                        for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
                    }
                    instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
                    if (result == 0) Thread.sleep(100)
                }
                assertTrue("Lyric must be rendered", result > 0)
            }
            return result
        }
        try {
            val small = height(70)
            val large = height(140)
            assertTrue("Saved lyric size must change measured height: $small -> $large", large > small)
        } finally {
            prefs.edit().putInt("lyrics_text_percent", previous).commit()
        }
    }
}
