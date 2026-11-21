package com.music.spotui.ui.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.myspoti.engine.R
import com.music.spotui.ui.components.formatSpotifySourceLabel
import com.music.spotui.ui.immersive.player.PlayerScrubberReadout
import com.music.spotui.ui.immersive.player.PlayerTimeLabels
import com.music.spotui.ui.immersive.player.QualityBadgeRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PlayerUiRegressionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun spotifyRemainsNamedWithoutBitrateAfterTheHandoff() {
        val name = context.getString(R.string.sources_account)
        val qualityFormat = context.getString(R.string.player_source_spotify)
        assertEquals(name, formatSpotifySourceLabel("spotify:track:track", "", name, qualityFormat))
        assertEquals(
            context.getString(R.string.player_source_spotify, "AAC · 160 kbit/s"),
            formatSpotifySourceLabel("spotify:track:track", "AAC · 160 kbit/s", name, qualityFormat),
        )
    }

    @Test fun downloadedMetadataDoesNotCoverTimesAtLargeFontScale() {
        val laidOut = CountDownLatch(3)
        val bounds = mutableMapOf<String, Rect>()
        fun measured(key: String) = Modifier.onGloballyPositioned {
            if (bounds.put(key, it.boundsInRoot()) == null) laidOut.countDown()
        }
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                        Box(Modifier.width(260.dp)) { // 320dp portrait minus the two player gutters
                            PlayerScrubberReadout(
                                times = {
                                    PlayerTimeLabels {
                                        Text("1:23", measured("elapsed"), fontSize = 12.sp)
                                        Text("-2:34", measured("remaining"), fontSize = 12.sp)
                                    }
                                },
                                source = {
                                    QualityBadgeRow(DOWNLOAD_SOURCE, null, false, measured("source"))
                                },
                            )
                        }
                    }
                }
            }
            assertTrue("The times and source must all be measured", laidOut.await(10, TimeUnit.SECONDS))
            scenario.onActivity {
                val elapsed = bounds.getValue("elapsed")
                val remaining = bounds.getValue("remaining")
                val source = bounds.getValue("source")
                assertTrue(elapsed.width > 0 && remaining.width > 0 && source.width > 0)
                assertTrue(elapsed.right <= remaining.left || remaining.right <= elapsed.left || elapsed.bottom <= remaining.top)
                assertTrue(maxOf(elapsed.bottom, remaining.bottom) <= source.top)
            }
        }
    }

    @Test fun missingMetadataDoesNotInventASourceForAnEpisodeOrLocalFile() {
        val name = context.getString(R.string.sources_account)
        val qualityFormat = context.getString(R.string.player_source_spotify)
        for (uri in listOf("", "spotify:track:", "spotify:episode:episode", "local:file")) {
            assertEquals(uri, "", formatSpotifySourceLabel(uri, "", name, qualityFormat))
        }
    }
}

private const val DOWNLOAD_SOURCE = "Downloaded · M4A · YouTube Music (AAC) · YouTube"
