package com.kurupdevs.tryfit.ui.components

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.rememberAsyncImagePainter

/**
 * Resolves a catalog [imageRef] to a Coil model:
 * - `asset://product_001` (or bare `product_001`) → bundled drawable-nodpi
 *   resource via [android.content.res.Resources.getIdentifier]
 * - `http(s)://…` / `file://…` → remote / file URI
 * - anything else → light-gray tile (never a crash, never a white flash)
 */
fun resolveImageModel(context: Context, imageRef: String): Any {
    val ref = imageRef.trim()
    if (ref.startsWith("http://") || ref.startsWith("https://") || ref.startsWith("file://")) {
        return ref
    }
    val name = ref.removePrefix("asset://").substringBefore('.')
    if (name.isNotEmpty()) {
        val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (resId != 0) return resId
    }
    return ColorDrawable(0xFFF1F1F3.toInt())
}

/**
 * Remembers a Coil painter for a catalog image reference. Backed by the
 * shared Coil 3 loader (25% memory + 250MB disk caches); Coil measures the
 * composable and sizes the decode to the view (SPEC §5).
 */
@Composable
fun rememberAssetImagePainter(imageRef: String): Painter {
    val context = LocalContext.current
    val model = remember(imageRef) { resolveImageModel(context, imageRef) }
    return rememberAsyncImagePainter(model = model)
}

/** Full-bleed-ready product image with content description (a11y). */
@Composable
fun ProductImage(
    imageRef: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    Image(
        painter = rememberAssetImagePainter(imageRef),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
