package com.dk.tvplayer.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dk.tvplayer.data.local.LocalVideoItem
import com.dk.tvplayer.data.local.LocalVideoDisplayItem
import com.dk.tvplayer.data.local.LocalVideoGrouping
import com.dk.tvplayer.data.local.PlaylistEntity
import com.dk.tvplayer.data.local.SortOption
import com.dk.tvplayer.data.local.TvChannelEntity
import com.dk.tvplayer.data.local.VideoGroupEntity
import com.dk.tvplayer.ui.TvPlayerViewModel
import com.dk.tvplayer.util.DeleteVideoResult
import com.dk.tvplayer.util.LocalVideoDeleter
import com.dk.tvplayer.util.ShareFileUtils
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
    val context = LocalContext.current

    // Long-press context menu state, shared across both tabs. Centralized here (rather
    // than per-card) so there's exactly one menu/dialog stack active at a time.
    var menuVideo by remember { mutableStateOf<LocalVideoItem?>(null) }
    var menuChannel by remember { mutableStateOf<TvChannelEntity?>(null) }
    var infoTarget by remember { mutableStateOf<MediaInfoTarget?>(null) }
    var addToPlaylistTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // title, url
    var addGroupToPlaylistTarget by remember { mutableStateOf<LocalVideoDisplayItem.Group?>(null) }
    var deleteVideoTarget by remember { mutableStateOf<LocalVideoItem?>(null) }
    var deleteChannelTarget by remember { mutableStateOf<TvChannelEntity?>(null) }
    var pendingDeleteVideo by remember { mutableStateOf<LocalVideoItem?>(null) }

    // Video grouping state (VLC-style "video groups")
    var groupPlaybackTarget by remember { mutableStateOf<LocalVideoDisplayItem.Group?>(null) }
    var menuGroup by remember { mutableStateOf<LocalVideoDisplayItem.Group?>(null) }
    var renameGroupTarget by remember { mutableStateOf<LocalVideoDisplayItem.Group?>(null) }
    var ungroupTarget by remember { mutableStateOf<LocalVideoDisplayItem.Group?>(null) }
    var addToGroupTarget by remember { mutableStateOf<LocalVideoItem?>(null) }

    // Android 10+ requires explicit user consent (via this launcher) to delete media
    // the app didn't create itself — see LocalVideoDeleter.
    val deletePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val video = pendingDeleteVideo
        pendingDeleteVideo = null
        if (result.resultCode == android.app.Activity.RESULT_OK && video != null) {
            LocalVideoDeleter.delete(context, video)
            viewModel.refreshLocalVideos()
        }
    }

    fun requestDeleteVideo(video: LocalVideoItem) {
        when (val result = LocalVideoDeleter.delete(context, video)) {
            is DeleteVideoResult.Success -> viewModel.refreshLocalVideos()
            is DeleteVideoResult.NeedsPermission -> {
                pendingDeleteVideo = video
                deletePermissionLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build())
            }
            is DeleteVideoResult.Failure -> { /* surfaced nowhere yet; menu already closed */ }
        }
    }

    if (menuVideo != null) {
        val video = menuVideo!!
        val isPlayed = state.localVideoMeta.firstOrNull { it.filePath == video.filePath }?.isPlayed == true
        MediaActionsMenu(
            onDismiss = { menuVideo = null },
            onAddToPlaylist = { addToPlaylistTarget = video.name to video.filePath; menuVideo = null },
            onShare = { ShareFileUtils.shareText(context, video.filePath, "Share video"); menuVideo = null },
            onInfo = {
                infoTarget = MediaInfoTarget(
                    title = video.name,
                    lines = listOf(
                        "Path" to video.filePath,
                        "Duration" to formatDuration(video.duration),
                        "Size" to formatFileSize(video.size)
                    )
                )
                menuVideo = null
            },
            onDelete = { deleteVideoTarget = video; menuVideo = null },
            favoriteState = null,
            playedState = isPlayed to { viewModel.setVideoPlayed(video, !isPlayed) },
            onAddToVideoGroup = { addToGroupTarget = video; menuVideo = null }
        )
    }

    if (menuChannel != null) {
        val channel = menuChannel!!
        val isFavorite = state.favoriteChannelIds.contains(channel.channelId)
        MediaActionsMenu(
            onDismiss = { menuChannel = null },
            onAddToPlaylist = { addToPlaylistTarget = channel.name to channel.streamUrl; menuChannel = null },
            onShare = { ShareFileUtils.shareText(context, channel.streamUrl, "Share channel"); menuChannel = null },
            onInfo = {
                infoTarget = MediaInfoTarget(
                    title = channel.name,
                    lines = listOf(
                        "Stream URL" to channel.streamUrl,
                        "Group" to channel.groupTitle,
                        "User-Agent" to (channel.userAgent ?: "Default"),
                        "Referer" to (channel.referrer ?: "None")
                    )
                )
                menuChannel = null
            },
            onDelete = { deleteChannelTarget = channel; menuChannel = null },
            favoriteState = isFavorite to { viewModel.toggleFavorite(channel.channelId) },
            playedState = null,
            onAddToVideoGroup = null
        )
    }

    infoTarget?.let { target ->
        MediaInfoDialog(target = target, onDismiss = { infoTarget = null })
    }

    addToPlaylistTarget?.let { (title, url) ->
        AddToPlaylistDialog(
            playlists = state.playlists,
            onDismiss = { addToPlaylistTarget = null },
            onPick = { playlist ->
                viewModel.addCustomItemToPlaylist(playlist.id, title, url)
                addToPlaylistTarget = null
            },
            onCreateNew = { name ->
                viewModel.createPlaylistAndAddItem(name, title, url)
                addToPlaylistTarget = null
            }
        )
    }

    addGroupToPlaylistTarget?.let { group ->
        AddToPlaylistDialog(
            playlists = state.playlists,
            onDismiss = { addGroupToPlaylistTarget = null },
            onPick = { playlist ->
                group.videos.forEach { viewModel.addCustomItemToPlaylist(playlist.id, it.name, it.filePath) }
                addGroupToPlaylistTarget = null
            },
            onCreateNew = { name ->
                viewModel.createPlaylistAndAddItems(name, group.videos.map { it.name to it.filePath })
                addGroupToPlaylistTarget = null
            }
        )
    }

    deleteVideoTarget?.let { video ->
        ConfirmDeleteDialog(
            itemName = video.name,
            message = "This permanently deletes the file from your device. This can't be undone.",
            onConfirm = { requestDeleteVideo(video); deleteVideoTarget = null },
            onDismiss = { deleteVideoTarget = null }
        )
    }

    deleteChannelTarget?.let { channel ->
        ConfirmDeleteDialog(
            itemName = channel.name,
            message = "Removes this channel from your IPTV Channels list. You can re-add it by importing the playlist again.",
            onConfirm = { viewModel.deleteChannel(channel); deleteChannelTarget = null },
            onDismiss = { deleteChannelTarget = null }
        )
    }

    // ---- Video group dialogs ----

    if (menuGroup != null) {
        val group = menuGroup!!
        VideoGroupActionsMenu(
            group = group,
            onDismiss = { menuGroup = null },
            onPlayAll = {
                group.videos.firstOrNull()?.let { onPlayVideo(it.filePath, it.name, false) }
                menuGroup = null
            },
            onAddToPlaylist = {
                // A whole group means every member gets added, which is a different
                // shape from the single (title, url) the standalone AddToPlaylistDialog
                // flow expects — routed through its own state instead of overloading
                // addToPlaylistTarget with a list.
                addGroupToPlaylistTarget = group
                menuGroup = null
            },
            onRename = { renameGroupTarget = group; menuGroup = null },
            onUngroup = { ungroupTarget = group; menuGroup = null },
            onMarkAllPlayed = { viewModel.setVideosPlayed(group.videos, true); menuGroup = null },
            onMarkAllNotPlayed = { viewModel.setVideosPlayed(group.videos, false); menuGroup = null }
        )
    }

    renameGroupTarget?.let { group ->
        RenameGroupDialog(
            currentName = group.displayName,
            onDismiss = { renameGroupTarget = null },
            onConfirm = { newName ->
                if (group.group != null) {
                    viewModel.renameVideoGroup(group.group.id, newName)
                } else {
                    // Not persisted yet (auto-suggested) — creating it now with the
                    // chosen name is what "renaming" an unconfirmed group means.
                    viewModel.createVideoGroup(newName, group.videos)
                }
                renameGroupTarget = null
            }
        )
    }

    ungroupTarget?.let { group ->
        ConfirmDeleteDialog(
            itemName = group.displayName,
            message = "Splits this group back into ${group.videos.size} separate videos. The files themselves are not affected.",
            onConfirm = {
                group.group?.let { viewModel.ungroupVideos(it.id) }
                ungroupTarget = null
            },
            onDismiss = { ungroupTarget = null }
        )
    }

    addToGroupTarget?.let { video ->
        AddToVideoGroupDialog(
            groups = state.videoGroups,
            onDismiss = { addToGroupTarget = null },
            onPickExisting = { group -> viewModel.addVideosToGroup(group.id, listOf(video)); addToGroupTarget = null },
            onCreateNew = { name -> viewModel.createVideoGroup(name, listOf(video)); addToGroupTarget = null }
        )
    }

    groupPlaybackTarget?.let { group ->
        GroupMemberPickerDialog(
            group = group,
            playedPaths = state.localVideoMeta.filter { it.isPlayed }.map { it.filePath }.toSet(),
            onDismiss = { groupPlaybackTarget = null },
            onPick = { video ->
                onPlayVideo(video.filePath, video.name, false)
                groupPlaybackTarget = null
            }
        )
    }

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

            val displayItems = remember(state.localVideos, state.videoGroups, state.localVideoMeta) {
                LocalVideoGrouping.buildDisplayItems(state.localVideos, state.videoGroups, state.localVideoMeta)
            }

            val filteredItems = remember(displayItems, localSearchQuery, localSortOption) {
                fun label(item: LocalVideoDisplayItem) = when (item) {
                    is LocalVideoDisplayItem.Single -> item.video.name
                    is LocalVideoDisplayItem.Group -> item.displayName
                }
                val filtered = if (localSearchQuery.isBlank()) {
                    displayItems
                } else {
                    displayItems.filter { item ->
                        when (item) {
                            is LocalVideoDisplayItem.Single -> item.video.name.contains(localSearchQuery, ignoreCase = true)
                            is LocalVideoDisplayItem.Group ->
                                item.displayName.contains(localSearchQuery, ignoreCase = true) ||
                                    item.videos.any { it.name.contains(localSearchQuery, ignoreCase = true) }
                        }
                    }
                }
                when (localSortOption) {
                    SortOption.NAME_ASC -> filtered.sortedBy { label(it).lowercase() }
                    SortOption.NAME_DESC -> filtered.sortedByDescending { label(it).lowercase() }
                    SortOption.RECENTLY_ADDED -> filtered.asReversed()
                    SortOption.FAVORITES_FIRST -> filtered // local videos have no favorites concept
                }
            }

            if (filteredItems.isEmpty()) {
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

                    fun itemLabel(item: LocalVideoDisplayItem) = when (item) {
                        is LocalVideoDisplayItem.Single -> item.video.name
                        is LocalVideoDisplayItem.Group -> item.displayName
                    }

                    @Composable
                    fun renderItem(item: LocalVideoDisplayItem) {
                        when (item) {
                            is LocalVideoDisplayItem.Single -> LocalVideoCard(
                                video = item.video,
                                showThumbnail = state.appSettings.videoThumbnailsEnabled,
                                isPlayed = item.isPlayed,
                                onClick = { onPlayVideo(item.video.filePath, item.video.name, false) },
                                onLongClick = { menuVideo = item.video },
                                modifier = Modifier.animateItem()
                            )
                            is LocalVideoDisplayItem.Group -> VideoGroupCard(
                                group = item,
                                onClick = { groupPlaybackTarget = item },
                                onLongClick = { menuGroup = item },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    if (showHeaders) {
                        val grouped = filteredItems.groupBy { item ->
                            val c = itemLabel(item).firstOrNull()?.uppercaseChar()
                            if (c != null && c.isLetter()) c.toString() else "#"
                        }
                        grouped.forEach { (header, headerItems) ->
                            item(span = { GridItemSpan(maxLineSpan) }, key = "header_$header") {
                                Text(
                                    text = header,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            gridItems(headerItems, key = { it.key }) { item -> renderItem(item) }
                        }
                    } else {
                        gridItems(filteredItems, key = { it.key }) { item -> renderItem(item) }
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
                                        onLongClick = { menuChannel = channel },
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
                                    },
                                    onLongClick = { menuChannel = channel }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalVideoCard(
    video: LocalVideoItem,
    showThumbnail: Boolean,
    isPlayed: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
                    if (isPlayed) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Played",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .background(MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.CircleShape)
                                .padding(2.dp)
                                .size(14.dp)
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
                overflow = TextOverflow.Ellipsis,
                color = if (isPlayed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
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


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IptvChannelCard(
    channel: TvChannelEntity,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "Unknown"
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", size, units[unitIndex])
}

/** Simple label/value pairs shown in [MediaInfoDialog] — kept generic since a local
 *  video and an IPTV channel have quite different metadata worth surfacing. */
private data class MediaInfoTarget(val title: String, val lines: List<Pair<String, String>>)

/**
 * Long-press context menu shared by both local videos and IPTV channels (VLC-style).
 * [favoriteState] is null for local videos, which have no favorite concept yet; when
 * present it's (isFavorite, onToggle) and renders a Favorite/Unfavorite row.
 */
@Composable
private fun MediaActionsMenu(
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    favoriteState: Pair<Boolean, () -> Unit>?,
    playedState: Pair<Boolean, () -> Unit>? = null,
    onAddToVideoGroup: (() -> Unit)? = null
) {
    // A DropdownMenu needs an anchor composable to position itself against; since this
    // is triggered by a long-press anywhere on a grid card rather than a fixed icon,
    // it's anchored to an invisible zero-size Box instead, which centers it on screen
    // via DropdownMenu's own default positioning fallback.
    Box {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
            if (favoriteState != null) {
                val (isFavorite, onToggle) = favoriteState
                DropdownMenuItem(
                    text = { Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites") },
                    leadingIcon = {
                        Icon(if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = null)
                    },
                    onClick = { onToggle(); onDismiss() }
                )
            }
            if (playedState != null) {
                val (isPlayed, onToggle) = playedState
                DropdownMenuItem(
                    text = { Text(if (isPlayed) "Mark as Not Played" else "Mark as Played") },
                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                    onClick = { onToggle(); onDismiss() }
                )
            }
            if (onAddToVideoGroup != null) {
                DropdownMenuItem(
                    text = { Text("Add to Video Group") },
                    leadingIcon = { Icon(Icons.Default.Movie, contentDescription = null) },
                    onClick = onAddToVideoGroup
                )
            }
            DropdownMenuItem(
                text = { Text("Add to Playlist") },
                leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                onClick = onAddToPlaylist
            )
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                onClick = onShare
            )
            DropdownMenuItem(
                text = { Text("Information") },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                onClick = onInfo
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun MediaInfoDialog(target: MediaInfoTarget, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.title) },
        text = {
            Column {
                target.lines.forEach { (label, value) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun AddToPlaylistDialog(
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onPick: (PlaylistEntity) -> Unit,
    onCreateNew: (name: String) -> Unit
) {
    var showCreateNew by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    if (showCreateNew) {
        AlertDialog(
            onDismissRequest = { showCreateNew = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onCreateNew(newPlaylistName.ifBlank { "New Playlist" }) },
                    enabled = true
                ) { Text("Create & Add") }
            },
            dismissButton = { TextButton(onClick = { showCreateNew = false }) { Text("Cancel") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            Column {
                if (playlists.isEmpty()) {
                    Text(
                        "You don't have any playlists yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    playlists.forEach { playlist ->
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(playlist) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
                Text(
                    text = "+ New Playlist",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreateNew = true }
                        .padding(vertical = 12.dp)
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ConfirmDeleteDialog(
    itemName: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$itemName\"?") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoGroupCard(
    group: LocalVideoDisplayItem.Group,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "${group.videos.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = group.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${group.playedCount}/${group.videos.size} played",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VideoGroupActionsMenu(
    group: LocalVideoDisplayItem.Group,
    onDismiss: () -> Unit,
    onPlayAll: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRename: () -> Unit,
    onUngroup: () -> Unit,
    onMarkAllPlayed: () -> Unit,
    onMarkAllNotPlayed: () -> Unit
) {
    Box {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
            DropdownMenuItem(
                text = { Text("Play All") },
                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                onClick = onPlayAll
            )
            DropdownMenuItem(
                text = { Text("Add to Playlist") },
                leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                onClick = onAddToPlaylist
            )
            DropdownMenuItem(
                text = { Text("Rename Video Group") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = onRename
            )
            // Ungrouping an auto-suggested group (never persisted — group.group == null)
            // is a no-op by definition: there's nothing in the DB to dissolve, and the
            // grouping heuristic would just re-suggest it again on next recompute. Only
            // offer it for a real, persisted group.
            if (group.group != null) {
                DropdownMenuItem(
                    text = { Text("Ungroup") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = onUngroup
                )
            }
            DropdownMenuItem(
                text = { Text("Mark all as Played") },
                leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                onClick = onMarkAllPlayed
            )
            DropdownMenuItem(
                text = { Text("Mark all as Not Played") },
                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                onClick = onMarkAllNotPlayed
            )
        }
    }
}

@Composable
private fun RenameGroupDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Video Group") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.ifBlank { currentName }) }) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddToVideoGroupDialog(
    groups: List<VideoGroupEntity>,
    onDismiss: () -> Unit,
    onPickExisting: (VideoGroupEntity) -> Unit,
    onCreateNew: (name: String) -> Unit
) {
    var showCreateNew by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    if (showCreateNew) {
        AlertDialog(
            onDismissRequest = { showCreateNew = false },
            title = { Text("New Video Group") },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    placeholder = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { onCreateNew(newGroupName.ifBlank { "New Group" }) }) { Text("Create & Add") }
            },
            dismissButton = { TextButton(onClick = { showCreateNew = false }) { Text("Cancel") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Video Group") },
        text = {
            Column {
                if (groups.isEmpty()) {
                    Text(
                        "You don't have any video groups yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    groups.forEach { group ->
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPickExisting(group) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
                Text(
                    text = "+ New Group",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreateNew = true }
                        .padding(vertical = 12.dp)
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun GroupMemberPickerDialog(
    group: LocalVideoDisplayItem.Group,
    playedPaths: Set<String>,
    onDismiss: () -> Unit,
    onPick: (LocalVideoItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(group.displayName) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                group.videos.forEach { video ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(video) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (playedPaths.contains(video.filePath)) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Played",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Spacer(modifier = Modifier.width(26.dp))
                        }
                        Text(video.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
