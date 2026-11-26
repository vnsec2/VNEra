package com.music.spotui.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myspoti.engine.R
import com.music.spotui.data.api.BadgeClient
import com.music.spotui.data.api.SupportReminder
import com.music.spotui.data.api.SupportReminder.Accent
import com.music.spotui.data.preferences.LookPref
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.tone
import kotlinx.coroutines.launch

private fun parseBadgeColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return runCatching { Color(android.graphics.Color.parseColor(hex.trim())) }.getOrDefault(fallback)
}

@Composable
fun SupporterBadge(
    label: String? = null,
    colorHex: String? = null,
    modifier: Modifier = Modifier,
) {
    val bg = parseBadgeColor(colorHex, AppAccent)
    val fg = if (bg.luminance() >= 0.45f) Color.Black else Color.White
    val text = label?.takeIf { it.isNotBlank() } ?: stringResource(R.string.supporter_badge)
    Text(
        text = text,
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserBadgesRow(state: BadgeClient.BadgeState?, modifier: Modifier = Modifier) {
    if (state == null || (state.badges.isEmpty() && !state.perks)) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.badges.isEmpty()) {
            SupporterBadge()
        } else {
            for (b in state.badges) {
                SupporterBadge(label = b.label, colorHex = b.colorHex)
            }
        }
    }
}

private fun accentName(a: Accent) = when (a) {
    Accent.GREEN -> R.string.color_green
    Accent.BLUE -> R.string.color_blue
    Accent.PURPLE -> R.string.color_purple
    Accent.PINK -> R.string.color_pink
    Accent.ORANGE -> R.string.color_orange
}

private fun iconMark(a: Accent) = when (a) {
    Accent.GREEN -> R.drawable.ic_launcher_foreground
    Accent.BLUE -> R.drawable.ic_launcher_foreground_blue
    Accent.PURPLE -> R.drawable.ic_launcher_foreground_purple
    Accent.PINK -> R.drawable.ic_launcher_foreground_pink
    Accent.ORANGE -> R.drawable.ic_launcher_foreground_orange
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupporterSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = BadgeClient.myState
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var statusError by remember { mutableStateOf(false) }

    val msgActivated = stringResource(R.string.badge_activated)
    val msgInvalid = stringResource(R.string.badge_error_invalid)
    val msgExhausted = stringResource(R.string.badge_error_exhausted)
    val msgExpired = stringResource(R.string.badge_error_expired)
    val msgSignIn = stringResource(R.string.badge_error_sign_in)
    val msgNetwork = stringResource(R.string.badge_error_network)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AppText,
        unfocusedTextColor = AppText,
        cursorColor = AppAccent,
        focusedBorderColor = AppAccent,
        unfocusedBorderColor = Color(0xFF555555),
        focusedLabelColor = AppAccent,
        unfocusedLabelColor = AppMuted,
    )

    LookSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = tone(0xFF282828),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text(stringResource(R.string.badge_sheet_title), color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (state == null || (state.badges.isEmpty() && !state.perks)) {
                Text(stringResource(R.string.badge_none), color = AppMuted, fontSize = 14.sp)
            } else {
                UserBadgesRow(state)
            }

            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text(stringResource(R.string.badge_code_label)) },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(50))
                        .background(AppAccent.copy(alpha = if (busy || code.isBlank()) 0.45f else 1f))
                        .pressable(0.96f) {
                            if (busy || code.isBlank()) return@pressable
                            busy = true
                            statusText = null
                            scope.launch {
                                when (BadgeClient.redeem(context, code)) {
                                    is BadgeClient.RedeemResult.Ok -> {
                                        code = ""
                                        statusError = false
                                        statusText = msgActivated
                                    }
                                    BadgeClient.RedeemResult.InvalidCode -> {
                                        statusError = true
                                        statusText = msgInvalid
                                    }
                                    BadgeClient.RedeemResult.Exhausted -> {
                                        statusError = true
                                        statusText = msgExhausted
                                    }
                                    BadgeClient.RedeemResult.Expired -> {
                                        statusError = true
                                        statusText = msgExpired
                                    }
                                    BadgeClient.RedeemResult.NotSignedIn -> {
                                        statusError = true
                                        statusText = msgSignIn
                                    }
                                    is BadgeClient.RedeemResult.Failed -> {
                                        statusError = true
                                        statusText = msgNetwork
                                    }
                                }
                                busy = false
                            }
                        },
                ) {
                    Text(stringResource(R.string.badge_code_activate), color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        statusText = null
                        scope.launch {
                            val updated = BadgeClient.refreshMe(context)
                            if (updated == null) {
                                statusError = true
                                statusText = msgNetwork
                            } else {
                                statusError = false
                            }
                            busy = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.badge_refresh), color = AppText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (!statusText.isNullOrBlank()) {
                Text(
                    text = statusText!!,
                    color = if (statusError) Color(0xFFFF8A80) else AppAccent,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (state?.perks == true) {
                Text(stringResource(R.string.supporter_accent), color = AppText, fontSize = 16.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (a in Accent.entries) {
                        val selected = SupportReminder.accentOf(LookPref.look) == a
                        val name = stringResource(accentName(a))
                        Box(
                            Modifier
                                .size(40.dp)
                                .semantics { contentDescription = name }
                                .clip(CircleShape)
                                .background(a.color)
                                .then(if (selected) Modifier.border(3.dp, AppText, CircleShape) else Modifier)
                                .pressable(0.92f) { SupportReminder.setAccent(context, LookPref.look, a) },
                        )
                    }
                }

                Text(stringResource(R.string.supporter_icon), color = AppText, fontSize = 16.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (a in Accent.entries) {
                        val selected = SupportReminder.icon == a
                        val name = stringResource(accentName(a))
                        Image(
                            painterResource(iconMark(a)),
                            contentDescription = name,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(tone(0xFF121212))
                                .then(if (selected) Modifier.border(3.dp, AppText, RoundedCornerShape(14.dp)) else Modifier)
                                .pressable(0.92f) { SupportReminder.setIcon(context, a) },
                        )
                    }
                }
                Text(stringResource(R.string.supporter_icon_note), color = AppMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
