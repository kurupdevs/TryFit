package com.kurupdevs.tryfit.ui.screens.tryon

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.kurupdevs.tryfit.tryon.TryOnError
import com.kurupdevs.tryfit.ui.components.AiPreviewLabel
import com.kurupdevs.tryfit.ui.components.rememberAppContainer
import com.kurupdevs.tryfit.ui.components.tryFitShimmer
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import kotlinx.coroutines.delay

/**
 * Step 3: processing (SPEC §3.4).
 *
 * Full-bleed photo + shimmer sweep + pulsing AI orb, gradient progress bar,
 * rotating playful copy every 2.2s (from the engine's `copyFor`), cancel X
 * (appears after 1s), "notify me" option. At RENDERING the progressive
 * preview swaps in with a "Refining preview…" label — never a dead spinner.
 * Failures show a friendly error + retry, never a stranded spinner.
 */
@Composable
fun TryOnProcessingStep(
    vm: TryOnFlowViewModel,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    val container = rememberAppContainer()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val settings by container.settingsStore.settings.collectAsState(
        initial = com.kurupdevs.tryfit.data.TryFitSettings()
    )
    val reducedMotion = settings.reducedMotion
    val hapticsEnabled = settings.hapticsEnabled

    var showCancel by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1000) // cancel X appears after 1s (SPEC §3.4)
        showCancel = true
        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val stage = vm.stage
    val copyList = remember(stage?.stage) {
        if (stage != null) container.tryOnEngine.copyFor(stage.stage)
        else listOf("Getting ready…")
    }
    var copyIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(copyList) {
        copyIndex = 0
        while (true) {
            delay(2200) // rotating copy every 2.2s (SPEC §3.4)
            copyIndex = (copyIndex + 1) % copyList.size
        }
    }
    LaunchedEffect(stage?.stageIndex) {
        if (hapticsEnabled && stage != null) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // ---- Terminal: error ----
    val error = vm.error
    if (error != null) {
        ProcessingErrorCard(error = error, onRetry = onRetry, onExit = onExit)
        return
    }

    // ---- Terminal: cancelled by user ----
    if (vm.wasCancelled) {
        ProcessingCancelledCard(onRetry = onRetry, onExit = onExit)
        return
    }

    // ---- Active processing ----
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Full-bleed photo (progressive preview once the engine reaches RENDERING).
        val preview = vm.previewBytes
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(preview ?: vm.photoBytes)
                .build(),
            contentDescription = if (preview != null) "Refining preview" else "Your photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .tryFitShimmer(enabled = !reducedMotion && preview == null),
        )

        // Dark gradient for legibility.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.15f),
                            Color.Black.copy(alpha = 0.72f),
                        ),
                        startY = 0.35f * 2000f,
                    )
                ),
        )

        if (showCancel) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancel try-on",
                    tint = Color.White,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Pulsing AI orb (SPEC §3.6 idle motion language, reused here).
            AiOrbPulsing(animate = !reducedMotion)

            Spacer(Modifier.height(16.dp))

            if (preview != null) {
                Text(
                    text = "Refining preview…",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            Text(
                text = copyList.getOrElse(copyIndex) { copyList.first() },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stageLabel(stage),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(16.dp))

            // Gradient progress bar.
            val fraction = stageFraction(stage)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.22f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    TryFitColors.OrbViolet,
                                    TryFitColors.OrbPink,
                                    TryFitColors.OrbBlue,
                                )
                            )
                        ),
                )
            }

            Spacer(Modifier.height(16.dp))

            // "Notify me" — writes a local notification row on completion.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Notify me when ready",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Switch(
                    checked = vm.notifyMe,
                    onCheckedChange = { vm.notifyMe = it },
                )
            }

            Spacer(Modifier.height(12.dp))
            AiPreviewLabel()
        }
    }
}

/** Pulsing AI orb — one shared infinite transition (SPEC §5). */
@Composable
private fun AiOrbPulsing(animate: Boolean) {
    if (!animate) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(TryFitColors.OrbGradient, CircleShape),
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "processingOrb")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbPulse",
    )
    val glow by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbGlow",
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = glow
                }
                .background(TryFitColors.OrbGlow, CircleShape),
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(TryFitColors.OrbGradient, CircleShape),
        )
    }
}

private fun stageFraction(stage: com.kurupdevs.tryfit.tryon.TryOnProgress.Stage?): Float {
    if (stage == null) return 0.05f
    return (stage.stageIndex.coerceIn(1, stage.stageCount) / stage.stageCount.toFloat())
        .coerceIn(0.05f, 1f)
}

private fun stageLabel(stage: com.kurupdevs.tryfit.tryon.TryOnProgress.Stage?): String {
    if (stage == null) return "Starting…"
    return "Step ${stage.stageIndex} of ${stage.stageCount}"
}

/** Friendly error — never a stranded spinner (SPEC §3.4). */
@Composable
private fun ProcessingErrorCard(
    error: TryOnError,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TryFitColors.BgScreen)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = when (error.code) {
                TryOnError.QUOTA_EXCEEDED -> "All out for today"
                TryOnError.MODERATION_REJECTED -> "Couldn't use that photo"
                else -> "Hmm, that didn't work"
            },
            style = MaterialTheme.typography.titleLarge,
            color = TryFitColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = error.message,
            style = MaterialTheme.typography.bodyMedium,
            color = TryFitColors.TextSecondary,
        )
        Spacer(Modifier.height(24.dp))
        if (error.retryable) {
            Button(
                onClick = onRetry,
                shape = TryFitRadii.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TryFitColors.TextPrimary,
                    contentColor = TryFitColors.TextOnPhoto,
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = "Retry", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = onExit,
            shape = TryFitRadii.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = TryFitColors.BgCanvas,
                contentColor = TryFitColors.TextPrimary,
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(text = "Back", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ProcessingCancelledCard(
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TryFitColors.BgScreen)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Try-on cancelled",
            style = MaterialTheme.typography.titleLarge,
            color = TryFitColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "No worries — your photo is still here when you want to try again.",
            style = MaterialTheme.typography.bodyMedium,
            color = TryFitColors.TextSecondary,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            shape = TryFitRadii.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = TryFitColors.TextPrimary,
                contentColor = TryFitColors.TextOnPhoto,
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(text = "Try again", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onExit,
            shape = TryFitRadii.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = TryFitColors.BgCanvas,
                contentColor = TryFitColors.TextPrimary,
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(text = "Back", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Small honest-label card used by the result screen header. */
@Composable
fun HonestResultHeader(title: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
            Text(
                text = "AI preview — style preview, not a fit guarantee",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}
