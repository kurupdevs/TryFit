package com.kurupdevs.tryfit.ui.screens.wardrobe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
// Worker D's sheets — REUSED per the 2026-10-02 scope update (do not duplicate).
// NOTE: Worker D lands these files; call sites below target the exact
// signatures `@Composable fun AddingToWardrobeSheet(product: Product,
// onDismiss: () -> Unit, onAdded: () -> Unit)` and `@Composable fun
// ProductQuickViewSheet(product: Product, onDismiss: () -> Unit)`.
import com.kurupdevs.tryfit.ui.components.AddingToWardrobeSheet
import com.kurupdevs.tryfit.ui.components.ProductQuickViewSheet
import com.kurupdevs.tryfit.R
import com.kurupdevs.tryfit.data.TryFitSettings
import com.kurupdevs.tryfit.data.catalog.Product
import com.kurupdevs.tryfit.data.catalog.ProductCategory
import com.kurupdevs.tryfit.data.db.TryOnHistoryEntity
import com.kurupdevs.tryfit.ui.components.containerViewModel
import com.kurupdevs.tryfit.ui.components.rememberAppContainer
import com.kurupdevs.tryfit.ui.screens.closet.ClosetTabContent
import com.kurupdevs.tryfit.ui.screens.tryon.ProductPickerSheet
import com.kurupdevs.tryfit.ui.screens.checkout.CheckoutSheet
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import com.kurupdevs.tryfit.ui.theme.TryFitSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wardrobe — SPEC §3.5 + mockup screen 3.
 *
 * Same header/chips/nav as home. Tabs: Items (2-col grid, 3:4 cards,
 * name/₹price, remove, try-on) / History (try-on sessions with thumbnails +
 * status). Item tap opens Worker D's [ProductQuickViewSheet]; all "Add to
 * Closet" actions open Worker D's [AddingToWardrobeSheet].
 */
@Composable
fun WardrobeScreen(
    onProductClick: (String) -> Unit,
    onBrowseProducts: () -> Unit,
    onTryProduct: (productId: String) -> Unit,
    onTryLook: (List<String>) -> Unit,
    onOpenResult: (sessionId: String) -> Unit,
    onNotificationsClick: () -> Unit,
    onStartTryOn: () -> Unit,
) {
    val container = rememberAppContainer()
    val vm: WardrobeViewModel = containerViewModel { WardrobeViewModel(it) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var selectedTab by remember { mutableStateOf(0) }
    var quickViewProduct by remember { mutableStateOf<Product?>(null) }
    var addSheetProduct by remember { mutableStateOf<Product?>(null) }
    var showProductPicker by remember { mutableStateOf(false) }
    var checkout by remember { mutableStateOf<Pair<Product, String>?>(null) }

    val settings by container.settingsStore.settings.collectAsState(initial = TryFitSettings())
    val userName = settings.userName.ifBlank { "Guest" }

    Scaffold(
        containerColor = TryFitColors.BgScreen,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            // Same header as home (SPEC §3.5).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = TryFitSpacing.ScreenPadding,
                        end = TryFitSpacing.ScreenPadding,
                        top = 12.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your closet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TryFitColors.TextSecondary,
                    )
                    Text(
                        text = "Hello, $userName",
                        style = MaterialTheme.typography.titleLarge,
                        color = TryFitColors.TextPrimary,
                    )
                }
                IconButton(onClick = onNotificationsClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = "Notifications",
                        tint = TryFitColors.TextPrimary,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Category chips (filter the Items tab).
            val chips = listOf(null to "All") + ProductCategory.entries.map { it to it.label }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(TryFitSpacing.ChipGap),
                contentPadding = PaddingValues(horizontal = TryFitSpacing.ScreenPadding),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                items(chips) { (category, label) ->
                    val selected = vm.categoryFilter == category
                    FilterChip(
                        selected = selected,
                        onClick = { vm.setCategoryFilter(category) },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) TryFitColors.TextPrimary else TryFitColors.TextSecondary,
                            )
                        },
                        shape = TryFitRadii.Pill,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TryFitColors.SurfaceChipSelected,
                            containerColor = Color.Transparent,
                        ),
                        border = null,
                    )
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = TryFitColors.BgScreen,
                contentColor = TryFitColors.TextPrimary,
                modifier = Modifier.padding(top = 4.dp),
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(positions[selectedTab]),
                        color = TryFitColors.TextPrimary,
                    )
                },
            ) {
                listOf("Items", "History", "My Closet").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selectedTab == index)
                                    TryFitColors.TextPrimary
                                else
                                    TryFitColors.TextSecondary,
                            )
                        },
                    )
                }
            }

            when {
                vm.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = TryFitColors.TextPrimary)
                }
                selectedTab == 0 -> ItemsGrid(
                    items = vm.filteredItems(),
                    onItemClick = { row ->
                        // Grid item tap -> Worker D's quick-view sheet.
                        row.product?.let { quickViewProduct = it }
                    },
                    onTryOn = { row ->
                        row.product?.let { onTryProduct(it.id) }
                    },
                    onRemove = { row -> vm.remove(row.productId) },
                    onBrowseProducts = onBrowseProducts,
                    onAdd = { showProductPicker = true },
                )
                selectedTab == 1 -> HistoryList(
                    history = vm.history,
                    onStartTryOn = onStartTryOn,
                    onOpen = { entry ->
                        if (entry.status == "DONE" && entry.resultPath != null) {
                            onOpenResult(entry.sessionId)
                        } else {
                            scope.launch { snackbar.showSnackbar("This try-on didn't produce a result.") }
                        }
                    },
                    onDelete = { vm.deleteHistory(it.sessionId) },
                    onSaveToWardrobe = { entry ->
                        scope.launch(Dispatchers.IO) {
                            val product = entry.productId?.let {
                                runCatching { container.products.byId(it) }.getOrNull()
                            }
                            withContext(Dispatchers.Main) {
                                if (product != null) addSheetProduct = product
                                else scope.launch { snackbar.showSnackbar("That product is no longer in the catalog.") }
                            }
                        }
                    },
                )
                else -> ClosetTabContent(
                    onStyleWith = onTryLook,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    // ---- Worker D's sheets (reused) ----
    quickViewProduct?.let { product ->
        ProductQuickViewSheet(
            product = product,
            onDismiss = { quickViewProduct = null },
            onAddedToWardrobe = {
                quickViewProduct = null
                scope.launch { snackbar.showSnackbar("Saved to wardrobe") }
            },
            onBuyNow = { p, size ->
                quickViewProduct = null
                checkout = p to size
            },
        )
    }
    addSheetProduct?.let { product ->
        AddingToWardrobeSheet(
            product = product,
            onDismiss = { addSheetProduct = null },
            onAdded = {
                addSheetProduct = null
                scope.launch { snackbar.showSnackbar("Saved to wardrobe") }
            },
        )
    }
    if (showProductPicker) {
        ProductPickerSheet(
            excludeIds = vm.items.map { it.productId }.toSet(),
            onPick = { product ->
                showProductPicker = false
                addSheetProduct = product
            },
            onDismiss = { showProductPicker = false },
        )
    }
    checkout?.let { (product, size) ->
        CheckoutSheet(
            product = product,
            size = size,
            onDismiss = { checkout = null },
            onOrderPlaced = {
                checkout = null
                scope.launch { snackbar.showSnackbar("Order placed!") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Items tab
// ---------------------------------------------------------------------------

@Composable
private fun ItemsGrid(
    items: List<WardrobeRowUi>,
    onItemClick: (WardrobeRowUi) -> Unit,
    onTryOn: (WardrobeRowUi) -> Unit,
    onRemove: (WardrobeRowUi) -> Unit,
    onBrowseProducts: () -> Unit,
    onAdd: () -> Unit,
) {
    if (items.isEmpty()) {
        WardrobeEmptyState(
            title = "Your wardrobe is empty",
            subtitle = "Save products you love and they'll live here — ready to try on anytime.",
            ctaText = "Browse products",
            onCta = onBrowseProducts,
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(TryFitSpacing.GridGutter),
        verticalArrangement = Arrangement.spacedBy(TryFitSpacing.GridGutter),
        contentPadding = PaddingValues(
            start = TryFitSpacing.ScreenPadding,
            end = TryFitSpacing.ScreenPadding,
            top = 12.dp,
            bottom = TryFitSpacing.FeedBottomClearance,
        ),
    ) {
        items(items, key = { it.productId }) { row ->
            WardrobeCard(
                row = row,
                onClick = { onItemClick(row) },
                onTryOn = { onTryOn(row) },
                onRemove = { onRemove(row) },
            )
        }
    }
}

@Composable
private fun WardrobeCard(
    row: WardrobeRowUi,
    onClick: () -> Unit,
    onTryOn: () -> Unit,
    onRemove: () -> Unit,
) {
    val container = rememberAppContainer()
    val context = LocalContext.current
    val product = row.product

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(TryFitRadii.GridCard),
        colors = CardDefaults.cardColors(containerColor = TryFitColors.SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Box {
                val resId = remember(product) {
                    product?.let { container.products.drawableRes(it) } ?: 0
                }
                AsyncImage(
                    model = ImageRequest.Builder(context).data(resId).size(500).build(),
                    contentDescription = product?.name ?: "Saved product",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .background(TryFitColors.SurfaceGridImg),
                )
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .background(Color.White, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove from wardrobe",
                        tint = TryFitColors.TextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = product?.name ?: "Saved item",
                    style = MaterialTheme.typography.labelLarge,
                    color = TryFitColors.TextPrimary,
                    maxLines = 1,
                )
                Text(
                    text = product?.priceLabel ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TryFitColors.TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onTryOn,
                    enabled = product != null,
                    shape = TryFitRadii.Pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TryFitColors.TextPrimary,
                        contentColor = TryFitColors.TextOnPhoto,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Try on", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Hanger-illustration empty state (SPEC §3.5). */
@Composable
fun WardrobeEmptyState(
    title: String,
    subtitle: String,
    ctaText: String,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_hanger),
            contentDescription = null,
            colorFilter = ColorFilter.tint(TryFitColors.TextSecondary),
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TryFitColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TryFitColors.TextSecondary,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onCta,
            shape = TryFitRadii.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = TryFitColors.TextPrimary,
                contentColor = TryFitColors.TextOnPhoto,
            ),
            modifier = Modifier.height(52.dp),
        ) {
            Text(text = ctaText, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ---------------------------------------------------------------------------
// History tab
// ---------------------------------------------------------------------------

@Composable
private fun HistoryList(
    history: List<TryOnHistoryEntity>,
    onStartTryOn: () -> Unit,
    onOpen: (TryOnHistoryEntity) -> Unit,
    onDelete: (TryOnHistoryEntity) -> Unit,
    onSaveToWardrobe: (TryOnHistoryEntity) -> Unit,
) {
    if (history.isEmpty()) {
        WardrobeEmptyState(
            title = "No try-ons yet",
            subtitle = "Your try-on sessions will appear here with their results.",
            ctaText = "Start a try-on",
            onCta = onStartTryOn,
        )
        return
    }
    androidx.compose.foundation.lazy.LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(
            start = TryFitSpacing.ScreenPadding,
            end = TryFitSpacing.ScreenPadding,
            top = 12.dp,
            bottom = TryFitSpacing.FeedBottomClearance,
        ),
    ) {
        items(history, key = { it.sessionId }) { entry ->
            HistoryCard(
                entry = entry,
                onOpen = { onOpen(entry) },
                onDelete = { onDelete(entry) },
                onSaveToWardrobe = { onSaveToWardrobe(entry) },
            )
        }
    }
}

@Composable
private fun HistoryCard(
    entry: TryOnHistoryEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onSaveToWardrobe: () -> Unit,
) {
    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }

    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(TryFitRadii.GridCard),
        colors = CardDefaults.cardColors(containerColor = TryFitColors.SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thumbFile = entry.resultPath?.let { java.io.File(it).takeIf { f -> f.exists() } }
                ?: entry.inputPath?.let { java.io.File(it).takeIf { f -> f.exists() } }
            AsyncImage(
                model = ImageRequest.Builder(context).data(thumbFile).size(300).build(),
                contentDescription = entry.productName ?: "Try-on",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(TryFitRadii.ThumbTile))
                    .background(TryFitColors.SurfaceThumb),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.productName ?: "Custom try-on",
                    style = MaterialTheme.typography.labelLarge,
                    color = TryFitColors.TextPrimary,
                    maxLines = 1,
                )
                Text(
                    text = dateFmt.format(Date(entry.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = TryFitColors.TextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                StatusChip(status = entry.status)
            }
            if (entry.status == "DONE" && entry.productId != null) {
                IconButton(onClick = onSaveToWardrobe, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.BookmarkAdd,
                        contentDescription = "Save to wardrobe",
                        tint = TryFitColors.TextPrimary,
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Delete try-on",
                    tint = TryFitColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (label, color) = when (status) {
        "DONE" -> "Ready" to Color(0xFF1F9D55)
        "FAILED" -> "Failed" to Color(0xFFD64545)
        "CANCELLED" -> "Cancelled" to TryFitColors.TextSecondary
        else -> "Processing" to Color(0xFFB7791F)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier
            .clip(CircleShape)
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
