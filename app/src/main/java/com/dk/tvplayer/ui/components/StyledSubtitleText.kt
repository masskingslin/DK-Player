package com.dk.tvplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk.tvplayer.util.SubtitleStyle

/**
 * Draws one subtitle block in the user's style: colour/opacity, bold, optional background box,
 * an outline of configurable thickness and a drop shadow — the last two can be on together.
 */
@Composable
fun StyledSubtitleText(text: String, style: SubtitleStyle, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val fontSize = style.size.sp.sp
    val base = TextStyle(
        fontSize = fontSize,
        lineHeight = fontSize * 1.25f,
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center
    )
    val shadow = if (style.shadowEnabled) {
        Shadow(
            color = Color(style.shadowColor).copy(alpha = style.shadowOpacity),
            offset = Offset(3f, 3f),
            blurRadius = 6f
        )
    } else null

    val boxModifier = if (style.backgroundEnabled) {
        modifier
            .background(Color(style.backgroundColor).copy(alpha = style.backgroundOpacity), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    } else modifier

    Box(modifier = boxModifier) {
        if (style.outlineEnabled) {
            // The fill is drawn over the stroke, which hides its inner half — so the stroke is
            // twice the visible outline thickness.
            val strokePx = with(density) { (style.outlineSize.dp * 2f).dp.toPx() }
            Text(
                text = text,
                style = base.copy(
                    color = Color(style.outlineColor).copy(alpha = style.outlineOpacity),
                    drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round),
                    shadow = shadow
                )
            )
        }
        Text(
            text = text,
            style = base.copy(
                color = Color(style.color).copy(alpha = style.opacity),
                shadow = if (style.outlineEnabled) null else shadow
            )
        )
    }
}
