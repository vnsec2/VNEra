package com.music.spotui.di

import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.local.LocalFileMatch.isDeviceFile
import com.music.spotui.data.local.LocalFileMatch.isUnmatchedLocal
import com.music.spotui.data.local.LocalMusic
import com.music.spotui.data.local.LocalTrack
import com.music.spotui.data.local.LocalFileMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class MixedQueuePlan(
    val contextUri: String,
    val contextName: String,
    val entries: List<SongsModel>,
    initialIndex: Int,
) {
    var index: Int = initialIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        private set

    fun current(): SongsModel? = entries.getOrNull(index)

    fun upcoming(): List<SongsModel> =
        if (index + 1 < entries.size) entries.subList(index + 1, entries.size) else emptyList()

    fun spotifyRunFrom(startIndex: Int = index): List<SongsModel> {
        if (startIndex !in entries.indices) return emptyList()
        val out = ArrayList<SongsModel>()
        var i = startIndex
        while (i < entries.size && !entries[i].isDeviceFile && entries[i].spotifyTrackId.isNotBlank()) {
            out += entries[i]
            i++
        }
        return out
    }

    fun stepForward(repeatMode: Int): SongsModel? {
        if (entries.isEmpty()) return null
        val next = when {
            index + 1 < entries.size -> index + 1
            repeatMode == 1 -> 0
            else -> return null
        }
        index = next
        return entries[index]
    }

    fun stepBackward(repeatMode: Int): SongsModel? {
        if (entries.isEmpty()) return null
        val prev = when {
            index > 0 -> index - 1
            repeatMode == 1 -> entries.lastIndex
            else -> return null
        }
        index = prev
        return entries[index]
    }

    fun jumpTo(targetIndex: Int): SongsModel? {
        if (targetIndex !in entries.indices) return null
        index = targetIndex
        return entries[index]
    }

    fun syncToSpotifyTrackId(trackId: String): Boolean {
        if (trackId.isBlank()) return false
        val cur = current()
        if (cur != null && !cur.isDeviceFile && cur.spotifyTrackId == trackId) return true
        val runEnd = runEndIndex()
        for (i in index..runEnd) {
            if (!entries[i].isDeviceFile && entries[i].spotifyTrackId == trackId) {
                index = i
                return true
            }
        }
        return false
    }

    fun isLastInSpotifyRun(): Boolean {
        val cur = current() ?: return false
        if (cur.isDeviceFile) return false
        return index == runEndIndex()
    }

    private fun runEndIndex(): Int {
        var i = index
        while (i + 1 < entries.size && !entries[i + 1].isDeviceFile && entries[i + 1].spotifyTrackId.isNotBlank()) {
            i++
        }
        return i
    }

    companion object {
        fun isMixed(tracks: List<SongsModel>): Boolean {
            var hasLocal = false
            var hasSpotify = false
            for (t in tracks) {
                if (t.isUnmatchedLocal) continue
                if (t.isDeviceFile) hasLocal = true
                else if (t.spotifyTrackId.isNotBlank() && t.playable) hasSpotify = true
                if (hasLocal && hasSpotify) return true
            }
            return false
        }

        fun playableEntries(tracks: List<SongsModel>): List<SongsModel> =
            tracks.filter { !it.isUnmatchedLocal && (it.isDeviceFile || (it.spotifyTrackId.isNotBlank() && it.playable)) }
    }
}

object MixedPlaylistQueue {
    @Volatile private var plan: MixedQueuePlan? = null
    @Volatile private var deviceFiles: Map<String, LocalTrack> = emptyMap()
    @Volatile internal var dispatching: Boolean = false
    @Volatile private var spotifyRunUris: Set<String> = emptySet()
    @Volatile private var sawPlayingCurrentSpotify: Boolean = false
    @Volatile private var lastAdvancedKey: String = ""

    val active: Boolean get() = plan != null

    fun isMixed(tracks: List<SongsModel>): Boolean = MixedQueuePlan.isMixed(tracks)

    fun start(
        tracks: List<SongsModel>,
        picked: SongsModel,
        shuffle: Boolean,
        contextName: String,
        contextUri: String,
    ) {
        val playable = MixedQueuePlan.playableEntries(tracks)
        if (playable.isEmpty()) return
        val ordered = if (shuffle) {
            val rest = playable.filterNot { it.id == picked.id && it.rowUid == picked.rowUid && it.url == picked.url }.shuffled()
            listOf(picked) + rest
        } else {
            playable
        }
        val startIndex = ordered.indexOfFirst { it.id == picked.id && it.rowUid == picked.rowUid && it.url == picked.url }.coerceAtLeast(0)
        app.myspoti.host.android.MyspotiRuntime.scope.launch {
            val files = withContext(Dispatchers.IO) {
                runCatching { LocalMusic.tracks(Graph.app) }.getOrDefault(emptyList()).associateBy { LocalFileMatch.key(it) }
            }
            withContext(Dispatchers.Main) {
                deviceFiles = files
                val nextPlan = MixedQueuePlan(contextUri, contextName, ordered, startIndex)
                plan = nextPlan
                lastAdvancedKey = ""
                playCurrent(nextPlan)
            }
        }
    }

    fun clear() {
        if (dispatching) return
        plan = null
        spotifyRunUris = emptySet()
        sawPlayingCurrentSpotify = false
        lastAdvancedKey = ""
    }

    fun toEngineQueue(): SongPlayer.EngineQueue? {
        val p = plan ?: return null
        val cur = p.current() ?: return null
        val nextItems = p.upcoming().mapIndexed { offset, s ->
            val uri = if (s.isDeviceFile) s.url else "spotify:track:${s.spotifyTrackId}"
            SongPlayer.QueueItem(s, uri, "mixed:${p.index + 1 + offset}", "")
        }
        return SongPlayer.EngineQueue(current = cur, queued = emptyList(), nextUp = nextItems)
    }

    fun step(forward: Boolean): Boolean {
        val p = plan ?: return false
        val repeatMode = SongPlayer.repeatMode.value
        val next = if (forward) p.stepForward(repeatMode) else p.stepBackward(repeatMode)
        if (next == null) {
            clear()
            return false
        }
        lastAdvancedKey = ""
        playCurrent(p)
        return true
    }

    fun jumpToQueueItem(uid: String): Boolean {
        val p = plan ?: return false
        if (!uid.startsWith("mixed:")) return false
        val target = uid.removePrefix("mixed:").toIntOrNull() ?: return false
        if (p.jumpTo(target) == null) return false
        lastAdvancedKey = ""
        playCurrent(p)
        return true
    }

    fun onLocalEnded(): Boolean {
        val p = plan ?: return false
        val cur = p.current() ?: return false
        if (!cur.isDeviceFile) return false
        val next = p.stepForward(SongPlayer.repeatMode.value)
        if (next == null) {
            clear()
            return false
        }
        lastAdvancedKey = ""
        playCurrent(p)
        return true
    }

    fun onSpotifyState(
        uri: String,
        trackId: String,
        provider: String,
        isPaused: Boolean,
        wasPlaying: Boolean,
        positionMs: Long,
        predictedMs: Long,
        durationMs: Long,
    ) {
        val p = plan ?: return
        if (dispatching || LocalPlayer.active.value) return
        val cur = p.current() ?: return
        if (cur.isDeviceFile) return

        val inRun = trackId.isNotBlank() && ("spotify:track:$trackId" in spotifyRunUris)
        if (inRun) {
            if (p.syncToSpotifyTrackId(trackId)) {
                SongPlayer.publishMixedQueue()
            }
            if (!isPaused) {
                sawPlayingCurrentSpotify = true
            }
            val endedAtSliceTail = p.isLastInSpotifyRun() &&
                sawPlayingCurrentSpotify &&
                wasPlaying &&
                isPaused &&
                (positionMs == 0L || (durationMs > 0L && predictedMs >= durationMs - 2_500L))
            if (endedAtSliceTail) {
                advanceFromSpotify(p, "end:${p.index}:$trackId")
            }
            return
        }

        val jumpedToAutoplay = sawPlayingCurrentSpotify &&
            p.isLastInSpotifyRun() &&
            (provider == "autoplay" || (uri.startsWith("spotify:track:") && uri !in spotifyRunUris))
        if (jumpedToAutoplay) {
            advanceFromSpotify(p, "autoplay:${p.index}:$uri")
        }
    }

    private fun advanceFromSpotify(p: MixedQueuePlan, key: String) {
        if (lastAdvancedKey == key) return
        lastAdvancedKey = key
        val next = p.stepForward(SongPlayer.repeatMode.value)
        if (next == null) {
            clear()
            return
        }
        playCurrent(p)
    }

    private fun playCurrent(p: MixedQueuePlan) {
        val entry = p.current() ?: return
        SongPlayer.registerContextName(p.contextUri, p.contextName)
        dispatching = true
        try {
            if (entry.isDeviceFile) {
                spotifyRunUris = emptySet()
                sawPlayingCurrentSpotify = false
                val localTrack = deviceFiles[entry.url]
                if (localTrack == null) {
                    dispatching = false
                    if (!step(forward = true)) clear()
                    return
                }
                LocalPlayer.play(
                    listOf(localTrack),
                    0,
                    shuffle = false,
                    context = p.contextName,
                    contextUri = p.contextUri,
                )
            } else {
                val run = p.spotifyRunFrom(p.index)
                val uris = run.map { "spotify:track:${it.spotifyTrackId}" }
                spotifyRunUris = uris.toSet()
                sawPlayingCurrentSpotify = false
                SongPlayer.playSong(
                    entry.url,
                    contextUri = p.contextUri,
                    order = uris,
                    askExtensions = false,
                )
            }
            SongPlayer.publishMixedQueue()
        } finally {
            dispatching = false
        }
    }
}
