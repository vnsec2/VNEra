package com.music.spotui.ui.components

import com.music.spotui.data.api.LyricsAvailability
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.ui.theme.AppText

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.myspoti.engine.R

/**
 * A track's lyrics, as a small note next to the title — shown only once the
 * availability is known and the song has lyrics. Unknown (not checked yet, or
 * on mobile data) and "none" both draw nothing: no badge is not "no lyrics".
 * The check is started the first time the row is shown; see [LyricsAvailability].
 */
@Composable
fun LyricsBadge(song: SongsModel, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    val key = LyricsAvailability.key(song)
    val state = LyricsAvailability.states[key]
    if (state == null) {
        LaunchedEffect(key) { LyricsAvailability.check(song) }
        return
    }
    if (state == LyricsAvailability.State.SYNCED || state == LyricsAvailability.State.PLAIN) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MusicNote, stringResource(R.string.lyrics_badge_content_description),
                tint = AppText.copy(alpha = 0.7f), modifier = Modifier.size(size))
            Spacer(Modifier.width(6.dp))
        }
    }
}
