package com.kurupdevs.tryfit.ui.screens.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import com.kurupdevs.tryfit.R
import com.kurupdevs.tryfit.data.PrefsKeys
import com.kurupdevs.tryfit.ui.components.rememberAppContainer
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Onboarding — SPEC §3.1, reference mockup screen 1.
 *
 * Full-bleed bright fashion photo (bright at top so the dark status icons
 * work), serif headline top-center ("Ecom with AI Powered" roman 600 /
 * "Virtual Try-On" italic 500, [FontFamily.Serif]), white full-width-minus-32dp
 * pill CTA "Ready? Let's Go!" (54dp) ~90dp above the bottom, "Skip" top-right.
 *
 * CTA press: pill morphs to a circle (240ms) then navigates — SPEC §4.
 * Completion is persisted to DataStore so returning users skip this screen.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val container = rememberAppContainer()
    val scope = rememberCoroutineScope()
    var leaving by remember { mutableStateOf(false) }

    // Pill → circle morph (240ms, SPEC §4) before navigating.
    val ctaWidthFraction by animateFloatAsState(
        targetValue = if (leaving) 0.18f else 1f,
        animationSpec = tween(240),
        label = "ctaMorph"
    )

    fun finish() {
        if (leaving) return
        leaving = true
        scope.launch {
            runCatching {
                container.prefs.edit { it[PrefsKeys.ONBOARDING_DONE] = true }
            }
            delay(240)
            onFinished()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.onboarding_hero),
            contentDescription = "Model wearing a denim outfit",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        TextButton(
            onClick = ::finish,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Text(
                text = stringResource(R.string.skip),
                style = MaterialTheme.typography.labelLarge,
                color = TryFitColors.TextPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 110.dp), // ~12-15% from top
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Ecom with AI Powered",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 34.sp,
                    lineHeight = 39.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = TryFitColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Virtual Try-On",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic,
                    fontSize = 34.sp,
                    lineHeight = 39.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = TryFitColors.TextPrimary,
                textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = ::finish,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(ctaWidthFraction)
                .padding(start = 16.dp, end = 16.dp, bottom = 90.dp)
                .height(54.dp) // 52-56dp pill CTA
                .shadow(
                    elevation = 6.dp,
                    shape = TryFitRadii.Pill,
                    spotColor = Color.Black.copy(alpha = 0.14f)
                ),
            shape = TryFitRadii.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = TryFitColors.TextPrimary,
                disabledContainerColor = Color.White,
                disabledContentColor = TryFitColors.TextPrimary
            ),
            enabled = !leaving
        ) {
            if (leaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = TryFitColors.TextPrimary,
                    strokeWidth = 2.5.dp
                )
            } else {
                Text(
                    text = stringResource(R.string.onboarding_cta),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}
