package com.music.spotui.di

import app.myspoti.api.sync.ListenSyncRules
import com.music.spotui.extensions.ExtensionAudio
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSyncDownloadEligibilityTest {
    private val validSpotifyTrackId = "4cOdK2wGLETKBW3PvgPWqT"

    @Test
    fun extensionStreamIsEligibleRegardlessOfOnlineFlag() {
        assertTrue(isMirrorEligibleLocal(ExtensionAudio.SCHEME, null, false, online = true))
        assertTrue(isMirrorEligibleLocal(ExtensionAudio.SCHEME, null, false, online = false))
    }

    @Test
    fun downloadedSpotifyMusicTrackEligibilityMatchesOnlineAndSettingMatrix() {
        val liveOnline = isMirrorEligibleLocal("file", validSpotifyTrackId, false, online = true)
        val liveOffline = isMirrorEligibleLocal("file", validSpotifyTrackId, false, online = false)

        assertTrue(liveOnline)
        assertFalse(liveOffline)

        assertTrue(ListenSyncRules.eligible(live = liveOnline, trackId = validSpotifyTrackId, on = true, dataSaver = false))
        assertFalse(ListenSyncRules.eligible(live = liveOnline, trackId = validSpotifyTrackId, on = false, dataSaver = false))
        assertFalse(ListenSyncRules.eligible(live = liveOnline, trackId = validSpotifyTrackId, on = true, dataSaver = true))
        assertFalse(ListenSyncRules.eligible(live = liveOffline, trackId = validSpotifyTrackId, on = true, dataSaver = false))
    }

    @Test
    fun downloadedPodcastEpisodeIsNeverEligibleEvenWithValidLengthId() {
        assertFalse(isMirrorEligibleLocal("file", "episode:1234567890", downloadedIsEpisode = true, online = true))
        assertFalse(isMirrorEligibleLocal("file", validSpotifyTrackId, downloadedIsEpisode = true, online = true))
    }

    @Test
    fun arbitraryLocalFilesAndRadioStreamsAreNotEligible() {
        for (scheme in listOf("content", "file", "http", "https", "", null)) {
            assertFalse(
                "scheme=$scheme should not be eligible without offline track record",
                isMirrorEligibleLocal(scheme, downloadedTrackId = null, downloadedIsEpisode = false, online = true),
            )
        }
    }

    @Test
    fun malformedTrackIdsAreRejected() {
        val badIds = listOf(
            "",
            "   ",
            "not-a-spotify-id",
            "${validSpotifyTrackId} ",
            " $validSpotifyTrackId",
            validSpotifyTrackId.dropLast(1),
            "${validSpotifyTrackId}A",
            "4cOdK2wGLETKBW3PvgPWq!",
            "spotify:track:$validSpotifyTrackId",
        )
        for (bad in badIds) {
            assertFalse("bad id '$bad' must not be eligible", isMirrorEligibleLocal("file", bad, false, online = true))
        }
    }
}
