package com.stream.tvplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stream.tvplayer.data.local.ChannelEntity
import com.stream.tvplayer.data.local.EpgEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TvEpgOverlay(
    channel: ChannelEntity?,
    currentProgram: EpgEntity?,
    nextProgram: EpgEntity?,
    modifier: Modifier = Modifier
) {
    if (channel == null) return

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val now = System.currentTimeMillis()

    val progress = if (currentProgram != null && currentProgram.endEpochMs > currentProgram.startEpochMs) {
        ((now - currentProgram.startEpochMs).toFloat() / (currentProgram.endEpochMs - currentProgram.startEpochMs).toFloat())
            .coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                )
            )
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${channel.channelNumber}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    channel.groupName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                    }
                }
            }

            if (currentProgram != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NOW: ${currentProgram.title}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${timeFormat.format(Date(currentProgram.startEpochMs))} - ${timeFormat.format(Date(currentProgram.endEpochMs))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.DarkGray
                )
            }

            if (nextProgram != null) {
                Text(
                    text = "NEXT: ${nextProgram.title} (${timeFormat.format(Date(nextProgram.startEpochMs))})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }
    }
}
