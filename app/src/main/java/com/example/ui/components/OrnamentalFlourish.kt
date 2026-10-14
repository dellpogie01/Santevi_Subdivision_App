package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun OrnamentalFlourishDivider(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFD4AF37) // Premium gold accent color
) {
    Canvas(
        modifier = modifier
            .width(220.dp)
            .height(20.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val centerX = width / 2f

        // Draw left curved flourish line with gradient fade
        val leftPath = Path().apply {
            moveTo(8f, centerY)
            cubicTo(
                width * 0.22f, centerY - 8f,
                width * 0.36f, centerY + 8f,
                centerX - 18f, centerY
            )
        }
        drawPath(
            path = leftPath,
            brush = Brush.horizontalGradient(
                colors = listOf(color.copy(alpha = 0f), color, color),
                startX = 0f,
                endX = centerX - 18f
            ),
            style = Stroke(width = 2.5f)
        )

        // Draw right curved flourish line with gradient fade
        val rightPath = Path().apply {
            moveTo(width - 8f, centerY)
            cubicTo(
                width * 0.78f, centerY - 8f,
                width * 0.64f, centerY + 8f,
                centerX + 18f, centerY
            )
        }
        drawPath(
            path = rightPath,
            brush = Brush.horizontalGradient(
                colors = listOf(color, color, color.copy(alpha = 0f)),
                startX = centerX + 18f,
                endX = width
            ),
            style = Stroke(width = 2.5f)
        )

        // Draw central diamond/filigree motif
        val diamondPath = Path().apply {
            moveTo(centerX, centerY - 9f)
            lineTo(centerX + 9f, centerY)
            lineTo(centerX, centerY + 9f)
            lineTo(centerX - 9f, centerY)
            close()
        }
        drawPath(path = diamondPath, color = color)

        // Flanking dots
        drawCircle(color = color, radius = 2.5f, center = Offset(centerX - 14f, centerY))
        drawCircle(color = color, radius = 2.5f, center = Offset(centerX + 14f, centerY))
    }
}
