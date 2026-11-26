/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.ui.immersive.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.myspoti.engine.R
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.music.spotui.ui.components.SongBlendCover
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.viewmodel.LyricsViewModel

@Composable
internal fun LyricsFullscreenActions(onCollapse: () -> Unit) {
    val vm: LyricsViewModel = viewModel()
    CircleGlyph(Icons.Rounded.Share, stringResource(R.string.lyrics_share), onClick = { vm.startSelecting() })
    Spacer(Modifier.width(8.dp))
    CircleGlyph(Icons.Rounded.CloseFullscreen, stringResource(R.string.immersive_lyrics_collapse), onClick = onCollapse)
}

/** Landscape gains a compact header as the controls pane folds away; portrait keeps its header. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun FullscreenLyricsHeader(state: ImmersivePlayerState, onCollapse: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gutter = maxOf(PLAYER_GUTTER, (maxWidth - 760.dp) / 2)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(8.dp))) {
                GlideImage(state.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                SongBlendCover(state.blend, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).graphicsLayer { alpha = state.blend?.wordsAlpha() ?: 1f }) {
                Text(
                    state.title, style = Immersive.Title.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.artist, style = Immersive.Body, color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Row { LyricsFullscreenActions(onCollapse) }
        }
    }
}
