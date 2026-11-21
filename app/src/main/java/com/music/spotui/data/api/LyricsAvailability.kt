package com.music.spotui.data.api

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.music.spotui.data.DataSaver
import com.music.spotui.data.entity.SongsModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Whether a song has lyrics, for the badge in the track rows — checked once
 * per song, only on an unmetered connection. A check costs a lyrics lookup
 * per row, so on mobile data it would recreate the entry traffic the
 * download alone was meant to avoid; there the badge simply stays hidden.
 * A failed check stays unknown and is tried again the next time the row is
 * shown (an error is never remembered as "no lyrics").
 */
object LyricsAvailability {
    enum class State { UNKNOWN, CHECKING, SYNCED, PLAIN, NONE }

    val states: SnapshotStateMap<String, State> = mutableStateMapOf()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Three lookups at a time: a long list must not fan out to every provider at once. */
    private val slots = Semaphore(3)
    /** When a check last failed per key: a failed row is not retried for a minute. */
    private val lastFailedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** The key a song is checked under: the Spotify id when known, else title + artist. */
    fun key(song: SongsModel): String =
        song.spotifyTrackId.ifBlank { "${song.title}\n${song.singer}" }

    /** Starts the check for [song] unless it already ran or the network costs data. */
    fun check(song: SongsModel) {
        val k = key(song)
        if (states[k] != null) return
        if (System.currentTimeMillis() - (lastFailedAt[k] ?: 0) < 60_000) return
        if (DataSaver.metered) return
        states[k] = State.CHECKING
        scope.launch {
            slots.withPermit {
                val track = LyricsApi.Track(
                    song.spotifyTrackId, song.title, song.singer, song.album,
                    song.durationMs.toLong(), song.coverUri,
                )
                val found = try {
                    LyricsApi.fetch(track)
                } catch (_: Exception) {
                    // No answer (offline, blocked, provider down): unknown again,
                    // so the next time the row is shown it is tried again — but
                    // not for a minute, or a failing row would retry forever.
                    lastFailedAt[k] = System.currentTimeMillis()
                    states.remove(k)
                    return@withPermit
                }
                states[k] = when {
                    found == null -> State.NONE
                    found.synced -> State.SYNCED
                    else -> State.PLAIN
                }
            }
        }
    }
}
