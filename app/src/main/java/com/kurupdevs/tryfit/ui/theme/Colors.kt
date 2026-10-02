package com.kurupdevs.tryfit.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * TryFit color tokens — SPEC §2, verbatim.
 */
object TryFitColors {
    // Backgrounds
    val BgScreen = Color(0xFFFFFFFF)            // bg-screen: home/wardrobe
    val BgCanvas = Color(0xFFF5F5F7)            // bg-canvas: light-gray canvas behind cards

    // Surfaces
    val SurfaceCard = Color(0xFFFFFFFF)         // surface-card: product/grid card
    val SurfaceChipSelected = Color(0xFFEDEDF0) // surface-chip-selected ("All" pill; unselected = transparent)
    val SurfaceOverlay = Color(0x8CFFFFFF)      // surface-overlay: glassmorphic hero overlay (55% white)
    val SurfaceThumb = Color(0xFFF1F1F3)       // surface-thumb: thumbnail strip tiles
    val SurfaceGridImg = Color(0xFFFAFAFA)      // surface-grid-img: grid card image wells

    // Nav
    val NavBg = Color(0xFF141416)               // nav-bg: dark floating bottom nav

    // Text
    val TextPrimary = Color(0xFF111111)        // text-primary: headlines, names, CTA
    val TextSecondary = Color(0xFF6E6E73)      // text-secondary: greeting, chip labels
    val TextOnPhoto = Color(0xFFFFFFFF)        // text-on-photo (always with shadow)

    // Accent
    val AccentBadge = Color(0xFFFF3B30)        // accent-badge: bell red dot

    // Center AI orb gradient stops (radial, hot center -> edge)
    val OrbHotCenter = Color(0xFFF5EFFF)
    val OrbViolet = Color(0xFFA855F7)
    val OrbPink = Color(0xFFEC4899)
    val OrbBlue = Color(0xFF3B82F6)
    val OrbGlow = Color(0x73A855F7)            // glow halo rgba(168,85,247,0.45)

    /**
     * The AI orb gradient: radial F5EFFF -> A855F7 -> EC4899 -> 3B82F6.
     * SPEC §5: pre-render / reuse a single brush per screen rather than
     * rebuilding gradients inside draw loops.
     */
    val OrbGradient: Brush = Brush.radialGradient(
        colors = listOf(OrbHotCenter, OrbViolet, OrbPink, OrbBlue)
    )
}
