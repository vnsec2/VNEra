package com.music.spotui.ui.immersive.shell.backdrop

import androidx.compose.ui.graphics.Paint

internal class BackdropPaint {
    private val paint = Paint()
    fun at(alpha: Float): Paint = paint.apply { this.alpha = alpha.coerceIn(0f, 1f) }
}
