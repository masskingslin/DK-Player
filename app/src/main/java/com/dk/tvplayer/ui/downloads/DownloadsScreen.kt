package com.dk.tvplayer.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import com.dk.tvplayer.DkPlayerApplication
import com.dk.tvplayer.download.DownloadItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val downloadTracker = remember {
        (context.applicationContext as DkPlayerApplication).downloadManagerHolder.downloadTracker
    }
    val downloads by downloadTracker.downloads.collectAsState()
    val items = downloads.values.sortedBy { it.title.lowercase() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No downloads yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Download items from a playlist to watch them offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.url }) { download ->
                    DownloadRow(
                        download = download,
                        onDelete = { downloadTracker.removeDownload(download.url) },
                        onRetry = { downloadTracker.startDownload(download.url, download.title) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(download: DownloadItem, onDelete: () -> Unit, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when (download.state) {
                    Download.STATE_COMPLETED -> Icons.Default.DownloadDone to MaterialTheme.colorScheme.primary
                    Download.STATE_FAILED -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
                    else -> Icons.Default.Downloading to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(icon, contentDescription = null, tint = tint)
                Text(
                    download.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
                if (download.state == Download.STATE_FAILED) {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Default.Downloading, contentDescription = "Retry")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete download", tint = MaterialTheme.colorScheme.error)
                }
            }
            if (download.state == Download.STATE_DOWNLOADING) {
                LinearProgressIndicator(
                    progress = (download.percentDownloaded / 100f).coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
    }
}
