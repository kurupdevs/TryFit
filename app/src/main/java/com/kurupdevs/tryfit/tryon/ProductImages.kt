package com.kurupdevs.tryfit.tryon

import android.content.Context
import android.graphics.BitmapFactory
import com.kurupdevs.tryfit.data.catalog.Product
import com.kurupdevs.tryfit.data.catalog.ProductCategory
import com.kurupdevs.tryfit.data.catalog.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Resolves garment image bytes for the try-on engine's [garmentProvider].
 *
 * Products come from the canonical catalog ([ProductRepository]); the bundled
 * drawable (`imageResName`, e.g. "product_001") is decoded and re-encoded as
 * JPEG q90 so the compositor always gets well-formed bytes. Falls back to
 * [DemoCompositor.synthesizeGarment] when the product or drawable is missing,
 * so the demo pipeline never breaks on catalog gaps.
 */
suspend fun loadGarmentBytes(
    context: Context,
    products: ProductRepository,
    productId: String,
): ByteArray = withContext(Dispatchers.IO) {
    runCatching {
        val product = products.byId(productId) ?: return@runCatching null
        val resId = products.drawableRes(product)
        if (resId == 0) return@runCatching null
        val bmp = BitmapFactory.decodeResource(context.resources, resId)
            ?: return@runCatching null
        val out = ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        bmp.recycle()
        out.toByteArray().takeIf { it.isNotEmpty() }
    }.getOrNull() ?: DemoCompositor.synthesizeGarment(productId)
}

/** Loads raw bytes for a [Product]'s bundled drawable (UI use: crop, preview). */
fun loadProductImageBytes(context: Context, product: Product): ByteArray? {
    val resId = context.resources.getIdentifier(
        product.imageResName, "drawable", context.packageName
    )
    if (resId == 0) return null
    return runCatching {
        context.resources.openRawResource(resId).use { it.readBytes() }
    }.getOrNull()
}

/** Maps a catalog category to the multi-garment layering slot. */
fun ProductCategory.toGarmentSlot(): GarmentSlot = when (this) {
    ProductCategory.shoes -> GarmentSlot.SHOES
    ProductCategory.casual,
    ProductCategory.jackets,
    ProductCategory.tops,
    ProductCategory.ethnic -> GarmentSlot.TOP
    ProductCategory.bags -> GarmentSlot.OTHER
}
