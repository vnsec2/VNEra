package com.music.spotui.ui.immersive.shell.backdrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackdropPaintTest {
    @Test fun transitionFramesReusePaint() {
        val cache = BackdropPaint()
        val first = cache.at(0.25f)
        assertSame(first, cache.at(0.75f))
        assertEquals(0.75f, first.alpha, 0.01f)
    }

    @Test fun alphaIsBounded() {
        val cache = BackdropPaint()
        assertEquals(0f, cache.at(-1f).alpha, 0.01f)
        assertEquals(1f, cache.at(2f).alpha, 0.01f)
    }
}
