package com.music.spotui.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal fun artworkLoopScale(ms: Long): Float =
    (1.02 - 0.02 * kotlin.math.cos((ms % 12000L) * (2 * Math.PI / 12000))).toFloat()

@Composable
internal fun Modifier.artworkLoop(visibleAndPlaying: Boolean): Modifier {
    if (!visibleAndPlaying || !com.music.spotui.data.preferences.ArtworkLoopPref.on || com.music.spotui.ui.look.rememberReduceMotion()) return this
    val energy by app.myspoti.power.EnergySettings.state.collectAsStateWithLifecycle()
    if (energy.policy.saverEnabled) return this
    val clock = rememberFrameClock(true, 24)
    return clipToBounds().graphicsLayer {
        val scale = artworkLoopScale(clock.value / 1_000_000L)
        scaleX = scale; scaleY = scale
    }
}
