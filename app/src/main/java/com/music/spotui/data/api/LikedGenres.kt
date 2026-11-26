/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.data.api

import android.content.Context
import app.myspoti.engine.R
import app.myspoti.host.android.MyspotiRuntime
import com.music.spotui.data.entity.SongsModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray

/** Broad filters inferred from Spotify's artist genres, which are not track-level genres. */
enum class LikedGenre(val label: Int, private vararg val words: String) {
    POP(R.string.liked_genre_pop, "pop"),
    ROCK(R.string.liked_genre_rock, "rock"),
    HIP_HOP(R.string.liked_genre_hip_hop, "hip hop", "hip-hop", "rap", "trap", "drill", "grime"),
    ELECTRONIC(R.string.liked_genre_electronic, "electronic", "electro", "edm", "house", "techno", "trance", "dubstep", "drum and bass", "dnb"),
    R_AND_B(R.string.liked_genre_r_and_b, "r&b", "rnb", "rhythm and blues", "soul", "funk"),
    INDIE(R.string.liked_genre_indie, "indie", "alternative"),
    METAL(R.string.liked_genre_metal, "metal"),
    JAZZ(R.string.liked_genre_jazz, "jazz", "blues"),
    CLASSICAL(R.string.liked_genre_classical, "classical", "orchestra", "opera"),
    LATIN(R.string.liked_genre_latin, "latin", "reggaeton", "salsa", "bachata"),
    COUNTRY(R.string.liked_genre_country, "country", "bluegrass");

    companion object {
        fun fromNames(names: List<String>): Set<LikedGenre> = entries.filterTo(linkedSetOf()) { genre ->
            names.any { name -> genre.words.any { it in name.lowercase() } }
        }
    }
}

/** Fetch each artist once, in Spotify's 50-artist batches; keep its raw genres across visits. */
object LikedGenres {
    private const val PREFS = "liked_artist_genres"
    private val idPattern = Regex("[A-Za-z0-9]{22}")

    fun filter(tracks: List<SongsModel>, genre: LikedGenre?, byArtist: Map<String, Set<LikedGenre>>): List<SongsModel> =
        if (genre == null) tracks else tracks.filter { song ->
            song.artistIds.any { raw -> genre in byArtist[raw.substringAfterLast(':')].orEmpty() }
        }

    suspend fun load(context: Context, tracks: List<SongsModel>, onBatch: (Map<String, Set<LikedGenre>>) -> Unit) {
        val ids = tracks.flatMap { it.artistIds }.map { it.substringAfterLast(':') }
            .filter { idPattern.matches(it) }.distinct()
        if (ids.isEmpty()) return
        val names = withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS, 0)
            ids.mapNotNull { id ->
                prefs.getString(id, null)?.let { stored ->
                    runCatching { JSONArray(stored) }.getOrNull()?.let { array ->
                        id to (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
                    }
                }
            }.toMap().toMutableMap()
        }
        onBatch(names.mapValues { LikedGenre.fromNames(it.value) })
        for (batch in ids.filterNot(names::containsKey).chunked(50)) {
            val fetched = withContext(Dispatchers.IO) {
                val raw = runCatching {
                    MyspotiRuntime.spotify.raw.get("https://api.spotify.com/v1/artists?ids=${batch.joinToString(",")}")
                }.getOrNull() as? JsonObject ?: return@withContext emptyMap<String, List<String>>()
                val artists = raw["artists"] as? JsonArray ?: return@withContext emptyMap<String, List<String>>()
                val found = artists.mapNotNull { entry ->
                    val artist = entry as? JsonObject ?: return@mapNotNull null
                    val id = artist["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val genres = (artist["genres"] as? JsonArray).orEmpty()
                        .mapNotNull { it.jsonPrimitive.contentOrNull }
                    id to genres
                }.toMap()
                val prefs = context.getSharedPreferences(PREFS, 0).edit()
                found.forEach { (id, genres) -> prefs.putString(id, JSONArray(genres).toString()) }
                prefs.apply()
                found
            }
            if (fetched.isNotEmpty()) {
                names.putAll(fetched)
                onBatch(names.mapValues { LikedGenre.fromNames(it.value) })
            }
        }
    }
}
