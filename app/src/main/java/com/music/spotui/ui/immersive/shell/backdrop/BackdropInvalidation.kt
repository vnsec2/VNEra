package com.music.spotui.ui.immersive.shell.backdrop

import androidx.compose.runtime.MutableIntState

internal fun signalBackdropChange(version: MutableIntState) {
    androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation {
        version.intValue++
    }
}
