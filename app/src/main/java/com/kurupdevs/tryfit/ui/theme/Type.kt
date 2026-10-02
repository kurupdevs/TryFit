package com.kurupdevs.tryfit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

/**
 * TryFit typography — SPEC §2.
 *
 * Headline: Playfair Display-class serif. We deliberately do NOT bundle or
 * download the Playfair Display font file: [FontFamily.Serif] is the system
 * serif fallback, which keeps the APK lean, avoids font licensing questions,
 * and needs no runtime network. Swap the [Headline] family for a bundled
 * font resource later if the brand requires the exact cut.
 */
private val HeadlineSerif = FontFamily.Serif

val TryFitTypography = Typography(
    // Onboarding headline: serif 34-36sp, 600 ("Virtual Try-On" italic 500), leading 1.15, ls -0.5
    displayLarge = TextStyle(
        fontFamily = HeadlineSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 39.sp, // ~1.15
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = HeadlineSerif,
        fontWeight = FontWeight.Medium,
        fontStyle = FontStyle.Italic,
        fontSize = 34.sp,
        lineHeight = 39.sp,
        letterSpacing = (-0.5).sp
    ),
    // Greeting: 13-14sp / 400 gray
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TryFitColors.TextSecondary
    ),
    // Name: 19-21sp / 700
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.25).sp
    ),
    // Overlay brand: 13sp/500, product name: 17-18sp/700, price: 14sp/600 (white + shadow)
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold, // CTA 15-16sp/600
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium, // chips 13-14sp/500-400
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.SemiBold, // pill labels 11-12sp/500-600
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        lineBreak = LineBreak.Paragraph
    )
)
