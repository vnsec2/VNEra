package com.music.spotui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import app.myspoti.engine.R
import app.myspoti.host.android.DiagnosticReport
import app.myspoti.host.android.MyspotiRuntime
import com.music.spotui.ui.screens.shareDiagnosticReport
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppSans
import com.music.spotui.ui.theme.AppText

/**
 * A failed sign-in had no way to a diagnostic report: the export lives in the settings, which
 * only a signed-in app reaches (the reported "Oops! Something went wrong" and e-mail loops came
 * with no log at all). What the page shows is noted first, then the usual share sheet.
 */
fun sendLoginReport(context: android.content.Context) {
    MyspotiRuntime.host.noteLoginPage {
        val file = runCatching { DiagnosticReport.export(context) }.getOrNull()
        if (file != null) shareDiagnosticReport(context, file)
        else android.widget.Toast.makeText(context, context.getString(R.string.diagnostic_export_failed), android.widget.Toast.LENGTH_SHORT).show()
    }
}

/**
 * "Help" over Spotify's sign-in page. The page is a WebView above the app's screens, so this is a
 * popup (a window of its own, above it), small, in the top corner Spotify's page leaves empty, and
 * not focusable: the keyboard and the form keep working under it.
 */
@Composable
fun LoginHelpButton() {
    var open by remember { mutableStateOf(false) }
    val top = WindowInsets.statusBars.getTop(LocalDensity.current)
    val gap = with(LocalDensity.current) { 12.dp.roundToPx() }
    Popup(alignment = Alignment.TopEnd, offset = IntOffset(-gap, top + gap)) {
        Text(
            stringResource(R.string.login_help_button),
            color = AppText, fontFamily = AppSans, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xCC2A2A2A))
                .clickable { open = true }.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
    if (open) LoginHelpDialog(onClose = { open = false })
}

@Composable
fun LoginHelpDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color(0xFF242424),
        title = { Text(stringResource(R.string.login_help_title), color = AppText) },
        text = { Text(stringResource(R.string.login_help_detail), color = AppText.copy(alpha = 0.7f)) },
        confirmButton = {
            TextButton(onClick = { copyLoginError(ctx) }) { Text(stringResource(R.string.login_copy_error), color = AppAccent) }
            TextButton(onClick = { onClose(); sendLoginReport(ctx) }) {
                Text(stringResource(R.string.login_help_send), color = AppAccent)
            }
        },
        dismissButton = {
            // A half-finished flow is what Spotify answers "Oops! Something went wrong" to: from nothing.
            TextButton(onClick = { onClose(); MyspotiRuntime.host.retryLogin() }) {
                Text(stringResource(R.string.login_help_restart), color = AppText)
            }
        },
    )
}

/** Under a wait that lasts (the loading screen after the sign-in), the same way to a report. */
fun copyLoginError(context: android.content.Context) {
    MyspotiRuntime.host.noteLoginPage {
        val webview = android.webkit.WebView.getCurrentWebViewPackage()?.versionName ?: "unknown"
        val tail = DiagnosticReport.recent(context).lineSequence().toList().takeLast(80).joinToString("\n")
        val report = buildString {
            append("Revera ").append(app.myspoti.engine.BuildConfig.VERSION_NAME)
                .append(" (").append(app.myspoti.engine.BuildConfig.VERSION_CODE).append(")\n")
            append("Android ").append(android.os.Build.VERSION.RELEASE)
                .append(" / SDK ").append(android.os.Build.VERSION.SDK_INT).append('\n')
            append("WebView ").append(webview)
                .append(" (multiProfile=").append(MyspotiRuntime.host.multiProfile).append(")\n")
            append(app.myspoti.host.android.LoginErrorDetails.summary())
            if (tail.isNotBlank()) {
                append("\n\n--- Recent Logcat ---\n")
                append(tail)
            }
        }
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Revera login error", report))
        android.widget.Toast.makeText(context, context.getString(R.string.login_error_copied), android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun LoginReportLink(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Text(
        stringResource(R.string.login_help_slow),
        color = AppText.copy(alpha = 0.6f), fontFamily = AppSans, fontSize = 14.sp,
        modifier = modifier.clip(RoundedCornerShape(50)).clickable { sendLoginReport(ctx) }.padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
