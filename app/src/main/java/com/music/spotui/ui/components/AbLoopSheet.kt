package com.music.spotui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myspoti.engine.R
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.tone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbLoopSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by SongPlayer.abLoop
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tone(0xFF282828),
        dragHandle = null,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ab_loop_title), color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (state.aMs != null || state.bMs != null) {
                    Text(
                        stringResource(R.string.ab_loop_clear),
                        color = AppAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { SongPlayer.clearAbLoop() }.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            AbPointRow(
                label = stringResource(R.string.ab_loop_set_a),
                ms = state.aMs,
                onSet = { SongPlayer.setAbLoopA() },
                onMinus = { SongPlayer.nudgeAbLoopA(-1_000L) },
                onPlus = { SongPlayer.nudgeAbLoopA(1_000L) },
            )
            AbPointRow(
                label = stringResource(R.string.ab_loop_set_b),
                ms = state.bMs,
                onSet = { SongPlayer.setAbLoopB() },
                onMinus = { SongPlayer.nudgeAbLoopB(-1_000L) },
                onPlus = { SongPlayer.nudgeAbLoopB(1_000L) },
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AbPointRow(
    label: String,
    ms: Long?,
    onSet: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = AppText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(ms?.let(::formatAbMs) ?: "--:--", color = if (ms != null) AppAccent else AppMuted, fontSize = 13.sp)
        }
        if (ms != null) {
            AbNudgeChip("-1s", onMinus)
            Spacer(Modifier.width(6.dp))
            AbNudgeChip("+1s", onPlus)
            Spacer(Modifier.width(8.dp))
        }
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .background(AppAccent)
                .clickable(onClick = onSet)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(label, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AbNudgeChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(Color(0x26FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(text, color = AppText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

internal fun formatAbMs(ms: Long): String {
    val s = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(s / 60, s % 60)
}
