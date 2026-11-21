package app.myspoti.spotify

import app.myspoti.spotify.gql.Gql
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException

class PartialSearchTest {
    private fun search(fail: (String) -> Boolean) = SpotifySearch(object : Gql {
        override suspend fun query(name: String, variables: JsonObject) = if (fail(name)) throw IOException(name) else Json.parseToJsonElement("""{"data":{"searchV2":{"artists":{"items":[{"data":{"uri":"spotify:artist:A","profile":{"name":"A"}}}]}}}}""")
    })

    @Test fun keepsArtistsWhenPlaylistsFail() = runBlocking {
        assertEquals("spotify:artist:A", search { it == "searchPlaylists" }.all("a", 10).artists.items.single().uri)
    }

    @Test fun totalFailureRemainsAnError() {
        assertThrows(IOException::class.java) { runBlocking { search { true }.all("a", 10) } }
    }
}
