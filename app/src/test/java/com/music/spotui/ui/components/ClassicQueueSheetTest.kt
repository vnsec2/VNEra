package com.music.spotui.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.di.Graph
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.theme.SpotuiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-port-xhdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClassicQueueSheetTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun draggingAnUpcomingRowUpThenDownKeepsTheSheetOpenAndPreservesTheQueue() {
        Graph::class.java.getDeclaredField("app").apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
        fun song(i: Int) = SongsModel(i, "Track $i", "Album", "Artist", "", "", spotifyTrackId = "")
        val tracks = (1..30).map { i -> SongPlayer.QueueItem(song(i), "local:-$i", "$i", SongPlayer.LOCAL) }
        SongPlayer.showProbeQueue(SongPlayer.EngineQueue(current = song(0), nextUp = tracks))
        var dismissals = 0
        rule.setContent { SpotuiTheme { QueueSheet("Probe playlist") { dismissals++ } } }

        val handle = hasContentDescription(rule.activity.getString(app.myspoti.engine.R.string.edit_playlist_items_move_chip))
        fun drag(from: Int, to: Int) {
            val rows = rule.onAllNodes(handle, useUnmergedTree = true).fetchSemanticsNodes()
            val fromCenter = rows[from].boundsInRoot.center
            val targetY = rows[to].boundsInRoot.center.y
            rule.onAllNodes(handle, useUnmergedTree = true)[from].performTouchInput {
                down(center)
                moveTo(center + Offset(0f, targetY - fromCenter.y), delayMillis = 180)
                up()
            }
            rule.waitForIdle()
            rule.runOnIdle { org.robolectric.shadows.ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS) }
        }

        drag(from = 3, to = 1)
        assertEquals(listOf("local:-1", "local:-4", "local:-2", "local:-3"), SongPlayer.engineQueue.value.nextUp.take(4).map { it.uri })
        drag(from = 1, to = 4)
        assertEquals(listOf("local:-1", "local:-2", "local:-3", "local:-5", "local:-4"), SongPlayer.engineQueue.value.nextUp.take(5).map { it.uri })
        assertEquals(tracks.map { it.uri }.sorted(), SongPlayer.engineQueue.value.nextUp.map { it.uri }.sorted())
        assertEquals(0, dismissals)
    }
}
