package com.music.spotui.ui.immersive.sheets

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.immersive.components.rememberImmersiveOverscrollFactory
import com.music.spotui.ui.immersive.motion.ImmersiveMotion
import com.music.spotui.ui.immersive.shell.immersiveGlassSurface
import com.music.spotui.ui.look.rememberReduceMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * A bottom sheet on glass: [ImmersiveSheetShape] (rounded top) drawn with the shared
 * `immersiveGlassSurface`, so it takes whatever the glass looks like, with the grab handle on it.
 * The sheet opens and settles on Material's own spring-anchored drag. The content brings its own
 * rows and insets (the Classic sheets' contents work unchanged on it). Fully expanded sheets
 * follow predictive back downwards instead of shrinking in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImmersiveGlassSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val backProgress = remember { Animatable(0f) }
    var sheetHeight by remember { mutableFloatStateOf(0f) }
    var backStartOffset by remember { mutableFloatStateOf(Float.NaN) }
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    // Keep Material's expand -> partial-expand back behavior for sheets with that anchor.
    val slideOnBack = !sheetState.hasPartiallyExpandedState
    val scrim = BottomSheetDefaults.ScrimColor
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier
            .onSizeChanged { sheetHeight = it.height.toFloat() }
            .graphicsLayer {
                if (!backStartOffset.isNaN()) {
                    // As hide() moves the sheet, consume the preview's remaining distance.
                    // This keeps the release continuous instead of jumping or moving twice.
                    val hiddenDistance = sheetHeight - (sheetState.requireOffset() - backStartOffset)
                    translationY = hiddenDistance.coerceAtLeast(0f) * backProgress.value
                }
            },
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !slideOnBack),
        scrimColor = scrim.copy(alpha = scrim.alpha * (1f - backProgress.value)),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        // Register inside the modal window so the gesture belongs to the sheet, not the page.
        PredictiveBackHandler(enabled = slideOnBack && sheetState.isVisible) { events ->
            try {
                backProgress.snapTo(0f)
                backStartOffset = sheetState.requireOffset()
                events.collect { event -> backProgress.snapTo(event.progress) }
                // Hiding disables this handler before the animation finishes; let the
                // sheet's scope finish it independently of the gesture coroutine.
                scope.launch {
                    sheetState.hide()
                    if (!sheetState.isVisible) {
                        onDismiss()
                    } else {
                        // A caller may veto hiding through SheetState.confirmValueChange.
                        backProgress.animateTo(0f, ImmersiveMotion.slideSpring(reduceMotion))
                        backStartOffset = Float.NaN
                    }
                }
            } catch (cancelled: CancellationException) {
                scope.launch {
                    backProgress.animateTo(0f, ImmersiveMotion.slideSpring(reduceMotion))
                    backStartOffset = Float.NaN
                }
                throw cancelled
            }
        }
        CompositionLocalProvider(LocalOverscrollFactory provides rememberImmersiveOverscrollFactory()) {
            Column(Modifier.fillMaxWidth().immersiveGlassSurface(ImmersiveSheetShape)) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(width = 34.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Immersive.Text.copy(alpha = 0.35f)),
                    )
                }
                content()
            }
        }
    }
}
