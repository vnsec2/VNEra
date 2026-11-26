/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.myspoti.engine.R
import com.music.spotui.data.api.LikedGenre
import com.music.spotui.data.api.LikedGenres
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.tone

data class LikedGenreState(val byArtist: Map<String, Set<LikedGenre>> = emptyMap(), val loading: Boolean = true)

@Composable
fun rememberLikedGenres(tracks: List<SongsModel>): LikedGenreState {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by produceState(initialValue = LikedGenreState(), tracks) {
        LikedGenres.load(context, tracks) { value = LikedGenreState(it) }
        value = value.copy(loading = false)
    }
    return state
}

@Composable
fun LikedGenreChips(
    tracks: List<SongsModel>,
    state: LikedGenreState,
    selected: LikedGenre?,
    onSelect: (LikedGenre?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val available = LikedGenre.entries.filter { genre ->
        tracks.any { song -> song.artistIds.any { genre in state.byArtist[it.substringAfterLast(':')].orEmpty() } }
    }
    if (available.isEmpty() && !state.loading && selected == null) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val colors = FilterChipDefaults.filterChipColors(
            containerColor = tone(0xFF292929), labelColor = AppText,
            selectedContainerColor = AppAccent, selectedLabelColor = Color.Black,
        )
        FilterChip(selected = selected == null, onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.liked_genre_all)) }, colors = colors, border = null)
        available.forEach { genre ->
            FilterChip(selected = selected == genre, onClick = { onSelect(if (selected == genre) null else genre) },
                label = { Text(stringResource(genre.label)) }, colors = colors, border = null, enabled = !state.loading)
        }
        if (state.loading) CircularProgressIndicator(color = AppMuted, strokeWidth = 2.dp,
            modifier = Modifier.padding(start = 4.dp).size(18.dp))
    }
}
