package echo.music.desktop.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import echo.music.desktop.ui.theme.NothingColors

@Composable
fun AmbientGlow(
    enabled: Boolean,
    size: Dp = 340.dp,
    modifier: Modifier = Modifier
) {
    if (!enabled) return

    val infiniteTransition = rememberInfiniteTransition(label = "AmbientGlowTransition")

    // Slow organic breathing scale
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientScale"
    )

    // Soft opacity pulse
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientAlpha"
    )

    Box(
        modifier = modifier
            .size(size * 1.35f)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // Deep radial red/dark glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            NothingColors.AccentRed.copy(alpha = alpha),
                            NothingColors.AccentRed.copy(alpha = alpha * 0.45f),
                            Color(0xFF3A0D10).copy(alpha = alpha * 0.25f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
