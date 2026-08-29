package com.dk.tvplayer.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dk.tvplayer.data.local.HistoryEntity
import com.dk.tvplayer.data.local.StreamEntity
import com.dk.tvplayer.ui.TvPlayerViewModel
import com.dk.tvplayer.ui.TvUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryAndStreamsScreen(
    viewModel: TvPlayerViewModel,
    uiState: TvUiState,
    onBack: () -> Unit,
    onPlayMedia: (url: String, title: String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    val tabs = listOf("History", "Custom Streams")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History & Custom Streams") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 1) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Stream")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            if (selectedTab == 0) {
                HistoryList(
                    history = uiState.history,
                    onPlay = { item -> onPlayMedia(item.mediaUrl, item.title) }
                )
            } else {
                CustomStreamsList(
                    streams = uiState.customStreams,
                    onPlay = { stream -> onPlayMedia(stream.streamUrl, stream.name) },
                    onDelete = { stream -> viewModel.deleteCustomStream(stream) }
                )
            }
        }

        if (showAddDialog) {
            NewStreamDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, url ->
                    viewModel.addCustomStream(name, url)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
private fun HistoryList(history: List<HistoryEntity>, onPlay: (HistoryEntity) -> Unit) {
    if (history.isEmpty()) {
        EmptyState("No playback history yet.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(history, key = { it.id }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onPlay(item) },
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            item.mediaUrl,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
            }
        }
    }
}

@Composable
private fun CustomStreamsList(
    streams: List<StreamEntity>,
    onPlay: (StreamEntity) -> Unit,
    onDelete: (StreamEntity) -> Unit
) {
    if (streams.isEmpty()) {
        EmptyState("No custom streams saved.\nTap + to add a direct stream link.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(streams, key = { it.id }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onPlay(item) },
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            item.streamUrl,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}