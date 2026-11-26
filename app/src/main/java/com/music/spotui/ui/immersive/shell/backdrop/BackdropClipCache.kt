package com.music.spotui.ui.immersive.shell.backdrop

import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline

internal class BackdropClipCache {
    private var lastOutline: Outline? = null
    private var lastPath: Path? = null

    fun path(outline: Outline): Path {
        if (outline !is Outline.Generic && lastOutline == outline) return lastPath!!
        return Path().apply { addOutline(outline) }.also {
            lastOutline = outline
            lastPath = it
        }
    }
}
