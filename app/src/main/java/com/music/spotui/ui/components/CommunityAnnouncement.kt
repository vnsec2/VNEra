package com.music.spotui.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.myspoti.engine.R

@Composable
fun CommunityAnnouncement() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("revera_announcements", Context.MODE_PRIVATE) }
    var visible by remember { mutableStateOf(!prefs.getBoolean("discord_2026_seen", false)) }
    if (!visible) return
    val dismiss = {
        prefs.edit().putBoolean("discord_2026_seen", true).apply()
        visible = false
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.community_discord_title)) },
        text = { Text(stringResource(R.string.community_discord_message)) },
        confirmButton = {
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_URL))) }
                    .onSuccess { dismiss() }
                    .onFailure { android.widget.Toast.makeText(context, context.getString(R.string.community_discord_failed), android.widget.Toast.LENGTH_LONG).show() }
            }) { Text(stringResource(R.string.community_discord_open)) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.community_discord_later)) } },
    )
}
