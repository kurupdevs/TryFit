package com.kurupdevs.tryfit.ui.screens.tryon

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kurupdevs.tryfit.ui.components.PrivacyConsentSheet
import com.kurupdevs.tryfit.ui.components.rememberAppContainer
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import com.kurupdevs.tryfit.ui.theme.TryFitSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

// ---------------------------------------------------------------------------
// Step 1: source picker (SPEC §3.4) — bottom sheet over a quiet backdrop.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryOnSourceStep(
    vm: TryOnFlowViewModel,
    onTakePhoto: () -> Unit,
    onGallery: () -> Unit,
    onScreenshot: () -> Unit,
    onBack: () -> Unit,
) {
    val container = rememberAppContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showConsent by remember { mutableStateOf(false) }
    var consentChecked by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        consentChecked = container.settingsStore.current().cameraConsentGiven
    }

    Box(modifier = Modifier.fillMaxSize().background(TryFitColors.BgScreen)) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(start = 8.dp, top = 12.dp).size(48.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TryFitColors.TextPrimary,
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "How should we see you?",
                style = MaterialTheme.typography.titleLarge,
                color = TryFitColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Pick a full-body photo — your avatar is the fastest option.",
                style = MaterialTheme.typography.bodyMedium,
                color = TryFitColors.TextSecondary,
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onBack,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TryFitColors.BgScreen,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Choose a photo",
                style = MaterialTheme.typography.titleMedium,
                color = TryFitColors.TextPrimary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            SourceRow(
                icon = Icons.Filled.CameraAlt,
                title = "Take a photo",
                subtitle = "Full-body, facing the camera",
                onClick = {
                    if (consentChecked == true) onTakePhoto()
                    else showConsent = true
                },
            )
            SourceRow(
                icon = Icons.Filled.Image,
                title = "Choose from gallery",
                subtitle = "Any clear full-body photo",
                onClick = onGallery,
            )
            SourceRow(
                icon = Icons.Filled.Person,
                title = "Use my avatar",
                subtitle = "Your saved one-photo avatar",
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val bytes = vm.readAvatarBytes(context)
                        withContext(Dispatchers.Main) {
                            if (bytes != null) vm.setPhoto(context, bytes)
                            else {
                                // No avatar yet — send them to set one up via camera/gallery.
                                onGallery()
                            }
                        }
                    }
                },
            )
            SourceRow(
                icon = Icons.Filled.History,
                title = "Use last photo",
                subtitle = "The photo from your previous try-on",
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val bytes = vm.readLastPhoto(context)
                        withContext(Dispatchers.Main) {
                            if (bytes != null) vm.setPhoto(context, bytes)
                            else onGallery()
                        }
                    }
                },
            )
            SourceRow(
                icon = Icons.Filled.Crop,
                title = "Try from screenshot",
                subtitle = "Crop any outfit photo into a garment",
                onClick = onScreenshot,
            )
        }
    }

    if (showConsent) {
        PrivacyConsentSheet(
            onAccept = {
                showConsent = false
                scope.launch {
                    container.settingsStore.setCameraConsent(true)
                    consentChecked = true
                    onTakePhoto()
                }
            },
            onDismiss = { showConsent = false },
        )
    }
}

@Composable
private fun SourceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(TryFitColors.BgCanvas),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TryFitColors.TextPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = TryFitColors.TextPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TryFitColors.TextSecondary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Quality coach (research v1 item 4): tips + graceful fallback, never silent.
// ---------------------------------------------------------------------------

@Composable
fun QualityCoachStep(vm: TryOnFlowViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TryFitColors.BgScreen)
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
                text = "Quick photo check",
                style = MaterialTheme.typography.titleLarge,
                color = TryFitColors.TextPrimary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Card(
            shape = RoundedCornerShape(TryFitRadii.GridCard),
            colors = CardDefaults.cardColors(containerColor = TryFitColors.BgCanvas),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Icon(
                    imageVector = Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = TryFitColors.TextPrimary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "This photo might not give the best preview:",
                    style = MaterialTheme.typography.labelLarge,
                    color = TryFitColors.TextPrimary,
                )
                Spacer(Modifier.height(8.dp))
                vm.qualityIssues.forEach { issue ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(text = "• ", color = TryFitColors.TextSecondary)
                        Text(
                            text = issue.userMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TryFitColors.TextSecondary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.retakePhoto() },
            shape = TryFitRadii.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = TryFitColors.TextPrimary,
                contentColor = TryFitColors.TextOnPhoto,
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(text = "Retake photo", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { vm.useSilhouetteFallback(context) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "No good photo? Try on a model shaped like you",
                style = MaterialTheme.typography.labelLarge,
                color = TryFitColors.TextSecondary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "The silhouette preview shows the style only — your real photo always looks better.",
            style = MaterialTheme.typography.bodySmall,
            color = TryFitColors.TextSecondary,
        )
    }
}

// ---------------------------------------------------------------------------
// Screenshot → garment crop: pan/zoom the photo under a fixed 3:4 box.
// ---------------------------------------------------------------------------

@Composable
fun CropGarmentScreen(
    imageBytes: ByteArray,
    onCropped: (ByteArray) -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }

    val bitmap = remember(imageBytes) {
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        offset += panChange
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val boxW = with(LocalDensity.current) { maxWidth.toPx() }
        val boxH = with(LocalDensity.current) { maxHeight.toPx() }

        // Image layer: explicitly sized + positioned to match the crop math
        // exactly (NOT ContentScale — the transform below IS the layout).
        if (bitmap != null) {
            val density = LocalDensity.current
            val baseScale = maxOf(boxW / bitmap.width, boxH / bitmap.height)
            val drawnW = bitmap.width * baseScale * scale
            val drawnH = bitmap.height * baseScale * scale
            val maxX = (drawnW - boxW).coerceAtLeast(0f) / 2f
            val maxY = (drawnH - boxH).coerceAtLeast(0f) / 2f
            val tx = offset.x.coerceIn(-maxX, maxX)
            val ty = offset.y.coerceIn(-maxY, maxY)
            val drawnLeft = (boxW - drawnW) / 2f + tx
            val drawnTop = (boxH - drawnH) / 2f + ty

            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Screenshot to crop",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .size(
                        width = with(density) { drawnW.toDp() },
                        height = with(density) { drawnH.toDp() },
                    )
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            drawnLeft.roundToInt(),
                            drawnTop.roundToInt(),
                        )
                    }
                    .transformable(transformState),
            )

            // Crop frame: fixed 3:4 box, dimmed surround (4 rects, no blend tricks).
            val cropW = boxW * 0.78f
            val cropH = cropW * 4f / 3f
            val cropLeft = (boxW - cropW) / 2f
            val cropTop = (boxH - cropH) / 2f
            val dim = Color.Black.copy(alpha = 0.55f)
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize().zIndex(1f),
            ) {
                val full = size
                // top / bottom / left / right dim rects around the crop box
                drawRect(dim, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(full.width, cropTop))
                drawRect(dim, topLeft = Offset(0f, cropTop + cropH), size = androidx.compose.ui.geometry.Size(full.width, full.height - cropTop - cropH))
                drawRect(dim, topLeft = Offset(0f, cropTop), size = androidx.compose.ui.geometry.Size(cropLeft, cropH))
                drawRect(dim, topLeft = Offset(cropLeft + cropW, cropTop), size = androidx.compose.ui.geometry.Size(full.width - cropLeft - cropW, cropH))
                drawRect(
                    color = Color.White,
                    topLeft = Offset(cropLeft, cropTop),
                    size = androidx.compose.ui.geometry.Size(cropW, cropH),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }

            fun cropToBytes(): ByteArray? {
                val pxPerUnit = baseScale * scale
                var bx = ((cropLeft - drawnLeft) / pxPerUnit).toInt()
                var by = ((cropTop - drawnTop) / pxPerUnit).toInt()
                var bw = (cropW / pxPerUnit).toInt()
                var bh = (cropH / pxPerUnit).toInt()
                bx = bx.coerceIn(0, bitmap.width - 1)
                by = by.coerceIn(0, bitmap.height - 1)
                bw = bw.coerceIn(1, bitmap.width - bx)
                bh = bh.coerceIn(1, bitmap.height - by)
                if (bw < 8 || bh < 8) return null
                val cropped = Bitmap.createBitmap(bitmap, bx, by, bw, bh)
                return try {
                    val out = ByteArrayOutputStream()
                    cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.toByteArray()
                } finally {
                    cropped.recycle()
                }
            }

            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 8.dp, end = 8.dp)
                    .zIndex(2f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel crop", tint = Color.White)
                }
                Text(
                    text = "Frame the outfit",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
                IconButton(
                    onClick = {
                        if (working) return@IconButton
                        working = true
                        scope.launch(Dispatchers.Default) {
                            val bytes = cropToBytes()
                            withContext(Dispatchers.Main) {
                                working = false
                                if (bytes != null) onCropped(bytes) else onCancel()
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Use this crop", tint = Color.White)
                }
            }

            Text(
                text = "Drag to move · pinch to zoom",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .zIndex(2f),
            )
        } else {
            Text(
                text = "Couldn't read that image.",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
            LaunchedEffect(Unit) { onCancel() }
        }
    }
}
