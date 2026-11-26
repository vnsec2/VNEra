package com.music.spotui.data.catalog

import app.myspoti.catalog.Counterpart
import app.myspoti.catalog.ItemUri
import app.myspoti.catalog.ProviderChain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** A song of another provider looked up on Spotify (see [CatalogRuntime.spotifyMatchFor]). */
sealed interface SpotifyMatch {
    data class Found(val id: String, val track: app.myspoti.api.Track) : SpotifyMatch
    /** Spotify answered: it has no such recording. */
    data object Absent : SpotifyMatch
    /** Spotify is not asked because its account is signed out. */
    data object NotAsked : SpotifyMatch
    /** No answer now ([why]): a network failure, a time limit, a provider resting. Never said to be absent. */
    data class Unavailable(val why: String) : SpotifyMatch

    companion object {
        /** Browsing providers supply the source track; the account resolves its playback id independently. */
        internal suspend fun lookup(uri: String, catalog: ProviderChain, spotify: ProviderChain?, knownId: String? = null): SpotifyMatch {
            if (spotify == null) return NotAsked
            val track = try { catalog.track(uri) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                return Unavailable("$uri not read: ${e.message ?: e.javaClass.simpleName}")
            }
            knownId?.let { return Found(it, track) }
            val isrc = try { catalog.isrc(uri) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            return when (val answer = spotify.counterpartAnswer("spotify", track.name, track.artists.firstOrNull()?.name.orEmpty(), track.durationMs, isrc)) {
                is Counterpart.Found -> Found(ItemUri.parse(answer.hit.track.uri).id, track)
                Counterpart.Absent -> Absent
                is Counterpart.Unknown -> Unavailable(answer.why)
            }
        }

        /** The waits before each new try of a lookup that got no answer. */
        val RETRY_WAITS_MS = longArrayOf(800, 2_000)

        /** [lookUp], tried again after each of [waits] while it gets no answer; the last answer otherwise. */
        suspend fun withRetries(waits: LongArray = RETRY_WAITS_MS, lookUp: suspend () -> SpotifyMatch): SpotifyMatch {
            var r = lookUp()
            for (w in waits) {
                if (r !is Unavailable) return r
                delay(w)
                r = lookUp()
            }
            return r
        }

        /** What a tap on the song says when it cannot play: the absence, or that Spotify did not answer; null when it plays. */
        fun message(m: SpotifyMatch): Int? = when (m) {
            is Found -> null
            Absent -> app.myspoti.engine.R.string.metadata_not_on_spotify
            NotAsked -> app.myspoti.engine.R.string.not_signed_in
            is Unavailable -> app.myspoti.engine.R.string.metadata_spotify_no_answer
        }
    }
}
