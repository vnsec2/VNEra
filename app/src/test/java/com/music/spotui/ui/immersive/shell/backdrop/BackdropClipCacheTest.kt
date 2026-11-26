package com.music.spotui.ui.immersive.shell.backdrop

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Outline
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackdropClipCacheTest {
    @Test fun unchangedOutlineReusesTheClipPath() {
        val cache = BackdropClipCache()
        val outline = Outline.Rectangle(Rect(0f, 0f, 100f, 40f))
        assertSame(cache.path(outline), cache.path(outline))
    }

    @Test fun changedOutlineRebuildsTheClipPath() {
        val cache = BackdropClipCache()
        val first = cache.path(Outline.Rectangle(Rect(0f, 0f, 100f, 40f)))
        val second = cache.path(Outline.Rectangle(Rect(0f, 0f, 120f, 40f)))
        assertNotSame(first, second)
    }
}
