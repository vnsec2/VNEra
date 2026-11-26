package com.music.spotui.ui.immersive.shell.backdrop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

internal class BackdropGeometry {
    private var lastOwner: Any? = null
    private var lastPosition = Offset.Unspecified
    private var lastSize = IntSize.Zero
    private var lastAxisX = Offset.Unspecified
    private var lastAxisY = Offset.Unspecified

    fun changed(owner: Any, position: Offset, size: IntSize, axisX: Offset, axisY: Offset): Boolean {
        if (owner === lastOwner && position == lastPosition && size == lastSize &&
            axisX == lastAxisX && axisY == lastAxisY) return false
        lastOwner = owner
        lastPosition = position
        lastSize = size
        lastAxisX = axisX
        lastAxisY = axisY
        return true
    }

    fun reset() { lastOwner = null }
}
