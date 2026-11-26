package com.music.spotui.ui.components

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.myspoti.engine.R

data class LyricsAppearance(val percent: Int, val argb: Int) {
    val scale: Float get() = percent / 100f
    fun color(default: Color): Color = if (argb == 0) default else Color(argb)
}

@Composable
fun rememberLyricsAppearance(): LyricsAppearance {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("settings_prefs", 0) }
    fun read() = LyricsAppearance(prefs.getInt("lyrics_text_percent", 100).coerceIn(70, 140), prefs.getInt("lyrics_text_color", 0))
    var appearance by remember(prefs) { mutableStateOf(read()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "lyrics_text_percent" || key == "lyrics_text_color") appearance = read()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        appearance = read()
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return appearance
}

@Composable
fun LyricsAppearanceControls() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("settings_prefs", 0) }
    val appearance = rememberLyricsAppearance()
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.extension_detail_size), color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (percent in listOf(70, 85, 100, 120, 140)) {
                Text("$percent%", color = if (appearance.percent == percent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clip(CircleShape).clickable { prefs.edit().putInt("lyrics_text_percent", percent).apply() }
                        .semantics { selected = appearance.percent == percent }.padding(vertical = 12.dp, horizontal = 4.dp))
            }
        }
        Text(stringResource(R.string.supporter_accent), color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.language_auto), color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(CircleShape).clickable { prefs.edit().putInt("lyrics_text_color", 0).apply() }
                    .semantics { selected = appearance.argb == 0 }.padding(vertical = 12.dp))
            for ((color, label) in listOf(0xFF80CBC4.toInt() to R.string.color_green, 0xFF90CAF9.toInt() to R.string.color_blue, 0xFFF48FB1.toInt() to R.string.color_pink)) {
                val name = stringResource(label)
                androidx.compose.foundation.layout.Box(Modifier.size(48.dp).clip(CircleShape).background(Color(color))
                    .clickable { prefs.edit().putInt("lyrics_text_color", color).apply() }
                    .semantics { contentDescription = name; selected = appearance.argb == color })
            }
        }
    }
}
