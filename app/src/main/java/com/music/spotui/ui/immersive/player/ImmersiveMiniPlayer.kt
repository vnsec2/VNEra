/*
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord), GPL-3.0-or-later,
 * app/src/main/java/com/music/bitchord/ui/components/MiniPlayer.kt at edc1e6f.
 * Modified for Myspoti, 2026-10.
 *
 * Changes: the 56dp capsule (radius 28) with cover, credits, play/pause and next, a swipe along
 * it to change track and a pull up to open the player. The dock and the
 * loading/locked states of a shared session are left out; the capsule is the Immersive glass
 * (immersiveGlassSurface: Liquid Glass or frost).
 */
package com.music.spotui.ui.immersive.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.myspoti.engine.R
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.di.Haptics
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.components.titleMarquee
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.immersive.shell.immersiveGlassSurface
import com.music.spotui.ui.navigation.PlayerSheet
import com.music.spotui.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private val GLYPH_SLOT = 40.dp
private val GLYPH_SIZE = 32.dp
private val ART_CORNER = 8.dp
private val MINI_HEIGHT = 56.dp

/** How far the capsule is swiped along to change track. */
private val TRACK_SWIPE_THRESHOLD = 84.dp

/**
 * The floating capsule over the pages: cover, title and artist, play/pause and next. A tap (or a
 * pull up) opens the player ([PlayerSheet.show]); a swipe along it changes track. Nothing is drawn
 * while nothing is playing. Give it the horizontal margin and the room above the tab bar through
 * [modifier]; it fills the width it is given.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ImmersiveMiniPlayer(modifier: Modifier = Modifier) {
    val vm: PlayerViewModel = viewModel()
    // A crossfade: the words stay the outgoing song's until half way (see SongBlend).
    val blend = com.music.spotui.ui.components.rememberSongBlend()
    val outgoing = blend?.takeIf { it.outgoingWords }?.song
    val title = outgoing?.title ?: vm.currentSongTitle.value
    if (title.isBlank()) return
    val singer = outgoing?.artist ?: vm.currentSongSinger.value
    val cover = vm.currentSongCoverUri.value
    val playing = vm.currentSongPlayingState.value
    val adPlaying = SongPlayer.adPlaying
    val buffering = SongPlayer.isBuffering
    val shape = RoundedCornerShape(MINI_HEIGHT / 2)
    val capsuleSource = remember { MutableInteractionSource() }
    val swipeScope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    val nextKickX = remember { Animatable(0f) }
    val nextKickPop = remember { Animatable(1f) }
    val slideOutPx = with(LocalDensity.current) { 120.dp.toPx() }
    val miniPalette = com.music.spotui.ui.look.rememberArtworkColors(cover.takeIf { it.isNotBlank() })
    val primaryGlow by animateColorAsState(
        targetValue = (miniPalette.colors.firstOrNull() ?: Color.Transparent).copy(alpha = if (cover.isNotBlank()) 0.34f else 0f),
        animationSpec = tween(700),
        label = "miniPrimaryGlow",
    )
    val secondaryGlow by animateColorAsState(
        targetValue = (miniPalette.colors.getOrNull(1) ?: miniPalette.colors.firstOrNull() ?: Color.Transparent).copy(alpha = if (cover.isNotBlank()) 0.20f else 0f),
        animationSpec = tween(700),
        label = "miniSecondaryGlow",
    )
    val miniTint by animateColorAsState(
        targetValue = playerTintOf(miniPalette),
        animationSpec = tween(700),
        label = "miniTint",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MINI_HEIGHT)
            .onGloballyPositioned { PlayerSheet.miniTop = it.boundsInWindow().top }
            .miniPlayerGestures(dragX, swipeScope)
            .immersiveGlassSurface(shape, interactionSource = capsuleSource)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(primaryGlow, secondaryGlow)))
            .clickable(interactionSource = capsuleSource, indication = null) { PlayerSheet.show() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer {
                    val dx = dragX.value
                    translationX = dx
                    alpha = (1f - (abs(dx) / 140.dp.toPx()).coerceIn(0f, 0.7f))
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(ART_CORNER)).background(Color.Black.copy(alpha = 0.18f))) {
                GlideImage(
                    model = cover.takeIf { it.isNotBlank() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    loading = placeholder(R.drawable.placeholder),
                    failure = placeholder(R.drawable.placeholder),
                    modifier = Modifier.size(40.dp),
                )
                com.music.spotui.ui.components.SongBlendCover(blend, Modifier.size(40.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).graphicsLayer { alpha = blend?.wordsAlpha() ?: 1f }) {
                Text(
                    text = if (adPlaying) stringResource(R.string.immersive_player_ad) else title,
                    style = Immersive.BodyStrong.copy(fontSize = 15.sp, lineHeight = 19.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.titleMarquee(iterations = 1),
                )
                if (!adPlaying) {
                    Text(
                        text = singer,
                        style = Immersive.Subhead.copy(lineHeight = 16.sp),
                        color = miniTint.copy(alpha = 0.80f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.titleMarquee(iterations = 1),
                    )
                }
            }
        }
        // The spinner and the button hand over in a short fade, not in one frame.
        com.music.spotui.ui.components.LoadedFade(buffering) { loading ->
        if (loading) {
            Box(Modifier.size(GLYPH_SLOT), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Immersive.Text, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
        } else {
            val playSource = remember { MutableInteractionSource() }
            val playPressed by playSource.collectIsPressedAsState()
            val playScale by animateFloatAsState(
                targetValue = if (playPressed) 0.85f else 1f,
                animationSpec = spring(dampingRatio = 0.60f, stiffness = 700f),
                label = "miniPlayScale",
            )
            Box(
                Modifier.size(GLYPH_SLOT).clickable(interactionSource = playSource, indication = null) {
                    Haptics.click()
                    if (playing) SongPlayer.pause() else SongPlayer.play()
                    vm.updateSongState(cover, vm.currentSongTitle.value, vm.currentSongSinger.value, !playing, vm.currentSongId.value, vm.currentSongIndex.value, vm.currentSongAlbum.value)
                },
                contentAlignment = Alignment.Center,
            ) {
                com.music.spotui.ui.components.GlyphSwap(playing) { on ->
                    Icon(
                        imageVector = if (on) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (on) R.string.immersive_player_pause else R.string.immersive_player_play),
                        tint = Immersive.Text,
                        modifier = Modifier.size(GLYPH_SIZE).graphicsLayer {
                            scaleX = playScale
                            scaleY = playScale
                        },
                    )
                }
            }
        }
        }
        val nextSource = remember { MutableInteractionSource() }
        val nextPressed by nextSource.collectIsPressedAsState()
        val nextPressScale by animateFloatAsState(
            targetValue = if (nextPressed) 0.85f else 1f,
            animationSpec = spring(dampingRatio = 0.60f, stiffness = 700f),
            label = "miniNextScale",
        )
        Box(
            Modifier.size(GLYPH_SLOT).clickable(interactionSource = nextSource, indication = null) {
                Haptics.click()
                swipeScope.launch {
                    launch {
                        nextKickPop.snapTo(1f)
                        nextKickPop.animateTo(1.10f, tween(80, easing = FastOutSlowInEasing))
                        nextKickPop.animateTo(1f, spring(dampingRatio = 0.56f, stiffness = 520f))
                    }
                    nextKickX.snapTo(0f)
                    nextKickX.animateTo(6.5f, tween(85, easing = FastOutSlowInEasing))
                    nextKickX.animateTo(0f, spring(dampingRatio = 0.52f, stiffness = 540f))
                }
                animateMiniTrackStep(-1, dragX, slideOutPx, swipeScope)
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.immersive_player_next),
                tint = Immersive.Text,
                modifier = Modifier.size(GLYPH_SIZE).graphicsLayer {
                    val s = nextPressScale * nextKickPop.value
                    translationX = nextKickX.value.dp.toPx()
                    scaleX = s
                    scaleY = s
                },
            )
        }
    }
}

private fun animateMiniTrackStep(
    dir: Int,
    dragX: Animatable<Float, *>,
    slideOut: Float,
    scope: CoroutineScope,
) {
    if (dir < 0) SongPlayer.swipeNext() else SongPlayer.swipePrevious()
    scope.launch {
        dragX.animateTo(dir * slideOut, tween(durationMillis = 155, easing = FastOutSlowInEasing))
        dragX.snapTo(-dir * slideOut * 0.6f)
        dragX.animateTo(0f, spring(dampingRatio = 0.80f, stiffness = 480f))
    }
}


/**
 * A swipe along the capsule changes track (past [TRACK_SWIPE_THRESHOLD]); a pull up opens the
 * player. A tap is left to the capsule's own click.
 */
private fun Modifier.miniPlayerGestures(dragX: Animatable<Float, *>, scope: CoroutineScope): Modifier = pointerInput(dragX) {
    val threshold = TRACK_SWIPE_THRESHOLD.toPx()
    val maxFree = 56.dp.toPx()
    val slideOut = 120.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slopped = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
        val moved = slopped.position - down.position
        if (abs(moved.y) > abs(moved.x)) {
            if (moved.y < 0f) PlayerSheet.show()
            drag(slopped.id) { it.consume() }
            return@awaitEachGesture
        }
        var dragged = moved.x
        var armedDir = 0
        fun applyDrag(raw: Float) {
            val damped = if (abs(raw) <= maxFree) raw else sign(raw) * (maxFree + (abs(raw) - maxFree) * 0.32f)
            scope.launch { dragX.snapTo(damped) }
        }
        applyDrag(dragged)
        val finished = drag(slopped.id) { change ->
            dragged += change.positionChange().x
            change.consume()
            applyDrag(dragged)
            val nowDir = when {
                dragged <= -threshold -> -1
                dragged >= threshold -> 1
                else -> 0
            }
            if (nowDir != armedDir) {
                armedDir = nowDir
                Haptics.tick()
            }
        }
        if (!finished) {
            scope.launch { dragX.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)) }
            return@awaitEachGesture
        }
        val dir = when {
            dragged <= -threshold -> -1
            dragged >= threshold -> 1
            else -> 0
        }
        if (dir == 0) {
            scope.launch { dragX.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)) }
        } else {
            animateMiniTrackStep(dir, dragX, slideOut, scope)
        }
    }
}
