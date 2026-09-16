package com.dk.tvplayer.ui.library

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dk.tvplayer.data.local.LocalVideoItem
import com.dk.tvplayer.data.local.SortOption
import com.dk.tvplayer.data.local.TvChannelEntity
import com.dk.tvplayer.ui.TvPlayerViewModel
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun VideoLibraryScreen(
    viewModel: TvPlayerViewModel,
    onPlayVideo: (url: String, title: String, isLive: Boolean) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Local Videos", "IPTV Channels")
    val state by viewModel.uiState.collectAsState()

    // Local-only search + sort (separate from the global channel search/sort in TvUiState,
    // since local video scanning is a plain in-memory list local to this screen).
    var localSearchQuery by remember { mutableStateOf("") }
    var localSortOption by remember { mutableStateOf(SortOption.NAME_ASC) }

    LaunchedEffect(Unit) {
        viewModel.refreshLocalVideos()
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = localSearchQuery,
                    onValueChange = { localSearchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search local videos...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                SortMenuButton(current = localSortOption, onSelected = { localSortOption = it })
            }

            val filteredVideos = remember(state.localVideos, localSearchQuery, localSortOption) {
                val filtered = if (localSearchQuery.isBlank()) {
                    state.localVideos
                } else {
                    state.localVideos.filter { it.name.contains(localSearchQuery, ignoreCase = true) }
                }
                when (localSortOption) {
                    SortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
                    SortOption.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
                    SortOption.RECENTLY_ADDED -> filtered.asReversed()
                    SortOption.FAVORITES_FIRST -> filtered // local videos have no favorites concept
                }
            }

            if (filteredVideos.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (state.localVideos.isEmpty()) "No local video files found" else "No videos match your search",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (state.localVideos.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ensure video permissions are granted in settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    val showHeaders = state.appSettings.showListHeaders &&
                        (localSortOption == SortOption.NAME_ASC || localSortOption == SortOption.NAME_DESC)
                    if (showHeaders) {
                        val grouped = filteredVideos.groupBy { video ->
                            val c = video.name.firstOrNull()?.uppercaseChar()
                            if (c != null && c.isLetter()) c.toString() else "#"
                        }
                        grouped.forEach { (header, groupItems) ->
                            item(span = { GridItemSpan(maxLineSpan) }, key = "header_$header") {
                                Text(
                                    text = header,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            gridItems(groupItems, key = { it.filePath }) { video ->
                                LocalVideoCard(
                                    video = video,
                                    showThumbnail = state.appSettings.videoThumbnailsEnabled,
                                    onClick = { onPlayVideo(video.filePath, video.name, false) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    } else {
                        gridItems(filteredVideos, key = { it.filePath }) { video ->
                            LocalVideoCard(
                                video = video,
                                showThumbnail = state.appSettings.videoThumbnailsEnabled,
                                onClick = { onPlayVideo(video.filePath, video.name, false) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        } else {
            var showLoadPlaylistDialog by remember { mutableStateOf(false) }

            if (showLoadPlaylistDialog) {
                LoadPlaylistDialog(
                    viewModel = viewModel,
                    onDismiss = { showLoadPlaylistDialog = false }
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Search bar + sort
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search channels, groups...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { showLoadPlaylistDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Load IPTV playlist from URL")
                    }
                    SortMenuButton(current = state.sortOption, onSelected = { viewModel.setSortOption(it) })
                }

                // Category + Favorites filter row
                var showCategoryPicker by remember { mutableStateOf(false) }

                if (showCategoryPicker) {
                    CategoryPickerDialog(
                        categories = state.categories,
                        selected = state.selectedCategory,
                        onSelect = {
                            viewModel.selectCategory(it)
                            showCategoryPicker = false
                        },
                        onDismiss = { showCategoryPicker = false }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.showFavoritesOnly,
                        onClick = { viewModel.toggleShowFavoritesOnly() },
                        label = { Text("Favorites") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (state.showFavoritesOnly) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null
                            )
                        }
                    )
                    // A plain chip row doesn't scale — a large combined IPTV playlist can
                    // easily have 100+ categories, most of which would be permanently
                    // off-screen and unreachable in a non-scrolling Row. A single button
                    // opening a searchable picker works regardless of category count.
                    FilterChip(
                        selected = state.selectedCategory != "All",
                        onClick = { showCategoryPicker = true },
                        label = { Text(if (state.selectedCategory == "All") "Category" else state.selectedCategory) }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (state.filteredChannels.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (state.channels.isEmpty()) {
                            Text(
                                text = "No IPTV playlist loaded yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap the + button above and paste an M3U playlist URL to load channels.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "No channels match your filters",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val showHeaders = state.appSettings.showListHeaders &&
                            (state.sortOption == SortOption.NAME_ASC || state.sortOption == SortOption.NAME_DESC)
                        if (showHeaders) {
                            val grouped = state.filteredChannels.groupBy { channel ->
                                val c = channel.name.firstOrNull()?.uppercaseChar()
                                if (c != null && c.isLetter()) c.toString() else "#"
                            }
                            grouped.forEach { (header, groupItems) ->
                                item(span = { GridItemSpan(maxLineSpan) }, key = "header_$header") {
                                    Text(
                                        text = header,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                gridItems(groupItems, key = { it.id }) { channel ->
                                    IptvChannelCard(
                                        channel = channel,
                                        isFavorite = state.favoriteChannelIds.contains(channel.channelId),
                                        onToggleFavorite = { viewModel.toggleFavorite(channel.channelId) },
                                        onClick = {
                                            viewModel.selectChannel(channel)
                                            onPlayVideo(channel.streamUrl, channel.name, true)
                                        },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        } else {
                            gridItems(state.filteredChannels, key = { it.id }) { channel ->
                                IptvChannelCard(
                                    channel = channel,
                                    isFavorite = state.favoriteChannelIds.contains(channel.channelId),
                                    onToggleFavorite = { viewModel.toggleFavorite(channel.channelId) },
                                    modifier = Modifier.animateItem(),
                                    onClick = {
                                        viewModel.selectChannel(channel)
                                        onPlayVideo(channel.streamUrl, channel.name, true)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryPickerDialog(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(categories, query) {
        if (query.isBlank()) {
            categories
        } else {
            categories.filter { it.contains(query, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Category") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search categories...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                // LazyColumn keeps this responsive even with hundreds of categories,
                // unlike composing every chip into a plain (non-virtualized) Row.
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.height(360.dp)
                ) {
                    items(filtered, key = { it }) { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(category) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (category == selected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Spacer(modifier = Modifier.width(32.dp))
                            }
                            Text(category, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun LoadPlaylistDialog(viewModel: TvPlayerViewModel, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Load IPTV Playlist") },
        text = {
            Column {
                Text(
                    "Paste a direct M3U playlist URL. This replaces the current IPTV " +
                        "Channels list with the channels found in it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; errorMessage = null },
                    placeholder = { Text("https://example.com/playlist.m3u") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isLoading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Couldn't load playlist: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    viewModel.importM3uFromUrl(url.trim()) { success, error ->
                        isLoading = false
                        if (success) {
                            onDismiss()
                        } else {
                            errorMessage = error ?: "Unknown error"
                        }
                    }
                },
                enabled = url.isNotBlank() && !isLoading
            ) { Text("Load") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancel") }
        }
    )
}

@Composable
fun SortMenuButton(current: SortOption, onSelected: (SortOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.SortByAlpha, contentDescription = "Sort")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = {
                        if (option == current) Icon(Icons.Default.Check, contentDescription = null)
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun LocalVideoCard(video: LocalVideoItem, showThumbnail: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (showThumbnail) {
                val thumbnail by produceState<android.graphics.Bitmap?>(initialValue = null, video.filePath) {
                    value = com.dk.tvplayer.util.VideoThumbnailLoader.getThumbnail(video.filePath)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = thumbnail
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = video.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDuration(video.duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
fun IptvChannelCard(
    channel: TvChannelEntity,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(36.dp)
                )
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = channel.groupTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
