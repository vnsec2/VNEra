/*
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord), GPL-3.0-or-later.
 * Modified for Myspoti / Revera.
 */
package com.music.spotui.ui.immersive.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameMillis
import com.music.spotui.ui.look.SweptLine
import com.music.spotui.ui.look.SweptLyricLine
import com.music.spotui.ui.look.toSwept
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.viewmodel.compose.viewModel
import app.myspoti.engine.R
import com.music.spotui.di.Haptics
import com.music.spotui.di.SongPlayer
import com.music.spotui.extensions.ExtensionAudio
import com.music.spotui.ui.components.RepeatIcons
import com.music.spotui.ui.components.rememberFrameClock
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.look.MeshPalette
import com.music.spotui.ui.look.ThinSlider
import com.music.spotui.ui.look.rememberReduceMotion
import com.music.spotui.ui.viewmodel.LyricsViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal val LocalPlayerTint = compositionLocalOf { Color.White }

private const val PLAYER_TINT_LIGHTNESS = 0.82f
private const val PLAYER_TINT_MAX_SATURATION = 0.72f
private const val TINT_MIN_CHROMA = 0.06f

internal object BitChordPlayerIcons {
    private const val STROKE = 2.2f
    private val ink = SolidColor(Color.Black)

    val Queue: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_queue",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(stroke = ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                for (y in listOf(6f, 12f, 18f)) {
                    moveTo(8f, y)
                    lineTo(21f, y)
                }
            }
            path(fill = ink) {
                for (y in listOf(6f, 12f, 18f)) {
                    moveTo(4.35f, y)
                    arcToRelative(1.25f, 1.25f, 0f, true, true, -2.5f, 0f)
                    arcToRelative(1.25f, 1.25f, 0f, true, true, 2.5f, 0f)
                    close()
                }
            }
        }.build()
    }

    val LyricsQuote: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_lyrics_quote",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = ink,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6f, 3.5f)
                lineTo(18f, 3.5f)
                quadTo(21f, 3.5f, 21f, 6.5f)
                lineTo(21f, 15f)
                quadTo(21f, 18f, 18f, 18f)
                lineTo(10f, 18f)
                lineTo(6f, 21f)
                lineTo(6f, 18f)
                quadTo(3f, 18f, 3f, 15f)
                lineTo(3f, 6.5f)
                quadTo(3f, 3.5f, 6f, 3.5f)
                close()
            }
            path(fill = ink) {
                for (x in listOf(7.6f, 12.8f)) {
                    moveTo(x, 8f)
                    lineTo(x + 3f, 8f)
                    lineTo(x + 3f, 11.1f)
                    quadTo(x + 3f, 13.2f, x + 0.5f, 13.7f)
                    lineTo(x + 0.5f, 12.3f)
                    quadTo(x + 1.7f, 12f, x + 1.7f, 11f)
                    lineTo(x, 11f)
                    close()
                }
            }
        }.build()
    }

    val Shuffle: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_shuffle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = ink,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.4f, 7.4f); lineTo(7f, 7.4f); lineTo(16.6f, 16.6f); lineTo(20.6f, 16.6f)
                moveTo(18.1f, 14.1f); lineTo(20.6f, 16.6f); lineTo(18.1f, 19.1f)
                moveTo(3.4f, 16.6f); lineTo(7f, 16.6f); lineTo(9.8f, 13.9f)
                moveTo(13.9f, 10.1f); lineTo(16.6f, 7.4f); lineTo(20.6f, 7.4f)
                moveTo(18.1f, 4.9f); lineTo(20.6f, 7.4f); lineTo(18.1f, 9.9f)
            }
        }.build()
    }

    val Repeat: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_repeat",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = ink,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.6f, 7.6f)
                lineTo(15.4f, 7.6f)
                arcToRelative(4.4f, 4.4f, 0f, false, true, 0f, 8.8f)
                lineTo(8.6f, 16.4f)
                arcToRelative(4.4f, 4.4f, 0f, false, true, 0f, -8.8f)
                close()
                moveTo(13.5f, 5.7f); lineTo(15.4f, 7.6f); lineTo(13.5f, 9.5f)
                moveTo(10.5f, 14.5f); lineTo(8.6f, 16.4f); lineTo(10.5f, 18.3f)
            }
        }.build()
    }
}

internal fun playerTintOf(palette: MeshPalette): Color {
    val source = palette.colors
        .filter { maxOf(it.red, it.green, it.blue) - minOf(it.red, it.green, it.blue) >= TINT_MIN_CHROMA }
        .maxByOrNull { maxOf(it.red, it.green, it.blue) - minOf(it.red, it.green, it.blue) }
        ?: return Color.White
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(source.toArgb(), hsl)
    hsl[1] = (hsl[1] * 1.25f).coerceIn(0.36f, PLAYER_TINT_MAX_SATURATION)
    hsl[2] = PLAYER_TINT_LIGHTNESS
    return Color(ColorUtils.HSLToColor(hsl))
}

internal fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
internal fun CurrentLyricStrip(
    state: ImmersivePlayerState,
    visible: Boolean,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val vm: LyricsViewModel = viewModel()
    val hold = state.blend?.outgoingWords == true
    val lyricsState = com.music.spotui.ui.screens.rememberLyricsState(vm, state.title, state.artist, hold)
    CurrentLyricLine(
        lyricsState = lyricsState,
        playing = state.playing && !state.buffering,
        position = { if (hold && state.blend != null) state.blend.positionMs() else state.positionMs() },
        onOpenLyrics = onOpenLyrics,
        modifier = modifier,
    )
}

@Composable
internal fun lyricPreviewHeight() = with(androidx.compose.ui.platform.LocalDensity.current) {
    val configured = com.music.spotui.data.preferences.PlayerLyricsPreviewLinesPref.lines
    val lines = if (com.music.spotui.data.preferences.PlayerLyricsWrapPref.on) maxOf(2, configured) else configured
    maxOf(28.dp, 19.sp.toDp() + 8.dp) * lines
}

private data class ActiveInlineLyric(
    val index: Int,
    val text: String,
    val swept: SweptLine?,
    val prevText: String?,
    val nextText: String?,
)

private fun previewLineTextOrNull(raw: String?): String? =
    raw?.trim()?.takeUnless { it.isBlank() || it == "♪" || it == "•••" || it == "..." }

@Composable
internal fun CurrentLyricLine(
    lyricsState: LyricsViewModel.State,
    playing: Boolean,
    position: () -> Long,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    if (!visible) {
        Spacer(modifier.height(lyricPreviewHeight()))
        return
    }
    val frame = rememberFrameClock(playing && lyricsState is LyricsViewModel.State.Loaded, fps = 8)
    val currentPosition by androidx.compose.runtime.rememberUpdatedState(position)
    val loadedLyrics = (lyricsState as? LyricsViewModel.State.Loaded)?.lyrics
    val wordMode = remember(loadedLyrics) { loadedLyrics?.lines?.any { !it.words.isNullOrEmpty() } == true }
    val clock = remember { mutableLongStateOf(currentPosition().coerceAtLeast(0L)) }
    LaunchedEffect(playing, wordMode, loadedLyrics) {
        clock.longValue = currentPosition().coerceAtLeast(0L)
        if (!playing || !wordMode) return@LaunchedEffect
        while (true) {
            withFrameMillis {
                clock.longValue = currentPosition().coerceAtLeast(0L)
            }
        }
    }
    val activeIndex by remember(lyricsState, frame) {
        derivedStateOf {
            frame.value
            val pos = currentPosition().coerceAtLeast(0L)
            if (!playing || !wordMode) clock.longValue = pos
            val lyrics = loadedLyrics ?: return@derivedStateOf -1
            if (!lyrics.synced || lyrics.lines.isEmpty()) return@derivedStateOf -1
            val idx = lyrics.lines.indexOfLast { it.timeMs <= pos + 180L }
            if (idx < 0) return@derivedStateOf -1
            val text = lyrics.lines[idx].text.trim()
            if (text.isBlank() || text == "♪" || text == "•••" || text == "...") -1 else idx
        }
    }
    val activeLine = remember(loadedLyrics, activeIndex, wordMode) {
        val lyrics = loadedLyrics
        if (lyrics == null || activeIndex !in lyrics.lines.indices) null
        else {
            val line = lyrics.lines[activeIndex]
            val nextMs = lyrics.lines.getOrNull(activeIndex + 1)?.timeMs
            val swept = if (wordMode && !line.words.isNullOrEmpty()) line.toSwept(nextMs).takeIf { it.words.isNotEmpty() } else null
            ActiveInlineLyric(
                index = activeIndex,
                text = line.text.trim(),
                swept = swept,
                prevText = previewLineTextOrNull(lyrics.lines.getOrNull(activeIndex - 1)?.text),
                nextText = previewLineTextOrNull(lyrics.lines.getOrNull(activeIndex + 1)?.text),
            )
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(lyricPreviewHeight())
            .then(
                if (activeLine != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        Haptics.click()
                        onOpenLyrics()
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        val wrapLongLine = com.music.spotui.data.preferences.PlayerLyricsWrapPref.on
        val configuredLines = com.music.spotui.data.preferences.PlayerLyricsPreviewLinesPref.lines
        val previewMaxLines = if (wrapLongLine) maxOf(2, configuredLines) else configuredLines
        val lyricStyle = TextStyle(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
        AnimatedContent(
            modifier = Modifier.clipToBounds(),
            targetState = activeLine,
            contentKey = { it?.index },
            transitionSpec = {
                val from = initialState
                val to = targetState
                if (previewMaxLines >= 2 && from != null && to != null) {
                    val direction = to.index - from.index
                    if (kotlin.math.abs(direction) == 1) {
                        (fadeIn(tween(260)) + slideInVertically(tween(260)) { direction * it / previewMaxLines })
                            .togetherWith(fadeOut(tween(260)) + slideOutVertically(tween(260)) { -direction * it / previewMaxLines })
                    } else fadeIn(tween(150)).togetherWith(fadeOut(tween(150)))
                } else (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 })
                    .togetherWith(fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 3 })
            },
            label = "currentLyricStrip",
        ) { item ->
            if (item != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val multiLineWindow = !wrapLongLine && previewMaxLines >= 3
                    val showNext = !wrapLongLine && ((previewMaxLines == 2 && item.nextText != null) || multiLineWindow)
                    Column(Modifier.weight(1f, fill = false)) {
                        if (multiLineWindow) {
                            Text(
                                text = item.prevText.orEmpty(),
                                color = Color.White.copy(alpha = 0.45f),
                                style = lyricStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (item.swept != null) {
                            SweptLyricLine(
                                line = item.swept,
                                clock = clock,
                                style = lyricStyle,
                                dimAlpha = 0.42f,
                                maxLines = if (showNext) 1 else previewMaxLines,
                                overflow = TextOverflow.Ellipsis,
                                feather = true,
                                rise = false,
                                color = Color.White,
                            )
                        } else {
                            Text(
                                text = item.text,
                                color = Color.White.copy(alpha = 0.92f),
                                style = lyricStyle,
                                maxLines = if (showNext) 1 else previewMaxLines,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (showNext) {
                            Text(
                                text = item.nextText.orEmpty(),
                                color = Color.White.copy(alpha = 0.65f),
                                style = lyricStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = LocalPlayerTint.current.copy(alpha = 0.55f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Spacer(Modifier.fillMaxWidth().height(19.dp))
            }
        }
    }
}

@Composable
internal fun PlayerScrubber(
    state: ImmersivePlayerState,
    onSeek: (Long) -> Unit,
    onSource: (() -> Unit)? = null,
    onSourceLong: (() -> Unit)? = null,
) {
    val scrub = rememberPlayerScrub(state.title, state.positionMs, state.durationMs)
    val frame = rememberFrameClock(state.playing && !state.buffering, fps = 15)
    val tint = LocalPlayerTint.current
    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = state.blend?.let { 0.3f + 0.7f * it.wordsAlpha() } ?: 1f }) {
        ThinSlider(
            value = { frame.value; scrub.shown(state.positionMs(), state.durationMs) },
            onValueChange = scrub::drag,
            onValueChangeFinished = { scrub.release { onSeek((it * (state.seekLengthMs?.invoke() ?: state.durationMs)).toLong()) } },
            loading = state.buffering && !scrub.scrubbing,
            activeColor = androidx.compose.ui.graphics.lerp(Color.White, tint, 0.30f).copy(alpha = 0.95f),
            inactiveColor = tint.copy(alpha = 0.30f),
        )
        PlayerScrubberReadout(
            times = { ScrubTimes(frame, scrub, state) },
            source = {
                if (onSource != null) PlaybackQualityBadge(
                    state = state,
                    onSource = onSource,
                    onSourceLong = onSourceLong ?: {},
                )
            },
        )
        com.music.spotui.ui.components.PlaybackSpeedNote(
            LocalPlayerTint.current.copy(alpha = 0.8f),
            Immersive.Accent,
            Modifier.align(Alignment.CenterHorizontally),
        )
        val ab by com.music.spotui.di.SongPlayer.abLoop
        if (ab.aMs != null) {
            val aStr = com.music.spotui.ui.components.formatAbMs(ab.aMs!!)
            val bStr = ab.bMs?.let { com.music.spotui.ui.components.formatAbMs(it) }
            val text = if (bStr != null) stringResource(R.string.ab_loop_active, aStr, bStr) else stringResource(R.string.ab_loop_waiting_b, aStr)
            Text(
                text = text,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(Immersive.Accent.copy(alpha = 0.85f))
                    .clickable {
                        if (ab.bMs == null) com.music.spotui.di.SongPlayer.setAbLoopB()
                        else com.music.spotui.di.SongPlayer.clearAbLoop()
                    }
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/** Keep the source below the times rather than layering a variable-width badge between them. */
@Composable
internal fun PlayerScrubberReadout(times: @Composable () -> Unit, source: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        times()
        source()
    }
}

@Composable
private fun ScrubTimes(
    frame: State<Long>,
    scrub: PlayerScrub,
    state: ImmersivePlayerState,
) {
    frame.value
    val shown = scrub.shown(state.positionMs(), state.durationMs)
    val duration = state.durationMs.coerceAtLeast(0L)
    val elapsed = (shown * duration).toLong()
    PlayerTimeLabels {
        Text(
            text = formatTime(elapsed),
            style = Immersive.Caption,
            color = LocalPlayerTint.current.copy(alpha = 0.72f),
        )
        Text(
            text = "-" + formatTime(duration - elapsed),
            style = Immersive.Caption,
            color = LocalPlayerTint.current.copy(alpha = 0.72f),
        )
    }
}

/** Source metadata never shares this row. Very large times can wrap onto separate rows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerTimeLabels(modifier: Modifier = Modifier, labels: @Composable () -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaybackQualityBadge(
    state: ImmersivePlayerState,
    onSource: () -> Unit,
    onSourceLong: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!com.music.spotui.data.preferences.PlayerSourceLinePref.shown || state.adPlaying) return
    val mixing = state.blend != null
    if (mixing) {
        QualityBadgeRow(
            text = stringResource(R.string.player_badge_mixing),
            iconPainter = null,
            animated = false,
            pulsing = true,
            modifier = modifier,
        )
        return
    }
    val routing = SongPlayer.routing
    val sourceLabel = com.music.spotui.ui.components.playingSourceLabel()
    val answers = ExtensionAudio.answers.intValue
    val localTrack = if (com.music.spotui.di.LocalPlayer.active.value) com.music.spotui.di.LocalPlayer.current() else null
    val stream = remember(localTrack, answers) {
        localTrack?.uri?.takeIf { it.scheme == ExtensionAudio.SCHEME }?.lastPathSegment?.let(ExtensionAudio::streamOf)
    }
    val clickableModifier = modifier.combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = { Haptics.click(); onSource() },
        onLongClick = { Haptics.click(); onSourceLong() },
    )
    if (routing) {
        QualityBadgeRow(
            text = stringResource(R.string.player_badge_upgrading),
            iconPainter = painterResource(R.drawable.ic_bc_lossless),
            animated = false,
            pulsing = true,
            modifier = clickableModifier,
        )
        return
    }
    // A download's metadata should not be mistaken for a live lossless/high-quality stream badge.
    if (localTrack?.uri?.scheme == "file" && sourceLabel.isNotBlank()) {
        QualityBadgeRow(sourceLabel, iconPainter = null, animated = false, modifier = clickableModifier)
        return
    }
    val codec = stream?.codec.orEmpty()
    val mime = stream?.mimeType.orEmpty()
    val isLossless = stream?.manifest != null ||
        codec.contains("flac", true) ||
        codec.contains("alac", true) ||
        codec.contains("wav", true) ||
        mime.contains("flac", true) ||
        mime.contains("alac", true) ||
        sourceLabel.contains("flac", true) ||
        sourceLabel.contains("alac", true) ||
        sourceLabel.contains("lossless", true)
    if (isLossless) {
        val isHiRes = (stream?.bitDepth ?: 0) > 16 ||
            (stream?.sampleRate ?: 0) > 48_000 ||
            sourceLabel.contains("24-bit", true) ||
            sourceLabel.contains("96 kHz", true) ||
            sourceLabel.contains("192 kHz", true) ||
            sourceLabel.contains("88.2 kHz", true)
        QualityBadgeRow(
            text = stringResource(if (isHiRes) R.string.player_badge_hi_res_lossless else R.string.player_badge_lossless),
            iconPainter = painterResource(R.drawable.ic_bc_lossless),
            animated = true,
            modifier = clickableModifier,
        )
        return
    }
    if (sourceLabel.isBlank()) return
    val isHighQuality = sourceLabel.contains("320", true) || sourceLabel.contains("256", true)
    if (isHighQuality) {
        QualityBadgeRow(
            text = stringResource(R.string.player_badge_high_quality),
            iconPainter = rememberVectorPainter(Icons.Rounded.Headphones),
            iconSize = 13.dp,
            animated = false,
            modifier = clickableModifier,
        )
    } else {
        QualityBadgeRow(
            text = sourceLabel,
            iconPainter = null,
            animated = false,
            modifier = clickableModifier,
        )
    }
}

@Composable
internal fun QualityBadgeRow(
    text: String,
    iconPainter: Painter?,
    animated: Boolean,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
    iconSize: Dp = Dp.Unspecified,
) {
    val reduceMotion = rememberReduceMotion()
    val pulse = if (pulsing && !reduceMotion) {
        rememberInfiniteTransition(label = "badgePulse").animateFloat(
            initialValue = 1f,
            targetValue = 0.42f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "badgePulseAlpha",
        )
    } else {
        null
    }
    val badgeStyle = Immersive.Caption.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
    )
    Row(
        modifier = modifier.graphicsLayer {
            alpha = pulse?.value ?: 1f
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconPainter != null) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = LocalPlayerTint.current.copy(alpha = if (animated) 0.88f else 0.68f),
                modifier = if (iconSize.isSpecified) Modifier.size(iconSize) else Modifier,
            )
            Spacer(Modifier.width(5.dp))
        }
        if (animated && !reduceMotion) {
            ShimmerText(text = text, style = badgeStyle, baseAlpha = 0.72f)
        } else {
            Text(
                text = text,
                style = badgeStyle,
                color = LocalPlayerTint.current.copy(alpha = if (animated) 0.88f else 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ShimmerText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    baseAlpha: Float = 0.55f,
) {
    val transition = rememberInfiniteTransition(label = "losslessShimmer")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "losslessShimmerProgress",
    )
    val tint = LocalPlayerTint.current
    val baseColor = tint.copy(alpha = baseAlpha)
    Text(
        text = text,
        style = style,
        color = tint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val width = size.width
                val band = width * 0.6f
                val center = -band + progress.value * (width + 2f * band)
                drawRect(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(0f to baseColor, 0.5f to tint, 1f to baseColor),
                        start = Offset(center - band, 0f),
                        end = Offset(center + band, 0f),
                    ),
                    blendMode = BlendMode.SrcIn,
                )
            },
    )
}

@Composable
internal fun CircleGlyph(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val discAlpha by animateFloatAsState(
        targetValue = if (active) 0.40f else 0.24f,
        label = "glyphDisc",
    )
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(LocalPlayerTint.current.copy(alpha = discAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                Haptics.click()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = icon,
            animationSpec = tween(durationMillis = 180),
            label = "playerMenuGlyph",
        ) { glyph ->
            Icon(
                imageVector = glyph,
                contentDescription = contentDescription,
                tint = LocalPlayerTint.current,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

private val PLAYER_SKIP_ICON_SIZE = 53.dp
private val PLAYER_SKIP_TOUCH_SIZE = 64.dp
private const val PLAYER_SKIP_HEIGHT_SCALE = 0.85f

@Composable
internal fun TransportRow(
    isPlaying: Boolean,
    isLoading: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    compact: Boolean = false,
) {
    val playSize = if (compact) 58.dp else 74.dp
    val playTouch = if (compact) 76.dp else 92.dp
    val skipSize = if (compact) 44.dp else PLAYER_SKIP_ICON_SIZE
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportGlyph(
            iconRes = R.drawable.ic_bc_previous,
            contentDescription = stringResource(R.string.immersive_player_previous),
            size = skipSize,
            touchSize = PLAYER_SKIP_TOUCH_SIZE,
            heightScale = PLAYER_SKIP_HEIGHT_SCALE,
            kickDirection = -1,
            onClick = onPrevious,
        )
        com.music.spotui.ui.components.LoadedFade(isLoading) { loading ->
            if (loading) {
                Box(Modifier.size(playTouch), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(if (compact) 30.dp else 38.dp),
                    )
                }
            } else {
                TransportGlyph(
                    iconRes = if (isPlaying) R.drawable.ic_bc_pause else R.drawable.ic_bc_play,
                    contentDescription = stringResource(if (isPlaying) R.string.immersive_player_pause else R.string.immersive_player_play),
                    size = playSize,
                    touchSize = playTouch,
                    onClick = onPlayPause,
                )
            }
        }
        TransportGlyph(
            iconRes = R.drawable.ic_bc_next,
            contentDescription = stringResource(R.string.immersive_player_next),
            size = skipSize,
            touchSize = PLAYER_SKIP_TOUCH_SIZE,
            heightScale = PLAYER_SKIP_HEIGHT_SCALE,
            kickDirection = 1,
            onClick = onNext,
        )
    }
}

@Composable
private fun TransportGlyph(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    size: Dp,
    touchSize: Dp = size,
    onClick: () -> Unit,
    heightScale: Float = 1f,
    kickDirection: Int = 0,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 700f),
        label = "transportPressScale",
    )
    val scope = rememberCoroutineScope()
    val kickX = remember { Animatable(0f) }
    val kickPop = remember { Animatable(1f) }
    Box(
        modifier = Modifier
            .size(touchSize)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                Haptics.click()
                scope.launch {
                    if (kickDirection != 0) {
                        launch {
                            kickPop.snapTo(1f)
                            kickPop.animateTo(1.10f, tween(85, easing = FastOutSlowInEasing))
                            kickPop.animateTo(1f, spring(dampingRatio = 0.56f, stiffness = 500f))
                        }
                        kickX.snapTo(0f)
                        kickX.animateTo(kickDirection * 9f, tween(90, easing = FastOutSlowInEasing))
                        kickX.animateTo(0f, spring(dampingRatio = 0.52f, stiffness = 520f))
                    } else {
                        kickPop.snapTo(0.90f)
                        kickPop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 520f))
                    }
                }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        com.music.spotui.ui.components.GlyphSwap(iconRes) { res ->
            Icon(
                painter = painterResource(res),
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        val s = pressScale * kickPop.value
                        translationX = kickX.value.dp.toPx()
                        scaleX = s
                        scaleY = s * heightScale
                    },
            )
        }
    }
}

private val VOLUME_ROW_HEIGHT = 32.dp

@Composable
internal fun VolumeRow(volume: PlayerVolume) {
    Row(
        modifier = Modifier.fillMaxWidth().height(VOLUME_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val buttonsEnabled = com.music.spotui.data.preferences.PlayerVolumeButtonsPref.enabled
        val step = com.music.spotui.data.preferences.PlayerVolumeButtonsPref.step
        Icon(
            Icons.AutoMirrored.Rounded.VolumeDown,
            contentDescription = stringResource(R.string.immersive_player_volume_down),
            tint = LocalPlayerTint.current.copy(alpha = if (buttonsEnabled) 0.9f else 0.5f),
            modifier = Modifier
                .size(20.dp)
                .then(if (buttonsEnabled) Modifier.clickable { Haptics.click(); volume.bump(-1, step) } else Modifier),
        )
        Spacer(Modifier.width(10.dp))
        ThinSlider(
            value = volume.level.value,
            onValueChange = volume::drag,
            onValueChangeFinished = volume::release,
            idleHeight = 6.dp,
            activeHeight = 10.dp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = stringResource(R.string.immersive_player_volume_up),
            tint = LocalPlayerTint.current.copy(alpha = if (buttonsEnabled) 0.9f else 0.5f),
            modifier = Modifier
                .size(20.dp)
                .then(if (buttonsEnabled) Modifier.clickable { Haptics.click(); volume.bump(1, step) } else Modifier),
        )
    }
}

private val BOTTOM_ACTION_SIZE = 44.dp
private val PILL_SEGMENT_WIDTH = 58.dp
private val PILL_ICON_SIZE = 23.dp
private const val PILL_SLIDE_MS = 320

private fun pillWidth(segments: Int): Dp = PILL_SEGMENT_WIDTH * segments + 1.dp * (segments - 1)

@Composable
private fun Pill(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .height(BOTTOM_ACTION_SIZE)
            .clip(CircleShape)
            .background(LocalPlayerTint.current.copy(alpha = 0.12f))
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun PillDivider() {
    Box(
        Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(LocalPlayerTint.current.copy(alpha = 0.20f)),
    )
}

@Composable
private fun PillSegment(
    contentDescription: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    label: String? = null,
    highlighted: Boolean = false,
    width: Dp = PILL_SEGMENT_WIDTH,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(BOTTOM_ACTION_SIZE)
            .background(if (highlighted) LocalPlayerTint.current.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                Haptics.click()
                onClick()
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        val tint = LocalPlayerTint.current.copy(alpha = if (highlighted) 1f else 0.72f)
        if (icon != null) {
            Crossfade(
                targetState = icon,
                animationSpec = tween(durationMillis = 200),
                label = "pillSegmentIcon",
            ) { current ->
                Icon(imageVector = current, contentDescription = null, tint = tint, modifier = Modifier.size(PILL_ICON_SIZE))
            }
        } else if (label != null) {
            Text(
                text = label,
                color = tint,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BottomGlyph(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(BOTTOM_ACTION_SIZE)
            .clip(CircleShape)
            .background(if (highlighted) LocalPlayerTint.current.copy(alpha = 0.20f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                Haptics.click()
                onClick()
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LocalPlayerTint.current.copy(alpha = if (highlighted) 1f else 0.84f),
            modifier = Modifier.size(25.dp),
        )
    }
}

@Composable
internal fun PlayerActionRow(
    lyricsOpen: Boolean,
    queueOpen: Boolean,
    shuffleEnabled: Boolean,
    smartEnabled: Boolean,
    repeatMode: Int,
    deviceActive: Boolean,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenDevices: () -> Unit,
) {
    val alwaysModes = com.music.spotui.data.preferences.PlayerModesPref.on
    val capsule = com.music.spotui.data.preferences.PlayerCapsulePref.shown
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widestRow = BOTTOM_ACTION_SIZE * 2 + pillWidth(3)
        val edgeInset = ((maxWidth - widestRow) / 4).coerceAtLeast(0.dp)
        val toStart = animateFloatAsState(
            targetValue = if (queueOpen) 1f else 0f,
            animationSpec = tween(PILL_SLIDE_MS, easing = FastOutSlowInEasing),
            label = "playerBottomPillSlide",
        )
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = edgeInset),
            contentAlignment = Alignment.CenterStart,
        ) {
            AnimatedVisibility(
                visible = !queueOpen,
                enter = fadeIn(tween(180, delayMillis = 120)),
                exit = fadeOut(tween(120)),
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                BottomGlyph(
                    icon = BitChordPlayerIcons.LyricsQuote,
                    contentDescription = stringResource(if (lyricsOpen) R.string.immersive_player_lyrics_close else R.string.immersive_player_lyrics),
                    onClick = onToggleLyrics,
                    highlighted = lyricsOpen,
                )
            }
            if (capsule) {
                val showPill = queueOpen || alwaysModes
                AnimatedContent(
                    modifier = Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minWidth = 0))
                        layout(constraints.maxWidth, placeable.height) {
                            val centred = (constraints.maxWidth - placeable.width) / 2f
                            placeable.place((centred * (1f - toStart.value)).roundToInt(), 0)
                        }
                    },
                    targetState = showPill,
                    transitionSpec = {
                        (fadeIn(tween(180, delayMillis = 80)) togetherWith fadeOut(tween(120)))
                            .using(SizeTransform(clip = false) { _, _ -> tween(220) })
                    },
                    label = "playerBottomCenter",
                ) { pillMode ->
                    if (pillMode) {
                        Pill {
                            PillSegment(
                                icon = if (smartEnabled) Icons.Rounded.AutoAwesome else BitChordPlayerIcons.Shuffle,
                                contentDescription = stringResource(
                                    when {
                                        smartEnabled -> R.string.smart_shuffle_title
                                        shuffleEnabled -> R.string.immersive_player_shuffle_on
                                        else -> R.string.immersive_player_shuffle_off
                                    },
                                ),
                                onClick = onToggleShuffle,
                                highlighted = shuffleEnabled,
                            )
                            PillDivider()
                            PillSegment(
                                icon = when (repeatMode) {
                                    ImmersiveRepeat.ONE -> null
                                    ImmersiveRepeat.ONCE -> RepeatIcons.once
                                    else -> BitChordPlayerIcons.Repeat
                                },
                                label = if (repeatMode == ImmersiveRepeat.ONE) "1" else null,
                                contentDescription = when (repeatMode) {
                                    ImmersiveRepeat.ONE -> stringResource(R.string.immersive_player_repeat_one)
                                    ImmersiveRepeat.ONCE -> stringResource(R.string.immersive_player_repeat_once)
                                    ImmersiveRepeat.ALL -> stringResource(R.string.immersive_player_repeat_all)
                                    else -> stringResource(R.string.immersive_player_repeat_off)
                                },
                                onClick = onCycleRepeat,
                                highlighted = repeatMode != ImmersiveRepeat.OFF,
                            )
                            PillDivider()
                            PillSegment(
                                icon = if (deviceActive) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                                contentDescription = stringResource(R.string.immersive_player_devices),
                                onClick = onOpenDevices,
                                highlighted = deviceActive,
                            )
                        }
                    } else {
                        BottomGlyph(
                            icon = if (deviceActive) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                            contentDescription = stringResource(R.string.immersive_player_devices),
                            onClick = onOpenDevices,
                            highlighted = deviceActive,
                        )
                    }
                }
            }
            Box(Modifier.align(Alignment.CenterEnd)) {
                BottomGlyph(
                    icon = BitChordPlayerIcons.Queue,
                    contentDescription = stringResource(if (queueOpen) R.string.immersive_player_queue_close else R.string.immersive_player_queue),
                    onClick = onToggleQueue,
                    highlighted = queueOpen,
                )
            }
        }
    }
}
