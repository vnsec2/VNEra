package com.music.spotui.ui.player

import androidx.test.platform.app.InstrumentationRegistry
import com.music.spotui.data.local.LocalMusic
import com.music.spotui.di.Graph
import com.music.spotui.di.LocalPlayer
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.viewmodel.LyricsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class LocalLyricsLiveTest {
    @Test fun translatesIndexedRecordingWithoutCatalogId() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveLocalLyrics") == "true")
        val inst = InstrumentationRegistry.getInstrumentation()
        val ctx = inst.targetContext
        val track = LocalMusic.tracks(ctx, true).first { it.title == "Frere Jacques" && it.artist == "Raffi" }
        val vm = LyricsViewModel()
        try {
            inst.runOnMainSync {
                SongPlayer.attachEngine(Graph.currentSongState)
                LocalPlayer.play(listOf(track), 0)
            }
            withTimeout(15000) { while (SongPlayer.currentLyricsTrack()?.title != track.title) delay(100) }
            val request = requireNotNull(SongPlayer.currentLyricsTrack())
            assertEquals("", request.trackId)
            assertEquals(track.artist, request.artist)
            inst.runOnMainSync { vm.load(request) }
            val loaded = withTimeout(90000) { vm.state.first { it is LyricsViewModel.State.Loaded } } as LyricsViewModel.State.Loaded
            assertTrue(loaded.lyrics.lines.any { it.text.contains("Jacques", true) })
            inst.runOnMainSync { vm.toggleTranslation("de") }
            val translated = withTimeout(90000) { vm.state.first { it is LyricsViewModel.State.Loaded && it.showTranslation && it.translationLanguage == "de" } } as LyricsViewModel.State.Loaded
            assertTrue(translated.translations.orEmpty().any { it.isNotBlank() && it !in loaded.lyrics.lines.map { line -> line.text } })
        } finally {
            inst.runOnMainSync { LocalPlayer.stop() }
        }
    }
}
