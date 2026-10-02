package com.kurupdevs.tryfit.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurupdevs.tryfit.data.catalog.Product
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Product quick-view sheet — reference IMAGE B.
 *
 * Trigger: tapping a product thumbnail (home strip, wardrobe grid). The
 * product's full-bleed photo stays behind, blurred + dimmed. White sheet,
 * 26dp top corners, drag handle, slide-up 280ms, swipe-to-dismiss.
 *
 * - Name 19sp/700, price 15sp/600 (₹), divider, description 13sp/400 #6E6E73
 *   (3 lines, ellipsis), "Choose Size" 15sp/700, size chips (44dp min,
 *   selected = #141416/white, 180ms color animation).
 * - "Add to Closet" (light gray, weight 1) → the IMAGE A sheet flow;
 *   "Buy Now" (black, weight 1) → [onBuyNow] with the selected size.
 */
@Composable
fun ProductQuickViewSheet(
    product: Product,
    onDismiss: () -> Unit,
    onAddedToWardrobe: () -> Unit,
    onBuyNow: (product: Product, size: String) -> Unit
) {
    val container = rememberAppContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var visible by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var selectedSize by remember(product.id) {
        mutableStateOf(
            product.sizes.firstOrNull { it.equals("M", ignoreCase = true) }
                ?: product.sizes.firstOrNull()
                ?: "M"
        )
    }
    val dragOffset = remember { Animatable(0f) }

    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(product.id) {
        saved = runCatching { container.wardrobeRepository.isSaved(product.id) }.getOrDefault(false)
    }

    fun dismiss() {
        scope.launch {
            visible = false
            delay(220)
            onDismiss()
        }
    }

    fun shareProduct() {
        val text = "${product.brand} ${product.name} — ${product.priceLabel} on TryFit"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
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
            // Background: full-bleed product photo, blurred + dimmed.
            ProductImage(
                imageRef = "asset://${product.imageResName}",
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .backgroundBlur(enabled = true)
            )
            BlurScrim()

            // Top overlay buttons.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp)
                    .padding(top = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular back button: white 40dp circle, dark chevron.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = ::dismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TryFitColors.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DarkCircleButton(onClick = ::shareProduct) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share ${product.name}",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DarkCircleButton(
                        onClick = {
                            scope.launch {
                                saved = runCatching {
                                    container.wardrobeRepository.toggle(product.id)
                                }.getOrDefault(saved)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (saved) "Remove from wardrobe" else "Save to wardrobe",
                            tint = if (saved) TryFitColors.AccentBadge else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // The sheet.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer { translationY = dragOffset.value }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                scope.launch {
                                    dragOffset.snapTo((dragOffset.value + dragAmount).coerceAtLeast(0f))
                                }
                            },
                            onDragEnd = {
                                scope.launch {
                                    if (dragOffset.value > 240f) {
                                        dismiss()
                                    } else {
                                        dragOffset.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            }
                        )
                    }
                    .shadow(16.dp, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(Color.White)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 14.dp)
            ) {
                // Drag handle.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color(0xFFE4E4E7))
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
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
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TryFitColors.TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = product.priceLabel,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TryFitColors.TextPrimary
                )

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFF1F1F3), thickness = 1.dp)
                Spacer(Modifier.height(10.dp))

                Text(
                    text = quickViewDescription(product),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = TryFitColors.TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Choose Size",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TryFitColors.TextPrimary
                )
                Spacer(Modifier.height(8.dp))

                // Size chips: 44dp min height, selected = #141416/white.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    product.sizes.take(6).forEach { size ->
                        val isSelected = size == selectedSize
                        val bg by animateColorAsState(
                            targetValue = if (isSelected) TryFitColors.NavBg else TryFitColors.SurfaceThumb,
                            animationSpec = tween(180),
                            label = "sizeChip"
                        )
                        Box(
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(bg)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    onClick = { selectedSize = size }
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = size,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = if (isSelected) Color.White else TryFitColors.TextPrimary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Bottom row: Add to Closet + Buy Now.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SheetActionButton(
                        text = "Add to Closet",
                        containerColor = TryFitColors.SurfaceThumb,
                        contentColor = TryFitColors.TextPrimary,
                        onClick = { adding = true },
                        modifier = Modifier.weight(1f)
                    )
                    SheetActionButton(
                        text = "Buy Now",
                        containerColor = TryFitColors.NavBg,
                        contentColor = Color.White,
                        onClick = { onBuyNow(product, selectedSize) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }

    // IMAGE A flow, embedded: sheet first, host snackbar after dismiss.
    if (adding) {
        AddingToWardrobeSheet(
            product = product,
            onDismiss = { adding = false },
            onAdded = {
                adding = false
                onAddedToWardrobe()
            }
        )
    }
}

/** Dark circular overlay button (48dp touch target, 40dp visual, #141416 at 70%). */
@Composable
private fun DarkCircleButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xFF141416).copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/** Bottom-row sheet action button (52dp pill). */
@Composable
private fun SheetActionButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(containerColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = contentColor
        )
    }
}

/**
 * Honest 2–3 line description composed only from the product's own fields —
 * never invented attributes, never lorem ipsum.
 */
private fun quickViewDescription(product: Product): String {
    val tags = product.tags.take(3).joinToString(", ")
    val base = "${product.name} by ${product.brand} — a ${product.category.label.lowercase()} " +
        "pick you can preview on yourself with AI try-on."
    return if (tags.isNotBlank()) "$base Tags: $tags." else base
}
