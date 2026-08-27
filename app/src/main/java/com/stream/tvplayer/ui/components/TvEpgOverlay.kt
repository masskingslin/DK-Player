package com.stream.tvplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stream.tvplayer.data.local.ChannelEntity
import com.stream.tvplayer.data.local.EpgEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TvEpgOverlay(
    channel: ChannelEntity,
    currentProgram: EpgEntity?,
    nextProgram: EpgEntity?,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xF7050505))
                    )
                )
                .padding(horizontal = 48.dp, vertical = 32.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${channel.channelNumber}. ${channel.name}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    channel.groupName?.let {
                        Text(
                            text = " • $it",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.LightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (currentProgram != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentProgram.title,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFFD54F),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            currentProgram.description?.let {
                                Text(
                                    text = it,
                                    fontSize = 13.sp,
                                    color = Color(0xFFCCCCCC),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        val timeText = "${timeFormat.format(Date(currentProgram.startEpochMs))} - ${timeFormat.format(Date(currentProgram.endEpochMs))}"
                        Text(
                            text = timeText,
                            fontSize = 14.sp,
                            color = Color.White,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }

                    val now = System.currentTimeMillis()
                    val totalDuration = (currentProgram.endEpochMs - currentProgram.startEpochMs).toFloat()
                    val progress = if (totalDuration > 0) {
                        ((now - currentProgram.startEpochMs) / totalDuration).coerceIn(0f, 1f)
                    } else 0f

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0x33FFFFFF))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(Color(0xFFFFD54F))
                        )
                    }
                } else {
                    Text(
                        text = "Live Stream Broadcast",
                        fontSize = 16.sp,
                        color = Color.LightGray
                    )
                }

                if (nextProgram != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "UP NEXT: ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        val nextTime = timeFormat.format(Date(nextProgram.startEpochMs))
                        Text(
                            text = "${nextProgram.title} ($nextTime)",
                            fontSize = 13.sp,
                            color = Color(0xFFE0E0E0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
