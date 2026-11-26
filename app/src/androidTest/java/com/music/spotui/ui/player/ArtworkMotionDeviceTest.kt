package com.music.spotui.ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.spotui.data.preferences.ArtworkLoopPref
import com.music.spotui.ui.components.artworkLoop
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArtworkMotionDeviceTest {
    @Test fun renderedCoverMovesDuringPlaybackAndStopsOnPause() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val playing = mutableStateOf(false)
        val old = ArtworkLoopPref.on
        fun frame(): android.graphics.Bitmap {
            val screen = inst.uiAutomation.takeScreenshot()
            return try { android.graphics.Bitmap.createBitmap(screen, 20, screen.height / 4, screen.width / 3, screen.height / 8) } finally { screen.recycle() }
        }
        fun changed(first: android.graphics.Bitmap): Boolean {
            val second = frame()
            return try { !first.sameAs(second) } finally { second.recycle() }
        }
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { a ->
                    ArtworkLoopPref.set(a, true)
                    a.setContent {
                        Canvas(Modifier.size(360.dp).artworkLoop(playing.value)) {
                            drawRect(Color.White)
                            for (x in 0 until size.width.toInt() step 15) drawLine(Color.Black, Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), 5f)
                        }
                    }
                }
                inst.waitForIdleSync(); Thread.sleep(700)
                val paused = frame()
                try {
                    Thread.sleep(700); assertFalse("paused frame moved", changed(paused))
                    scenario.onActivity { playing.value = true }
                    val deadline = android.os.SystemClock.elapsedRealtime() + 8500
                    var moved = false
                    while (!moved && android.os.SystemClock.elapsedRealtime() < deadline) { Thread.sleep(250); moved = changed(paused) }
                    assertTrue("rendered artwork stayed static", moved)
                    scenario.onActivity { playing.value = false }; Thread.sleep(500)
                    assertFalse("pause did not restore the still cover", changed(paused))
                } finally { paused.recycle() }
            }
        } finally { inst.runOnMainSync { ArtworkLoopPref.set(inst.targetContext, old) } }
    }
}
