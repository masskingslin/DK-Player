package com.dk.tvplayer.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dk.tvplayer.data.local.LocalVideoDisplayItem

/** VLC-style sort choices for the local video library (criterion + direction). */
enum class LocalSort(val criterion: String, val optionLabel: String) {
    NAME_ASC("Name", "A → Z"),
    NAME_DESC("Name", "Z → A"),
    LENGTH_ASC("Length", "Shortest first"),
    LENGTH_DESC("Length", "Longest first"),
    ADDED_OLD("Recently added", "Oldest first"),
    ADDED_NEW("Recently added", "Newest first"),
    TRACKS_MORE("Nb tracks", "More videos in group"),
    TRACKS_LESS("Nb tracks", "Less videos in group")
}

fun LocalVideoDisplayItem.durationMs(): Long = when (this) {
    is LocalVideoDisplayItem.Single -> video.duration
    is LocalVideoDisplayItem.Group -> videos.sumOf { it.duration }
}

/** MediaStore ids grow as files are added, so the largest id is the newest file. */
fun LocalVideoDisplayItem.addedOrder(): Long = when (this) {
    is LocalVideoDisplayItem.Single -> video.id
    is LocalVideoDisplayItem.Group -> videos.maxOfOrNull { it.id } ?: 0L
}

fun LocalVideoDisplayItem.trackCount(): Int = when (this) {
    is LocalVideoDisplayItem.Single -> 1
    is LocalVideoDisplayItem.Group -> videos.size
}

fun formatDurationMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Draws a selection outline + tick over any card or row. */
@Composable
fun SelectableFrame(selected: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) {
        content()
        if (selected) {
            val shape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), shape)
                    .border(3.dp, MaterialTheme.colorScheme.primary, shape)
            )
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
            )
        }
    }
}

/** Compact one-line row, used when "Display in list" is on. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalVideoListRow(
    item: LocalVideoDisplayItem,
    isFavorite: Boolean,
    isPlayed: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (item) {
        is LocalVideoDisplayItem.Single -> item.video.name
        is LocalVideoDisplayItem.Group -> item.displayName
    }
    val subtitle = when (item) {
        is LocalVideoDisplayItem.Single -> formatDurationMs(item.video.duration)
        is LocalVideoDisplayItem.Group -> "${item.videos.size} videos • ${formatDurationMs(item.durationMs())}"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item is LocalVideoDisplayItem.Group) Icons.Default.FolderOpen else Icons.Default.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlayed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isFavorite) {
            Icon(Icons.Default.Favorite, contentDescription = "Favourite", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onMenu) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
    }
}

/** "5 selected  4:35:26" bar with play / queue / playlist / group actions (VLC style). */
@Composable
fun LocalSelectionBar(
    count: Int,
    totalDurationMs: Long,
    onClose: () -> Unit,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onPlaylist: () -> Unit,
    onGroup: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Cancel selection") }
        Column(Modifier.weight(1f)) {
            Text("$count selected", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(formatDurationMs(totalDurationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onPlay) { Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary) }
        IconButton(onClick = onQueue) { Icon(Icons.Default.QueuePlayNext, contentDescription = "Add to play queue", tint = MaterialTheme.colorScheme.primary) }
        IconButton(onClick = onPlaylist) { Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to playlist", tint = MaterialTheme.colorScheme.primary) }
        IconButton(onClick = onGroup) { Icon(Icons.Default.CreateNewFolder, contentDescription = "Add to video group", tint = MaterialTheme.colorScheme.primary) }
    }
}

/** "Display settings" sheet: list/grid, favourites filter and the full sort matrix. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalDisplaySettingsSheet(
    sort: LocalSort,
    onSort: (LocalSort) -> Unit,
    listLayout: Boolean,
    onListLayout: (Boolean) -> Unit,
    onlyFavourites: Boolean,
    onOnlyFavourites: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            SheetHeading("Display settings")
            SheetToggleRow(Icons.Default.ViewList, "Display in list", listLayout, onListLayout, asSwitch = true)
            SheetToggleRow(Icons.Default.Favorite, "Show only favourites", onlyFavourites, onOnlyFavourites, asSwitch = false)

            Spacer(Modifier.height(8.dp))
            SheetHeading("Sort by…")
            val groups = listOf(
                Triple(Icons.Default.SortByAlpha, "Name", listOf(LocalSort.NAME_ASC, LocalSort.NAME_DESC)),
                Triple(Icons.Default.Timer, "Length", listOf(LocalSort.LENGTH_ASC, LocalSort.LENGTH_DESC)),
                Triple(Icons.Default.History, "Recently added", listOf(LocalSort.ADDED_OLD, LocalSort.ADDED_NEW)),
                Triple(Icons.Default.FormatListNumbered, "Nb tracks", listOf(LocalSort.TRACKS_MORE, LocalSort.TRACKS_LESS))
            )
            groups.forEach { (icon, title, options) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 12.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(0.9f).padding(top = 12.dp))
                    Column(Modifier.weight(1.3f)) {
                        options.forEach { option ->
                            val selected = option == sort
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSort(option); onDismiss() }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = option.optionLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selected) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 10.dp)
    )
}

@Composable
private fun SheetToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    asSwitch: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (asSwitch) Switch(checked = checked, onCheckedChange = onChecked)
        else Checkbox(checked = checked, onCheckedChange = onChecked)
    }
}
