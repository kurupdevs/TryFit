package com.kurupdevs.tryfit.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kurupdevs.tryfit.R
import com.kurupdevs.tryfit.data.catalog.Product
import com.kurupdevs.tryfit.data.catalog.ProductCategory
import com.kurupdevs.tryfit.ui.components.AddingToWardrobeSheet
import com.kurupdevs.tryfit.ui.components.ProductImage
import com.kurupdevs.tryfit.ui.components.ProductQuickViewSheet
import com.kurupdevs.tryfit.ui.components.StyledForYouSection
import com.kurupdevs.tryfit.ui.components.backgroundBlur
import com.kurupdevs.tryfit.ui.components.containerViewModel
import com.kurupdevs.tryfit.ui.components.rememberAppContainer
import com.kurupdevs.tryfit.ui.components.tryFitShimmer
import com.kurupdevs.tryfit.ui.screens.checkout.CheckoutSheet
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import com.kurupdevs.tryfit.ui.theme.TryFitSpacing
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Home feed — SPEC §3.2, reference mockup screen 2.
 *
 * Status greeting ("Good Morning! ☀️" / "Hello, {name}"), bell with red dot,
 * avatar; category chips (All/Casual/Jackets/Shoes/Bags/Tops/Ethnic) with
 * SPEC §4 chip animation; vertical feed of 4:5 hero cards (28–32dp radius,
 * "Add to wardrobe" pill with press/spring, glass overlay with brand/name/
 * ₹price + "Try-On" pill); thumbnail strip per card (56dp tiles, 2dp gradient
 * border on selection); next card peeking; pull-to-refresh (600ms min);
 * shimmer skeletons on load; entrance stagger once; floating nav hides on
 * scroll-down past 12dp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onProductClick: (String) -> Unit,
    onTryOnClick: (String) -> Unit,
    onTryLook: (List<String>) -> Unit,
    onNotificationsClick: () -> Unit,
    onWardrobeClick: () -> Unit,
    onNavVisibilityChange: (Boolean) -> Unit
) {
    val container = rememberAppContainer()
    val vm: HomeViewModel = containerViewModel { c ->
        HomeViewModel(c.catalogStore, c.wardrobeRepository, c.settingsStore)
    }

    val products by vm.products.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val userName by vm.userName.collectAsStateWithLifecycle()
    val savedIds by vm.savedIds.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val showShimmer by vm.showShimmer.collectAsStateWithLifecycle()
    val showError by vm.showError.collectAsStateWithLifecycle()
    val unreadCount by container.notificationsRepository.observeUnreadCount()
        .collectAsStateWithLifecycle(initialValue = 0)

    var quickView by remember { mutableStateOf<Product?>(null) }
    var addingProduct by remember { mutableStateOf<Product?>(null) }
    var checkout by remember { mutableStateOf<Pair<Product, String>?>(null) }
    var snackbarProduct by remember { mutableStateOf<Product?>(null) }

    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Hide the floating nav on scroll-down past 12dp, show on scroll-up (SPEC §4).
    LaunchedEffect(listState) {
        var lastIndex = 0
        var lastOffset = 0
        val thresholdPx = with(density) { 12.dp.toPx() }
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val dy = (index - lastIndex) * 100_000 + (offset - lastOffset)
                if (dy > thresholdPx) onNavVisibilityChange(false)
                else if (dy < -thresholdPx) onNavVisibilityChange(true)
                lastIndex = index
                lastOffset = offset
            }
    }

    // Entrance stagger — once only (SPEC §4).
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val sheetOpen = quickView != null || addingProduct != null || checkout != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TryFitColors.BgScreen)
    ) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier
                .fillMaxSize()
                .backgroundBlur(sheetOpen)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = TryFitSpacing.FeedBottomClearance)
            ) {
                item(key = "header") {
                    HomeHeader(
                        userName = userName,
                        unreadCount = unreadCount,
                        onNotificationsClick = onNotificationsClick
                    )
                }

                item(key = "chips") {
                    CategoryChips(
                        selected = category,
                        onSelect = vm::selectCategory
                    )
                }

                when {
                    showShimmer -> {
                        items(2, key = { "shimmer$it" }, contentType = { "shimmer" }) {
                            ShimmerHeroCard()
                        }
                    }
                    showError -> {
                        item(key = "error", contentType = "error") {
                            FeedErrorState(onRetry = vm::refresh)
                        }
                    }
                    products.isEmpty() -> {
                        item(key = "empty", contentType = "empty") {
                            FeedEmptyState(
                                category = category,
                                onReset = { vm.selectCategory(null) }
                            )
                        }
                    }
                    else -> {
                        items(
                            items = products,
                            key = { it.id },
                            contentType = { "hero" }
                        ) { product ->
                            val index = products.indexOf(product)
                            with(sharedTransitionScope) {
                                HeroCard(
                                    product = product,
                                    index = index,
                                    entered = entered,
                                    saved = savedIds.contains(product.id),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onOpen = { onProductClick(product.id) },
                                    onAdd = { addingProduct = product },
                                    onTryOn = { onTryOnClick(product.id) }
                                )
                            }
                            ThumbStrip(
                                product = product,
                                all = products,
                                onThumbClick = { quickView = it }
                            )
                        }
                        // "Styled for You" auto-collage from saved items (hides itself
                        // unless at least 4 saved products exist).
                        item(key = "styledForYou", contentType = "styled") {
                            StyledForYouSection(
                                products = products.filter { savedIds.contains(it.id) },
                                onTryLook = onTryLook,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Sheets (each carries its own BlurScrim; the feed behind is blurred
        // via backgroundBlur above).
        quickView?.let { product ->
            ProductQuickViewSheet(
                product = product,
                onDismiss = { quickView = null },
                onAddedToWardrobe = { snackbarProduct = product },
                onBuyNow = { p, size ->
                    quickView = null
                    checkout = p to size
                }
            )
        }

        addingProduct?.let { product ->
            AddingToWardrobeSheet(
                product = product,
                onDismiss = { addingProduct = null },
                onAdded = { snackbarProduct = product }
            )
        }

        checkout?.let { (product, size) ->
            CheckoutSheet(
                product = product,
                size = size,
                onDismiss = { checkout = null },
                onOrderPlaced = {
                    checkout = null
                    onNotificationsClick()
                }
            )
        }

        SavedSnackbar(
            product = snackbarProduct,
            onDismiss = { snackbarProduct = null },
            onView = {
                snackbarProduct = null
                onWardrobeClick()
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun HomeHeader(
    userName: String,
    unreadCount: Int,
    onNotificationsClick: () -> Unit
) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(hour) {
        when (hour) {
            in 5..11 -> "Good Morning! ☀️"
            in 12..16 -> "Good Afternoon! ☀️"
            in 17..21 -> "Good Evening! 🌙"
            else -> "Hello! 🌙"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = TryFitSpacing.ScreenPadding,
                end = TryFitSpacing.ScreenPadding,
                top = 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = TryFitColors.TextSecondary
            )
            Text(
                text = "Hello, ${userName.ifBlank { "Guest" }}",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                color = TryFitColors.TextPrimary
            )
        }

        IconButton(
            onClick = onNotificationsClick,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = stringResource(R.string.notifications),
                    tint = TryFitColors.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(TryFitColors.AccentBadge)
                    )
                }
            }
        }

        Spacer(Modifier.width(4.dp))

        // Guest avatar: initial in a thumb tile with a white ring.
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(TryFitColors.SurfaceThumb)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = userName.firstOrNull()?.uppercase() ?: "G",
                style = MaterialTheme.typography.labelLarge,
                color = TryFitColors.TextSecondary
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Category chips (SPEC §4: 180ms color + selected padding 12dp vs 8dp)
// ---------------------------------------------------------------------------

@Composable
private fun CategoryChips(
    selected: ProductCategory?,
    onSelect: (ProductCategory?) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val options = remember {
        listOf(null to "All") + ProductCategory.entries.map { it to it.label }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = TryFitSpacing.HeaderToChips),
        horizontalArrangement = Arrangement.spacedBy(TryFitSpacing.ChipGap),
        contentPadding = PaddingValues(horizontal = TryFitSpacing.ScreenPadding)
    ) {
        items(options, key = { (_, label) -> label }) { (category, label) ->
            val isSelected = selected == category
            val bg by animateColorAsState(
                targetValue = if (isSelected) TryFitColors.SurfaceChipSelected else Color.Transparent,
                animationSpec = tween(180),
                label = "chipBg"
            )
            val hPad by animateDpAsState(
                targetValue = if (isSelected) 12.dp else 8.dp,
                animationSpec = tween(180),
                label = "chipPad"
            )
            Box(
                modifier = Modifier
                    .sizeIn(minHeight = 48.dp, minWidth = 48.dp)
                    .clip(TryFitRadii.Pill)
                    .background(bg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(category)
                        }
                    )
                    .padding(horizontal = hPad, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    color = TryFitColors.TextPrimary
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Hero card
// ---------------------------------------------------------------------------

@Composable
private fun SharedTransitionScope.HeroCard(
    product: Product,
    index: Int,
    entered: Boolean,
    saved: Boolean,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onTryOn: () -> Unit
) {
    // Entrance stagger: fade + slideUp 24dp, 60ms stagger, once (SPEC §4).
    val cardAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(350, delayMillis = (index * 60).coerceAtMost(600)),
        label = "cardAlpha"
    )
    val cardRise by animateFloatAsState(
        targetValue = if (entered) 0f else 24f,
        animationSpec = tween(350, delayMillis = (index * 60).coerceAtMost(600)),
        label = "cardRise"
    )
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TryFitSpacing.ScreenPadding)
            .padding(top = if (index == 0) 12.dp else TryFitSpacing.HeroGap)
            .graphicsLayer {
                alpha = cardAlpha
                translationY = with(density) { cardRise.dp.toPx() }
            }
            .clip(RoundedCornerShape(TryFitRadii.HeroCard))
            .background(TryFitColors.BgCanvas)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onOpen
            )
            .aspectRatio(4f / 5f)
    ) {
        ProductImage(
            imageRef = "asset://${product.imageResName}",
            contentDescription = "${product.brand} ${product.name}",
            modifier = Modifier
                .fillMaxSize()
                .sharedElement(
                    sharedContentState = rememberSharedContentState(key = "product-image/${product.id}"),
                    animatedVisibilityScope = animatedVisibilityScope
                )
        )

        // Bottom vignette for overlay legibility.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.38f)
                    )
                )
        )

        // "Add to wardrobe" pill, top-right — press 0.96 (80ms) → spring back (SPEC §4).
        AddToWardrobePill(
            saved = saved,
            onAdd = onAdd,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        )

        // Glass overlay: brand / name / ₹price + Try-On pill.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(TryFitSpacing.OverlayInset)
                .clip(RoundedCornerShape(TryFitRadii.GlassOverlay))
                .background(TryFitColors.SurfaceOverlay)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.brand,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = TryFitColors.TextSecondary
                )
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TryFitColors.TextPrimary
                )
                Text(
                    text = product.priceLabel,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TryFitColors.TextPrimary
                )
            }

            Spacer(Modifier.width(8.dp))

            // Try-On pill (white, dark text).
            Row(
                modifier = Modifier
                    .sizeIn(minHeight = 48.dp)
                    .shadow(2.dp, TryFitRadii.Pill, spotColor = Color.Black.copy(alpha = 0.15f))
                    .clip(TryFitRadii.Pill)
                    .background(Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onTryOn
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = TryFitColors.TextPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.try_on),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TryFitColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun AddToWardrobePill(
    saved: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = if (pressed) {
            tween(80)
        } else {
            spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)
        },
        label = "pillPress"
    )
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .shadow(2.dp, TryFitRadii.Pill, spotColor = Color.Black.copy(alpha = 0.15f))
                .clip(TryFitRadii.Pill)
                .background(Color.White)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            try {
                                tryAwaitRelease()
                            } finally {
                                pressed = false
                            }
                        },
                        onTap = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAdd()
                        }
                    )
                }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.add_to_wardrobe),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = TryFitColors.TextPrimary
            )
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = TryFitColors.TextPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Thumbnail strip
// ---------------------------------------------------------------------------

@Composable
private fun ThumbStrip(
    product: Product,
    all: List<Product>,
    onThumbClick: (Product) -> Unit
) {
    val related = remember(product.id, all) {
        val others = all.filter { it.id != product.id }
        (others.filter { it.category == product.category } +
            others.filter { it.category != product.category }).take(6)
    }
    if (related.isEmpty()) return

    val stripState = rememberLazyListState()
    var selectedId by remember(product.id) { mutableStateOf<String?>(null) }

    // Auto-scroll the selected thumb into view (SPEC §4).
    LaunchedEffect(selectedId) {
        selectedId?.let { id ->
            val index = related.indexOfFirst { it.id == id }
            if (index >= 0) stripState.animateScrollToItem(index)
        }
    }

    LazyRow(
        state = stripState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(TryFitSpacing.ThumbGap),
        contentPadding = PaddingValues(horizontal = TryFitSpacing.ScreenPadding)
    ) {
        items(related, key = { it.id }, contentType = { "thumb" }) { item ->
            val isSelected = item.id == selectedId
            val tileScale by animateFloatAsState(
                targetValue = if (isSelected) 1.06f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "thumbScale"
            )
            Box(
                modifier = Modifier
                    .size(TryFitSpacing.ThumbSize)
                    .graphicsLayer {
                        scaleX = tileScale
                        scaleY = tileScale
                    }
                    .clip(RoundedCornerShape(TryFitRadii.ThumbTile))
                    .background(TryFitColors.SurfaceThumb)
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                2.dp,
                                TryFitColors.OrbGradient,
                                RoundedCornerShape(TryFitRadii.ThumbTile)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            selectedId = item.id
                            onThumbClick(item)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                ProductImage(
                    imageRef = "asset://${item.imageResName}",
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Loading / empty / error states
// ---------------------------------------------------------------------------

@Composable
private fun ShimmerHeroCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TryFitSpacing.ScreenPadding)
            .padding(top = TryFitSpacing.HeroGap)
            .aspectRatio(4f / 5f)
            .clip(RoundedCornerShape(TryFitRadii.HeroCard))
            .tryFitShimmer()
    )
}

@Composable
private fun FeedEmptyState(
    category: ProductCategory?,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (category == null) "No products yet" else "No ${category.label.lowercase()} picks yet",
            style = MaterialTheme.typography.titleMedium,
            color = TryFitColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (category == null) {
                "New drops are on their way — pull down to refresh."
            } else {
                "Try another category, or pull down to refresh."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TryFitColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        if (category != null) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .sizeIn(minHeight = 48.dp)
                    .clip(TryFitRadii.Pill)
                    .background(TryFitColors.SurfaceChipSelected)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onReset
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Show all",
                    style = MaterialTheme.typography.labelLarge,
                    color = TryFitColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun FeedErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Couldn't load the feed",
            style = MaterialTheme.typography.titleMedium,
            color = TryFitColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Check your connection and try again.",
            style = MaterialTheme.typography.bodyMedium,
            color = TryFitColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .sizeIn(minHeight = 48.dp)
                .clip(TryFitRadii.Pill)
                .background(TryFitColors.TextPrimary)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onRetry
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// "Saved to wardrobe" snackbar (slides up 220ms, SPEC §4)
// ---------------------------------------------------------------------------

@Composable
private fun SavedSnackbar(
    product: Product?,
    onDismiss: () -> Unit,
    onView: () -> Unit
) {
    AnimatedVisibility(
        visible = product != null,
        enter = slideInVertically(
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            initialOffsetY = { it }
        ) + fadeIn(tween(150)),
        exit = slideOutVertically(
            animationSpec = tween(200),
            targetOffsetY = { it }
        ) + fadeOut(tween(150)),
        modifier = Modifier.fillMaxSize()
    ) {
        if (product != null) {
            LaunchedEffect(product.id) {
                delay(2600)
                onDismiss()
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 96.dp)
                        .shadow(8.dp, TryFitRadii.Pill)
                        .clip(TryFitRadii.Pill)
                        .background(TryFitColors.NavBg)
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProductImage(
                        imageRef = "asset://${product.imageResName}",
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Saved to wardrobe",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                        color = Color.White,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "View",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = Color.White,
                        modifier = Modifier
                            .sizeIn(minHeight = 48.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = onView
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}
