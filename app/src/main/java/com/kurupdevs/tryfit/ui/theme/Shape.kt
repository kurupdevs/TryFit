package com.kurupdevs.tryfit.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * TryFit shape tokens — SPEC §2 radii.
 */
val TryFitShapes = Shapes(
    // Product hero card: 28-32dp (overlay glass: 16-18dp)
    large = RoundedCornerShape(30.dp),
    // Grid cards: 20-22dp (image well 14dp inner)
    medium = RoundedCornerShape(21.dp),
    // Thumbnail tiles: 10-12dp
    small = RoundedCornerShape(12.dp)
)

/** Shape tokens not covered by Material3 [Shapes]. */
object TryFitRadii {
    val HeroCard = 30.dp            // hero card 28-32dp
    val GlassOverlay = 17.dp        // glass overlay 16-18dp
    val GridCard = 21.dp            // grid card 20-22dp
    val GridImageWell = 14.dp       // grid image well 14dp inner
    val ThumbTile = 12.dp           // thumbnail tiles 10-12dp
    val Pill = CircleShape          // chips, CTA, pills, bottom nav: full pill
}
