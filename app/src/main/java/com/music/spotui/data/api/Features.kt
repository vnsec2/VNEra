package com.music.spotui.data.api

object Features {
    val PODCASTS: Boolean
        get() = !com.music.spotui.data.preferences.HidePodcastsPref.on
}
