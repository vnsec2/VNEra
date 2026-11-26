package com.music.spotui.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.myspoti.engine.R

@Composable
fun CommunityMenuButton() {
    val context = LocalContext.current
    TextButton(onClick = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_URL))) }
            .onFailure { android.widget.Toast.makeText(context, context.getString(R.string.community_discord_failed), android.widget.Toast.LENGTH_LONG).show() }
    }) { Text(stringResource(R.string.community_discord_open)) }
}
