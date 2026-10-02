package com.kurupdevs.tryfit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * TryFit app theme. The app is light-first per the mockup (white screens,
 * dark floating nav); dark mode maps to the same tokens for now and will be
 * refined in the polish pass (Phase 4).
 */
private val LightScheme = lightColorScheme(
    primary = TryFitColors.TextPrimary,
    onPrimary = Color.White,
    secondary = TryFitColors.OrbViolet,
    tertiary = TryFitColors.OrbPink,
    background = TryFitColors.BgScreen,
    surface = TryFitColors.SurfaceCard,
    surfaceVariant = TryFitColors.BgCanvas,
    onBackground = TryFitColors.TextPrimary,
    onSurface = TryFitColors.TextPrimary,
    onSurfaceVariant = TryFitColors.TextSecondary,
    error = TryFitColors.AccentBadge
)

private val DarkScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = TryFitColors.TextPrimary,
    secondary = TryFitColors.OrbViolet,
    tertiary = TryFitColors.OrbPink,
    background = TryFitColors.BgScreen,
    surface = TryFitColors.SurfaceCard,
    onBackground = TryFitColors.TextPrimary,
    onSurface = TryFitColors.TextPrimary,
    onSurfaceVariant = TryFitColors.TextSecondary,
    error = TryFitColors.AccentBadge
)

@Composable
fun TryFitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = TryFitTypography,
        shapes = TryFitShapes,
        content = content
    )
}
