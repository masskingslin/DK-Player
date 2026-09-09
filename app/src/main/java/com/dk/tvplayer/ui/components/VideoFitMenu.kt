package com.dk.tvplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.ui.AspectRatioFrameLayout

/** Maps a PlayerView resize mode to a short, user-facing label. */
data class VideoFitOption(val resizeMode: Int, val label: String)

val VideoFitOptions = listOf(
    VideoFitOption(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Fit (Original)"),
    VideoFitOption(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Zoom / Crop"),
    VideoFitOption(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Stretch to Fill"),
    VideoFitOption(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, "Fixed Width"),
    VideoFitOption(AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT, "Fixed Height")
)

@Composable
fun VideoFitMenu(
    currentResizeMode: Int,
    onModeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(vertical = 8.dp)
    ) {
        VideoFitOptions.forEach { option ->
            val isSelected = option.resizeMode == currentResizeMode
            Text(
                text = option.label,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                style = if (isSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModeSelected(option.resizeMode) }
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            )
        }
    }
}
