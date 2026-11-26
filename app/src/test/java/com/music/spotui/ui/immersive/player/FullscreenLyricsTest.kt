/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.ui.immersive.player

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.ViewModelProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.spotui.data.entity.LyricLine
import com.music.spotui.data.entity.Lyrics
import com.music.spotui.di.Graph
import com.music.spotui.ui.AdaptiveScreenshotTest
import com.music.spotui.ui.components.ProvideWindowSize
import com.music.spotui.ui.look.LocalLook
import com.music.spotui.data.preferences.Look
import com.music.spotui.ui.theme.SpotuiTheme
import com.music.spotui.ui.viewmodel.LyricsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/** Uses the existing offline player fixture; no playback engine or network is needed. */
abstract class FullscreenLyricsTest(private val size: String) : AdaptiveScreenshotTest(size) {
    @Test fun expandsAndReturnsWithoutLosingScrollOrSelection() {
        @Suppress("UNCHECKED_CAST")
        (Graph.currentSongState.javaClass.getDeclaredField("_playingState").apply { isAccessible = true }
            .get(Graph.currentSongState) as MutableState<Boolean>).value = false
        val vm = ViewModelProvider(rule.activity)[LyricsViewModel::class.java]
        @Suppress("UNCHECKED_CAST")
        val state = vm.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(vm)
            as MutableStateFlow<LyricsViewModel.State>
        state.value = LyricsViewModel.State.Loaded(Lyrics(
            List(30) { LyricLine(0L, "A lyric line number $it") }, synced = false,
        ))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SpotuiTheme {
                CompositionLocalProvider(LocalLook provides Look.IMMERSIVE) {
                    ProvideWindowSize {
                        Box(Modifier.fillMaxSize()) {
                            ImmersivePlayer(
                                ImmersivePlayerState(title = "About You", artist = "The 1975"),
                                ImmersivePlayerActions(),
                            )
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithContentDescription("Lyrics").performClick()
        rule.mainClock.advanceTimeBy(1_000)
        val list = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        list.performScrollToIndex(8)
        val before = list.fetchSemanticsNode().boundsInRoot
        rule.onNodeWithContentDescription("Open lyrics full screen").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(100)
        val during = list.fetchSemanticsNode().boundsInRoot
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/fullscreen-lyrics-$size-expanding.png")
        rule.mainClock.advanceTimeBy(1_000)
        val expanded = list.fetchSemanticsNode().boundsInRoot
        assertTrue("Lyrics must gain space in $size", expanded.height > before.height + 100 || expanded.width > before.width + 100)
        if (expanded.height > before.height + 100) {
            assertTrue("Height should animate through an intermediate value in $size", during.height > before.height + 1 && during.height < expanded.height - 1)
        } else {
            assertTrue("Width should animate through an intermediate value in $size", during.width > before.width + 1 && during.width < expanded.width - 1)
        }
        rule.onNodeWithContentDescription("Play").assertDoesNotExist()
        rule.onNodeWithText("A lyric line number 8").assertIsDisplayed()
        rule.onNodeWithText("About You").assertIsDisplayed()
        // LazyColumn fills the newly exposed viewport after the resize's final measure.
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/fullscreen-lyrics-$size.png")

        rule.onNodeWithContentDescription("Share lyrics").performClick()
        rule.runOnIdle { vm.pick(8) }
        rule.mainClock.advanceTimeBy(300)
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(300)
        assertTrue(!vm.selecting.value)
        rule.onNodeWithContentDescription("Exit full screen lyrics").assertIsDisplayed()
        rule.onNodeWithContentDescription("Share lyrics").performClick()
        rule.runOnIdle { vm.pick(8) }
        rule.onNodeWithContentDescription("Exit full screen lyrics").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(100)
        val returning = list.fetchSemanticsNode().boundsInRoot
        assertTrue("Collapsing should also animate in $size", returning.height < expanded.height - 1 || returning.width < expanded.width - 1)
        rule.mainClock.advanceTimeBy(1_000)
        assertTrue("Moving the list must keep selection", vm.selecting.value && vm.picked.value == 8..8)
        rule.onNodeWithContentDescription("Play").assertIsDisplayed()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(300)
        assertTrue(!vm.selecting.value)

        rule.onNodeWithContentDescription("Open lyrics full screen").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithContentDescription("Open lyrics full screen").assertIsDisplayed()
        rule.onNodeWithContentDescription("Close the lyrics").assertIsDisplayed()
    }
}

@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xxhdpi", application = Application::class)
class FullscreenLyricsTestPhone : FullscreenLyricsTest("phone")

@Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xxhdpi", application = Application::class)
class FullscreenLyricsTestLandscape : FullscreenLyricsTest("landscape")

@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-xhdpi", application = Application::class)
class FullscreenLyricsTestTablet : FullscreenLyricsTest("tablet")
