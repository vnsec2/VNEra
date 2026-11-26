package com.music.spotui.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myspoti.api.Track
import app.myspoti.engine.R
import app.myspoti.host.android.MyspotiRuntime
import com.music.spotui.di.Graph
import com.music.spotui.di.SongPlayer
import com.music.spotui.di.YtmRuntime
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

enum class RecProvider(val key: String, val labelRes: Int) {
    SPOTIFY("spotify", R.string.rec_provider_spotify),
    YTMUSIC("ytmusic", R.string.rec_provider_ytmusic),
    BOTH("both", R.string.rec_provider_both);

    companion object {
        fun of(key: String?): RecProvider = entries.firstOrNull { it.key == key } ?: SPOTIFY
    }
}

object RecProviderPrefs {
    private const val PREF = "rec_provider_prefs"
    private const val KEY_QUEUE = "queue_provider"
    private const val KEY_PLAYLIST = "playlist_provider"

    fun queue(context: Context): RecProvider =
        RecProvider.of(context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_QUEUE, null))

    fun setQueue(context: Context, provider: RecProvider) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_QUEUE, provider.key).apply()
    }

    fun playlist(context: Context): RecProvider =
        RecProvider.of(context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_PLAYLIST, null))

    fun setPlaylist(context: Context, provider: RecProvider) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_PLAYLIST, provider.key).apply()
    }
}

enum class QueueMood(val labelRes: Int, val query: String) {
    SIMILAR(R.string.queue_mood_similar, "similar mix"),
    FAMILIAR(R.string.queue_mood_familiar, "greatest hits essentials"),
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

    fun dominantArtists(singers: List<String>, limit: Int = 2): List<String> {
        val counts = LinkedHashMap<String, Int>()
        val display = LinkedHashMap<String, String>()
        for (raw in singers) {
            for (part in raw.split(',')) {
                val clean = part.trim()
                if (clean.isBlank()) continue
                val key = clean.lowercase()
                counts[key] = (counts[key] ?: 0) + 1
                display.putIfAbsent(key, clean)
            }
        }
        return counts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .mapNotNull { display[it.key] }
    }

    fun buildQuery(mood: QueueMood, seedArtists: List<String>): String {
        val primary = seedArtists.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
        val secondary = seedArtists.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        return when {
            primary != null && (mood == QueueMood.DISCOVER || mood == QueueMood.SIMILAR) ->
                if (secondary != null) "$primary $secondary mix" else "$primary mix"
            primary != null && mood == QueueMood.FAMILIAR -> "$primary essentials"
            else -> mood.query
        }
    }

    fun buildQuery(mood: QueueMood, seedArtist: String?): String =
        buildQuery(mood, listOfNotNull(seedArtist?.trim()?.substringBefore(',')?.takeIf { it.isNotBlank() }))

    fun buildSaveTrackIds(
        currentId: String?,
        queuedIds: List<String>,
        nextUpIds: List<String>,
        includeCurrent: Boolean,
        includeNextUp: Boolean,
    ): List<String> = buildList {
        if (includeCurrent && !currentId.isNullOrBlank()) add(currentId)
        addAll(queuedIds)
        if (includeNextUp) addAll(nextUpIds)
    }.map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private suspend fun searchSpotifyTracks(query: String, limit: Int): List<Track> =
        runCatching {
            withContext(Dispatchers.IO) { MyspotiRuntime.api.search.tracks(query, limit = limit).items }
        }.getOrDefault(emptyList())

    private suspend fun searchYtmAsSpotifyTracks(query: String, limit: Int): List<Track> =
        runCatching {
            withContext(Dispatchers.IO) {
                val ytmTracks: List<Track> = YtmRuntime.account.metadata.tracks(query, limit = limit).items
                ytmTracks.take(8).mapNotNull { yt ->
                    val artist = yt.artists.firstOrNull()?.name.orEmpty()
                    val q = listOf(yt.name, artist).filter { it.isNotBlank() }.joinToString(" ")
                    if (q.isBlank()) null
                    else runCatching { MyspotiRuntime.api.search.tracks(q, limit = 1).items.firstOrNull() }.getOrNull()
                }
            }
        }.getOrDefault(emptyList())

    suspend fun recommendTracks(
        query: String,
        provider: RecProvider,
        limit: Int = 20,
    ): List<Track> = coroutineScope {
        when (provider) {
            RecProvider.SPOTIFY -> searchSpotifyTracks(query, limit)
            RecProvider.YTMUSIC -> searchYtmAsSpotifyTracks(query, limit).ifEmpty { searchSpotifyTracks(query, limit) }
            RecProvider.BOTH -> {
                val spDef = async { searchSpotifyTracks(query, limit) }
                val ytDef = async { searchYtmAsSpotifyTracks(query, limit) }
                val sp = spDef.await()
                val yt = ytDef.await()
                buildList {
                    val max = maxOf(sp.size, yt.size)
                    for (i in 0 until max) {
                        sp.getOrNull(i)?.let(::add)
                        yt.getOrNull(i)?.let(::add)
                    }
                }.distinctBy { it.uri }
            }
        }
    }

    suspend fun reshapeQueue(
        context: Context,
        mood: QueueMood,
        provider: RecProvider = RecProviderPrefs.queue(context),
    ): Int {
        val q = SongPlayer.engineQueue.value
        val existing = buildSet {
            q.current?.spotifyTrackId?.takeIf { it.isNotBlank() }?.let { add("spotify:track:$it") }
            q.queued.forEach { add(it.uri) }
            q.nextUp.forEach { add(it.uri) }
        }
        val allSingers = buildList {
            Graph.currentSongState.singer.value.takeIf { it.isNotBlank() }?.let(::add)
            q.queued.forEach { add(it.song.singer) }
            q.nextUp.take(15).forEach { add(it.song.singer) }
        }
        val query = buildQuery(mood, dominantArtists(allSingers))
        val tracks = recommendTracks(query, provider, limit = 20)
        val fresh = selectFreshUris(tracks.map { it.uri }, existing, limit = 8)
        if (fresh.isEmpty()) return 0
        runCatching {
            withContext(Dispatchers.IO) { MyspotiRuntime.api.player.addToQueue(fresh) }
        }.onFailure { return 0 }
        return fresh.size
    }

    suspend fun surpriseMe(
        context: Context,
        provider: RecProvider = RecProviderPrefs.queue(context),
    ): Boolean {
        val mood = QueueMood.entries.random()
        val q = SongPlayer.engineQueue.value
        val allSingers = buildList {
            Graph.currentSongState.singer.value.takeIf { it.isNotBlank() }?.let(::add)
            q.queued.forEach { add(it.song.singer) }
            q.nextUp.take(10).forEach { add(it.song.singer) }
        }
        val query = buildQuery(mood, dominantArtists(allSingers))
        val tracks = recommendTracks(query, provider, limit = 20).shuffled()
        val first = tracks.firstOrNull { it.uri.startsWith("spotify:track:") } ?: return false
        val rest = tracks.drop(1).map { it.uri }.filter { it.startsWith("spotify:track:") }.distinct().take(10)
        val played = runCatching {
            withContext(Dispatchers.IO) {
                MyspotiRuntime.api.player.play(first.uri)
                if (rest.isNotEmpty()) MyspotiRuntime.api.player.addToQueue(rest)
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
fun RecProviderChipRow(
    selected: RecProvider,
    immersive: Boolean = true,
    onSelect: (RecProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecProvider.entries.forEach { p ->
            val active = p == selected
            val bg = when {
                active && immersive -> Color.White
                active -> AppAccent
                immersive -> Color.White.copy(alpha = 0.12f)
                else -> tone(0xFF2A2A2A)
            }
            val fg = when {
                active -> Color.Black
                immersive -> Color.White.copy(alpha = 0.9f)
                else -> AppText
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(p) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = stringResource(p.labelRes),
                    color = fg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
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
                selected && immersive -> Color.White
                selected -> AppAccent
                immersive -> Color.White.copy(alpha = 0.14f)
                else -> tone(0xFF2A2A2A)
            }
            val fg = when {
                selected -> Color.Black
                immersive -> Color.White.copy(alpha = if (busy) 0.55f else 0.92f)
                else -> AppText.copy(alpha = if (busy) 0.55f else 1f)
            }
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

@Composable
fun SaveQueueDialog(
    initialName: String,
    currentId: String?,
    queuedIds: List<String>,
    nextUpIds: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, trackIds: List<String>) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var includeCurrent by remember { mutableStateOf(!currentId.isNullOrBlank()) }
    var includeNextUp by remember { mutableStateOf(true) }
    val selectedIds = remember(currentId, queuedIds, nextUpIds, includeCurrent, includeNextUp) {
        QueueDiscovery.buildSaveTrackIds(currentId, queuedIds, nextUpIds, includeCurrent, includeNextUp)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tone(0xFF282828),
        title = { Text(stringResource(R.string.save_queue_as_playlist), color = AppText) },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = tone(0xFF3A3A3A),
                        unfocusedContainerColor = tone(0xFF3A3A3A),
                        focusedTextColor = AppText,
                        unfocusedTextColor = AppText,
                        cursorColor = AppAccent,
                        focusedIndicatorColor = AppAccent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                if (!currentId.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeCurrent = !includeCurrent },
                    ) {
                        Checkbox(
                            checked = includeCurrent,
                            onCheckedChange = { includeCurrent = it },
                            colors = CheckboxDefaults.colors(checkedColor = AppAccent, checkmarkColor = Color.Black, uncheckedColor = AppMuted),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.save_queue_include_current), color = AppText, fontSize = 13.sp)
                    }
                }
                if (nextUpIds.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeNextUp = !includeNextUp },
                    ) {
                        Checkbox(
                            checked = includeNextUp,
                            onCheckedChange = { includeNextUp = it },
                            colors = CheckboxDefaults.colors(checkedColor = AppAccent, checkmarkColor = Color.Black, uncheckedColor = AppMuted),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.save_queue_include_upcoming), color = AppText, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.save_queue_track_count, selectedIds.size),
                    fontSize = 12.sp,
                    color = AppMuted,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && selectedIds.isNotEmpty(),
                onClick = { onSave(name.trim(), selectedIds) },
            ) {
                Text(stringResource(R.string.create_playlist_create_button), color = AppAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.add_to_playlist_button_cancel), color = AppText)
            }
        },
    )
}
