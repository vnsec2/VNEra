package com.music.spotui.ui.immersive.shell.backdrop

internal class BackdropSources<T> {
    private val values = ArrayList<T>()
    var snapshot: List<T> = emptyList()
        private set
    fun add(value: T) {
        values.add(value)
        snapshot = values.toList()
    }
    fun remove(value: T) {
        if (values.remove(value)) snapshot = values.toList()
    }
}
