package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(
    hasValidSession: Boolean,
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    // Single-execution flag initialized per SplashScreen presentation
    var hasNavigated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasNavigated) {
            // Hold splash screen for strictly 3 seconds every time app opens
            delay(3000)

            if (!hasNavigated) {
                hasNavigated = true
                if (hasValidSession) {
                    onNavigateToHome()
                } else {
                    onNavigateToLogin()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("splash_screen_root"),
        contentAlignment = Alignment.Center
    ) {
        // 1. Background image
        Image(
            painter = painterResource(id = R.drawable.splash_bg),
            contentDescription = "Splash Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Dark scrim overlay to ensure clear contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.40f),
                            Color.Black.copy(alpha = 0.60f)
                        )
                    )
                )
        )

        // Branding content positioned cleanly in upper portion
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 2. Upper image at the TOP: Transparent Santeví Logo
            Image(
                painter = painterResource(id = R.drawable.santevi_trans_logo),
                contentDescription = "Santeví Logo",
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(85.dp)
                    .testTag("splash_icon_top"),
                contentScale = ContentScale.Fit
            )

            // 3. Tagline in WHITE text with zero gap
            Text(
                text = stringResource(id = R.string.app_tagline),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .testTag("splash_app_tagline")
            )

            // 4. Second image (Ovialand Logo) placed right below tagline
            Image(
                painter = painterResource(id = R.drawable.ovialand),
                contentDescription = "Ovialand Logo",
                modifier = Modifier
                    .fillMaxWidth(0.70f)
                    .height(160.dp)
                    .testTag("splash_icon_bottom"),
                contentScale = ContentScale.Fit
            )
        }

        // Vertically & Horizontally Centered 300% Enlarged Loading Animation
        SplashLoadingIndicator()
    }
}

@Composable
private fun SplashLoadingIndicator() {
    val transition = rememberInfiniteTransition(label = "splash loading dots")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "splash loading rotation"
    )

    Canvas(
        modifier = Modifier
            .size(96.dp)
            .testTag("splash_progress_indicator")
    ) {
        val dotRadius = 8.25.dp.toPx()
        val orbitRadius = 34.5.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val emerald = Color(0xFF10B981)
        val progressAngle = rotation * (2f * PI.toFloat())

        repeat(8) { index ->
            val angle = -PI.toFloat() / 2f + index * (2f * PI.toFloat() / 8f)
            val opacity = 0.2f + 0.7f * ((cos(angle - progressAngle) + 1f) / 2f)
            drawCircle(
                color = emerald.copy(alpha = opacity),
                radius = dotRadius,
                center = Offset(
                    x = center.x + cos(angle) * orbitRadius,
                    y = center.y + sin(angle) * orbitRadius
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    MyApplicationTheme {
        SplashScreen(
            hasValidSession = true,
            onNavigateToHome = {},
            onNavigateToLogin = {}
        )
    }
}
