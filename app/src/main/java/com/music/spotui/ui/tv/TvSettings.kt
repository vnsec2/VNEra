package com.music.spotui.ui.tv

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myspoti.power.EnergySettings
import app.myspoti.engine.BuildConfig
import app.myspoti.engine.R
import com.music.spotui.data.preferences.AutoplayPref
import com.music.spotui.data.preferences.ConnectMirrorPref
import com.music.spotui.di.Graph
import com.music.spotui.di.SessionActions
import com.music.spotui.di.SongPlayer
import kotlinx.coroutines.delay

@Composable
internal fun TvSettings(onBlackout: () -> Unit) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    val energy by EnergySettings.state.collectAsState()
    val sleepEndsAt by SongPlayer.sleepEndsAt
    LaunchedEffect(Unit) { runCatching { delay(80); first.requestFocus() } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TvSpace.start, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("connect") {
            TvSettingsToggleRow(
                title = stringResource(R.string.tv_settings_connect_title),
                subtitle = stringResource(R.string.tv_settings_connect_summary),
                checked = ConnectMirrorPref.on,
                modifier = Modifier.focusRequester(first),
                onToggle = { ConnectMirrorPref.set(context, !ConnectMirrorPref.on) },
            )
        }
        item("autoplay") {
            TvSettingsToggleRow(
                title = stringResource(R.string.tv_settings_autoplay_title),
                subtitle = stringResource(R.string.tv_settings_autoplay_summary),
                checked = AutoplayPref.on,
                onToggle = { AutoplayPref.set(context, !AutoplayPref.on) },
            )
        }
        item("energy") {
            TvSettingsToggleRow(
                title = stringResource(R.string.energy_saver_title),
                subtitle = stringResource(R.string.energy_saver_summary),
                checked = energy.manualSaver,
                onToggle = { EnergySettings.setSaver(!energy.manualSaver) },
            )
        }
        item("blackout") {
            TvSettingsActionRow(
                title = stringResource(R.string.tv_blackout_screen),
                subtitle = stringResource(R.string.tv_blackout_summary),
                value = "",
                onClick = onBlackout,
            )
        }
        item("sleep") {
            TvSettingsActionRow(
                title = stringResource(R.string.tv_sleep_timer),
                subtitle = "",
                value = tvSleepLabel(sleepEndsAt),
                onClick = { cycleTvSleepTimer(sleepEndsAt) },
            )
        }
        item("signout") {
            TvSettingsActionRow(
                title = stringResource(R.string.tv_settings_sign_out),
                subtitle = "Revera ${BuildConfig.VERSION_NAME}",
                value = "",
                onClick = { SessionActions.logout(Graph.app) },
            )
        }
    }
}

@Composable
internal fun tvSleepLabel(sleepEndsAt: Long?): String = when {
    sleepEndsAt == null -> stringResource(R.string.tv_sleep_off)
    sleepEndsAt < 0L -> stringResource(R.string.tv_sleep_end_of_track)
    else -> {
        val mins = ((sleepEndsAt - SystemClock.elapsedRealtime() + 59_999L) / 60_000L).coerceAtLeast(1L).toInt()
        stringResource(R.string.tv_sleep_minutes, mins)
    }
}

internal fun cycleTvSleepTimer(current: Long?) {
    val next = when {
        current == null -> 15
        current < 0L -> null
        else -> {
            val mins = (current - SystemClock.elapsedRealtime()) / 60_000L
            when {
                mins <= 15L -> 30
                mins <= 30L -> 60
                else -> 0
            }
        }
    }
    SongPlayer.setSleepTimer(next)
}

@Composable
private fun TvSettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(150), label = "setFocus")
    val bg by animateColorAsState(if (focused) TvOnSurface else Color(0xFF1A1A1D), tween(150), label = "setBg")
    val fg = if (focused) TvInverseOnSurface else Color.White
    val sub = if (focused) TvInverseOnSurface.copy(alpha = 0.72f) else TvSubtle
    Row(
        modifier.fillMaxWidth()
            .graphicsLayer { val s = 1f + 0.015f * f; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = fg)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 13.sp, color = sub)
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = TvAccent,
                uncheckedThumbColor = Color(0xFFB0B0B6),
                uncheckedTrackColor = Color(0xFF3A3A3E),
            ),
        )
    }
}

@Composable
private fun TvSettingsActionRow(
    title: String,
    subtitle: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(150), label = "actFocus")
    val bg by animateColorAsState(if (focused) TvOnSurface else Color(0xFF1A1A1D), tween(150), label = "actBg")
    val fg = if (focused) TvInverseOnSurface else Color.White
    val sub = if (focused) TvInverseOnSurface.copy(alpha = 0.72f) else TvSubtle
    Row(
        modifier.fillMaxWidth()
            .graphicsLayer { val s = 1f + 0.015f * f; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = fg)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 13.sp, color = sub)
            }
        }
        if (value.isNotBlank()) {
            Spacer(Modifier.width(16.dp))
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (focused) TvAccent else Color(0x26FFFFFF))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}
