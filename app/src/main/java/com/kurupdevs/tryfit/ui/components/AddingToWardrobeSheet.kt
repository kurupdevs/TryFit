package com.kurupdevs.tryfit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurupdevs.tryfit.data.catalog.Product
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AddPhase { Adding, Success, Error }

/**
 * "Adding to wardrobe!" sheet — reference IMAGE A.
 *
 * The home feed stays visible behind, dimmed + blurred ([BlurScrim] +
 * [backgroundBlur] on the host content). White sheet, 26dp top corners,
 * slides up 280ms [FastOutSlowInEasing]. Glassy iridescent AI orb (~130dp,
 * layered radial gradients + glossy highlight + bright rim, breathing
 * 1.0↔1.05 @2400ms + ±6dp float). While adding: rotating shimmer sweep;
 * on success: checkmark morph then auto-dismiss after 600ms.
 *
 * The wardrobe write goes through [com.kurupdevs.tryfit.data.WardrobeRepository]
 * (idempotent add); [onAdded] fires after success so the host can show the
 * confirmation snackbar.
 */
@Composable
fun AddingToWardrobeSheet(
    product: Product,
    onDismiss: () -> Unit,
    onAdded: () -> Unit
) {
    val container = rememberAppContainer()
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(AddPhase.Adding) }
    var attempt by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }

    fun dismiss() {
        scope.launch {
            visible = false
            delay(220)
            onDismiss()
        }
    }

    // Idempotent add: only writes when not already saved.
    LaunchedEffect(product.id, attempt) {
        phase = AddPhase.Adding
        val ok = runCatching {
            val repo = container.wardrobeRepository
            if (!repo.isSaved(product.id)) repo.toggle(product.id)
            check(repo.isSaved(product.id)) { "wardrobe write did not stick" }
        }.isSuccess
        if (ok) {
            phase = AddPhase.Success
            delay(600)
            onAdded()
            dismiss()
        } else {
            phase = AddPhase.Error
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(280, easing = FastOutSlowInEasing),
            initialOffsetY = { it }
        ) + fadeIn(tween(200)),
        exit = slideOutVertically(
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            targetOffsetY = { it }
        ) + fadeOut(tween(180))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BlurScrim(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = ::dismiss
                )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(Color.White)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .sizeIn(minHeight = 48.dp)
                ) {
                    Text(
                        text = "Adding to wardrobe!",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = TryFitColors.TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    IconButton(
                        onClick = ::dismiss,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = TryFitColors.TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                WardrobeOrb(
                    phase = phase,
                    modifier = Modifier.size(130.dp)
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = when (phase) {
                        AddPhase.Adding -> "Saving ${product.name}…"
                        AddPhase.Success -> "${product.name} saved!"
                        AddPhase.Error -> "Couldn't save — check your connection and retry."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TryFitColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                if (phase == AddPhase.Error) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(TryFitColors.TextPrimary)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = { attempt += 1 }
                            )
                            .padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Cancel pill — light gray, full width, 52dp.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(TryFitColors.SurfaceThumb)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = ::dismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                        color = TryFitColors.TextPrimary
                    )
                }

                // Home-indicator spacing.
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/**
 * Glassy iridescent AI sphere: layered radial gradients (pink #F9A8D4
 * top-left, purple #A855F7 mid, blue #3B82F6 bottom-right, white core glow),
 * a rotated white glossy highlight (alpha 0.85), and a thin bright rim.
 * Breathing scale 1.0↔1.05 (2400ms) + gentle ±6dp vertical float. While
 * [AddPhase.Adding]: rotating shimmer sweep; on [AddPhase.Success]: a
 * checkmark morphs over the orb.
 */
@Composable
private fun WardrobeOrb(
    phase: AddPhase,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "wardrobeOrb")
    val breathe by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )
    val floatY by infinite.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (phase == AddPhase.Success) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "check"
    )

    Canvas(
        modifier = modifier.graphicsLayer {
            scaleX = breathe
            scaleY = breathe
            translationY = floatY
        }
    ) {
        val radius = size.minDimension / 2f
        val c = center

        // Base + layered iridescent gradients.
        drawCircle(color = Color.White, radius = radius, center = c)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFF9A8D4), Color.Transparent),
                center = c + Offset(-radius * 0.45f, -radius * 0.45f),
                radius = radius * 1.15f
            ),
            radius = radius,
            center = c
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFA855F7).copy(alpha = 0.85f), Color.Transparent),
                center = c,
                radius = radius * 1.2f
            ),
            radius = radius,
            center = c
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF3B82F6), Color.Transparent),
                center = c + Offset(radius * 0.5f, radius * 0.55f),
                radius = radius * 1.1f
            ),
            radius = radius,
            center = c
        )
        // White core glow.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.9f), Color.Transparent),
                center = c + Offset(-radius * 0.1f, -radius * 0.15f),
                radius = radius * 0.55f
            ),
            radius = radius,
            center = c
        )
        // Glossy highlight: rotated rounded bar, top-left, alpha 0.85.
        rotate(degrees = -28f, pivot = c) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(c.x - radius * 0.62f, c.y - radius * 0.8f),
                size = Size(radius * 0.32f, radius * 0.86f),
                cornerRadius = CornerRadius(radius * 0.16f, radius * 0.16f)
            )
        }
        // Thin bright rim.
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = radius - 1.dp.toPx(),
            center = c,
            style = Stroke(width = 2.dp.toPx())
        )

        // Working state: rotating shimmer sweep.
        if (phase == AddPhase.Adding) {
            rotate(degrees = sweep, pivot = c) {
                drawArc(
                    color = Color.White.copy(alpha = 0.55f),
                    startAngle = -30f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(c.x - radius, c.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Success: checkmark morphs over the orb.
        if (checkScale > 0.02f) {
            val s = radius * 0.5f * checkScale
            drawPath(
                path = Path().apply {
                    moveTo(c.x - s * 0.55f, c.y + s * 0.02f)
                    lineTo(c.x - s * 0.1f, c.y + s * 0.42f)
                    lineTo(c.x + s * 0.62f, c.y - s * 0.42f)
                },
                color = Color(0xFF111111),
                style = Stroke(
                    width = 9.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
