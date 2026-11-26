package com.music.spotui.ui.player

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.immersive.pages.ImmersiveQueueScreen
import com.music.spotui.ui.screens.QueueScreen
import com.music.spotui.ui.theme.SpotuiTheme
import org.junit.Assert.*
import org.junit.Test

class QueueSearchDeviceTest {
    @Test fun classic() = exercise(false)
    @Test fun immersive() = exercise(true)

    private fun exercise(immersive: Boolean) {
        val inst = InstrumentationRegistry.getInstrumentation()
        fun row(id: Int, title: String, artist: String) = SongPlayer.QueueItem(
            SongsModel(id, title, "Album", artist, "", "", spotifyTrackId = ""), "local:$id", "$id", SongPlayer.LOCAL)
        val one = row(1, "Quartz", "Cedar")
        val two = row(2, "Indigo", "Willow")
        val queue = SongPlayer.EngineQueue(queued = listOf(one), nextUp = listOf(two))
        fun nodes(n: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(n) + (0 until n.childCount).flatMap { n.getChild(it)?.let(::nodes).orEmpty() }
        fun waitFor(predicate: (List<AccessibilityNodeInfo>) -> Boolean) {
            val end = android.os.SystemClock.elapsedRealtime() + 10000
            do {
                if (inst.uiAutomation.rootInActiveWindow?.let(::nodes)?.let(predicate) == true) return
                Thread.sleep(100)
            } while (android.os.SystemClock.elapsedRealtime() < end)
            fail("Expected visible queue search state was not reached")
        }
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                SongPlayer.showProbeQueue(queue)
                a.setContent { SpotuiTheme { val nav = rememberNavController(); if (immersive) ImmersiveQueueScreen(nav) else QueueScreen(nav) } }
            }
            fun visible(rows: List<AccessibilityNodeInfo>, s: String) = rows.any { it.text?.toString()?.contains(s) == true && !it.isEditable }
            fun query(value: String) = waitFor { rows -> rows.firstOrNull { it.isEditable }?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }) == true }
            waitFor { visible(it, "Quartz") && visible(it, "Indigo") }
            query("wIlLoW")
            waitFor { !visible(it, "Quartz") && visible(it, "Indigo") }
            query("quar")
            waitFor { visible(it, "Quartz") && !visible(it, "Indigo") }
            query("")
            waitFor { visible(it, "Quartz") && visible(it, "Indigo") }
            scenario.onActivity { assertEquals(queue, SongPlayer.engineQueue.value) }
        }
    }
}
