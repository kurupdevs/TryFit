package com.kurupdevs.tryfit.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * TryFit spacing tokens — SPEC §2, 8dp base.
 */
object TryFitSpacing {
    val ScreenPadding: Dp = 16.dp        // screen padding 16-20dp
    val ScreenPaddingLarge: Dp = 20.dp
    val HeaderToChips: Dp = 16.dp        // header -> chips
    val ChipGap: Dp = 8.dp              // chip gaps
    val HeroGap: Dp = 16.dp             // hero <-> next card
    val OverlayInset: Dp = 12.dp        // overlay insets
    val ThumbSize: Dp = 60.dp           // thumbnail tiles 56-64dp
    val ThumbGap: Dp = 8.dp
    val GridGutter: Dp = 14.dp          // grid 2-col, 12-16dp gutters

    // Floating bottom nav geometry
    val NavHeight: Dp = 68.dp           // 64-68dp tall
    val NavSideMargin: Dp = 16.dp       // 16-20dp side margins (never edge-docked)
    val NavBottomOffset: Dp = 12.dp     // 12dp above home indicator
    val OrbSize: Dp = 64.dp             // center AI orb (48dp min touch target)
    val NavIconSize: Dp = 24.dp
    val NavIconTouchTarget: Dp = 48.dp  // a11y: 48dp touch targets

    // Feed scroll clearance under the floating nav
    val FeedBottomClearance: Dp = 100.dp
}
