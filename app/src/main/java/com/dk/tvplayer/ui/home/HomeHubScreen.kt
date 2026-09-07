package com.dk.tvplayer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dk.tvplayer.data.local.HistoryEntity
import com.dk.tvplayer.data.local.StreamEntity
import com.dk.tvplayer.data.local.TvChannelEntity
import com.dk.tvplayer.ui.TvPlayerViewModel

/**
 * The app's primary landing tab: continue watching, quick actions, favorite channels,
 * and saved streams. Previously this content was tucked away behind Settings as a
 * "History & Saved Streams" detail screen — it's the natural home screen instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeHubScreen(
    viewModel: TvPlayerViewModel,
    onPlayMedia: (url: String, title: String) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenPlaylists: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        NewStreamDialog(
            onDismiss = { showDialog = false },
            onConfirm = { name, url ->
                viewModel.addCustomStream(name, url)
                showDialog = false
            }
        )
    }

    val favoriteChannels = remember(state.channels, state.favoriteChannelIds) {
        state.channels.filter { state.favoriteChannelIds.contains(it.channelId) }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Stream") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item {
                Text(
                    text = "DK-Player",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            item {
                QuickActionsRow(
                    onOpenDownloads = onOpenDownloads,
                    onOpenPlaylists = onOpenPlaylists,
                    onNewStream = { showDialog = true }
                )
            }

            if (state.history.isNotEmpty()) {
                item {
                    SectionHeader("Continue Watching")
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.history, key = { it.mediaUrl }) { historyItem ->
                            HistoryCard(
                                history = historyItem,
                                onClick = { onPlayMedia(historyItem.mediaUrl, historyItem.title) }
                            )
                        }
                    }
                }
            }

            if (favoriteChannels.isNotEmpty()) {
                item {
                    SectionHeader("Favorite Channels")
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(favoriteChannels, key = { it.channelId }) { channel ->
                            FavoriteChannelCard(
                                channel = channel,
                                onClick = {
                                    viewModel.selectChannel(channel)
                                    onPlayMedia(channel.streamUrl, channel.name)
                                }
                            )
                        }
                    }
                }
            }

            item { SectionHeader("Saved Streams & IPTV") }

            if (state.customStreams.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "No custom streams saved yet.",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Tap '+ New Stream' to add direct HLS, MP4 or RTMP streams.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(state.customStreams, key = { it.id }) { stream ->
                    StreamRowItem(
                        stream = stream,
                        onPlay = { onPlayMedia(stream.streamUrl, stream.name) },
                        onDelete = { viewModel.deleteCustomStream(stream) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(text = title, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun QuickActionsRow(
    onOpenDownloads: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onNewStream: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        QuickActionCard(
            icon = Icons.Default.PlaylistPlay,
            label = "Playlists",
            onClick = onOpenPlaylists,
            modifier = Modifier.weight(1f)
        )
        QuickActionCard(
            icon = Icons.Default.Download,
            label = "Downloads",
            onClick = onOpenDownloads,
            modifier = Modifier.weight(1f)
        )
        QuickActionCard(
            icon = Icons.Default.Add,
            label = "New Stream",
            onClick = onNewStream,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun HistoryCard(history: HistoryEntity, onClick: () -> Unit) {
    val progress = if (history.durationMs > 0) {
        (history.lastPositionMs.toFloat() / history.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Card(
        modifier = Modifier
            .size(width = 210.dp, height = 122.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = history.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Column {
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text(
                    text = history.mediaUrl,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FavoriteChannelCard(channel: TvChannelEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .size(width = 150.dp, height = 90.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = channel.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@Composable
fun StreamRowItem(
    stream: StreamEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stream.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stream.streamUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Stream",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
