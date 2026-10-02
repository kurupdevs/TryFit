package com.kurupdevs.tryfit.ui.screens.product

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurupdevs.tryfit.R
import com.kurupdevs.tryfit.data.AffiliateLinks
import com.kurupdevs.tryfit.data.catalog.Product
import com.kurupdevs.tryfit.ui.components.AddingToWardrobeSheet
import com.kurupdevs.tryfit.ui.components.AiPreviewLabel
import com.kurupdevs.tryfit.ui.components.BlurScrim
import com.kurupdevs.tryfit.ui.components.PriceAlertRow
import com.kurupdevs.tryfit.ui.components.ProductImage
import com.kurupdevs.tryfit.ui.components.backgroundBlur
import com.kurupdevs.tryfit.ui.components.rememberAppContainer
import com.kurupdevs.tryfit.ui.screens.checkout.CheckoutSheet
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import com.kurupdevs.tryfit.ui.theme.TryFitSpacing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Product detail — SPEC §3.3.
 *
 * Image pager + dots, pinch/double-tap zoom (1–4x via [graphicsLayer] +
 * transformable), brand/name/₹price, size selector chips, description/tags,
 * Try-On + Add-to-wardrobe CTAs, Share, and the affiliate "Buy at:" row
 * (Myntra / Flipkart / Amazon.in / Ajio / Meesho → search URL intents).
 *
 * Entered via shared-element transition on the hero image (340ms,
 * [androidx.compose.animation.core.FastOutSlowInEasing]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductDetailScreen(
    productId: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onTryOn: (String) -> Unit
) {
    val container = rememberAppContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var product by remember(productId) { mutableStateOf<Product?>(null) }
    var notFound by remember(productId) { mutableStateOf(false) }
    var selectedSize by remember(productId) { mutableStateOf<String?>(null) }
    var saved by remember(productId) { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var checkout by remember { mutableStateOf<Pair<Product, String>?>(null) }

    LaunchedEffect(productId) {
        val loaded = runCatching {
            withContext(Dispatchers.IO) { container.products.byId(productId) }
        }.getOrNull()
        product = loaded
        notFound = loaded == null
        if (loaded != null) {
            selectedSize = loaded.sizes.firstOrNull { it.equals("M", ignoreCase = true) }
                ?: loaded.sizes.firstOrNull()
            saved = runCatching { container.wardrobeRepository.isSaved(loaded.id) }.getOrDefault(false)
        }
    }

    val current = product
    val sheetOpen = adding || checkout != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TryFitColors.BgScreen)
    ) {
        when {
            current == null && !notFound -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TryFitColors.TextPrimary)
                }
            }
            current == null -> {
                DetailNotFound(onBack = onBack)
            }
            else -> {
                val p = current
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .backgroundBlur(sheetOpen)
                        .verticalScroll(rememberScrollState())
                ) {
                    with(sharedTransitionScope) {
                        DetailGallery(
                            product = p,
                            animatedVisibilityScope = animatedVisibilityScope,
                            saved = saved,
                            onBack = onBack,
                            onShare = {
                                val text = "${p.brand} ${p.name} — ${p.priceLabel} on TryFit"
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, text)
                                        },
                                        "Share"
                                    )
                                )
                            },
                            onToggleSave = {
                                scope.launch {
                                    saved = runCatching {
                                        container.wardrobeRepository.toggle(p.id)
                                    }.getOrDefault(saved)
                                }
                            }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TryFitSpacing.ScreenPadding)
                            .padding(top = 16.dp, bottom = 32.dp)
                    ) {
                        Text(
                            text = p.brand,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = TryFitColors.TextSecondary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = p.name,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = TryFitColors.TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = p.priceLabel,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = TryFitColors.TextPrimary
                        )

                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.choose_size),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = TryFitColors.TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            p.sizes.forEach { size ->
                                val isSelected = size == selectedSize
                                Box(
                                    modifier = Modifier
                                        .heightIn(min = 44.dp)
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(
                                            if (isSelected) TryFitColors.NavBg else TryFitColors.SurfaceThumb
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            role = Role.RadioButton,
                                            onClick = { selectedSize = size }
                                        )
                                        .padding(horizontal = 18.dp, vertical = 10.dp),
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
                        Text(
                            text = detailDescription(p),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = TryFitColors.TextSecondary
                        )

                        if (p.tags.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                p.tags.take(6).forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(percent = 50))
                                            .background(TryFitColors.BgCanvas)
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TryFitColors.TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        AiPreviewLabel()

                        Spacer(Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DetailCta(
                                text = stringResource(R.string.try_on),
                                containerColor = TryFitColors.NavBg,
                                contentColor = Color.White,
                                onClick = { onTryOn(p.id) },
                                modifier = Modifier.weight(1f)
                            )
                            DetailCta(
                                text = stringResource(R.string.add_to_wardrobe),
                                containerColor = TryFitColors.SurfaceThumb,
                                contentColor = TryFitColors.TextPrimary,
                                onClick = { adding = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        DetailCta(
                            text = stringResource(R.string.buy_now),
                            containerColor = Color.White,
                            contentColor = TryFitColors.TextPrimary,
                            outlined = true,
                            onClick = {
                                checkout = p to (selectedSize ?: p.sizes.firstOrNull() ?: "M")
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(20.dp))
                        PriceAlertRow(
                            productId = p.id,
                            currentPriceInr = p.priceInr,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = "Buy at:",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = TryFitColors.TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AffiliateLinks.all.forEach { retailer ->
                                Box(
                                    modifier = Modifier
                                        .heightIn(min = 44.dp)
                                        .clip(RoundedCornerShape(percent = 50))
                                        .border(
                                            1.dp,
                                            Color(0xFFE4E4E7),
                                            RoundedCornerShape(percent = 50)
                                        )
                                        .background(Color.White)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            role = Role.Button,
                                            onClick = {
                                                val url = retailer.build(p.brand, p.name)
                                                try {
                                                    context.startActivity(
                                                        Intent(
                                                            Intent.ACTION_VIEW,
                                                            android.net.Uri.parse(url)
                                                        )
                                                    )
                                                } catch (_: ActivityNotFoundException) {
                                                    Toast.makeText(
                                                        context,
                                                        "No browser found",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        )
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = retailer.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = TryFitColors.TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (sheetOpen) BlurScrim()

        if (adding && current != null) {
            AddingToWardrobeSheet(
                product = current,
                onDismiss = { adding = false },
                onAdded = {
                    adding = false
                    scope.launch {
                        saved = runCatching {
                            container.wardrobeRepository.isSaved(current.id)
                        }.getOrDefault(true)
                    }
                }
            )
        }

        checkout?.let { (p, size) ->
            CheckoutSheet(
                product = p,
                size = size,
                onDismiss = { checkout = null },
                onOrderPlaced = { checkout = null }
            )
        }
    }
}

/**
 * Gallery: pager + dots, pinch/double-tap zoom 1–4x via graphicsLayer
 * transformable. The hero image carries the shared-element transition.
 */
@Composable
private fun SharedTransitionScope.DetailGallery(
    product: Product,
    animatedVisibilityScope: AnimatedVisibilityScope,
    saved: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onToggleSave: () -> Unit
) {
    // Single bundled image today; the pager is ready for multi-image catalogs.
    val images = remember(product) { listOf("asset://${product.imageResName}") }
    val pagerState = rememberPagerState(pageCount = { images.size })

    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scope.launch {
            val next = (scale.value * zoomChange).coerceIn(1f, 4f)
            scale.snapTo(next)
            offset.snapTo(if (next > 1f) offset.value + panChange else Offset.Zero)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 5f)
            .background(TryFitColors.BgCanvas)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transformState)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                scope.launch {
                                    if (scale.value > 1.5f) {
                                        scale.animateTo(1f)
                                        offset.animateTo(Offset.Zero)
                                    } else {
                                        scale.animateTo(2.5f)
                                    }
                                }
                            }
                        )
                    }
            ) {
                with(this@DetailGallery) {
                    ProductImage(
                        imageRef = images[page],
                        contentDescription = "${product.brand} ${product.name}",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale.value
                                scaleY = scale.value
                                translationX = offset.value.x
                                translationY = offset.value.y
                            }
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "product-image/${product.id}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                    )
                }
            }
        }

        // Pager dots.
        if (images.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(images.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) Color.White
                                else Color.White.copy(alpha = 0.5f)
                            )
                    )
                }
            }
        }

        // Top overlay: back (white circle) + share/save (dark circles).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp)
                .padding(top = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TryFitColors.TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OverlayCircleButton(onClick = onShare, contentDescription = "Share ${product.name}") {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                OverlayCircleButton(
                    onClick = onToggleSave,
                    contentDescription = if (saved) "Remove from wardrobe" else "Save to wardrobe"
                ) {
                    Icon(
                        imageVector = if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (saved) TryFitColors.AccentBadge else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayCircleButton(
    onClick: () -> Unit,
    contentDescription: String,
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
        content()
    }
}

@Composable
private fun DetailCta(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false
) {
    var modifierChain = modifier
        .height(54.dp)
        .clip(TryFitRadii.Pill)
        .background(containerColor)
    if (outlined) {
        modifierChain = modifierChain.border(1.5.dp, TryFitColors.TextPrimary, TryFitRadii.Pill)
    }
    Box(
        modifier = modifierChain.clickable(
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
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = contentColor
        )
    }
}

@Composable
private fun DetailNotFound(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Product not found",
            style = MaterialTheme.typography.titleMedium,
            color = TryFitColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "This item may have been removed from the catalog.",
            style = MaterialTheme.typography.bodyMedium,
            color = TryFitColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        DetailCta(
            text = "Back",
            containerColor = TryFitColors.TextPrimary,
            contentColor = Color.White,
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Honest description from the product's own fields only. */
private fun detailDescription(product: Product): String {
    val tags = product.tags.take(4).joinToString(", ")
    return buildString {
        append("${product.name} by ${product.brand} — a ${product.category.label.lowercase()} essential. ")
        append("Preview it on yourself with AI try-on before you buy: style preview, not a fit guarantee.")
        if (tags.isNotBlank()) append(" Details: $tags.")
    }
}
