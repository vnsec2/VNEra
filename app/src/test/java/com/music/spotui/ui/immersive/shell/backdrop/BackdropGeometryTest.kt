package com.music.spotui.ui.immersive.shell.backdrop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropGeometryTest {
    @Test fun unchangedGeometryDoesNotPublishTwice() {
        val gate = BackdropGeometry()
        val owner = Any()
        assertTrue(gate.changed(owner, Offset.Zero, IntSize(100, 50), Offset(1f, 0f), Offset(0f, 1f)))
        assertFalse(gate.changed(owner, Offset.Zero, IntSize(100, 50), Offset(1f, 0f), Offset(0f, 1f)))
    }

    @Test fun changedTransformPublishesEvenAtTheSameOrigin() {
        val gate = BackdropGeometry()
        val owner = Any()
        gate.changed(owner, Offset.Zero, IntSize(100, 50), Offset(1f, 0f), Offset(0f, 1f))
        assertTrue(gate.changed(owner, Offset.Zero, IntSize(100, 50), Offset(2f, 0f), Offset(0f, 2f)))
    }

    @Test fun replacementSurfacePublishesUnchangedGeometry() {
        val gate = BackdropGeometry()
        gate.changed(Any(), Offset.Zero, IntSize(100, 50), Offset(1f, 0f), Offset(0f, 1f))
        assertTrue(gate.changed(Any(), Offset.Zero, IntSize(100, 50), Offset(1f, 0f), Offset(0f, 1f)))
    }

    @Test fun resetPublishesReattachedSurface() {
        val gate = BackdropGeometry()
        val owner = Any()
        gate.changed(owner, Offset.Zero, IntSize(100, 50), Offset(1f, 0f), Offset(0f, 1f))
        gate.reset()
        assertTrue(gate.changed(owner, Offset.Zero, IntSize(100, 50), Offset(1f, 0f), Offset(0f, 1f)))
    }
}
