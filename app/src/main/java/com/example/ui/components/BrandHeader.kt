package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun BrandHeaderWithFlankingLogos(
    modifier: Modifier = Modifier,
    testTag: String = "brand_header_logo"
) {
    // Continuous looping rotation animation for the circular border
    val infiniteTransition = rememberInfiniteTransition(label = "CircleRotationTransition")
    val rotationDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CircleRotationDegrees"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Left image: Santeví logo anchored flush to left textbox margin
        Image(
            painter = painterResource(id = R.drawable.santevi_trans_logo),
            contentDescription = "Santeví Logo Left",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(85.dp)
                .height(46.dp),
            contentScale = ContentScale.Fit
        )

        // Centered circular logo with continuously rotating animated circular border
        Box(
            modifier = Modifier
                .size(98.dp)
                .align(Alignment.Center)
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            // Continuously rotating circular border
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotationDegrees)
            ) {
                val strokeWidth = 3.5.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.White,
                            primaryColor,
                            secondaryColor,
                            Color.White.copy(alpha = 0.2f),
                            Color.White
                        )
                    ),
                    radius = radius,
                    style = Stroke(width = strokeWidth)
                )
            }

            // Inner circular logo image
            Image(
                painter = painterResource(id = R.drawable.appstore),
                contentDescription = "Santeví House Logo",
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        // Right image: Ovialand logo anchored flush to right textbox margin
        Image(
            painter = painterResource(id = R.drawable.ovialand),
            contentDescription = "Ovialand Logo Right",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(72.dp),
            contentScale = ContentScale.Fit
        )
    }
}
