package com.music.spotui.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppText
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Portable backup of every SharedPreferences file used by the app. */
@Composable
internal fun VnSettingsBackupRow() {
    val context = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { writeBackup(context, uri) }
            .onFailure { android.widget.Toast.makeText(context, "Settings export failed", android.widget.Toast.LENGTH_LONG).show() }
            .onSuccess { android.widget.Toast.makeText(context, "Settings exported", android.widget.Toast.LENGTH_SHORT).show() }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { readBackup(context, uri) }
            .onFailure { android.widget.Toast.makeText(context, "Invalid backup or import failed", android.widget.Toast.LENGTH_LONG).show() }
            .onSuccess { android.widget.Toast.makeText(context, "Settings imported. Restart VNEra to apply all changes.", android.widget.Toast.LENGTH_LONG).show() }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Import / Export settings", color = AppText, fontSize = 16.sp)
        Text("Save all app SharedPreferences to a JSON backup or restore them later.", color = AppMuted, fontSize = 12.sp)
        Text("Export settings", color = AppText, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().clickable {
                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                export.launch("VNEra-settings-$stamp.json")
            }.padding(vertical = 10.dp))
        Text("Import settings", color = AppText, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().clickable { import.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }.padding(vertical = 10.dp))
    }
}

private fun writeBackup(context: Context, uri: Uri) {
    val dir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
    val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".xml") }.orEmpty()
    val root = JSONObject().put("format", "vnera-settings").put("version", 1)
    val prefs = JSONObject()
    files.forEach { file ->
        val name = file.name.removeSuffix(".xml")
        val all = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
        val values = JSONObject()
        all.forEach { (key, value) ->
            when (value) {
                is String -> values.put(key, JSONObject().put("type", "string").put("value", value))
                is Boolean -> values.put(key, JSONObject().put("type", "boolean").put("value", value))
                is Int -> values.put(key, JSONObject().put("type", "int").put("value", value))
                is Long -> values.put(key, JSONObject().put("type", "long").put("value", value))
                is Float -> values.put(key, JSONObject().put("type", "float").put("value", value.toDouble()))
                is Set<*> -> values.put(key, JSONObject().put("type", "stringSet").put("value", JSONArray(value.filterIsInstance<String>())))
            }
        }
        prefs.put(name, values)
    }
    root.put("preferences", prefs)
    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(root.toString(2)) }
        ?: error("Cannot open destination")
}

private fun readBackup(context: Context, uri: Uri) {
    val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        ?: error("Cannot read backup")
    val root = JSONObject(raw)
    require(root.optString("format") == "vnera-settings" && root.optInt("version") == 1) { "Unsupported backup" }
    val prefs = root.getJSONObject("preferences")
    val names = prefs.keys()
    while (names.hasNext()) {
        val name = names.next()
        val values = prefs.getJSONObject(name)
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next(); val item = values.getJSONObject(key); val value = item.get("value")
            when (item.getString("type")) {
                "string" -> editor.putString(key, value.toString())
                "boolean" -> editor.putBoolean(key, value as Boolean)
                "int" -> editor.putInt(key, (value as Number).toInt())
                "long" -> editor.putLong(key, (value as Number).toLong())
                "float" -> editor.putFloat(key, (value as Number).toFloat())
                "stringSet" -> editor.putStringSet(key, (value as JSONArray).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
            }
        }
        check(editor.commit()) { "Could not save $name" }
    }
}
