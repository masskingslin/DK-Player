package com.dk.tvplayer.ui.playlists

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dk.tvplayer.data.local.PlaylistEntity
import com.dk.tvplayer.data.local.PlaylistItemEntity
import com.dk.tvplayer.ui.TvPlayerViewModel
import com.dk.tvplayer.util.ShareFileUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistEntity,
    viewModel: TvPlayerViewModel,
    onPlayItem: (url: String, title: String) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadTracker = remember {
        (context.applicationContext as com.dk.tvplayer.DkPlayerApplication).downloadManagerHolder.downloadTracker
    }
    val downloads by downloadTracker.downloads.collectAsState()

    var isSelectionMode by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Debounced the same way VideoLibraryScreen debounces the IPTV channel search —
    // a playlist imported from a large M3U (e.g. iptv-org's index.m3u) can hold
    // thousands of items, and re-filtering that on every keystroke feels laggy.
    LaunchedEffect(searchQuery) {
        kotlinx.coroutines.delay(250)
        debouncedQuery = searchQuery
    }

    val categories = remember(state.selectedPlaylistItems) {
        listOf("All") + state.selectedPlaylistItems.mapNotNull { it.groupTitle }.distinct().sorted()
    }

    val visibleItems = remember(state.selectedPlaylistItems, debouncedQuery, selectedCategory, showFavoritesOnly) {
        state.selectedPlaylistItems.filter { item ->
            val matchesQuery = debouncedQuery.isBlank() || item.title.contains(debouncedQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == "All" || item.groupTitle == selectedCategory
            val matchesFavorite = !showFavoritesOnly || item.isFavorite
            matchesQuery && matchesCategory && matchesFavorite
        }
    }

    LaunchedEffect(playlist.id) {
        viewModel.selectPlaylist(playlist)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                viewModel.importM3uIntoSelectedPlaylist(stream)
            }
        }
    }

    if (showRenameDialog) {
        RenameDialog(
            initialName = state.selectedPlaylist?.name ?: playlist.name,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                state.selectedPlaylist?.let { viewModel.renamePlaylist(it, newName) }
                showRenameDialog = false
            }
        )
    }

    if (showAddDialog) {
        AddPlaylistItemDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, url ->
                viewModel.addCustomItemToPlaylist(playlist.id, title, url)
                showAddDialog = false
            },
            onImportAsPlaylist = { url ->
                showAddDialog = false
                viewModel.importM3uFromUrlIntoSelectedPlaylist(url) { success, errorMessage ->
                    scope.launch {
                        val message = if (success) "Playlist imported" else "Import failed: ${errorMessage ?: "unknown error"}"
                        snackbarHostState.showSnackbar(message)
                    }
                }
            }
        )
    }

    if (showMoveDialog) {
        MoveToPlaylistDialog(
            playlists = state.playlists.filter { it.id != playlist.id },
            onDismiss = { showMoveDialog = false },
            onSelect = { target ->
                viewModel.moveSelectedPlaylistItemsTo(target.id)
                showMoveDialog = false
                isSelectionMode = false
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.selectedPlaylist?.name ?: playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) {
                            isSelectionMode = false
                            viewModel.clearPlaylistItemSelection()
                        } else onBack()
                    }) {
                        Icon(
                            if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.selectAllPlaylistItems() }) {
                            Icon(Icons.Default.Check, contentDescription = "Select all")
                        }
                        IconButton(onClick = { showMoveDialog = true }) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = "Move selected")
                        }
                        IconButton(onClick = {
                            viewModel.deleteSelectedPlaylistItems()
                            isSelectionMode = false
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename")
                        }
                        IconButton(onClick = { importLauncher.launch(arrayOf("audio/x-mpegurl", "application/x-mpegurl", "*/*")) }) {
                            Icon(Icons.Default.FileUpload, contentDescription = "Import M3U")
                        }
                        Box {
                            IconButton(onClick = { showExportMenu = true }) {
                                Icon(Icons.Default.IosShare, contentDescription = "Export")
                            }
                            DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Export as M3U") },
                                    onClick = {
                                        showExportMenu = false
                                        scope.launch {
                                            val m3u = viewModel.exportSelectedPlaylistAsM3u()
                                            ShareFileUtils.shareTextFile(context, "${playlist.name}.m3u", "audio/x-mpegurl", m3u)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export as XSPF") },
                                    onClick = {
                                        showExportMenu = false
                                        scope.launch {
                                            val xspf = viewModel.exportSelectedPlaylistAsXspf()
                                            ShareFileUtils.shareTextFile(context, "${playlist.name}.xspf", "application/xspf+xml", xspf)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add Item") }
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Search channels") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.padding(start = 8.dp))
                IconButton(onClick = { showFavoritesOnly = !showFavoritesOnly }) {
                    Icon(
                        if (showFavoritesOnly) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorites only",
                        tint = if (showFavoritesOnly) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                if (categories.size > 1) {
                    Box {
                        IconButton(onClick = { showCategoryMenu = true }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Filter by category",
                                tint = if (selectedCategory != "All") MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                        DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category) },
                                    onClick = {
                                        selectedCategory = category
                                        showCategoryMenu = false
                                    },
                                    leadingIcon = {
                                        if (category == selectedCategory) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (state.selectedPlaylistItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("This playlist is empty", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.padding(top = 4.dp))
                    Text(
                        "Add items manually, or import an existing M3U file.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (visibleItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No channels match your filters", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(items = visibleItems, key = { _, item -> item.id }) { index, item ->
                        PlaylistItemRow(
                            item = item,
                            index = index,
                            total = visibleItems.size,
                            isSelectionMode = isSelectionMode,
                            isSelected = state.selectedPlaylistItemIds.contains(item.id),
                            onClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleItemSelected(item.id)
                                } else {
                                    onPlayItem(item.mediaUrl, item.title)
                                }
                            },
                            onLongClick = {
                                isSelectionMode = true
                                viewModel.toggleItemSelected(item.id)
                            },
                            onMoveUp = { viewModel.movePlaylistItem(item, moveUp = true) },
                            onMoveDown = { viewModel.movePlaylistItem(item, moveUp = false) },
                            onDelete = { viewModel.removeItemFromPlaylist(item) },
                            onToggleFavorite = { viewModel.toggleFavoritePlaylistItem(item) },
                            downloadItem = downloads[item.mediaUrl],
                            onToggleDownload = {
                                val existing = downloads[item.mediaUrl]
                                when {
                                    existing == null -> downloadTracker.startDownload(item.mediaUrl, item.title)
                                    existing.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED ->
                                        downloadTracker.removeDownload(item.mediaUrl)
                                    existing.state == androidx.media3.exoplayer.offline.Download.STATE_FAILED ->
                                        downloadTracker.startDownload(item.mediaUrl, item.title)
                                    else -> Unit // already downloading/queued — tapping again does nothing
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PlaylistItemRow(
    item: PlaylistItemEntity,
    index: Int,
    total: Int,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    downloadItem: com.dk.tvplayer.download.DownloadItem?,
    onToggleDownload: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.padding(start = 12.dp))
            } else {
                Icon(Icons.Default.LiveTv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.padding(start = 12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.mediaUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isSelectionMode) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (item.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                DownloadStatusButton(downloadItem = downloadItem, onClick = onToggleDownload)
                Column {
                    IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    IconButton(onClick = onMoveDown, enabled = index < total - 1, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadStatusButton(
    downloadItem: com.dk.tvplayer.download.DownloadItem?,
    onClick: () -> Unit
) {
    val isDownloading = downloadItem != null && downloadItem.state == androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
    val isCompleted = downloadItem != null && downloadItem.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
    val isFailed = downloadItem != null && downloadItem.state == androidx.media3.exoplayer.offline.Download.STATE_FAILED

    IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
        when {
            isDownloading -> {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = (downloadItem?.percentDownloaded ?: 0f) / 100f,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
            isCompleted -> Icon(
                Icons.Default.DownloadDone,
                contentDescription = "Downloaded — tap to remove",
                tint = MaterialTheme.colorScheme.primary
            )
            isFailed -> Icon(
                Icons.Default.ErrorOutline,
                contentDescription = "Download failed — tap to retry",
                tint = MaterialTheme.colorScheme.error
            )
            else -> Icon(
                Icons.Default.Download,
                contentDescription = "Download for offline playback"
            )
        }
    }
}

@Composable
private fun RenameDialog(initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.ifBlank { initialName }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddPlaylistItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, url: String) -> Unit,
    onImportAsPlaylist: (url: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    // A URL ending in .m3u (not .m3u8, which is a legitimate single HLS stream) is
    // almost always a channel *list* (e.g. iptv-org's index.m3u) rather than a single
    // playable stream. Adding it as a plain item here would always fail to play with a
    // "format not supported" / manifest-parsing error, because a list of thousands of
    // unrelated channels isn't a valid single-stream manifest — same heuristic as
    // NewStreamDialog uses for the "New Stream" flow.
    val looksLikePlaylist = remember(url) {
        val trimmed = url.trim().substringBefore('?')
        trimmed.endsWith(".m3u", ignoreCase = true) && !trimmed.endsWith(".m3u8", ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Item") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Media URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (looksLikePlaylist) {
                    Spacer(modifier = Modifier.padding(top = 8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.padding(start = 4.dp))
                        Text(
                            text = "This looks like an IPTV playlist (a list of channels), not a single " +
                                "stream — it won't play directly. Use \"Import as Playlist\" below instead " +
                                "to add every channel in it as its own item here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (url.isNotBlank()) onConfirm(title.ifBlank { "Untitled" }, url.trim()) },
                enabled = url.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            Row {
                if (looksLikePlaylist) {
                    TextButton(onClick = { onImportAsPlaylist(url.trim()) }) {
                        Text("Import as Playlist")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun MoveToPlaylistDialog(
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onSelect: (PlaylistEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to playlist") },
        text = {
            if (playlists.isEmpty()) {
                Text("No other playlists available. Create one first.")
            } else {
                Column {
                    playlists.forEach { playlist ->
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(playlist) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
