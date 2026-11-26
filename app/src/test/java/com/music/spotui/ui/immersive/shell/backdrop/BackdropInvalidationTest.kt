package com.music.spotui.ui.immersive.shell.backdrop

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class BackdropInvalidationTest {
    @Test fun signalingDoesNotSubscribeTheDrawingProducerToItsOwnVersion() {
        val version = mutableIntStateOf(0)
        var reads = 0
        Snapshot.observe(readObserver = { reads++ }) {
            signalBackdropChange(version)
        }
        assertEquals(1, version.intValue)
        assertEquals(0, reads)
    }
}
