package com.kurupdevs.tryfit.ui.screens.closet

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.kurupdevs.tryfit.ui.components.rememberAppContainer
import com.kurupdevs.tryfit.data.closet.CLOSET_CATEGORIES
import com.kurupdevs.tryfit.data.closet.ClosetItem
import com.kurupdevs.tryfit.data.closet.ClosetRepository
import com.kurupdevs.tryfit.ui.components.OutfitCalendar
import com.kurupdevs.tryfit.ui.components.WearTrackerCard
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import com.kurupdevs.tryfit.ui.theme.TryFitSpacing
import kotlinx.coroutines.launch
import java.io.File

/**
 * "My Closet" tab content — the user's own digitized garments.
 * Worker E: mount this as a third tab ("My Closet") in WardrobeScreen's
 * TabRow. [onStyleWith] receives [catalogProductId, "closet:<id>"] for
 * multi-garment try-on.
 */
@Composable
fun ClosetTabContent(
    onStyleWith: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val repo = rememberAppContainer().closet
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val items by repo.items.collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf<ClosetItem?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    if (selected != null) {
        ClosetItemDetail(
            item = selected!!,
            repo = repo,
            onBack = { selected = null },
            onDelete = {
                scope.launch {
                    repo.delete(selected!!)
                    selected = null
                }
            },
            onStyleWith = onStyleWith
        )
        return
    }

    Scaffold(
        containerColor = TryFitColors.BgScreen,
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showAdd = true
                },
                containerColor = TryFitColors.TextPrimary,
                contentColor = TryFitColors.TextOnPhoto
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add my item")
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(TryFitSpacing.ScreenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Your closet is empty",
                    style = MaterialTheme.typography.titleMedium,
                    color = TryFitColors.TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Snap your own clothes — then style them with catalog pieces.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TryFitColors.TextSecondary
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showAdd = true },
                    shape = TryFitRadii.Pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TryFitColors.TextPrimary,
                        contentColor = TryFitColors.TextOnPhoto
                    )
                ) {
                    Text("Add my first item", style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(TryFitSpacing.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    ClosetGridCard(item = item, repo = repo, onClick = { selected = item })
                }
            }
        }
    }

    if (showAdd) {
        AddClosetItemSheet(
            repo = repo,
            onDismiss = { showAdd = false },
            onSaved = { showAdd = false }
        )
    }
}

@Composable
private fun ClosetGridCard(item: ClosetItem, repo: ClosetRepository, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        AsyncImage(
            model = repo.photoFile(item),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(14.dp))
                .background(TryFitColors.SurfaceGridImg)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.labelMedium,
            color = TryFitColors.TextPrimary,
            maxLines = 1
        )
        Text(
            text = item.category,
            style = MaterialTheme.typography.labelSmall,
            color = TryFitColors.TextSecondary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddClosetItemSheet(
    repo: ClosetRepository,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CLOSET_CATEGORIES.first()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) photoUri = uri }

    val pendingCameraFile = remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraFile.value?.let { photoUri = Uri.fromFile(it) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TryFitColors.BgScreen
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TryFitSpacing.ScreenPadding)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Add my item",
                style = MaterialTheme.typography.titleLarge,
                color = TryFitColors.TextPrimary
            )
            Spacer(Modifier.height(12.dp))

            if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = "Item photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(TryFitColors.SurfaceGridImg)
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        galleryLauncher.launch("image/*")
                    },
                    shape = TryFitRadii.Pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TryFitColors.BgCanvas,
                        contentColor = TryFitColors.TextPrimary
                    )
                ) {
                    Text("Gallery", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val file = File(context.filesDir, "closet/capture_${System.currentTimeMillis()}.jpg")
                            .also { it.parentFile?.mkdirs() }
                        pendingCameraFile.value = file
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        cameraLauncher.launch(uri)
                    },
                    shape = TryFitRadii.Pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TryFitColors.BgCanvas,
                        contentColor = TryFitColors.TextPrimary
                    )
                ) {
                    Text("Camera", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(12.dp))

            TextField(
                value = name,
                onValueChange = { name = it.take(60) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Name it — e.g. Blue denim jacket", color = TryFitColors.TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = TryFitColors.BgCanvas,
                    unfocusedContainerColor = TryFitColors.BgCanvas,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelMedium,
                color = TryFitColors.TextSecondary
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CLOSET_CATEGORIES.take(4).forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TryFitColors.SurfaceChipSelected,
                            containerColor = Color.Transparent,
                            selectedLabelColor = TryFitColors.TextPrimary,
                            labelColor = TryFitColors.TextSecondary
                        ),
                        shape = TryFitRadii.Pill,
                        border = null
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CLOSET_CATEGORIES.drop(4).forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TryFitColors.SurfaceChipSelected,
                            containerColor = Color.Transparent,
                            selectedLabelColor = TryFitColors.TextPrimary,
                            labelColor = TryFitColors.TextSecondary
                        ),
                        shape = TryFitRadii.Pill,
                        border = null
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            error?.let {
                Text(text = it, color = Color(0xFFFF3B30), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = {
                    val uri = photoUri ?: return@Button
                    saving = true
                    scope.launch {
                        try {
                            repo.add(uri, name, category)
                            onSaved()
                        } catch (e: Exception) {
                            error = "Couldn't save the photo — try again."
                            saving = false
                        }
                    }
                },
                enabled = photoUri != null && !saving,
                shape = TryFitRadii.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TryFitColors.TextPrimary,
                    contentColor = TryFitColors.TextOnPhoto
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (saving) "Saving…" else "Save to my closet",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClosetItemDetail(
    item: ClosetItem,
    repo: ClosetRepository,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onStyleWith: (List<String>) -> Unit
) {
    val haptics = LocalHapticFeedback.current

    var showPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TryFitColors.BgScreen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TryFitColors.TextPrimary
                )
            }
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = TryFitColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { confirmDelete = true }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete item",
                    tint = TryFitColors.TextSecondary
                )
            }
        }

        AsyncImage(
            model = repo.photoFile(item),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TryFitSpacing.ScreenPadding)
                .height(320.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(TryFitColors.SurfaceGridImg)
        )
        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TryFitSpacing.ScreenPadding)
        ) {
            FilterChip(
                selected = true,
                onClick = {},
                label = { Text(item.category, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TryFitColors.SurfaceChipSelected,
                    selectedLabelColor = TryFitColors.TextPrimary
                ),
                shape = TryFitRadii.Pill,
                border = null
            )
            Spacer(Modifier.height(10.dp))
            // Closet items have no price; cost-per-wear tracks wears only.
            WearTrackerCard(itemId = item.id, priceInr = 0)
            Spacer(Modifier.height(10.dp))
            OutfitCalendar(itemId = item.id)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showPicker = true
                },
                shape = TryFitRadii.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TryFitColors.TextPrimary,
                    contentColor = TryFitColors.TextOnPhoto
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Style with my stuff", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPicker) {
        CatalogPickerSheet(
            onDismiss = { showPicker = false },
            onPick = { productId ->
                showPicker = false
                onStyleWith(listOf(productId, item.tryOnToken))
            }
        )
    }

    if (confirmDelete) {
        ModalBottomSheet(
            onDismissRequest = { confirmDelete = false },
            containerColor = TryFitColors.BgScreen
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(TryFitSpacing.ScreenPadding)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Delete \"${item.name}\"?",
                    style = MaterialTheme.typography.titleMedium,
                    color = TryFitColors.TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "The photo and its wear history are removed from this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TryFitColors.TextSecondary
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onDelete,
                    shape = TryFitRadii.Pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF3B30),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Pick one catalog product to pair with the closet item. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogPickerSheet(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val container = rememberAppContainer()
    var products by remember { mutableStateOf(emptyList<com.kurupdevs.tryfit.data.catalog.Product>()) }
    LaunchedEffect(Unit) {
        products = container.products.all()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TryFitColors.BgScreen
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TryFitSpacing.ScreenPadding)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Pick a catalog piece to pair",
                style = MaterialTheme.typography.titleLarge,
                color = TryFitColors.TextPrimary
            )
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(products, key = { it.id }) { product ->
                    val resId = container.products.drawableRes(product)
                    Box(
                        modifier = Modifier
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TryFitColors.SurfaceGridImg)
                            .clickable { onPick(product.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (resId != 0) {
                            AsyncImage(
                                model = resId,
                                contentDescription = product.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = TryFitColors.TextSecondary,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
