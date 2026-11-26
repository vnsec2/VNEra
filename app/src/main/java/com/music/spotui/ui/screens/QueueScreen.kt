package com.music.spotui.ui.screens

import com.music.spotui.ui.components.animatedRow
import com.music.spotui.ui.theme.AppText

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.rounded.AutoAwesome
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import app.myspoti.engine.R
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.theme.AppPalette

/**
 * The "play queue" — the track that's playing now plus everything coming up
 * (Spotify's Queue view). Tapping an upcoming track jumps straight to it;
 * dragging the handle reorders it; swiping a row removes it.
 */
@OptIn(ExperimentalGlideComposeApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(navController: NavController) {
    // Spotify's real queue, pushed by the engine.
    val q by SongPlayer.engineQueue
    var query by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    val term = query.trim()
    fun matches(row: SongPlayer.QueueItem) = row.song.title.contains(term, ignoreCase = true) || row.song.singer.contains(term, ignoreCase = true)
    val queued = remember(q.queued, term) { q.queued.filter(::matches) }
    val upcoming = remember(q.nextUp, term) { q.nextUp.filter(::matches) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val saveableTrackIds = remember(q.current, q.queued, q.nextUp) {
        (listOfNotNull(q.current?.spotifyTrackId) + q.queued.map { it.song.spotifyTrackId } + q.nextUp.map { it.song.spotifyTrackId })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
    var saveDialogOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
    var activeMood by remember { androidx.compose.runtime.mutableStateOf<com.music.spotui.ui.components.QueueMood?>(null) }
    var recProvider by remember { androidx.compose.runtime.mutableStateOf(com.music.spotui.ui.components.RecProviderPrefs.queue(context)) }
    var moodBusy by remember { androidx.compose.runtime.mutableStateOf(false) }
    val discoveryScope = androidx.compose.runtime.rememberCoroutineScope()

    if (saveDialogOpen) {
        val defaultName = SongPlayer.nowPlaying.value.contextName.ifBlank { stringResource(R.string.action_queue_label) }
        com.music.spotui.ui.components.SaveQueueDialog(
            initialName = defaultName,
            currentId = q.current?.spotifyTrackId,
            queuedIds = q.queued.map { it.song.spotifyTrackId },
            nextUpIds = q.nextUp.map { it.song.spotifyTrackId },
            onDismiss = { saveDialogOpen = false },
        ) { name, ids ->
            saveDialogOpen = false
            com.music.spotui.data.api.SpotifySync.createPlaylistWithTracks(context, name, ids) { ok ->
                val msg = context.getString(if (ok) R.string.save_queue_saved else R.string.save_queue_failed, name)
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.music.spotui.ui.theme.AppBackground)
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.bottom_actions_close),
                tint = AppText,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { navController.navigateUp() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                stringResource(R.string.action_queue_label),
                color = AppText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = stringResource(R.string.queue_surprise_me),
                tint = AppText,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(
                        enabled = !moodBusy,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        moodBusy = true
                        discoveryScope.launch {
                            try {
                                com.music.spotui.ui.components.QueueDiscovery.surpriseMe(context, recProvider)
                            } finally {
                                moodBusy = false
                            }
                        }
                    }
            )
            if (saveableTrackIds.isNotEmpty()) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = stringResource(R.string.save_queue_as_playlist),
                    tint = AppText,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { saveDialogOpen = true }
                )
            }
        }
        com.music.spotui.ui.components.RecProviderChipRow(
            selected = recProvider,
            immersive = false,
            onSelect = {
                recProvider = it
                com.music.spotui.ui.components.RecProviderPrefs.setQueue(context, it)
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        com.music.spotui.ui.components.QueueMoodChipStrip(
            activeMood = activeMood,
            busy = moodBusy,
            immersive = false,
            onSelect = { mood ->
                activeMood = mood
                moodBusy = true
                discoveryScope.launch {
                    try {
                        val added = com.music.spotui.ui.components.QueueDiscovery.reshapeQueue(context, mood, recProvider)
                        val msg = if (added > 0) context.getString(R.string.queue_mood_added, added, context.getString(mood.labelRes))
                        else context.getString(R.string.queue_mood_empty)
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    } finally {
                        moodBusy = false
                    }
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        androidx.compose.material3.OutlinedTextField(value = query, onValueChange = { query = it },
            singleLine = true, label = { Text(stringResource(R.string.add_songs_search_hint)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            q.current?.let {
                item(key = "now") {
                    QueueHeader(stringResource(R.string.default_notification_channel_name))
                    QueueRow(song = it, highlight = true, onClick = {})
                }
            }
            // Keyed by song and occurrence (as the Immersive queue): a row removed or played lets
            // the ones under it slide up instead of every row after it being drawn anew.
            val keys = queueKeys(queued + upcoming)
            if (queued.isNotEmpty()) {
                item(key = "heading-queued") { QueueHeader(stringResource(R.string.next_in_queue), animatedRow()) }
                itemsIndexed(queued, key = { i, _ -> keys[i] }) { _, item ->
                    Box(animatedRow()) {
                        SwipeRemove(item) { QueueRow(song = item.song, highlight = false, onClick = { SongPlayer.skipTo(item) }) }
                    }
                }
            }
            if (upcoming.isNotEmpty()) {
                item(key = "heading-next") { QueueHeader(stringResource(R.string.next_up), animatedRow()) }
                itemsIndexed(upcoming, key = { i, _ -> keys[queued.size + i] }) { _, item ->
                    Box(animatedRow()) {
                        SwipeRemove(item) { QueueRow(song = item.song, highlight = false, onClick = { SongPlayer.skipTo(item) }) }
                    }
                }
            }
        }
    }
}

/** One key per row: the song's uri, with its occurrence after the first ("uri~2"), never twice the same. */
private fun queueKeys(rows: List<SongPlayer.QueueItem>): List<String> {
    val seen = HashMap<String, Int>()
    return rows.map { item ->
        val count = seen.merge(item.uri, 1, Int::plus)!!
        if (count == 1) item.uri else "${item.uri}~$count"
    }
}

@Composable
private fun QueueHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = AppText,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(16.dp, 18.dp, 16.dp, 6.dp)
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRemove(item: SongPlayer.QueueItem, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                SongPlayer.removeFromQueue(item)
                true
            } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF7A1F1F))
                    .padding(horizontal = 24.dp),
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.aligned_curation_first_save_default_row_remove_action_description), tint = AppText)
            }
        },
        content = { content() }
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun QueueRow(
    song: com.music.spotui.data.entity.SongsModel,
    highlight: Boolean,
    onClick: () -> Unit,
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragDelta: (Float) -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = dragOffsetY }
            .background(if (isDragging) com.music.spotui.ui.theme.headerTint(Color(0xFF1E1E24), 0f) else com.music.spotui.ui.theme.AppBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(16.dp, 8.dp)
    ) {
        GlideImage(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp)),
            model = song.coverUri,
            contentScale = ContentScale.Crop,
            loading = placeholder(R.drawable.placeholder),
            failure = placeholder(R.drawable.placeholder),
            contentDescription = ""
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (highlight) Color(AppPalette.toArgb()) else AppText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.singer,
                color = Color.Gray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
