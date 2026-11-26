package com.music.spotui.ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.spotui.data.AppStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CacheSettingsUiTest {
    @Test fun classicSwitchPersistsBothPositions() = verify(false)
    @Test fun immersiveSwitchPersistsBothPositions() = verify(true)

    private fun verify(immersive: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val previous = AppStorage.clearMediaOnNewSession
        instrumentation.runOnMainSync { AppStorage.chooseClearMediaOnNewSession(false) }
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    if (immersive) com.music.spotui.ui.immersive.pages.StoragePage {}
                    else Column { com.music.spotui.ui.screens.StorageRows() }
                } }
                val title = context.getString(app.myspoti.engine.R.string.settings_storage_clear_on_start)
                for (on in listOf(true, false)) {
                    val deadline = System.currentTimeMillis() + 10000
                    var clicked = false
                    while (System.currentTimeMillis() < deadline && !clicked) {
                        val root = instrumentation.uiAutomation.rootInActiveWindow
                        fun find(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
                            if (node.text?.toString()?.contains(title) == true || node.contentDescription?.toString()?.contains(title) == true) return node
                            for (i in 0 until node.childCount) node.getChild(i)?.let { child -> find(child)?.let { return it } }
                            return null
                        }
                        val node = root?.let(::find)
                        if (node != null) {
                            var target: android.view.accessibility.AccessibilityNodeInfo = node
                            while (!target.isClickable) target = target.parent ?: break
                            clicked = target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        }
                        if (!clicked) Thread.sleep(100)
                    }
                    assertTrue("Cache switch must be reachable", clicked)
                    instrumentation.waitForIdleSync()
                    val settled = System.currentTimeMillis() + 3000
                    while (AppStorage.clearMediaOnNewSession != on && System.currentTimeMillis() < settled) Thread.sleep(50)
                    Thread.sleep(250)
                    assertEquals(on, AppStorage.clearMediaOnNewSession)
                    assertEquals(on, context.getSharedPreferences("myspoti_prefs", 0).getBoolean("clear_media_on_new_session", !on))
                }
            }
        } finally {
            instrumentation.runOnMainSync { AppStorage.chooseClearMediaOnNewSession(previous) }
        }
    }
}
