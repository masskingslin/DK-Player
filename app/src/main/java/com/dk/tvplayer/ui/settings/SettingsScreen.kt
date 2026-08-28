package com.dk.tvplayer.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dk.tvplayer.ui.TvPlayerViewModel
import com.dk.tvplayer.ui.TvUiState
import com.dk.tvplayer.ui.components.SleepTimerDialog
import com.dk.tvplayer.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    viewModel: TvPlayerViewModel,
    uiState: TvUiState,
    onOpenHistoryAndStreams: () -> Unit
) {
    val context = LocalContext.current
    var showSleepDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.exportSettings(it) { success ->
                Toast.makeText(
                    context,
                    if (success) "Settings & streams exported!" else "Export failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importSettings(it) { success ->
                Toast.makeText(
                    context,
                    if (success) "Settings & streams restored!" else "Import failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Theme Customization", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                ThemeMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setThemeMode(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = uiState.themeMode == mode, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(mode.name.lowercase().capitalize())
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Utilities & Timer", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenHistoryAndStreams)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("History & Custom Streams", style = MaterialTheme.typography.titleMedium)
                    Text("Recently watched items and saved direct stream links", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSleepDialog = true }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Timer, contentDescription = null)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Sleep Timer", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (uiState.remainingSleepSeconds != null)
                            "Stops playback in ${uiState.remainingSleepSeconds / 60} min"
                        else "Inactive",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Backup & Restore", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { exportLauncher.launch("dk_player_backup.json") }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Backup, contentDescription = null)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Export Configuration", style = MaterialTheme.typography.titleMedium)
                    Text("Backup custom streams, playlists & preferences", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { importLauncher.launch(arrayOf("application/json")) }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Import Configuration", style = MaterialTheme.typography.titleMedium)
                    Text("Restore saved backup file from storage", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showSleepDialog) {
        SleepTimerDialog(
            currentMinutes = uiState.remainingSleepSeconds,
            onSetTimer = { viewModel.setSleepTimer(it) },
            onCancelTimer = { viewModel.setSleepTimer(0) },
            onDismiss = { showSleepDialog = false }
        )
    }
}
