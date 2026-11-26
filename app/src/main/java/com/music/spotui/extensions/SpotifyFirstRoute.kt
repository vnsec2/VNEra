package com.music.spotui.extensions

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Spotify startup never waits for resolution. Only an acknowledged, current handoff starts local audio. */
internal suspend fun <T : Any> spotifyFirstRoute(
    startSpotify: () -> Unit,
    resolve: suspend () -> T?,
    awaitPlaying: suspend () -> Boolean,
    handoff: suspend (T) -> Long?,
    isCurrent: () -> Boolean,
    startLocal: (T, Long) -> Unit,
) = coroutineScope {
    startSpotify()
    if (!isCurrent()) return@coroutineScope
    val playing = async { awaitPlaying() }
    val answer = resolve()
    if (answer == null || !isCurrent()) {
        playing.cancel()
        return@coroutineScope
    }
    if (!playing.await() || !isCurrent()) return@coroutineScope
    val position = handoff(answer) ?: return@coroutineScope
    if (isCurrent()) startLocal(answer, position)
}
