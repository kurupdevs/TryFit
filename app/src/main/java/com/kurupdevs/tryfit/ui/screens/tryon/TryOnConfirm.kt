package com.kurupdevs.tryfit.ui.screens.tryon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.kurupdevs.tryfit.data.catalog.Product
import com.kurupdevs.tryfit.tryon.GarmentSlot
import com.kurupdevs.tryfit.tryon.loadProductImageBytes
import com.kurupdevs.tryfit.tryon.toGarmentSlot
import com.kurupdevs.tryfit.ui.components.AiPreviewLabel
import com.kurupdevs.tryfit.ui.components.rememberAppContainer
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import com.kurupdevs.tryfit.ui.theme.TryFitSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Step 2: garment confirm (SPEC §3.4).
 *
 * Photo + garment(s) side-by-side, size override chips, "add another piece"
 * for the full-outfit mode (research v1.5 item 20). The honest label is
 * always visible; sizes are suggestions, never fit promises.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryOnConfirmStep(
    vm: TryOnFlowViewModel,
    onStart: () -> Unit,
    onBack: () -> Unit,
    onExit: () -> Unit,
) {
    val container = rememberAppContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }

    // Available sizes = union of selected products' sizes (or a sane default).
    var allSizes by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(vm.garments.toList()) {
        val sizes = withContext(Dispatchers.IO) {
            vm.garments.mapNotNull { g ->
                g.productId?.let { runCatching { container.products.byId(it) }.getOrNull()?.sizes }
            }.flatten().distinct()
        }
        allSizes = sizes.ifEmpty { listOf("S", "M", "L", "XL") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TryFitColors.BgScreen)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TryFitSpacing.ScreenPadding),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TryFitColors.TextPrimary,
                )
            }
            Text(
                text = "Confirm your look",
                style = MaterialTheme.typography.titleLarge,
                color = TryFitColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onExit, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close try-on",
                    tint = TryFitColors.TextPrimary,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---- Photo + garments side-by-side ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Your photo
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "You",
                    style = MaterialTheme.typography.labelMedium,
                    color = TryFitColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                vm.photoBytes?.let { bytes ->
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(bytes).build(),
                        contentDescription = "Your photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(TryFitRadii.GridCard))
                            .background(TryFitColors.SurfaceGridImg),
                    )
                }
            }
            // Garments (1–3)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (vm.garments.size > 1) "Full outfit (${vm.garments.size})" else "Garment",
                    style = MaterialTheme.typography.labelMedium,
                    color = TryFitColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                vm.garments.forEach { garment ->
                    GarmentThumb(
                        garment = garment,
                        onRemove = { vm.removeGarment(garment) },
                        showRemove = vm.garments.size > 1,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (vm.garments.size < 3) {
                    AddPieceTile(onClick = { showPicker = true })
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Size override ----
        Text(
            text = "Size (suggestion only)",
            style = MaterialTheme.typography.labelLarge,
            color = TryFitColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(allSizes) { size ->
                FilterChip(
                    selected = vm.sizeOverride == size,
                    onClick = { vm.sizeOverride = if (vm.sizeOverride == size) null else size },
                    label = { Text(size) },
                )
            }
        }
        Text(
            text = "Sizes guide the preview style — they are not a fit promise.",
            style = MaterialTheme.typography.bodySmall,
            color = TryFitColors.TextSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(16.dp))
        AiPreviewLabel()
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (starting) return@Button
                starting = true
                onStart()
            },
            enabled = vm.garments.isNotEmpty() && vm.photoBytes != null,
            shape = TryFitRadii.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = TryFitColors.TextPrimary,
                contentColor = TryFitColors.TextOnPhoto,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            if (starting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = TryFitColors.TextOnPhoto,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(text = "Starting…", style = MaterialTheme.typography.labelLarge)
            } else {
                Text(text = "Start try-on", style = MaterialTheme.typography.labelLarge)
            }
        }
        vm.quota?.let { q ->
            Text(
                text = "${q.remaining} of ${q.limit} free try-ons left today",
                style = MaterialTheme.typography.bodySmall,
                color = TryFitColors.TextSecondary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(100.dp))
    }

    if (showPicker) {
        ProductPickerSheet(
            excludeIds = vm.garments.mapNotNull { it.productId }.toSet(),
            onPick = { product ->
                scope.launch(Dispatchers.IO) {
                    val bytes = loadProductImageBytes(context, product)
                    withContext(Dispatchers.Main) {
                        if (bytes != null) {
                            vm.addGarment(
                                SelectedGarment(
                                    productId = product.id,
                                    name = "${product.brand} ${product.name}",
                                    slot = product.category.toGarmentSlot(),
                                    imageBytes = bytes,
                                )
                            )
                        }
                        showPicker = false
                    }
                }
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun GarmentThumb(
    garment: SelectedGarment,
    onRemove: () -> Unit,
    showRemove: Boolean,
) {
    val context = LocalContext.current
    Box {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(context).data(garment.imageBytes).size(400).build(),
                contentDescription = garment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(TryFitRadii.GridCard))
                    .background(TryFitColors.SurfaceGridImg),
            )
            Text(
                text = garment.name,
                style = MaterialTheme.typography.labelSmall,
                color = TryFitColors.TextSecondary,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (showRemove) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .background(Color.White, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove garment",
                    tint = TryFitColors.TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AddPieceTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(TryFitRadii.GridCard))
            .border(1.dp, TryFitColors.BgCanvas, RoundedCornerShape(TryFitRadii.GridCard))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = TryFitColors.TextSecondary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = "Add piece",
                style = MaterialTheme.typography.labelMedium,
                color = TryFitColors.TextSecondary,
            )
            Text(
                text = "top + bottom + shoes",
                style = MaterialTheme.typography.bodySmall,
                color = TryFitColors.TextSecondary,
            )
        }
    }
}

/** Bottom-sheet product picker for the multi-garment outfit builder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductPickerSheet(
    excludeIds: Set<String>,
    onPick: (Product) -> Unit,
    onDismiss: () -> Unit,
) {
    val container = rememberAppContainer()
    val context = LocalContext.current
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }

    LaunchedEffect(Unit) {
        products = runCatching { container.products.all() }
            .getOrDefault(emptyList())
            .filter { it.id !in excludeIds }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TryFitColors.BgScreen,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Add a piece",
                style = MaterialTheme.typography.titleMedium,
                color = TryFitColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
            ) {
                items(products, key = { it.id }) { product ->
                    val resId = remember(product) { container.products.drawableRes(product) }
                    Card(
                        onClick = { onPick(product) },
                        shape = RoundedCornerShape(TryFitRadii.GridCard),
                        colors = CardDefaults.cardColors(containerColor = TryFitColors.SurfaceCard),
                        modifier = Modifier.width(130.dp),
                    ) {
                        Column {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(resId).size(300).build(),
                                contentDescription = product.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(3f / 4f)
                                    .background(TryFitColors.SurfaceGridImg),
                            )
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = TryFitColors.TextPrimary,
                                maxLines = 1,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
