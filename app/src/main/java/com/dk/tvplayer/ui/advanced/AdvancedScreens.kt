package com.dk.tvplayer.ui.advanced

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.dk.tvplayer.data.local.TvDatabase
import com.dk.tvplayer.ui.TvPlayerViewModel
import com.dk.tvplayer.util.AdvancedPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk.tvplayer.util.DebugLogger
import com.dk.tvplayer.util.ShareFileUtils
import kotlin.concurrent.thread

/**
 * Advanced settings, laid out like VLC's: network options, application data actions,
 * performance, and developer tools. Options that only make sense for VLC's own engine
 * (deblocking, frame skip, dav1d threads, OpenGL, SMB) are intentionally not here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedScreen(
    viewModel: TvPlayerViewModel,
    onBack: () -> Unit,
    onOpenDebugLogs: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val verbose by DebugLogger.verbose.collectAsState()
    val caching by AdvancedPrefs.networkCachingMs.collectAsState()
    val userAgent by AdvancedPrefs.httpUserAgent.collectAsState()
    val timeStretch by AdvancedPrefs.timeStretchAudio.collectAsState()

    var dialog by remember { mutableStateOf<AdvancedDialog?>(null) }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            val json = runCatching {
                context.contentResolver.openInputStream(it)?.use { stream -> stream.bufferedReader().readText() }
            }.getOrNull()
            if (json == null) {
                Toast.makeText(context, "Couldn't read that file", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.importSettingsBackup(json) { success ->
                    Toast.makeText(
                        context,
                        if (success) "Settings restored" else "Restore failed — invalid backup file",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            PrefRow(
                title = "Network caching value",
                subtitle = "The amount of time to buffer network media (in ms). " +
                    "Set to 0 for the default. Applies after restarting the app.\n" +
                    if (caching == 0) "Not set" else "$caching ms",
                onClick = { dialog = AdvancedDialog.Caching }
            )
            PrefRow(
                title = "HTTP user agent",
                subtitle = (if (userAgent.isBlank()) "Not set" else userAgent) + "\nApplies after restarting the app.",
                onClick = { dialog = AdvancedDialog.UserAgent }
            )
            PrefRow(
                title = "Quit and restart application",
                onClick = { restartApp(context) }
            )

            SectionDivider("Application data")
            PrefRow(
                title = "Dump app database",
                subtitle = "Share a copy of the app database file",
                onClick = { dumpDatabase(context, scope) }
            )
            PrefRow(
                title = "Clear media database",
                subtitle = "Forgets played marks and video groups to start over",
                onClick = { dialog = AdvancedDialog.ClearMedia }
            )
            PrefRow(
                title = "Clear app data",
                subtitle = "Clears all DK Player data, like a fresh install",
                onClick = { dialog = AdvancedDialog.ClearAppData }
            )
            PrefRow(
                title = "Clear playback history",
                onClick = { dialog = AdvancedDialog.ClearHistory }
            )
            PrefRow(
                title = "Export settings",
                subtitle = "Export your settings, streams and playlists to a file to import them later",
                onClick = {
                    scope.launch {
                        val json = viewModel.exportSettingsBackup()
                        ShareFileUtils.shareTextFile(context, "dk_player_backup.json", "application/json", json)
                    }
                }
            )
            PrefRow(
                title = "Restore settings",
                subtitle = "Restore your settings from a previous export",
                onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }
            )

            SectionDivider("Performance")
            PrefRow(
                title = "Time-stretching audio",
                subtitle = "Speed up and slow down audio without changing the pitch",
                checked = timeStretch,
                onClick = {
                    AdvancedPrefs.setTimeStretchAudio(!timeStretch)
                    // Re-apply the current speed so the change is heard immediately.
                    viewModel.playerManager.setPlaybackSpeed(viewModel.playerManager.playbackSpeedFlow.value)
                }
            )

            SectionDivider("Developer")
            PrefRow(
                title = "Verbose",
                subtitle = "Increase the verbosity (logcat)",
                checked = verbose,
                onClick = { DebugLogger.setVerbose(!verbose) }
            )
            PrefRow(title = "Debug logs", onClick = onOpenDebugLogs)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    when (dialog) {
        AdvancedDialog.Caching -> TextInputDialog(
            title = "Network caching value",
            label = "Milliseconds (0 = default)",
            initial = if (caching == 0) "" else caching.toString(),
            numeric = true,
            onDismiss = { dialog = null },
            onConfirm = { text ->
                AdvancedPrefs.setNetworkCachingMs(text.toIntOrNull() ?: 0)
                dialog = null
                Toast.makeText(context, "Restart the app to apply", Toast.LENGTH_SHORT).show()
            }
        )
        AdvancedDialog.UserAgent -> TextInputDialog(
            title = "HTTP user agent",
            label = "User agent (blank = default)",
            initial = userAgent,
            numeric = false,
            onDismiss = { dialog = null },
            onConfirm = { text ->
                AdvancedPrefs.setHttpUserAgent(text)
                dialog = null
                Toast.makeText(context, "Restart the app to apply", Toast.LENGTH_SHORT).show()
            }
        )
        AdvancedDialog.ClearMedia -> ConfirmDialog(
            title = "Clear media database?",
            message = "Played marks and the video groups you made will be forgotten. Your video files are not touched.",
            confirmLabel = "Clear",
            onDismiss = { dialog = null },
            onConfirm = {
                viewModel.clearLocalVideoData()
                dialog = null
                Toast.makeText(context, "Media database cleared", Toast.LENGTH_SHORT).show()
            }
        )
        AdvancedDialog.ClearHistory -> ConfirmDialog(
            title = "Clear playback history?",
            message = "Your watch history and resume positions will be removed.",
            confirmLabel = "Clear",
            onDismiss = { dialog = null },
            onConfirm = {
                viewModel.clearPlaybackHistory()
                dialog = null
                Toast.makeText(context, "Playback history cleared", Toast.LENGTH_SHORT).show()
            }
        )
        AdvancedDialog.ClearAppData -> ConfirmDialog(
            title = "Clear all app data?",
            message = "This deletes everything DK Player stores — settings, playlists, history, downloads — " +
                "and closes the app. This can't be undone.",
            confirmLabel = "Clear everything",
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                if (!am.clearApplicationUserData()) {
                    Toast.makeText(context, "Couldn't clear app data", Toast.LENGTH_SHORT).show()
                }
            }
        )
        null -> Unit
    }
}

private enum class AdvancedDialog { Caching, UserAgent, ClearMedia, ClearHistory, ClearAppData }

/** One settings row: title, optional subtitle, and an optional checkbox on the right. */
@Composable
private fun PrefRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (checked != null) Checkbox(checked = checked, onCheckedChange = { onClick() })
    }
}

@Composable
private fun SectionDivider(label: String) {
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    numeric: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = if (numeric) it.filter(Char::isDigit).take(5) else it.take(300) },
                label = { Text(label) },
                singleLine = numeric,
                keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text)
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Relaunches the app's launcher activity, then ends this process. */
private fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(launch)
    Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 300)
}

/** Copies the Room database (after flushing its write-ahead log) and opens the share sheet. */
private fun dumpDatabase(context: Context, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val db = TvDatabase.getDatabase(context)
                db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
                val source = context.getDatabasePath("dk_tvplayer_database.db")
                val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                File(dir, "dk_tvplayer_database.db").also { source.copyTo(it, overwrite = true) }
            }.getOrNull()
        }
        if (file == null) {
            Toast.makeText(context, "Couldn't copy the database", Toast.LENGTH_SHORT).show()
        } else {
            ShareFileUtils.shareFile(context, file, "application/octet-stream")
        }
    }
}

/** Start / stop a live logcat capture, copy it, dump it to a shareable file, or clear it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val logging by DebugLogger.logging.collectAsState()
    val lines by DebugLogger.lines.collectAsState()
    val listState = rememberLazyListState()

    // Follow the newest line while a capture is running.
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { DebugLogger.start() }, enabled = !logging, modifier = Modifier.weight(1f)) {
                    Text("Start logging")
                }
                Button(onClick = { DebugLogger.stop() }, enabled = logging, modifier = Modifier.weight(1f)) {
                    Text("Stop logging")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Button(
                    onClick = { copyLog(context) },
                    enabled = lines.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("Copy to clipboard") }
                Button(onClick = { dumpAndShare(context) }, modifier = Modifier.weight(1f)) {
                    Text("Dump logcat log")
                }
            }
            Button(
                onClick = { DebugLogger.clear() },
                enabled = lines.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Clear log") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            if (lines.isEmpty()) {
                Text(
                    if (logging) "Waiting for log lines…" else "Tap “Start logging”, reproduce the problem, then copy or dump the log.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                    items(lines) { line ->
                        Text(
                            line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

private fun copyLog(context: Context) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("DK Player log", DebugLogger.text()))
    Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
}

/** Reads the app's current logcat (off the main thread) and hands it to the share sheet. */
private fun dumpAndShare(context: Context) {
    thread {
        val captured = DebugLogger.text()
        val text = if (captured.isNotBlank()) captured else DebugLogger.dumpLogcat()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            if (text.isBlank()) {
                Toast.makeText(context, "No log lines available", Toast.LENGTH_SHORT).show()
            } else {
                ShareFileUtils.shareTextFile(context, "dk-player-logcat.txt", "text/plain", text)
            }
        }
    }
}
