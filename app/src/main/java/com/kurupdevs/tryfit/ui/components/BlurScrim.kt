package com.kurupdevs.tryfit.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * True background blur is [Modifier.blur] applied to the content UNDER the
 * sheet (RenderEffect, API 31+; no-op below 31). Apply this to the host's
 * content while a sheet is open:
 *
 * ```
 * FeedContent(modifier = Modifier.backgroundBlur(sheetOpen))
 * if (sheetOpen) { BlurScrim(); TheSheet() }
 * ```
 */
fun Modifier.backgroundBlur(enabled: Boolean, radius: Dp = 22.dp): Modifier =
    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.blur(radius)
    } else {
        this
    }

/**
 * Dim scrim drawn over the (blurred) content behind a bottom sheet —
 * black at 40% alpha per the wardrobe/quick-view sheet specs.
 */
@Composable
fun BlurScrim(
    modifier: Modifier = Modifier,
    dimAlpha: Float = 0.4f
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = dimAlpha))
    )
}
