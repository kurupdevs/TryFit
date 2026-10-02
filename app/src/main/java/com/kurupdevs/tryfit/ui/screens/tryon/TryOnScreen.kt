package com.kurupdevs.tryfit.ui.screens.tryon

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.kurupdevs.tryfit.data.catalog.Product
import com.kurupdevs.tryfit.ui.components.AiPreviewLabel
import com.kurupdevs.tryfit.ui.components.containerViewModel
import com.kurupdevs.tryfit.ui.components.rememberAppContainer
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import com.kurupdevs.tryfit.ui.theme.TryFitSpacing

/**
 * Try-on tab — SPEC §3.4 entry point.
 *
 * Avatar card ("My Avatar", set once, reused forever) + "New Try-On" CTA +
 * product picker. The 4-step flow itself lives in [TryOnFlowScreen].
 */
@Composable
fun TryOnScreen(
    onNewTryOn: () -> Unit,
    onTryProduct: (productId: String) -> Unit,
    onScreenshotTryOn: () -> Unit,
) {
    val container = rememberAppContainer()
    val vm: TryOnTabViewModel = containerViewModel { TryOnTabViewModel(it) }
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.load(context) }

    Scaffold(containerColor = TryFitColors.BgScreen) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TryFitSpacing.ScreenPadding)
                .padding(bottom = TryFitSpacing.FeedBottomClearance),
        ) {
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Try-On",
                    style = MaterialTheme.typography.titleLarge,
                    color = TryFitColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                vm.quotaText?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        color = TryFitColors.TextSecondary,
                        modifier = Modifier
                            .clip(TryFitRadii.Pill)
                            .background(TryFitColors.SurfaceChipSelected)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- Avatar card ----
            AvatarCard(
                hasAvatar = vm.hasAvatar,
                onSetAvatar = onNewTryOn, // flow source picker doubles as avatar setup
            )

            Spacer(Modifier.height(16.dp))

            // ---- New try-on CTA ----
            Button(
                onClick = onNewTryOn,
                shape = TryFitRadii.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TryFitColors.TextPrimary,
                    contentColor = TryFitColors.TextOnPhoto,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = "New Try-On", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(8.dp))

            // ---- Screenshot entry ----
            Card(
                onClick = onScreenshotTryOn,
                shape = RoundedCornerShape(TryFitRadii.GridCard),
                colors = CardDefaults.cardColors(containerColor = TryFitColors.BgCanvas),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Crop,
                        contentDescription = null,
                        tint = TryFitColors.TextPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Try from screenshot",
                            style = MaterialTheme.typography.labelLarge,
                            color = TryFitColors.TextPrimary,
                        )
                        Text(
                            text = "Crop any outfit photo — influencer post, thrift find, anything",
                            style = MaterialTheme.typography.bodySmall,
                            color = TryFitColors.TextSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Pick a product",
                style = MaterialTheme.typography.titleMedium,
                color = TryFitColors.TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Or start empty and add up to 3 pieces for a full outfit",
                style = MaterialTheme.typography.bodySmall,
                color = TryFitColors.TextSecondary,
            )
            Spacer(Modifier.height(12.dp))

            if (vm.loadingProducts) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = TryFitColors.TextPrimary)
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 4.dp),
                ) {
                    items(vm.products, key = { it.id }) { product ->
                        ProductPickCard(
                            product = product,
                            onTry = { onTryProduct(product.id) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            AiPreviewLabel(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun AvatarCard(hasAvatar: Boolean, onSetAvatar: () -> Unit) {
    val container = rememberAppContainer()
    val context = LocalContext.current
    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(hasAvatar) {
        avatarBytes = if (hasAvatar) {
            runCatching {
                java.io.File(context.filesDir, "avatar/avatar.jpg")
                    .takeIf { it.exists() }?.readBytes()
            }.getOrNull()
        } else null
    }

    Card(
        onClick = onSetAvatar,
        shape = RoundedCornerShape(TryFitRadii.GridCard),
        colors = CardDefaults.cardColors(containerColor = TryFitColors.BgCanvas),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (avatarBytes != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(avatarBytes).build(),
                    contentDescription = "My avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(TryFitColors.SurfaceThumb),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(TryFitColors.SurfaceThumb),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = TryFitColors.TextSecondary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "My Avatar",
                    style = MaterialTheme.typography.labelLarge,
                    color = TryFitColors.TextPrimary,
                )
                Text(
                    text = if (hasAvatar)
                        "Set — reused for every try-on. Tap to change."
                    else
                        "One full-body photo, reused for every try-on forever.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TryFitColors.TextSecondary,
                )
            }
            Icon(
                imageVector = Icons.Filled.AddAPhoto,
                contentDescription = if (hasAvatar) "Change avatar" else "Set avatar",
                tint = TryFitColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun ProductPickCard(product: Product, onTry: () -> Unit) {
    val container = rememberAppContainer()
    val context = LocalContext.current
    val resId = remember(product) { container.products.drawableRes(product) }

    Card(
        shape = RoundedCornerShape(TryFitRadii.GridCard),
        colors = CardDefaults.cardColors(containerColor = TryFitColors.SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.width(150.dp),
    ) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(context).data(resId).size(400).build(),
                contentDescription = product.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(TryFitRadii.GridImageWell))
                    .background(TryFitColors.SurfaceGridImg)
                    .clickable(onClick = onTry),
            )
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = TryFitColors.TextPrimary,
                    maxLines = 1,
                )
                Text(
                    text = product.priceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TryFitColors.TextSecondary,
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onTry,
                    shape = TryFitRadii.Pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TryFitColors.TextPrimary,
                        contentColor = TryFitColors.TextOnPhoto,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Try-On", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
