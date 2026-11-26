/*
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord), GPL-3.0-or-later,
 * sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/NowPlayingScreen.kt at edc1e6f.
 * Modified for Myspoti, 2026-10.
 *
 * Changes: only the portrait layout is taken: the artwork with its step back on pause, the move
 * of the artwork to a small header (title beside it) when the lyrics or the queue open, the
 * title row with its round glyphs, the scrubber, transport, volume and bottom rows, and the
 * sheet's drag to close. The full-bleed banner, Canvas clips, BitChord's track-swipe on the cover,
 * version switching, the lyric strip and the dock are left out. The landscape layout (cover beside
 * the controls), the Canvas behind the player (with the cover opening into it) and the swipe
 * along the cover that changes track (the mini player's) are Myspoti's own.
 */
package com.music.spotui.ui.immersive.player

import com.music.spotui.ui.components.artworkLoop

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.spotui.ui.navigation.PlayerSheet
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.State
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.myspoti.engine.R
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.ui.components.DevicesSheet
import com.music.spotui.ui.immersive.sheets.ImmersiveSongOptionsSheet
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.immersive.lyrics.ImmersiveLyricsPanel
import com.music.spotui.ui.immersive.lyrics.ImmersiveQueuePanel
import com.music.spotui.ui.look.MeshGradientBackground
import com.music.spotui.ui.look.MeshPalette
import com.music.spotui.ui.look.lerp
import com.music.spotui.ui.components.SongBlend
import com.music.spotui.ui.components.SongBlendCover
import com.music.spotui.ui.components.mixSqueeze
import com.music.spotui.ui.components.mixWave
import com.music.spotui.ui.components.titleMarquee
import com.music.spotui.ui.look.rememberArtworkColors
import com.music.spotui.ui.look.rememberPauseScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

// Sizes and gaps of the portrait layout, as the player this follows has them.
private val THUMB_SIZE = 54.dp
private val HEADER_HEIGHT = 60.dp
// Covers.large is 640px: the cover is always decoded at that, whatever size the box has right now.
private const val COVER_DECODE_PX = 640
private val ART_TITLE_GAP = 20.dp
private val ART_BOX_TOP_PAD = 8.dp
private val DISMISS_STRIP_HEIGHT = 32.dp

/** The player's side margin. Scrollable panels reach back across it. */
internal val PLAYER_GUTTER = 30.dp

/**
 * How wide the player's content is ever allowed to get: a sleeve and a volume slider stretched
 * across a tablet aren't a bigger player, just a coarser one.
 */
internal val PLAYER_MAX_WIDTH = 560.dp

/** How long the sleeve takes to travel between the full player and the header. */
private const val PANEL_TRAVEL_MS = com.music.spotui.ui.immersive.motion.ImmersiveMotion.LONG_MS

private val ArtCorner = RoundedCornerShape(8.dp)
private const val HERO_FADE_FRACTION = 0.42f
private const val MIX_BEAT_GROW = 0.025f

/** What the middle of the player shows. */
private object Panel {
    const val NONE = 0
    const val LYRICS = 1
    const val QUEUE = 2
}

/**
 * The sheet the player sits in, as the player sees it: dragged down from the top of the player
 * (the handle, the artwork, the credits) it follows the finger, and lets go with its speed.
 */
internal interface PlayerSheetDrag {
    fun drag(delta: Float)
    fun release(velocity: Float)
}

internal val LocalPlayerSheetDrag = staticCompositionLocalOf<PlayerSheetDrag?> { null }

/**
 * The downward half of a vertical drag on this element, handed to the sheet. Upward is not ours:
 * the crossing of the touch slop is left alone and nothing is consumed, so a tap, a scroll or a
 * child's own gesture keeps working.
 */
@Composable
internal fun Modifier.dragToClose(): Modifier {
    val sheet = LocalPlayerSheetDrag.current ?: return this
    val current by rememberUpdatedState(sheet)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var pulled = 0f
            val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                if (overSlop > 0f) {
                    pulled = overSlop
                    change.consume()
                }
            }
            if (drag == null || pulled <= 0f) return@awaitEachGesture
            val velocity = VelocityTracker()
            velocity.addPointerInputChange(drag)
            current.drag(pulled)
            val finished = verticalDrag(drag.id) { change ->
                velocity.addPointerInputChange(change)
                current.drag(change.positionChange().y)
                change.consume()
            }
            current.release(if (finished) velocity.calculateVelocity().y else 0f)
        }
    }
}

/** The grab handle over the full player: a drag down closes the sheet, and so does a tap. */
@Composable
internal fun PlayerDismissStrip(tint: Color, onClose: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(DISMISS_STRIP_HEIGHT).dragToClose()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        if (com.music.spotui.data.preferences.ImmersiveChromePref.handle) Box(Modifier.width(36.dp).height(5.dp).clip(CircleShape).background(tint.copy(alpha = 0.35f)))
    }
}

private val TRACK_SWIPE_MIN_THRESHOLD = 96.dp
private val TRACK_SWIPE_CARD_GAP = 20.dp
private val TRACK_SWIPE_FLING_VELOCITY = 900.dp
private val TRACK_SWIPE_FLING_MIN_DRAG = 36.dp

private class CoverSwipeController(private val scope: CoroutineScope) {
    val offsetX = Animatable(0f)
    val enterAlpha = Animatable(1f)
    val creditsOffsetX = Animatable(0f)
    val creditsAlpha = Animatable(1f)
    var outgoingCoverUrl by mutableStateOf<String?>(null)
    var incomingCoverUrl by mutableStateOf<String?>(null)
    var overrideCoverUrl by mutableStateOf<String?>(null)
    var spanPx: Float = 0f
    private var latestActualCoverUrl: String = ""
    private var lockedOutCovers: Set<String> = emptySet()
    private var lastRewindAtMs: Long = 0L
    private var settleJob: Job? = null

    fun onActualCoverChanged(actualUrl: String) {
        latestActualCoverUrl = actualUrl
        if (actualUrl.isBlank()) return
        if (outgoingCoverUrl != null && incomingCoverUrl.isNullOrBlank() && actualUrl != outgoingCoverUrl && actualUrl !in lockedOutCovers) {
            incomingCoverUrl = actualUrl
        }
        if (lockedOutCovers.isNotEmpty() && actualUrl !in lockedOutCovers) {
            lockedOutCovers = emptySet()
            if (outgoingCoverUrl == null) {
                overrideCoverUrl = null
            }
        } else if (overrideCoverUrl != null && actualUrl == overrideCoverUrl) {
            overrideCoverUrl = null
        }
    }

    fun dragTo(rawDelta: Float, currentSpanPx: Float) {
        settleJob?.cancel()
        outgoingCoverUrl = null
        incomingCoverUrl = null
        val limit = currentSpanPx.coerceAtLeast(1f)
        spanPx = limit
        val free = limit * 0.55f
        val damped = if (abs(rawDelta) <= free) {
            rawDelta
        } else {
            val extra = abs(rawDelta) - free
            sign(rawDelta) * (free + extra / (1f + extra / (limit * 0.65f)))
        }
        val frac = (abs(damped) / limit).coerceIn(0f, 1f)
        scope.launch {
            enterAlpha.snapTo(1f)
            offsetX.snapTo(damped)
            creditsOffsetX.snapTo(damped * 0.14f)
            creditsAlpha.snapTo(1f - 0.35f * frac)
        }
    }

    fun springBack() {
        settleJob?.cancel()
        outgoingCoverUrl = null
        incomingCoverUrl = null
        settleJob = scope.launch {
            enterAlpha.snapTo(1f)
            launch {
                creditsAlpha.animateTo(1f, tween(durationMillis = 180, easing = FastOutSlowInEasing))
            }
            launch {
                creditsOffsetX.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
            }
            offsetX.animateTo(
                0f,
                spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    fun stepByButton(
        direction: Int,
        fallbackSpanPx: Float,
        currentCover: String,
        neighborCover: String?,
        positionMs: Long,
        trigger: () -> Unit,
    ) {
        val effectiveSpan = spanPx.takeIf { it > 1f } ?: fallbackSpanPx.coerceAtLeast(1f)
        val now = android.os.SystemClock.elapsedRealtime()
        if (direction > 0 && positionMs > 3_000L && now - lastRewindAtMs > 3_000L) {
            lastRewindAtMs = now
            settleJob?.cancel()
            outgoingCoverUrl = null
            incomingCoverUrl = null
            trigger()
            settleJob = scope.launch {
                enterAlpha.snapTo(1f)
                launch {
                    creditsOffsetX.snapTo(0f)
                    creditsOffsetX.animateTo(effectiveSpan * 0.04f, tween(85, easing = FastOutSlowInEasing))
                    creditsOffsetX.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 520f))
                }
                offsetX.snapTo(0f)
                offsetX.animateTo(effectiveSpan * 0.12f, tween(90, easing = FastOutSlowInEasing))
                offsetX.animateTo(0f, spring(dampingRatio = 0.64f, stiffness = 500f))
            }
            return
        }
        lastRewindAtMs = 0L
        val baseCover = if (outgoingCoverUrl != null) {
            incomingCoverUrl ?: overrideCoverUrl ?: latestActualCoverUrl.ifBlank { currentCover }
        } else {
            currentCover
        }
        commit(
            direction = direction,
            currentSpanPx = effectiveSpan,
            currentCover = baseCover,
            neighborCover = neighborCover,
            fromButton = true,
            trigger = trigger,
        )
    }

    fun commit(
        direction: Int,
        currentSpanPx: Float,
        currentCover: String,
        neighborCover: String?,
        fromButton: Boolean = false,
        trigger: () -> Unit,
    ) {
        settleJob?.cancel()
        val effectiveSpan = currentSpanPx.coerceAtLeast(1f)
        spanPx = effectiveSpan
        val cleanNeighbor = neighborCover?.takeIf { it.isNotBlank() && it != currentCover }
        if (currentCover.isNotBlank()) {
            lockedOutCovers = lockedOutCovers + currentCover
        }
        outgoingCoverUrl = currentCover
        incomingCoverUrl = cleanNeighbor
        val targetX = direction * effectiveSpan
        val slideMs = if (fromButton) 265 else 200
        trigger()
        settleJob = scope.launch {
            if (fromButton) {
                offsetX.snapTo(0f)
                enterAlpha.snapTo(1f)
            }
            launch {
                creditsAlpha.animateTo(0.18f, tween(durationMillis = slideMs / 2, easing = FastOutSlowInEasing))
            }
            launch {
                creditsOffsetX.animateTo(direction * effectiveSpan * 0.08f, tween(durationMillis = slideMs / 2, easing = FastOutSlowInEasing))
                creditsOffsetX.snapTo(-direction * effectiveSpan * 0.07f)
                launch {
                    creditsAlpha.animateTo(1f, tween(durationMillis = 190, easing = FastOutSlowInEasing))
                }
                creditsOffsetX.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 480f))
            }
            offsetX.animateTo(
                targetX,
                tween(durationMillis = slideMs, easing = FastOutSlowInEasing),
            )
            val landedCover = incomingCoverUrl
            outgoingCoverUrl = null
            incomingCoverUrl = null
            val actualReady = latestActualCoverUrl.isNotBlank() && latestActualCoverUrl !in lockedOutCovers
            if (actualReady) {
                lockedOutCovers = emptySet()
                overrideCoverUrl = null
                offsetX.snapTo(0f)
                enterAlpha.snapTo(1f)
            } else if (!landedCover.isNullOrBlank()) {
                overrideCoverUrl = landedCover
                offsetX.snapTo(0f)
                enterAlpha.snapTo(1f)
                delay(1_400)
                overrideCoverUrl = null
                lockedOutCovers = emptySet()
            } else {
                enterAlpha.snapTo(0f)
                offsetX.snapTo(-direction * effectiveSpan * 0.50f)
                var waited = 0
                while (lockedOutCovers.isNotEmpty() && waited < 260) {
                    delay(20)
                    waited += 20
                }
                lockedOutCovers = emptySet()
                overrideCoverUrl = null
                launch {
                    enterAlpha.animateTo(1f, tween(durationMillis = 210, easing = FastOutSlowInEasing))
                }
                offsetX.animateTo(
                    0f,
                    spring(dampingRatio = 0.82f, stiffness = 460f),
                )
            }
        }
    }
}

@Composable
private fun rememberCoverSwipeController(actualCoverUrl: String): CoverSwipeController {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) { CoverSwipeController(scope) }
    LaunchedEffect(actualCoverUrl) {
        controller.onActualCoverChanged(actualCoverUrl)
    }
    return controller
}

@Composable
private fun rememberNeighborCovers(): Pair<String?, String?> {
    val np by com.music.spotui.di.SongPlayer.nowPlaying
    val queue by com.music.spotui.di.SongPlayer.engineQueue
    val prevRaw = np.prev?.coverUri?.takeIf { it.isNotBlank() }
    val nextRaw = (queue.queued.firstOrNull()?.song?.coverUri?.takeIf { it.isNotBlank() }
        ?: queue.nextUp.firstOrNull()?.song?.coverUri?.takeIf { it.isNotBlank() }
        ?: np.next?.coverUri?.takeIf { it.isNotBlank() })
    val prev = prevRaw?.let(app.myspoti.host.android.Covers::large)
    val next = nextRaw?.let(app.myspoti.host.android.Covers::large)
    return prev to next
}

@Composable
private fun Modifier.swipeToChangeTrack(
    enabled: Boolean,
    actions: ImmersivePlayerActions,
    swipe: CoverSwipeController,
    currentCoverUrl: String,
    prevCoverUrl: String?,
    nextCoverUrl: String?,
): Modifier {
    val on by rememberUpdatedState(enabled)
    val current by rememberUpdatedState(actions)
    val curCover by rememberUpdatedState(currentCoverUrl)
    val prevCover by rememberUpdatedState(prevCoverUrl)
    val nextCover by rememberUpdatedState(nextCoverUrl)
    return pointerInput(swipe) {
        val minThreshold = TRACK_SWIPE_MIN_THRESHOLD.toPx()
        val gapPx = TRACK_SWIPE_CARD_GAP.toPx()
        val flingVel = TRACK_SWIPE_FLING_VELOCITY.toPx()
        val flingMinDrag = TRACK_SWIPE_FLING_MIN_DRAG.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!on) return@awaitEachGesture
            val slopped = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                val moved = change.position - down.position
                if (abs(moved.x) > abs(moved.y)) change.consume()
            } ?: return@awaitEachGesture
            val sidePx = size.width.toFloat().coerceAtLeast(1f)
            val spanPx = sidePx + gapPx
            val threshold = maxOf(minThreshold, sidePx * 0.30f)
            val velocity = VelocityTracker()
            velocity.addPointerInputChange(down)
            velocity.addPointerInputChange(slopped)
            var dragged = (slopped.position - down.position).x
            var armedDir = 0
            swipe.dragTo(dragged, spanPx)
            val finished = drag(slopped.id) { change ->
                velocity.addPointerInputChange(change)
                dragged += change.positionChange().x
                change.consume()
                swipe.dragTo(dragged, spanPx)
                val nowDir = when {
                    dragged <= -threshold -> -1
                    dragged >= threshold -> 1
                    else -> 0
                }
                if (nowDir != armedDir) {
                    armedDir = nowDir
                    com.music.spotui.di.Haptics.tick()
                }
            }
            if (!finished) {
                swipe.springBack()
                return@awaitEachGesture
            }
            val vx = velocity.calculateVelocity().x
            val commitDir = when {
                dragged <= -threshold || (vx <= -flingVel && dragged <= -flingMinDrag) -> -1
                dragged >= threshold || (vx >= flingVel && dragged >= flingMinDrag) -> 1
                else -> 0
            }
            when (commitDir) {
                -1 -> swipe.commit(-1, spanPx, curCover, nextCover) { current.next() }
                1 -> swipe.commit(1, spanPx, curCover, prevCover) { current.swipePrevious() }
                else -> swipe.springBack()
            }
        }
    }
}

/**
 * The Immersive look's player: the cover's colours drifting behind a large cover, the credits,
 * a scrubber, the transport, the volume and a row of lyrics, devices and queue.
 *
 * Opening the lyrics or the queue moves the cover up into a small header with the credits beside
 * it; [ImmersiveLyricsPanel] or [ImmersiveQueuePanel] takes the middle and the controls below
 * stay where they are. [navController] is for the "more" sheet (artist, album, playlists…):
 * without it the button is not shown.
 */
@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun ImmersivePlayer(
    state: ImmersivePlayerState,
    actions: ImmersivePlayerActions,
    modifier: Modifier = Modifier,
    navController: NavController? = null,
) {
    val context = LocalContext.current
    val palette = rememberArtworkColors(state.coverUrl.takeIf { it.isNotBlank() })
    // A crossfade: the colours of the song going out, as they were when it began (the new cover's
    // own come once it is loaded, and rememberArtworkColors keeps the old ones until then).
    val blend = state.blend
    val outgoingPalette = remember(blend) { palette }
    // The tint is the words' colour: it changes with them, half way.
    val tintPalette = if (blend?.outgoingWords == true) outgoingPalette else palette
    val tint by animateColorAsState(playerTintOf(tintPalette), tween(com.music.spotui.ui.immersive.motion.ImmersiveMotion.TINT_MS), label = "playerTint")

    var panel by rememberSaveable { mutableIntStateOf(Panel.NONE) }
    var lyricsFullscreen by rememberSaveable { mutableStateOf(false) }
    val reduceMotion = com.music.spotui.ui.look.rememberReduceMotion()
    val lyricsMotionMs = if (reduceMotion) 0 else PANEL_TRAVEL_MS
    // Move the same list between layouts: expanding keeps manual scroll, translation, selection
    // and the playhead alive, including when the window switches to the landscape layout.
    val lyricsContent = remember {
        movableContentOf<PaddingValues> { insets ->
            ImmersiveLyricsPanel(
                Modifier.fillMaxSize(), insets,
                onExpand = if (lyricsFullscreen) null else ({ lyricsFullscreen = true }),
            )
        }
    }
    var lyricsExpanded by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDevices by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var showNerd by remember { mutableStateOf(false) }
    val lyricsOpen = panel == Panel.LYRICS
    val queueOpen = panel == Panel.QUEUE

    // 0 with the full player, 1 with the cover in the header. Read at measure, placement and draw.
    val collapse = animateFloatAsState(
        targetValue = if (panel == Panel.NONE) 0f else 1f,
        animationSpec = tween(PANEL_TRAVEL_MS, easing = FastOutSlowInEasing),
        label = "playerCollapse",
    )
    val pauseScale = rememberPauseScale(state.playing)
    val volume = rememberPlayerVolume()
    val density = LocalDensity.current
    val fallbackSpanPx = with(density) { 340.dp.toPx() }
    val gapPx = with(density) { TRACK_SWIPE_CARD_GAP.toPx() }
    val coverSwipe = rememberCoverSwipeController(state.coverUrl)
    val (prevCoverUrl, nextCoverUrl) = rememberNeighborCovers()
    val shownCoverUrl = coverSwipe.outgoingCoverUrl ?: coverSwipe.overrideCoverUrl ?: state.coverUrl
    val swipeSign by remember(coverSwipe) {
        derivedStateOf {
            when {
                coverSwipe.offsetX.value < -0.5f -> -1
                coverSwipe.offsetX.value > 0.5f -> 1
                else -> 0
            }
        }
    }
    val curShownCover by rememberUpdatedState(shownCoverUrl)
    val curPrevCover by rememberUpdatedState(prevCoverUrl)
    val curNextCover by rememberUpdatedState(nextCoverUrl)
    val curActions by rememberUpdatedState(actions)
    val curPositionMs by rememberUpdatedState(state.positionMs)
    val onNextWithAnim: () -> Unit = remember(coverSwipe, fallbackSpanPx) {
        {
            coverSwipe.stepByButton(
                direction = -1,
                fallbackSpanPx = fallbackSpanPx,
                currentCover = curShownCover,
                neighborCover = curNextCover,
                positionMs = curPositionMs(),
            ) { curActions.next() }
        }
    }
    val onPreviousWithAnim: () -> Unit = remember(coverSwipe, fallbackSpanPx) {
        {
            coverSwipe.stepByButton(
                direction = 1,
                fallbackSpanPx = fallbackSpanPx,
                currentCover = curShownCover,
                neighborCover = curPrevCover,
                positionMs = curPositionMs(),
            ) { curActions.previous() }
        }
    }

    // The song's Canvas, behind the player once its first frame is drawn. The cover opens into it:
    // the clip fades in from a little closer while the sleeve grows and fades away. The lyrics or
    // the queue close it the same way back: the clip (paused under them) fades out to the cover's
    // backdrop and the sleeve returns as the header's thumbnail, rather than a still clip showing
    // around the panel; closing the panel opens the cover into the clip again.
    val canvasUrl = rememberImmersiveCanvas(state)
    var canvasShown by remember(canvasUrl) {
        mutableStateOf(canvasUrl != null && com.music.spotui.ui.components.CanvasPlayer.isReady(canvasUrl))
    }
    val canvasOn = canvasUrl != null && canvasShown && !state.adPlaying && panel == Panel.NONE
    // Opening on a clip already showing starts at the end; every later change animates from where it is.
    val canvasOpen = remember { androidx.compose.animation.core.Animatable(if (canvasOn) 1f else 0f) }
    // Opening the player: the heart of the song shown is checked again if its state is old.
    LaunchedEffect(Unit) { com.music.spotui.di.SongPlayer.refreshLikedIfStale() }
    LaunchedEffect(canvasOn) {
        val target = if (canvasOn) 1f else 0f
        val left = kotlin.math.abs(target - canvasOpen.value)
        // A panel opening: the clip leaves with the cover's travel to the header, not after it.
        val ms = if (!canvasOn && panel != Panel.NONE) PANEL_TRAVEL_MS else CANVAS_OPEN_MS
        if (left > 0f) canvasOpen.animateTo(target, tween((ms * left).toInt(), easing = FastOutSlowInEasing))
    }

    // Back leaves the panel first; the sheet (outside) closes on the next one.
    BackHandler(enabled = lyricsExpanded || panel != Panel.NONE) {
        if (lyricsExpanded) lyricsExpanded = false
        else if (lyricsFullscreen) lyricsFullscreen = false
        else panel = Panel.NONE
    }

    // The wave that leaves the cover when a mix is over, over everything.
    // Not over an open panel: the lyrics or the queue drawing in and springing back with the mix
    // moved the lines being read (reported as particularly bad on the lyrics). Nor over a Canvas:
    // the clip stays full screen, so the controls sprang about over it and the wave crossed the
    // video (reported as the end of the mix bugging on a Canvas).
    val mixMotion = state.mixPulse.takeIf { panel == Panel.NONE && !canvasOn }
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black).mixWave(mixMotion, Color.White)) {
        // Wider than tall (a phone on its side, a tablet), or a large portrait tablet: the cover
        // beside the controls. Very wide windows (DeX, large tablets): the lyrics and the queue
        // open in a third pane instead of over the cover.
        val window = com.music.spotui.ui.components.windowSize()
        // Never a phone held upright, whatever this box measures for a moment (WindowSize.portraitPhone).
        val landscape = window.playerSideBySide(maxWidth, maxHeight)
        val sidePane = window.large && maxWidth >= SIDE_PANE_MIN_WIDTH
        val playerWidth = maxWidth
        PlayerBackdrop(palette, outgoingPalette, blend, state.coverUrl, shownCoverUrl, portrait = !landscape)
        if (canvasUrl != null) {
            com.music.spotui.ui.screens.CanvasVideo(
                url = canvasUrl,
                onFirstFrame = { canvasShown = true },
                playing = PlayerSheet.open.value && state.playing && !state.buffering && panel == Panel.NONE,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val o = canvasOpen.value
                    alpha = o
                    val s = 1f + CANVAS_OPEN_ZOOM * (1f - o)
                    scaleX = s
                    scaleY = s
                },
            )
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = canvasOpen.value * (0.55f + 0.45f * collapse.value) }
                    .background(CanvasShade),
            )
        }
        var canvasControls by remember(canvasOn, canvasUrl, com.music.spotui.data.preferences.ImmersiveChromePref.canvas) { mutableStateOf(false) }
        if (canvasOn && com.music.spotui.data.preferences.ImmersiveChromePref.canvas && !canvasControls) {
            Box(Modifier.fillMaxSize().clickable(onClickLabel = stringResource(R.string.chrome_canvas_detail)) { canvasControls = true })
        } else {
        Box(Modifier.fillMaxSize().mixSqueeze(mixMotion)) {
        CompositionLocalProvider(LocalPlayerTint provides tint) {
            if (landscape) LandscapePlayer(
                state = state,
                actions = actions,
                tint = tint,
                panel = panel,
                lyricsContent = lyricsContent,
                lyricsFullscreen = lyricsFullscreen && lyricsOpen,
                onCollapseLyrics = { lyricsFullscreen = false },
                lyricsMotionMs = lyricsMotionMs,
                pauseScale = pauseScale,
                volume = volume,
                coverSwipe = coverSwipe,
                swipeSign = swipeSign,
                shownCoverUrl = shownCoverUrl,
                prevCoverUrl = prevCoverUrl,
                nextCoverUrl = nextCoverUrl,
                gapPx = gapPx,
                onPreviousStep = onPreviousWithAnim,
                onNextStep = onNextWithAnim,
                onToggleLyrics = { lyricsFullscreen = false; panel = if (lyricsOpen) Panel.NONE else Panel.LYRICS },
                onToggleQueue = { lyricsFullscreen = false; panel = if (queueOpen) Panel.NONE else Panel.QUEUE },
                onOpenDevices = { showDevices = true },
                onMore = if (navController != null && state.song != null) ({ showMenu = true }) else null,
                onSource = { showQuality = true },
                onSourceLong = { showNerd = true },
                sidePane = sidePane,
            ) else {
                val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                // The contained style shares the large-window square layout, while full screen
                // keeps the current phone-first artwork treatment.
                val heroMode = !window.large && com.music.spotui.data.preferences.ImmersivePlayerCoverPref.fullScreen
                Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Box(Modifier.fillMaxWidth().zIndex(2f)) {
                    PlayerDismissStrip(tint) { actions.close() }
                }

                val lyricPreview = com.music.spotui.data.preferences.PlayerLyricsCardPref.shown && !state.adPlaying
                val stripHeight = lyricPreviewHeight()
                val titleGap = ART_TITLE_GAP + if (lyricPreview) stripHeight else 0.dp
                val blockMax = if (window.large) minOf(playerWidth - PLAYER_GUTTER * 2, PLAYER_MAX_WIDTH) +
                    ART_BOX_TOP_PAD + titleGap + HEADER_HEIGHT + 16.dp else Dp.Infinity
                Column(Modifier.weight(1f).fillMaxWidth().zIndex(1f), verticalArrangement = Arrangement.Center) {
                BoxWithConstraints(Modifier.weight(1f, fill = !window.large).heightIn(max = blockMax).fillMaxWidth()) {
                    val contentWidth = minOf(maxWidth - PLAYER_GUTTER * 2, PLAYER_MAX_WIDTH)
                    val left = (maxWidth - contentWidth) / 2
                    val fullArt = minOf(contentWidth, maxHeight - ART_BOX_TOP_PAD - titleGap - HEADER_HEIGHT - 16.dp)
                        .coerceAtLeast(THUMB_SIZE)
                    val spare = (maxHeight - ART_BOX_TOP_PAD - fullArt - titleGap - HEADER_HEIGHT - 16.dp)
                        .coerceAtLeast(0.dp)
                    val groupTop = spare / 2
                    val heroBleedTop = statusTop + DISMISS_STRIP_HEIGHT
                    val heroBottomInBox = ART_BOX_TOP_PAD + spare + fullArt + ART_TITLE_GAP * 0.65f
                    fun p() = collapse.value
                    fun artWidth(): Dp = if (heroMode) lerp(maxWidth, THUMB_SIZE, p()) else lerp(fullArt, THUMB_SIZE, p())
                    fun artHeight(): Dp = if (heroMode) lerp(heroBleedTop + heroBottomInBox, THUMB_SIZE, p()) else lerp(fullArt, THUMB_SIZE, p())
                    fun artTop(): Dp = if (heroMode) lerp(-heroBleedTop, ART_BOX_TOP_PAD, p()) else ART_BOX_TOP_PAD + lerp(groupTop, 0.dp, p())
                    fun artStart(): Dp = if (heroMode) lerp(0.dp, left, p()) else left + lerp((contentWidth - fullArt) / 2, 0.dp, p())
                    fun titleTop(): Dp = ART_BOX_TOP_PAD + lerp(spare + fullArt + titleGap, 0.dp, p())
                    fun titleStart(): Dp = lerp(0.dp, THUMB_SIZE + 12.dp, p())
                    val scrolls by remember { derivedStateOf { collapse.value < 0.01f } }

                    val panelAlpha = { ((p() - 0.6f) / 0.4f).coerceIn(0f, 1f) }
                    Crossfade(
                        targetState = panel,
                        animationSpec = tween(250),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = ART_BOX_TOP_PAD + HEADER_HEIGHT)
                            .graphicsLayer { alpha = panelAlpha() },
                        label = "playerPanel",
                    ) { shown ->
                        val inset = PaddingValues(start = left, end = left, top = 12.dp, bottom = 12.dp)
                        when (shown) {
                            Panel.LYRICS -> Box(
                                Modifier.fillMaxSize().lyricsExpandGesture(
                                    enabled = com.music.spotui.data.preferences.LyricsSwipeExpandPref.on && !lyricsExpanded,
                                    onExpand = { lyricsExpanded = true },
                                )
                            ) {
                                lyricsContent(inset)
                            }
                            Panel.QUEUE -> ImmersiveQueuePanel(Modifier.fillMaxSize(), inset)
                            else -> Box(Modifier.fillMaxSize())
                        }
                    }

                    val activeGapPx = if (heroMode) 0f else gapPx
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(artStart().roundToPx(), artTop().roundToPx()) }
                            .layout { measurable, _ ->
                                val w = artWidth().roundToPx().coerceAtLeast(1)
                                val h = artHeight().roundToPx().coerceAtLeast(1)
                                val placeable = measurable.measure(Constraints.fixed(w, h))
                                layout(w, h) { placeable.place(0, 0) }
                            }
                            .onSizeChanged { if (panel == Panel.NONE && it.width > 0) coverSwipe.spanPx = it.width + activeGapPx }
                            .graphicsLayer {
                                val corner = if (heroMode) 8.dp * p() else 8.dp
                                shape = RoundedCornerShape(corner)
                                clip = true
                                compositingStrategy = if (heroMode && p() < 0.995f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                            }
                            .drawWithContent {
                                drawContent()
                                if (heroMode) {
                                    val fadeShare = (1f - p()).coerceIn(0f, 1f)
                                    if (fadeShare > 0.005f) {
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(Color.Black, Color.Black.copy(alpha = 1f - fadeShare)),
                                                startY = size.height * (1f - HERO_FADE_FRACTION),
                                                endY = size.height,
                                            ),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    }
                                }
                            }
                            .dragToClose()
                            .swipeToChangeTrack(
                                enabled = panel == Panel.NONE,
                                actions = actions,
                                swipe = coverSwipe,
                                currentCoverUrl = shownCoverUrl,
                                prevCoverUrl = prevCoverUrl,
                                nextCoverUrl = nextCoverUrl,
                            )
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                if (panel != Panel.NONE) panel = Panel.NONE
                            },
                    ) {
                        val peekUrl = coverSwipe.incomingCoverUrl
                            ?: (if (swipeSign < 0) nextCoverUrl else if (swipeSign > 0) prevCoverUrl else null)?.takeIf { it != shownCoverUrl }
                        if (!peekUrl.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val dx = coverSwipe.offsetX.value * (1f - p())
                                        val span = (size.width + activeGapPx).coerceAtLeast(1f)
                                        val progress = (abs(dx) / span).coerceIn(0f, 1f)
                                        val opened = canvasOpen.value * (1f - p())
                                        val baseScale = if (heroMode) {
                                            1f + 0.03f * (1f - progress)
                                        } else {
                                            (pauseScale.value + (1f - pauseScale.value) * p()) * (0.90f + 0.10f * progress)
                                        }
                                        translationX = if (dx < 0f) span + dx else -span + dx
                                        scaleX = baseScale
                                        scaleY = baseScale
                                        alpha = (if (heroMode) 0.55f + 0.45f * progress else 0.35f + 0.65f * progress) * (1f - opened) * (1f - p())
                                        if (!heroMode) {
                                            shadowElevation = 8.dp.toPx() * progress * (1f - p()) * (1f - opened)
                                            shape = ArtCorner
                                            clip = true
                                        }
                                    }
                                    .background(Color.Black.copy(alpha = 0.18f)),
                            ) {
                                GlideImage(
                                    model = peekUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    loading = placeholder(R.drawable.placeholder),
                                    failure = placeholder(R.drawable.placeholder),
                                    modifier = Modifier.fillMaxSize(),
                                    requestBuilderTransform = { it.override(COVER_DECODE_PX) },
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val opened = canvasOpen.value * (1f - p())
                                    val beat = 1f + MIX_BEAT_GROW * (state.mixPulse?.level() ?: 0f) * (1f - p())
                                    val dx = coverSwipe.offsetX.value * (1f - p())
                                    val span = (size.width + activeGapPx).coerceAtLeast(1f)
                                    val dragFrac = (abs(dx) / span).coerceIn(0f, 1f)
                                    val scale = if (heroMode) {
                                        (1f + CANVAS_COVER_GROW * opened) * beat * (1f + 0.02f * dragFrac)
                                    } else {
                                        (pauseScale.value + (1f - pauseScale.value) * p()) *
                                            (1f + CANVAS_COVER_GROW * opened) *
                                            beat *
                                            (1f - 0.06f * dragFrac)
                                    }
                                    translationX = dx
                                    rotationZ = if (heroMode) 0f else (dx / span) * 4.5f * (1f - p())
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = (1f - opened) * (1f - 0.18f * dragFrac) * coverSwipe.enterAlpha.value
                                    if (!heroMode) {
                                        shadowElevation = 10.dp.toPx() * (1f - p()) * (1f - opened)
                                        shape = ArtCorner
                                        clip = true
                                    }
                                }
                                .background(Color.Black.copy(alpha = 0.18f)),
                        ) {
                            GlideImage(
                                model = shownCoverUrl.takeIf { it.isNotBlank() },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                loading = placeholder(R.drawable.placeholder),
                                failure = placeholder(R.drawable.placeholder),
                                modifier = Modifier.fillMaxSize().artworkLoop(PlayerSheet.open.value && state.playing && !state.buffering && !state.adPlaying && !canvasOn && panel == Panel.NONE),
                                requestBuilderTransform = { it.override(COVER_DECODE_PX) },
                            )
                            if (coverSwipe.overrideCoverUrl == null && coverSwipe.outgoingCoverUrl == null) {
                                SongBlendCover(blend, Modifier.fillMaxSize()) { it.override(COVER_DECODE_PX) }
                            }
                        }
                    }

                    CurrentLyricStrip(
                        state = state,
                        visible = lyricPreview && panel == Panel.NONE && scrolls,
                        onOpenLyrics = { panel = Panel.LYRICS },
                        modifier = Modifier
                            .zIndex(2f)
                            .offset { IntOffset(left.roundToPx(), (titleTop() - stripHeight).roundToPx()) }
                            .width(contentWidth),
                    )
                    Row(
                        modifier = Modifier
                            .zIndex(2f)
                            .offset {
                                IntOffset(
                                    left.roundToPx(),
                                    (titleTop() - lerp(0.dp, (HEADER_HEIGHT - THUMB_SIZE) / 2, p())).roundToPx(),
                                )
                            }
                            .width(contentWidth)
                            .layout { measurable, constraints ->
                                val start = titleStart().roundToPx()
                                val placeable = measurable.measure(constraints.offset(horizontal = -start))
                                layout(
                                    constraints.constrainWidth(placeable.width + start),
                                    constraints.constrainHeight(placeable.height),
                                ) { placeable.placeRelative(start, 0) }
                            }
                            .height(HEADER_HEIGHT)
                            .dragToClose(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerCredits(
                            state = state, actions = actions, tint = tint, scrolls = scrolls, collapse = { p() },
                            coverSwipe = coverSwipe,
                            onMore = if (navController != null && state.song != null) ({ showMenu = true }) else null,
                            onCollapseLyrics = if (lyricsFullscreen && lyricsOpen) ({ lyricsFullscreen = false }) else null,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !lyricsFullscreen || !lyricsOpen,
                    enter = fadeIn(tween(lyricsMotionMs)) + expandVertically(
                        tween(lyricsMotionMs, easing = FastOutSlowInEasing), expandFrom = Alignment.Bottom,
                    ) + slideInVertically(tween(lyricsMotionMs, easing = FastOutSlowInEasing)) { it / 2 },
                    exit = fadeOut(tween(lyricsMotionMs / 2)) + shrinkVertically(
                        tween(lyricsMotionMs, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Bottom,
                    ) + slideOutVertically(tween(lyricsMotionMs, easing = FastOutSlowInEasing)) { it / 2 },
                    modifier = Modifier.align(Alignment.CenterHorizontally).zIndex(2f),
                ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = PLAYER_MAX_WIDTH + PLAYER_GUTTER * 2)
                        .fillMaxWidth()
                        .padding(horizontal = PLAYER_GUTTER)
                        .zIndex(2f),
                ) {
                    val showVolume = com.music.spotui.data.preferences.PlayerVolumePref.on
                    PlayerControlsBlock(
                        state = state, actions = actions, volume = volume, lyricsOpen = lyricsOpen, queueOpen = queueOpen,
                        showVolume = showVolume,
                        onPreviousStep = onPreviousWithAnim,
                        onNextStep = onNextWithAnim,
                        onToggleLyrics = { lyricsFullscreen = false; panel = if (lyricsOpen) Panel.NONE else Panel.LYRICS },
                        onToggleQueue = { lyricsFullscreen = false; panel = if (queueOpen) Panel.NONE else Panel.QUEUE },
                        onOpenDevices = { showDevices = true },
                        onSource = { showQuality = true },
                        onSourceLong = { showNerd = true },
                    )
                }
                }
                }
                }
                if (heroMode) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(statusTop + DISMISS_STRIP_HEIGHT + 28.dp)
                            .align(Alignment.TopCenter)
                            .graphicsLayer { alpha = (1f - collapse.value).coerceIn(0f, 1f) }
                            .background(TopHeroScrim),
                    )
                }
                }
            }
        }
        }
        if (com.music.spotui.data.preferences.ImmersiveChromePref.stats && panel == Panel.NONE && !lyricsExpanded) {
            androidx.compose.material3.IconButton(onClick = { showNerd = true }, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 4.dp)) {
                androidx.compose.material3.Icon(Icons.Rounded.Equalizer, stringResource(R.string.nerd_title), tint = tint)
            }
        }
        AnimatedVisibility(
            visible = lyricsExpanded && panel == Panel.LYRICS,
            modifier = Modifier.fillMaxSize().zIndex(20f),
            enter = fadeIn(tween(180)) + slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it / 8 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it / 8 },
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding().statusBarsPadding()
                .lyricsExpandGesture(
                    enabled = com.music.spotui.data.preferences.LyricsSwipeExpandPref.on,
                    onCollapse = { lyricsExpanded = false; panel = Panel.NONE },
                )) {
                ImmersiveLyricsPanel(Modifier.fillMaxSize(), PaddingValues(horizontal = 18.dp, vertical = 8.dp))
            }
        }
        }
    }

    if (showDevices) DevicesSheet { showDevices = false }
    if (showQuality) com.music.spotui.ui.components.QualitySheet { showQuality = false }
    if (showNerd) com.music.spotui.ui.components.StatsForNerdsSheet { showNerd = false }
    val song = state.song
    if (showMenu && song != null && navController != null) {
        ImmersiveSongOptionsSheet(
            song = song,
            navController = navController,
            context = context,
            onDismiss = { showMenu = false },
            nowPlaying = true,
            onOpenQueue = { panel = Panel.QUEUE },
            lyricsOpen = lyricsOpen,
        )
    }
}

/** Vertical gesture used only to move between the compact and full-screen lyrics views. */
private fun Modifier.lyricsExpandGesture(
    enabled: Boolean,
    onExpand: () -> Unit = {},
    onCollapse: () -> Unit = {},
): Modifier = if (!enabled) this else pointerInput(enabled) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var total = 0f
        var triggered = false
        while (!triggered) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            total += change.positionChange().y
            val threshold = 76.dp.toPx()
            if (total >= threshold) {
                change.consume()
                onExpand()
                triggered = true
            } else if (total <= -threshold) {
                change.consume()
                onCollapse()
                triggered = true
            }
            if (!change.pressed) break
        }
    }
}

/** The cover opening into the Canvas, and the clip's fade in. */
private const val CANVAS_OPEN_MS = 720
/** How much closer the clip starts as it opens. */
private const val CANVAS_OPEN_ZOOM = 0.08f
/** How much the sleeve grows as it opens into the clip. */
private const val CANVAS_COVER_GROW = 0.12f
private val CanvasShade = Brush.verticalGradient(
    listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.8f)),
)

private val TopHeroScrim = Brush.verticalGradient(
    0.00f to Color.Black.copy(alpha = 0.38f),
    0.55f to Color.Black.copy(alpha = 0.14f),
    1.00f to Color.Transparent,
)

private val PortraitBackdropShade = Brush.verticalGradient(
    0.00f to Color.Transparent,
    0.42f to Color.Black.copy(alpha = 0.14f),
    0.72f to Color.Black.copy(alpha = 0.22f),
    1.00f to Color.Black.copy(alpha = 0.16f),
)

/**
 * The Canvas of the song shown, as the Classic player finds it (same view model, cache and
 * settings: Canvases off, data saver, Wi-Fi only, energy saving); null when there is none.
 */
@Composable
private fun rememberImmersiveCanvas(state: ImmersivePlayerState): String? {
    val context = LocalContext.current
    val vm: com.music.spotui.ui.viewmodel.PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val energy by app.myspoti.power.EnergySettings.state.collectAsStateWithLifecycle()
    val allowed = energy.policy.allowCanvas && !com.music.spotui.data.DataSaver.active &&
        !(com.music.spotui.data.DataSaver.metered && com.music.spotui.data.preferences.isCanvasWifiOnly(context))
    val trackId = state.trackId
    LaunchedEffect(trackId, allowed, state.buffering) {
        vm.loadCanvas(if (allowed) trackId else "", allowFetch = !state.buffering)
    }
    val canvas = vm.canvasState.value
    // Known already (looked up when the song started): this very frame, not one later.
    val known = remember(trackId) { com.music.spotui.di.Graph.api.knownCanvas(trackId)?.getOrNull() }
    return (if (canvas.trackId == trackId) canvas.url else known).takeIf { allowed && trackId.isNotBlank() && !state.adPlaying }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun PlayerBackdrop(
    palette: MeshPalette,
    outgoing: MeshPalette,
    blend: SongBlend?,
    trackKey: String,
    coverUrl: String = "",
    portrait: Boolean = false,
) {
    MeshGradientBackground(
        palette = if (blend == null) palette else lerp(outgoing, palette, blend.progress()),
        trackKey = trackKey,
        colorFadeMillis = if (blend == null) 1400 else 250,
    )
    if (portrait && coverUrl.isNotBlank()) {
        GlideImage(
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.35f
                    scaleY = -1.35f
                    alpha = 0.44f
                },
            requestBuilderTransform = { it.override(96).transform(BackdropBlurTransformation) },
        )
    }
    if (portrait) {
        Box(Modifier.fillMaxSize().background(PortraitBackdropShade))
    }
}

private object BackdropBlurTransformation : com.bumptech.glide.load.resource.bitmap.BitmapTransformation() {
    private val ID_BYTES = "revera.backdrop.blur.v1".toByteArray(Charsets.UTF_8)

    override fun updateDiskCacheKey(messageDigest: java.security.MessageDigest) {
        messageDigest.update(ID_BYTES)
    }

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = ID_BYTES.contentHashCode()

    override fun transform(
        pool: com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool,
        toTransform: android.graphics.Bitmap,
        outWidth: Int,
        outHeight: Int,
    ): android.graphics.Bitmap {
        val w = toTransform.width.coerceAtLeast(1)
        val h = toTransform.height.coerceAtLeast(1)
        val out = pool.get(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val src = if (toTransform.config == android.graphics.Bitmap.Config.ARGB_8888) toTransform
        else toTransform.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        src.getPixels(a, 0, w, 0, 0, w, h)
        if (src !== toTransform) src.recycle()
        boxBlur(a, b, w, h, radius = 10)
        boxBlur(b, a, w, h, radius = 10)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out
    }

    private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        val tmp = IntArray(w * h)
        val div = radius * 2 + 1
        for (y in 0 until h) {
            val row = y * w
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (k in -radius..radius) {
                val c = src[row + k.coerceIn(0, w - 1)]
                sa += (c ushr 24) and 0xff; sr += (c ushr 16) and 0xff; sg += (c ushr 8) and 0xff; sb += c and 0xff
            }
            for (x in 0 until w) {
                tmp[row + x] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val outC = src[row + (x - radius).coerceIn(0, w - 1)]
                val inC = src[row + (x + radius + 1).coerceIn(0, w - 1)]
                sa += ((inC ushr 24) and 0xff) - ((outC ushr 24) and 0xff)
                sr += ((inC ushr 16) and 0xff) - ((outC ushr 16) and 0xff)
                sg += ((inC ushr 8) and 0xff) - ((outC ushr 8) and 0xff)
                sb += (inC and 0xff) - (outC and 0xff)
            }
        }
        for (x in 0 until w) {
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (k in -radius..radius) {
                val c = tmp[k.coerceIn(0, h - 1) * w + x]
                sa += (c ushr 24) and 0xff; sr += (c ushr 16) and 0xff; sg += (c ushr 8) and 0xff; sb += c and 0xff
            }
            for (y in 0 until h) {
                dst[y * w + x] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val outC = tmp[(y - radius).coerceIn(0, h - 1) * w + x]
                val inC = tmp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                sa += ((inC ushr 24) and 0xff) - ((outC ushr 24) and 0xff)
                sr += ((inC ushr 16) and 0xff) - ((outC ushr 16) and 0xff)
                sg += ((inC ushr 8) and 0xff) - ((outC ushr 8) and 0xff)
                sb += (inC and 0xff) - (outC and 0xff)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.PlayerCredits(
    state: ImmersivePlayerState,
    actions: ImmersivePlayerActions,
    tint: Color,
    scrolls: Boolean,
    collapse: () -> Float,
    coverSwipe: CoverSwipeController,
    onMore: (() -> Unit)?,
    onCollapseLyrics: (() -> Unit)? = null,
) {
    val lineH = if (state.adPlaying) 24.sp else 23.sp
    Column(
        Modifier
            .weight(1f)
            .wrapContentHeight(unbounded = true)
            .graphicsLayer {
                val scale = 1f - 0.2f * collapse()
                translationX = coverSwipe.creditsOffsetX.value
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
                alpha = (state.blend?.wordsAlpha() ?: 1f) * coverSwipe.creditsAlpha.value
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.explicit && !state.adPlaying) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(tint.copy(alpha = 0.52f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.explicit_badge),
                        style = Immersive.Caption.copy(fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold),
                        color = Color.Black,
                    )
                }
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = if (state.adPlaying) stringResource(R.string.immersive_player_ad) else state.title,
                style = Immersive.Title.copy(fontSize = 20.sp, lineHeight = lineH, fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { actions.openAlbum() }
                    .titleMarquee(enabled = scrolls, iterations = Int.MAX_VALUE),
            )
        }
        if (!state.adPlaying) {
            val credit = com.music.spotui.ui.components.rememberArtistCredit(state.trackId, state.artist, tint.copy(alpha = 0.78f)) { a -> actions.openArtistRef(a) }
            val lineScrolls = com.music.spotui.ui.components.rememberArtistLineScrolls(state.artist)
            Text(
                text = credit?.takeIf { !lineScrolls.value } ?: androidx.compose.ui.text.AnnotatedString(state.artist),
                style = Immersive.Title.copy(fontSize = 20.sp, lineHeight = lineH, fontWeight = FontWeight.W500),
                color = tint.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (it.hasVisualOverflow) lineScrolls.value = true },
                modifier = Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { actions.openArtist() }
                    .titleMarquee(enabled = scrolls && lineScrolls.value, iterations = Int.MAX_VALUE),
            )
        }
    }
    Spacer(Modifier.width(10.dp))
    val actionsWidth by animateDpAsState(
        if (onMore != null || onCollapseLyrics != null) 76.dp else 34.dp,
        tween(if (com.music.spotui.ui.look.rememberReduceMotion()) 0 else PANEL_TRAVEL_MS),
        label = "lyricsHeaderActionsWidth",
    )
    // Keep the cover and credits in the same layout while the actions dissolve in place.
    Crossfade(targetState = onCollapseLyrics != null, modifier = Modifier.width(actionsWidth), label = "lyricsHeaderActions") { expanded ->
    Row(verticalAlignment = Alignment.CenterVertically) {
    if (expanded) {
        LyricsFullscreenActions(onCollapse = { onCollapseLyrics?.invoke() })
    } else {
    CircleGlyph(
        icon = if (state.liked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
        contentDescription = stringResource(if (state.liked) R.string.immersive_player_unlike else R.string.immersive_player_like),
        onClick = actions.toggleLike,
        active = state.liked,
    )
    if (onMore != null) {
        Spacer(Modifier.width(8.dp))
        CircleGlyph(
            icon = Icons.Rounded.MoreHoriz,
            contentDescription = stringResource(R.string.immersive_player_more),
            onClick = onMore,
        )
    }
    }
    }
    }
}

@Composable
private fun PlayerControlsBlock(
    state: ImmersivePlayerState,
    actions: ImmersivePlayerActions,
    volume: PlayerVolume,
    lyricsOpen: Boolean,
    queueOpen: Boolean,
    showVolume: Boolean,
    onPreviousStep: () -> Unit,
    onNextStep: () -> Unit,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    onOpenDevices: () -> Unit,
    onSource: (() -> Unit)? = null,
    onSourceLong: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    PlayerScrubber(
        state = state,
        onSeek = actions.seekTo,
        onSource = onSource,
        onSourceLong = onSourceLong,
    )
    Spacer(Modifier.height(8.dp))
    TransportRow(
        isPlaying = state.playing,
        isLoading = state.buffering,
        onPrevious = onPreviousStep,
        onPlayPause = actions.playPause,
        onNext = onNextStep,
        compact = compact,
    )
    Spacer(Modifier.height(if (showVolume) 12.dp else 6.dp))
    if (showVolume) {
        VolumeRow(volume)
        Spacer(Modifier.height(6.dp))
    }
    PlayerActionRow(
        lyricsOpen = lyricsOpen,
        queueOpen = queueOpen,
        shuffleEnabled = state.shuffle || state.smart,
        smartEnabled = state.smart,
        repeatMode = state.repeatMode,
        deviceActive = state.deviceName != null,
        onToggleLyrics = onToggleLyrics,
        onToggleQueue = onToggleQueue,
        onToggleShuffle = actions.toggleShuffle,
        onCycleRepeat = actions.cycleRepeat,
        onOpenDevices = onOpenDevices,
    )
    // Buttons from extensions (player.buttons), under the player's own controls.
    val extTrack = remember(state.title, state.artist) { com.music.spotui.extensions.Extensions.currentTrack() }
    com.music.spotui.extensions.MirrorIndicator()
    com.music.spotui.extensions.ExtensionPlayerButtons(extTrack)
    Spacer(Modifier.height(18.dp))
}

/**
 * The landscape layout: the cover on the left (the lyrics or the queue take its place when they
 * are up), on the right the credits, the scrubber, every transport control and the bottom row.
 * The right side scrolls if a small window cannot hold it, so no control is out of reach.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun LandscapePlayer(
    state: ImmersivePlayerState,
    actions: ImmersivePlayerActions,
    tint: Color,
    panel: Int,
    lyricsContent: @Composable (PaddingValues) -> Unit,
    lyricsFullscreen: Boolean,
    onCollapseLyrics: () -> Unit,
    lyricsMotionMs: Int,
    pauseScale: State<Float>,
    volume: PlayerVolume,
    coverSwipe: CoverSwipeController,
    swipeSign: Int,
    shownCoverUrl: String,
    prevCoverUrl: String?,
    nextCoverUrl: String?,
    gapPx: Float,
    onPreviousStep: () -> Unit,
    onNextStep: () -> Unit,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    onOpenDevices: () -> Unit,
    onMore: (() -> Unit)?,
    onSource: () -> Unit,
    onSourceLong: () -> Unit = {},
    sidePane: Boolean = false,
) {
    val expansion = animateFloatAsState(
        if (lyricsFullscreen) 1f else 0f,
        tween(lyricsMotionMs, easing = FastOutSlowInEasing), label = "landscapeLyricsExpansion",
    )
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        PlayerDismissStrip(tint) { actions.close() }
        AnimatedVisibility(
            visible = lyricsFullscreen,
            enter = fadeIn(tween(lyricsMotionMs)) + expandVertically(tween(lyricsMotionMs, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(lyricsMotionMs / 2)) + shrinkVertically(tween(lyricsMotionMs, easing = FastOutSlowInEasing)),
        ) {
            FullscreenLyricsHeader(state, onCollapseLyrics)
        }
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().padding(start = PLAYER_GUTTER, end = PLAYER_GUTTER, bottom = 8.dp),
        ) {
        val progress = expansion.value
        val panes = if (sidePane && panel != Panel.NONE) 3.1f else 2f
        val normalPane = (maxWidth - PLAYER_GUTTER * if (panes > 2f) 2 else 1) / panes
        val fullWidth = maxWidth
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(PLAYER_GUTTER * (1f - progress)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!sidePane || progress < 1f) BoxWithConstraints(
                Modifier.width(lerp(normalPane, if (sidePane) 0.dp else fullWidth, progress))
                    .fillMaxHeight().clipToBounds()
                    .graphicsLayer { if (sidePane) alpha = (1f - progress * 2f).coerceAtLeast(0f) },
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight)
                Crossfade(targetState = if (sidePane) Panel.NONE else panel, animationSpec = tween(250), modifier = Modifier.fillMaxSize(), label = "playerLandscapePanel") { shown ->
                    when (shown) {
                        Panel.LYRICS -> lyricsContent(PaddingValues(vertical = 4.dp))
                        Panel.QUEUE -> ImmersiveQueuePanel(Modifier.fillMaxSize(), PaddingValues(vertical = 4.dp))
                        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier
                                    .size(side)
                                    .onSizeChanged { if (it.width > 0) coverSwipe.spanPx = it.width + gapPx }
                                    .dragToClose()
                                    .swipeToChangeTrack(
                                        enabled = true,
                                        actions = actions,
                                        swipe = coverSwipe,
                                        currentCoverUrl = shownCoverUrl,
                                        prevCoverUrl = prevCoverUrl,
                                        nextCoverUrl = nextCoverUrl,
                                    ),
                            ) {
                                val peekUrl = coverSwipe.incomingCoverUrl
                                    ?: (if (swipeSign < 0) nextCoverUrl else if (swipeSign > 0) prevCoverUrl else null)?.takeIf { it != shownCoverUrl }
                                if (!peekUrl.isNullOrBlank()) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                val dx = coverSwipe.offsetX.value
                                                val span = size.width + TRACK_SWIPE_CARD_GAP.toPx()
                                                val progress = (abs(dx) / span.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                                val s = pauseScale.value * (0.90f + 0.10f * progress)
                                                translationX = if (dx < 0f) span + dx else -span + dx
                                                scaleX = s
                                                scaleY = s
                                                alpha = 0.35f + 0.65f * progress
                                                shadowElevation = 8.dp.toPx() * progress
                                                shape = ArtCorner
                                                clip = true
                                            }
                                            .background(Color.Black.copy(alpha = 0.18f)),
                                    ) {
                                        GlideImage(
                                            model = peekUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            loading = placeholder(R.drawable.placeholder),
                                            failure = placeholder(R.drawable.placeholder),
                                            modifier = Modifier.fillMaxSize(),
                                            requestBuilderTransform = { it.override(COVER_DECODE_PX) },
                                        )
                                    }
                                }
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            val beat = 1f + MIX_BEAT_GROW * (state.mixPulse?.level() ?: 0f)
                                            val dx = coverSwipe.offsetX.value
                                            val span = (size.width + TRACK_SWIPE_CARD_GAP.toPx()).coerceAtLeast(1f)
                                            val dragFrac = (abs(dx) / span).coerceIn(0f, 1f)
                                            val s = pauseScale.value * beat * (1f - 0.06f * dragFrac)
                                            translationX = dx
                                            rotationZ = (dx / span) * 4.5f
                                            scaleX = s
                                            scaleY = s
                                            alpha = (1f - 0.20f * dragFrac) * coverSwipe.enterAlpha.value
                                            shadowElevation = 10.dp.toPx()
                                            shape = ArtCorner
                                            clip = true
                                        }
                                        .background(Color.Black.copy(alpha = 0.18f)),
                                ) {
                                    GlideImage(
                                        model = shownCoverUrl.takeIf { it.isNotBlank() },
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        loading = placeholder(R.drawable.placeholder),
                                        failure = placeholder(R.drawable.placeholder),
                                        modifier = Modifier.fillMaxSize(),
                                        requestBuilderTransform = { it.override(COVER_DECODE_PX) },
                                    )
                                    if (coverSwipe.overrideCoverUrl == null && coverSwipe.outgoingCoverUrl == null) {
                                        SongBlendCover(state.blend, Modifier.fillMaxSize()) { it.override(COVER_DECODE_PX) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (progress < 1f) BoxWithConstraints(
                Modifier.width(normalPane * (1f - progress)).fillMaxHeight().clipToBounds()
                    .graphicsLayer { alpha = (1f - progress * 2f).coerceAtLeast(0f) },
                contentAlignment = Alignment.Center,
            ) {
                val showVolume = com.music.spotui.data.preferences.PlayerVolumePref.on && maxHeight >= LANDSCAPE_VOLUME_MIN_HEIGHT
                Column(Modifier.widthIn(max = PLAYER_MAX_WIDTH).verticalScroll(rememberScrollState())) {
                    CurrentLyricStrip(
                        state = state,
                        visible = panel == Panel.NONE && !state.adPlaying && com.music.spotui.data.preferences.PlayerLyricsCardPref.shown,
                        onOpenLyrics = onToggleLyrics,
                    )
                    Row(Modifier.fillMaxWidth().height(HEADER_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                        PlayerCredits(state, actions, tint, scrolls = true, collapse = { 0f }, coverSwipe = coverSwipe, onMore = onMore)
                    }
                    Spacer(Modifier.height(4.dp))
                    PlayerControlsBlock(
                        state = state, actions = actions, volume = volume,
                        lyricsOpen = panel == Panel.LYRICS, queueOpen = panel == Panel.QUEUE, showVolume = showVolume,
                        onPreviousStep = onPreviousStep, onNextStep = onNextStep,
                        onToggleLyrics = onToggleLyrics, onToggleQueue = onToggleQueue, onOpenDevices = onOpenDevices,
                        onSource = onSource, onSourceLong = onSourceLong, compact = true,
                    )
                }
            }
            if (sidePane && panel != Panel.NONE) Box(Modifier.width(lerp(normalPane * 1.1f, fullWidth, progress)).fillMaxHeight()) {
                Crossfade(targetState = panel, animationSpec = tween(250), modifier = Modifier.fillMaxSize(), label = "playerSidePane") { shown ->
                    when (shown) {
                        Panel.LYRICS -> lyricsContent(PaddingValues(vertical = 4.dp))
                        Panel.QUEUE -> ImmersiveQueuePanel(Modifier.fillMaxSize(), PaddingValues(vertical = 4.dp))
                        else -> Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        }
    }
}

/** A window at least this wide shows the lyrics and the queue beside the cover and the controls. */
private val SIDE_PANE_MIN_WIDTH = 1100.dp

/** The right-hand column needs about this much height to show the volume bar as well. */
private val LANDSCAPE_VOLUME_MIN_HEIGHT = 400.dp
