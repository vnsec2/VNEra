package com.music.spotui.ui.immersive.shell.backdrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BackdropSourcesTest {
    @Test fun repeatedReadsReuseTheSameSnapshot() {
        val sources = BackdropSources<String>()
        sources.add("first")
        assertSame(sources.snapshot, sources.snapshot)
    }

    @Test fun mutationsKeepPreviouslyPublishedSnapshotsStable() {
        val sources = BackdropSources<String>()
        sources.add("first")
        val old = sources.snapshot
        sources.add("second")
        sources.remove("first")
        assertEquals(listOf("first"), old)
        assertEquals(listOf("second"), sources.snapshot)
    }
}
