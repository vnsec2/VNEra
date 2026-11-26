package com.music.spotui.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myspoti.engine.R
import com.music.spotui.di.Graph
import com.music.spotui.di.SongPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class QueueMood(val labelRes: Int, val query: String) {
    CHILL(R.string.queue_mood_chill, "chill ambient lofi"),
    WORKOUT(R.string.queue_mood_workout, "workout gym energy"),
    FOCUS(R.string.queue_mood_focus, "deep focus instrumental"),
    PARTY(R.string.queue_mood_party, "dance party hits"),
    LATE_NIGHT(R.string.queue_mood_late_night, "late night r&b vibes"),
    DISCOVER(R.string.queue_mood_discover, "indie electronic discovery"),
}

internal object QueueDiscovery {
    fun selectFreshUris(
        candidates: List<String>,
        existingUris: Set<String>,
        limit: Int = 8,
    ): List<String> =
        candidates
            .map { it.trim() }
            .filter { it.startsWith("spotify:track:") && it !in existingUris }
            .distinct()
            .take(limit)

    fun buildQuery(mood: QueueMood, seedArtist: String?): String {
        val artist = seedArtist?.trim()?.substringBefore(',')?.takeIf { it.isNotBlank() }
        return if (artist != null && mood == QueueMood.DISCOVER) "$artist mix" else mood.query
    }

    suspend fun reshapeQueue(context: Context, mood: QueueMood): Int {
        val q = SongPlayer.engineQueue.value
        val existing = buildSet {
            q.current?.spotifyTrackId?.takeIf { it.isNotBlank() }?.let { add("spotify:track:$it") }
            q.queued.forEach { add(it.uri) }
            q.nextUp.forEach { add(it.uri) }
        }
        val query = buildQuery(mood, Graph.currentSongState.singer.value)
        val tracks = runCatching {
            withContext(Dispatchers.IO) { app.myspoti.host.android.MyspotiRuntime.api.search.tracks(query, limit = 20).items }
        }.getOrDefault(emptyList())
        val fresh = selectFreshUris(tracks.map { it.uri }, existing, limit = 8)
        if (fresh.isEmpty()) return 0
        runCatching {
            withContext(Dispatchers.IO) { app.myspoti.host.android.MyspotiRuntime.api.player.addToQueue(fresh) }
        }.onFailure { return 0 }
        return fresh.size
    }

    suspend fun surpriseMe(context: Context): Boolean {
        val mood = QueueMood.entries.random()
        val query = buildQuery(mood, Graph.currentSongState.singer.value)
        val tracks = runCatching {
            withContext(Dispatchers.IO) { app.myspoti.host.android.MyspotiRuntime.api.search.tracks(query, limit = 20).items }
        }.getOrDefault(emptyList()).shuffled()
        val first = tracks.firstOrNull { it.uri.startsWith("spotify:track:") } ?: return false
        val rest = tracks.drop(1).map { it.uri }.filter { it.startsWith("spotify:track:") }.distinct().take(10)
        val played = runCatching {
            withContext(Dispatchers.IO) {
                app.myspoti.host.android.MyspotiRuntime.api.player.play(first.uri)
                if (rest.isNotEmpty()) app.myspoti.host.android.MyspotiRuntime.api.player.addToQueue(rest)
            }
        }.isSuccess
        if (played) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.queue_surprise_started, context.getString(mood.labelRes)),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        return played
    }
}

@Composable
fun QueueMoodChipStrip(
    activeMood: QueueMood?,
    busy: Boolean,
    immersive: Boolean,
    onSelect: (QueueMood) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QueueMood.entries.forEach { mood ->
            val selected = activeMood == mood
            val bg = when {
                selected -> Color.White
                immersive -> Color.White.copy(alpha = 0.14f)
                else -> Color.White.copy(alpha = 0.10f)
            }
            val fg = if (selected) Color.Black else Color.White.copy(alpha = if (busy) 0.55f else 0.92f)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .clickable(
                        enabled = !busy,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(mood) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(mood.labelRes),
                    color = fg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
