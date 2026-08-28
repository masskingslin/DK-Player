package com.dk.tvplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class PlaylistItem(
    val id: Long,
    val title: String,
    val itemCount: Int,
    val lastModified: String,
    val isLocal: Boolean
)

data class PlaylistOrganization(
    val sortOrder: PlaylistSortOrder = PlaylistSortOrder.NAME,
    val groupBy: PlaylistGroupBy = PlaylistGroupBy.NONE,
    val viewMode: PlaylistViewMode = PlaylistViewMode.LIST
)

enum class PlaylistSortOrder {
    NAME, DATE_MODIFIED, ITEM_COUNT
}

enum class PlaylistGroupBy {
    NONE, TYPE, DATE
}

enum class PlaylistViewMode {
    LIST, GRID
}

@Composable
fun AdvancedPlaylistManager(
    modifier: Modifier = Modifier,
    playlists: List<PlaylistItem>,
    onPlaylistClick: (PlaylistItem) -> Unit,
    onPlaylistDelete: (PlaylistItem) -> Unit,
    onPlaylistRename: (PlaylistItem, String) -> Unit,
    onPlaylistDuplicate: (PlaylistItem) -> Unit,
    onPlaylistExport: (PlaylistItem) -> Unit,
    onNewPlaylist: () -> Unit
) {
    var organization by remember { mutableStateOf(PlaylistOrganization()) }
    var selectedPlaylist by remember { mutableStateOf<PlaylistItem?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showViewModeMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        PlaylistManagerToolbar(
            sortOrder = organization.sortOrder,
            viewMode = organization.viewMode,
            onSortOrderChanged = { organization = organization.copy(sortOrder = it) },
            onViewModeChanged = { organization = organization.copy(viewMode = it) },
            onNewPlaylist = onNewPlaylist
        )

        PlaylistList(
            playlists = playlists.sortedBy { playlist ->
                when (organization.sortOrder) {
                    PlaylistSortOrder.NAME -> playlist.title
                    PlaylistSortOrder.ITEM_COUNT -> playlist.itemCount.toString()
                    PlaylistSortOrder.DATE_MODIFIED -> playlist.lastModified
                }
            },
            onPlaylistClick = onPlaylistClick,
            onPlaylistLongClick = { playlist ->
                selectedPlaylist = playlist
                showContextMenu = true
            },
            viewMode = organization.viewMode
        )

        if (showContextMenu && selectedPlaylist != null) {
            PlaylistContextMenu(
                playlist = selectedPlaylist!!,
                onPlaylistDelete = {
                    onPlaylistDelete(selectedPlaylist!!)
                    showContextMenu = false
                    selectedPlaylist = null
                },
                onPlaylistRename = { newName ->
                    onPlaylistRename(selectedPlaylist!!, newName)
                    showContextMenu = false
                    selectedPlaylist = null
                },
                onPlaylistDuplicate = {
                    onPlaylistDuplicate(selectedPlaylist!!)
                    showContextMenu = false
                    selectedPlaylist = null
                },
                onPlaylistExport = {
                    onPlaylistExport(selectedPlaylist!!)
                    showContextMenu = false
                    selectedPlaylist = null
                },
                onDismiss = { showContextMenu = false }
            )
        }
    }
}

@Composable
fun PlaylistManagerToolbar(
    modifier: Modifier = Modifier,
    sortOrder: PlaylistSortOrder,
    viewMode: PlaylistViewMode,
    onSortOrderChanged: (PlaylistSortOrder) -> Unit,
    onViewModeChanged: (PlaylistViewMode) -> Unit,
    onNewPlaylist: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.DarkGray, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Button(
                onClick = { /* showSortMenu = !showSortMenu */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Icon(Icons.Default.Sort, "Sort", modifier = Modifier.size(20.dp))
                Text("Sort", modifier = Modifier.padding(start = 4.dp))
            }
        }

        IconButton(
            onClick = {
                val nextMode = when (viewMode) {
                    PlaylistViewMode.LIST -> PlaylistViewMode.GRID
                    PlaylistViewMode.GRID -> PlaylistViewMode.LIST
                }
                onViewModeChanged(nextMode)
            }
        ) {
            Icon(
                if (viewMode == PlaylistViewMode.LIST) Icons.Default.ViewList else Icons.Default.GridView,
                "View Mode"
            )
        }

        Button(
            onClick = onNewPlaylist,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
        ) {
            Icon(Icons.Default.Add, "New", modifier = Modifier.size(20.dp))
            Text("New", modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
fun PlaylistList(
    modifier: Modifier = Modifier,
    playlists: List<PlaylistItem>,
    onPlaylistClick: (PlaylistItem) -> Unit,
    onPlaylistLongClick: (PlaylistItem) -> Unit,
    viewMode: PlaylistViewMode
) {
    when (viewMode) {
        PlaylistViewMode.LIST -> PlaylistListView(
            playlists = playlists,
            onPlaylistClick = onPlaylistClick,
            onPlaylistLongClick = onPlaylistLongClick,
            modifier = modifier
        )
        PlaylistViewMode.GRID -> PlaylistGridView(
            playlists = playlists,
            onPlaylistClick = onPlaylistClick,
            onPlaylistLongClick = onPlaylistLongClick,
            modifier = modifier
        )
    }
}

@Composable
fun PlaylistListView(
    modifier: Modifier = Modifier,
    playlists: List<PlaylistItem>,
    onPlaylistClick: (PlaylistItem) -> Unit,
    onPlaylistLongClick: (PlaylistItem) -> Unit
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(playlists) { playlist ->
            PlaylistItemCard(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist) },
                onLongClick = { onPlaylistLongClick(playlist) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun PlaylistGridView(
    modifier: Modifier = Modifier,
    playlists: List<PlaylistItem>,
    onPlaylistClick: (PlaylistItem) -> Unit,
    onPlaylistLongClick: (PlaylistItem) -> Unit
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(playlists) { playlist ->
            PlaylistGridItemCard(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist) },
                onLongClick = { onPlaylistLongClick(playlist) }
            )
        }
    }
}

@Composable
fun PlaylistItemCard(
    modifier: Modifier = Modifier,
    playlist: PlaylistItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(Color.DarkGray, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${playlist.itemCount} items", style = MaterialTheme.typography.labelSmall)
                Text(playlist.lastModified, style = MaterialTheme.typography.labelSmall)
            }
            Icon(
                if (playlist.isLocal) Icons.Default.FolderOpen else Icons.Default.Cloud,
                contentDescription = if (playlist.isLocal) "Local" else "Cloud"
            )
        }
    }
}

@Composable
fun PlaylistGridItemCard(
    modifier: Modifier = Modifier,
    playlist: PlaylistItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = modifier
            .width(150.dp)
            .clickable(onClick = onClick)
            .background(Color.DarkGray, RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.PlaylistPlay,
                "Playlist",
                modifier = Modifier.size(48.dp)
            )
            Text(playlist.title, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${playlist.itemCount} items", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun PlaylistContextMenu(
    modifier: Modifier = Modifier,
    playlist: PlaylistItem,
    onPlaylistDelete: () -> Unit,
    onPlaylistRename: (String) -> Unit,
    onPlaylistDuplicate: () -> Unit,
    onPlaylistExport: () -> Unit,
    onDismiss: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(playlist.title) }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Enter new name") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    onPlaylistRename(newName)
                    showRenameDialog = false
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                Button(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(modifier = modifier) {
        Column {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = { showRenameDialog = true },
                leadingIcon = { Icon(Icons.Default.Edit, "Rename") }
            )
            DropdownMenuItem(
                text = { Text("Duplicate") },
                onClick = onPlaylistDuplicate,
                leadingIcon = { Icon(Icons.Default.ContentCopy, "Duplicate") }
            )
            DropdownMenuItem(
                text = { Text("Export") },
                onClick = onPlaylistExport,
                leadingIcon = { Icon(Icons.Default.FileDownload, "Export") }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = onPlaylistDelete,
                leadingIcon = { Icon(Icons.Default.Delete, "Delete") }
            )
        }
    }
}
