package com.kurupdevs.tryfit.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kurupdevs.tryfit.ui.theme.TryFitColors

/**
 * AI orb loading state — SPEC §3.6: 1000ms conic sweep + pulse.
 * Used for the stylist "thinking" indicator and any AI-busy moment.
 */
@Composable
fun OrbLoading(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val infinite = rememberInfiniteTransition(label = "orbLoading")
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "conicSweep"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loadPulse"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            }
    ) {
        // Base orb gradient.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TryFitColors.OrbGradient, CircleShape)
        )
        // Rotating conic sweep highlight.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = sweep }
                .background(
                    Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
    }
}
