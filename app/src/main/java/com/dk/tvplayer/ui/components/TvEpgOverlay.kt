package com.dk.tvplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dk.tvplayer.data.local.ChannelEntity
import com.dk.tvplayer.data.local.EpgProgramEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TvEpgOverlay(
    channel: ChannelEntity?,
    programs: List<EpgProgramEntity>,
    modifier: Modifier = Modifier
) {
    if (channel == null) return

    val currentProgram = programs.firstOrNull()
    val nextProgram = if (programs.size > 1) programs[1] else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                )
            )
            .padding(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "[${channel.groupTitle}]",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            currentProgram?.let { prog ->
                Text(
                    text = "NOW: ${prog.title} (${formatEpgTime(prog.startTime)} - ${formatEpgTime(prog.endTime)})",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                prog.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                }
            }

            nextProgram?.let { next ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "NEXT: ${next.title} (${formatEpgTime(next.startTime)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

private fun formatEpgTime(timeMs: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timeMs))
}
