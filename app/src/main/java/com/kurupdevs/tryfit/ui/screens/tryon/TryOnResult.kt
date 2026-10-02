package com.kurupdevs.tryfit.ui.screens.tryon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
// Worker D's sheets — REUSED per the 2026-10-02 scope update (do not duplicate).
// NOTE: Worker D lands these files; call sites below target the exact
// signatures `@Composable fun AddingToWardrobeSheet(product: Product,
// onDismiss: () -> Unit, onAdded: () -> Unit)` and `@Composable fun
// ProductQuickViewSheet(product: Product, onDismiss: () -> Unit)`.
import com.kurupdevs.tryfit.ui.components.AddingToWardrobeSheet
import com.kurupdevs.tryfit.data.TryFitSettings
import com.kurupdevs.tryfit.data.catalog.Product
import com.kurupdevs.tryfit.ui.components.HonestResultHeader
import com.kurupdevs.tryfit.ui.components.copyToSharedCache
import com.kurupdevs.tryfit.ui.components.rememberAppContainer
import com.kurupdevs.tryfit.ui.components.shareImageFile
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import com.kurupdevs.tryfit.ui.theme.TryFitSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Step 4: result (SPEC §3.4).
 *
 * 3-stage reveal (~900ms): fade → diagonal clip wipe with white edge +
 * haptic tick → spring. Then before/after slider + actions: save to wardrobe,
 * transition video, share, ask-friends vote, retry, thumbs rating.
 */
@Composable
fun TryOnResultStep(
    vm: TryOnFlowViewModel,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    val container = rememberAppContainer()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val settings by container.settingsStore.settings.collectAsState(initial = TryFitSettings())
    val reducedMotion = settings.reducedMotion
    val hapticsEnabled = settings.hapticsEnabled

    val resultBytes = vm.result?.resultImage
    val beforeBytes = remember(vm.inputFile) {
        vm.inputFile?.takeIf { it.exists() }?.readBytes()
    }

    var wardrobeSheetProduct by remember { mutableStateOf<Product?>(null) }
    var firstProduct by remember { mutableStateOf<Product?>(null) }
    LaunchedEffect(vm.garments.toList()) {
        val pid = vm.garments.firstOrNull()?.productId
        firstProduct = if (pid != null) {
            withContext(Dispatchers.IO) { runCatching { container.products.byId(pid) }.getOrNull() }
        } else null
    }

    // ---- 3-stage reveal ----
    val photoAlpha = remember { Animatable(1f) }
    val wipe = remember { Animatable(0f) }
    val pop = remember { Animatable(0.96f) }
    var revealDone by remember { mutableStateOf(false) }

    LaunchedEffect(resultBytes) {
        if (resultBytes == null) return@LaunchedEffect
        if (reducedMotion) {
            // Reduced motion: single 150ms crossfade (SPEC §4).
            photoAlpha.animateTo(0f, tween(150))
            wipe.snapTo(1f)
            pop.snapTo(1f)
        } else {
            // Stage 1: photo fades out.
            photoAlpha.animateTo(0f, tween(300))
            // Stage 2: diagonal wipe with white edge + haptic tick at midpoint.
            launch {
                delay(200)
                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            wipe.animateTo(1f, tween(400))
            // Stage 3: spring to 1.0.
            pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
            if (hapticsEnabled) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(120)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        revealDone = true
    }

    Box(modifier = Modifier.fillMaxSize().background(TryFitColors.BgScreen)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TryFitSpacing.ScreenPadding),
        ) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TryFitColors.TextPrimary,
                    )
                }
                Text(
                    text = "Your try-on",
                    style = MaterialTheme.typography.titleLarge,
                    color = TryFitColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onExit, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = TryFitColors.TextPrimary,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ---- Reveal stage ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(TryFitRadii.HeroCard))
                    .background(TryFitColors.SurfaceGridImg),
            ) {
                if (resultBytes != null && beforeBytes != null) {
                    // Base: before photo (fades out in stage 1).
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(beforeBytes).build(),
                        contentDescription = "Before",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = photoAlpha.value },
                    )
                    // Result: diagonal wipe + spring pop.
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(resultBytes).build(),
                        contentDescription = "Try-on result",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = pop.value
                                scaleY = pop.value
                            }
                            .drawWithContent {
                                val w = size.width * wipe.value
                                val slant = size.height * 0.22f
                                clipPath(
                                    Path().apply {
                                        moveTo(0f, 0f)
                                        lineTo(w, 0f)
                                        lineTo((w - slant).coerceAtLeast(0f), size.height)
                                        lineTo(0f, size.height)
                                        close()
                                    }
                                ) {
                                    this@drawWithContent.drawContent()
                                }
                                if (wipe.value > 0.01f && wipe.value < 0.999f) {
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(w, 0f),
                                        end = Offset((w - slant).coerceAtLeast(0f), size.height),
                                        strokeWidth = 3.dp.toPx(),
                                    )
                                }
                            },
                    )
                    // One subtle confetti burst on reveal (SPEC §3.4: 24 particles, no loop).
                    if (revealDone && !reducedMotion) {
                        ConfettiBurst(modifier = Modifier.fillMaxSize())
                    }
                }
                // Honest header overlaid.
                HonestResultHeader(
                    title = vm.garments.firstOrNull()?.name ?: "Your try-on",
                )
            }

            Spacer(Modifier.height(16.dp))

            // ---- Before/after slider ----
            if (resultBytes != null && beforeBytes != null && revealDone) {
                Text(
                    text = "Drag to compare",
                    style = MaterialTheme.typography.labelLarge,
                    color = TryFitColors.TextPrimary,
                )
                Spacer(Modifier.height(8.dp))
                BeforeAfterSlider(before = beforeBytes, after = resultBytes)
                Spacer(Modifier.height(16.dp))
            }

            // ---- Thumbs rating ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "How's the preview?",
                    style = MaterialTheme.typography.labelLarge,
                    color = TryFitColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                ThumbButton(
                    selected = vm.rating == 2,
                    onClick = { vm.setRating(if (vm.rating == 2) null else 2) },
                    contentDescription = "Thumbs up",
                ) {
                    Icon(Icons.Filled.ThumbUp, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                ThumbButton(
                    selected = vm.rating == 1,
                    onClick = { vm.setRating(if (vm.rating == 1) null else 1) },
                    contentDescription = "Thumbs down",
                ) {
                    Icon(Icons.Filled.ThumbDown, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- Actions ----
            ResultActionGrid(
                showSaveToWardrobe = firstProduct != null,
                onSaveToWardrobe = { wardrobeSheetProduct = firstProduct },
                onTransitionVideo = {
                    val file = vm.resultFile
                    if (!container.transitionVideo.isAvailable || file == null) {
                        scope.launch { snackbar.showSnackbar("Transition video is coming soon.") }
                        return@ResultActionGrid
                    }
                    scope.launch(Dispatchers.IO) {
                        val before = vm.inputFile
                        if (before == null) {
                            withContext(Dispatchers.Main) {
                                scope.launch { snackbar.showSnackbar("Transition video is coming soon.") }
                            }
                            return@launch
                        }
                        val out = java.io.File(context.cacheDir, "shared/transition_${vm.sessionId}.mp4")
                            .apply { parentFile?.mkdirs() }
                        val res = container.transitionVideo.generate(before, file, out)
                        withContext(Dispatchers.Main) {
                            res.onFailure {
                                scope.launch { snackbar.showSnackbar("Transition video is coming soon.") }
                            }
                        }
                    }
                },
                onShare = {
                    val file = vm.resultFile
                    if (file == null) {
                        scope.launch { snackbar.showSnackbar("Result image isn't ready to share yet.") }
                        return@ResultActionGrid
                    }
                    scope.launch(Dispatchers.IO) {
                        val shared = runCatching {
                            copyToSharedCache(context, file, "tryfit_result.jpg")
                        }.getOrNull()
                        withContext(Dispatchers.Main) {
                            if (shared != null) {
                                shareImageFile(
                                    context, shared,
                                    "My AI try-on preview from TryFit — style preview, not a fit guarantee",
                                )
                            } else {
                                scope.launch { snackbar.showSnackbar("Couldn't prepare the image for sharing.") }
                            }
                        }
                    }
                },
                onAskFriends = {
                    val file = vm.resultFile
                    if (file == null) {
                        scope.launch { snackbar.showSnackbar("Result image isn't ready yet.") }
                        return@ResultActionGrid
                    }
                    scope.launch(Dispatchers.IO) {
                        val look = container.sharedLooks.createSharedLook(
                            file, vm.garments.mapNotNull { it.productId }
                        ).getOrNull()
                        val shared = runCatching {
                            copyToSharedCache(context, file, "tryfit_result.jpg")
                        }.getOrNull()
                        withContext(Dispatchers.Main) {
                            if (look != null) {
                                clipboard.setText(AnnotatedString(look.voteUrl))
                                if (shared != null) {
                                    shareImageFile(
                                        context, shared,
                                        "Vote on my look! ${look.voteUrl}",
                                        title = "Ask friends",
                                    )
                                }
                                scope.launch { snackbar.showSnackbar("Vote link copied — share it anywhere.") }
                            } else {
                                scope.launch { snackbar.showSnackbar("Couldn't create the vote link — try again.") }
                            }
                        }
                    }
                },
                onRetry = onRetry,
            )
            Spacer(Modifier.height(100.dp))
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    // ---- Worker D's "Add to Closet" sheet (reused, not duplicated) ----
    wardrobeSheetProduct?.let { product ->
        AddingToWardrobeSheet(
            product = product,
            onDismiss = { wardrobeSheetProduct = null },
            onAdded = {
                wardrobeSheetProduct = null
                scope.launch { snackbar.showSnackbar("Saved to wardrobe") }
            },
        )
    }
}

/** One subtle confetti burst: 24 particles, fires once, no loop (SPEC §3.4). */
@Composable
private fun ConfettiBurst(modifier: Modifier = Modifier) {
    val particles = remember {
        List(24) {
            Triple(
                Random.nextFloat() * 360f, // angle deg
                0.55f + Random.nextFloat() * 0.45f, // distance (fraction of min dimension)
                listOf(
                    Color(0xFFA855F7), Color(0xFFEC4899), Color(0xFF3B82F6),
                    Color(0xFFF4A259), Color(0xFF4ECDC4),
                ).random(),
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(900))
    }
    val density = LocalDensity.current
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.35f
        val maxDist = minOf(size.width, size.height)
        val p = progress.value
        // Ease-out cubic.
        val eased = 1f - (1f - p) * (1f - p) * (1f - p)
        particles.forEach { (angleDeg, distFrac, color) ->
            val rad = Math.toRadians(angleDeg.toDouble())
            val d = maxDist * distFrac * eased
            drawCircle(
                color = color.copy(alpha = 1f - p),
                radius = with(density) { 5.dp.toPx() } * (1f - p * 0.5f),
                center = Offset(
                    cx + (Math.cos(rad) * d).toFloat(),
                    cy + (Math.sin(rad) * d).toFloat() + maxDist * 0.25f * p * p, // gravity
                ),
            )
        }
    }
}

/** Before/after comparison slider — drag horizontally (48dp grab zone, SPEC §4). */
@Composable
fun BeforeAfterSlider(before: ByteArray, after: ByteArray, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var fraction by remember { mutableFloatStateOf(0.5f) }
    var boxWidthPx by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(TryFitRadii.GridCard))
            .background(TryFitColors.SurfaceGridImg)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    fraction = (fraction + dragAmount / boxWidthPx).coerceIn(0.02f, 0.98f)
                }
            },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(after).build(),
            contentDescription = "After — try-on result",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        AsyncImage(
            model = ImageRequest.Builder(context).data(before).build(),
            contentDescription = "Before — your photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    boxWidthPx = size.width
                    clipRect(right = size.width * fraction) {
                        this@drawWithContent.drawContent()
                    }
                },
        )
        // Divider + grabber.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    val x = size.width * fraction
                    drawLine(
                        color = Color.White,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 3.dp.toPx(),
                    )
                },
        )
        // Grabber knob positioned by fraction (drawn on canvas for exact placement).
        // (44dp visible knob inside the 48dp touch target — SPEC §4.)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val x = size.width * fraction
                drawCircle(
                    color = Color.White,
                    radius = 22.dp.toPx(),
                    center = Offset(x, size.height / 2f),
                )
                drawCircle(
                    color = Color(0xFF111111),
                    radius = 22.dp.toPx(),
                    center = Offset(x, size.height / 2f),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        // Before/After chips.
        Text(
            text = "Before",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text(
            text = "After",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ThumbButton(
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    val containerColor = if (selected) TryFitColors.TextPrimary else TryFitColors.BgCanvas
    val contentColor = if (selected) Color.White else TryFitColors.TextSecondary
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(containerColor)
            .semantics { this.contentDescription = contentDescription },
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor,
            content = content,
        )
    }
}

@Composable
private fun ResultActionGrid(
    showSaveToWardrobe: Boolean,
    onSaveToWardrobe: () -> Unit,
    onTransitionVideo: () -> Unit,
    onShare: () -> Unit,
    onAskFriends: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showSaveToWardrobe) {
                ResultActionButton(
                    icon = { Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    label = "Save to wardrobe",
                    primary = true,
                    onClick = onSaveToWardrobe,
                    modifier = Modifier.weight(1f),
                )
            }
            ResultActionButton(
                icon = { Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(20.dp)) },
                label = "Transition video",
                primary = !showSaveToWardrobe,
                onClick = onTransitionVideo,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultActionButton(
                icon = { Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(20.dp)) },
                label = "Share",
                primary = false,
                onClick = onShare,
                modifier = Modifier.weight(1f),
            )
            ResultActionButton(
                icon = { Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(20.dp)) },
                label = "Ask friends",
                primary = false,
                onClick = onAskFriends,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedButton(
            onClick = onRetry,
            shape = TryFitRadii.Pill,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = "Retry with a new variation", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ResultActionButton(
    icon: @Composable () -> Unit,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (primary) {
        Button(
            onClick = onClick,
            shape = TryFitRadii.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = TryFitColors.TextPrimary,
                contentColor = TryFitColors.TextOnPhoto,
            ),
            modifier = modifier.height(52.dp),
        ) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = TryFitRadii.Pill,
            modifier = modifier.height(52.dp),
        ) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
