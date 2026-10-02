package com.kurupdevs.tryfit.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.kurupdevs.tryfit.data.catalog.Product
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import com.kurupdevs.tryfit.ui.theme.TryFitSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Styled for You" — auto-collage from the user's tried-on/saved items.
 * Renders a 2x2 collage card (Canvas-drawn bitmaps); "Try this look"
 * deep-links to multi-garment try-on.
 *
 * Worker D: drop this into HomeScreen below the hero feed, passing the
 * user's saved/tried-on products.
 */
@Composable
fun StyledForYouSection(
    products: List<Product>,
    onTryLook: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val picks = remember(products) { products.take(4) }
    if (picks.size < 4) return // need a full 2x2

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var collage by remember(picks) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(picks) {
        collage = withContext(Dispatchers.Default) {
            buildCollage(
                resIds = picks.map {
                    context.resources.getIdentifier(it.imageResName, "drawable", context.packageName)
                },
                resources = context.resources
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TryFitSpacing.ScreenPadding, vertical = 12.dp)
    ) {
        Text(
            text = "Styled for You",
            style = MaterialTheme.typography.titleLarge,
            color = TryFitColors.TextPrimary
        )
        Text(
            text = "A look built from your saved items",
            style = MaterialTheme.typography.bodySmall,
            color = TryFitColors.TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        collage?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Collage: ${picks.joinToString { it.name }}",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(TryFitColors.BgCanvas)
            )
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onTryLook(picks.map { it.id })
            },
            shape = TryFitRadii.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = TryFitColors.TextPrimary,
                contentColor = TryFitColors.TextOnPhoto
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Try this look", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Draw a 2x2 grid bitmap with white gutters (900px square). */
private fun buildCollage(resIds: List<Int>, resources: android.content.res.Resources): Bitmap? {
    val size = 900
    val gutter = 14
    val cell = (size - gutter * 3) / 2
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    resIds.forEachIndexed { index, resId ->
        if (resId == 0) return@forEachIndexed
        val src = BitmapFactory.decodeResource(resources, resId) ?: return@forEachIndexed
        // Center-crop source to a square.
        val side = minOf(src.width, src.height)
        val srcRect = Rect(
            (src.width - side) / 2,
            (src.height - side) / 2,
            (src.width + side) / 2,
            (src.height + side) / 2
        )
        val left = gutter + (index % 2) * (cell + gutter)
        val top = gutter + (index / 2) * (cell + gutter)
        val dstRect = Rect(left, top, left + cell, top + cell)
        canvas.drawBitmap(src, srcRect, dstRect, paint)
        src.recycle()
    }
    return out
}
