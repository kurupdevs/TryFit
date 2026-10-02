package com.kurupdevs.tryfit.tryon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Bitmap-based demo compositor injected into [DemoTryOnEngine] by the app
 * module (see EngineProvider in di/). Handles JPEG/PNG/WebP via BitmapFactory
 * — the pure-Kotlin [DemoCompositor] in the engine module only speaks PNG.
 *
 * Honest framing: this is a DEMO-grade pixel blend (garment overlaid on the
 * torso region), NOT a diffusion try-on. Every surface that shows its output
 * labels it "AI preview — style preview, not a fit guarantee".
 */

/** Which body region a garment covers — drives multi-garment layering. */
enum class GarmentSlot { TOP, BOTTOM, SHOES, OTHER }

/**
 * Single-garment composite.
 *
 * Output = [photoBytes] with [garmentBytes] blended over the torso region
 * (lower-center, ~55% of photo width) using a vertical feathered alpha mask,
 * plus a slight warm color grade. Re-encoded as JPEG q85.
 *
 * @param blendStrength 1.0 = full overlay; <1.0 = lighter "preview" blend
 *   (the processing screen renders an early progressive preview at 0.55).
 */
fun AndroidDemoCompositor(
    photoBytes: ByteArray,
    garmentBytes: ByteArray,
    blendStrength: Float = 1f,
): ByteArray {
    val photo = decodeBounded(photoBytes) ?: return photoBytes
    val garment = BitmapFactory.decodeByteArray(garmentBytes, 0, garmentBytes.size)
        ?: return encodeJpeg(photo)
    return try {
        val out = photo.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        drawGarment(canvas, out, garment, GarmentSlot.TOP, blendStrength)
        applyWarmGrade(canvas, out)
        encodeJpeg(out)
    } finally {
        if (garment !== photo) garment.recycle()
        photo.recycle()
    }
}

/**
 * Multi-garment full-outfit composite (research v1.5 item 20 — "try this
 * whole fit on me", folded into v1 since the demo path makes it cheap).
 *
 * Layers up to 3 garments: TOP at the torso, BOTTOM at the lower body, SHOES
 * small at the bottom edge. Extra garments beyond 3 are ignored; slots are
 * drawn back-to-front (bottom first so tops overlap naturally).
 */
fun AndroidOutfitCompositor(
    photoBytes: ByteArray,
    garments: List<Pair<ByteArray, GarmentSlot>>,
    blendStrength: Float = 1f,
): ByteArray {
    val photo = decodeBounded(photoBytes) ?: return photoBytes
    if (garments.isEmpty()) return encodeJpeg(photo)
    val decoded = garments.take(3).mapNotNull { (bytes, slot) ->
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { it to slot }
    }
    return try {
        val out = photo.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        // Back-to-front: shoes, bottom, top, then unslotted extras.
        val order = listOf(GarmentSlot.SHOES, GarmentSlot.BOTTOM, GarmentSlot.TOP, GarmentSlot.OTHER)
        for (slot in order) {
            decoded.filter { it.second == slot }.forEach { (bmp, s) ->
                drawGarment(canvas, out, bmp, s, blendStrength)
            }
        }
        applyWarmGrade(canvas, out)
        encodeJpeg(out)
    } finally {
        decoded.forEach { (bmp, _) -> bmp.recycle() }
        photo.recycle()
    }
}

/** Decodes [bytes], downscaling so the long side is at most [maxSide] px. */
private fun decodeBounded(bytes: ByteArray, maxSide: Int = 1024): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val longSide = maxOf(bounds.outWidth, bounds.outHeight)
    val sample = (longSide / maxSide).coerceAtLeast(1).takeHighestOneBit()
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

private fun encodeJpeg(bitmap: Bitmap, quality: Int = 85): ByteArray {
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}

/**
 * Draws one garment bitmap onto [canvas] (which draws into [photo]) at the
 * region for [slot], with a vertical feathered alpha mask on the top edge
 * (and bottom edge for BOTTOM/SHOES so layers melt into each other).
 */
private fun drawGarment(
    canvas: Canvas,
    photo: Bitmap,
    garment: Bitmap,
    slot: GarmentSlot,
    blendStrength: Float,
) {
    val pw = photo.width.toFloat()
    val ph = photo.height.toFloat()

    // Target rect per slot (fractions of the photo).
    val (fx, fy, fw, fh) = when (slot) {
        GarmentSlot.TOP -> floatArrayOf(0.225f, 0.30f, 0.55f, 0.38f)
        GarmentSlot.BOTTOM -> floatArrayOf(0.25f, 0.62f, 0.50f, 0.26f)
        GarmentSlot.SHOES -> floatArrayOf(0.36f, 0.86f, 0.28f, 0.12f)
        GarmentSlot.OTHER -> floatArrayOf(0.225f, 0.30f, 0.55f, 0.38f)
    }
    val dst = RectF(pw * fx, ph * fy, pw * (fx + fw), ph * (fy + fh))

    // Fit garment into dst preserving aspect (center-crop).
    val src = centerCropSrc(garment, dst.width() / dst.height())

    // Feathered mask: alpha ramps in over the top 12% (and bottom 12% for
    // lower-body slots so stacked garments blend).
    val featherTop = dst.height() * 0.12f
    val featherBottom = if (slot == GarmentSlot.TOP || slot == GarmentSlot.OTHER) 0f
    else dst.height() * 0.12f
    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, dst.top, 0f, dst.bottom,
            intArrayOf(0x00000000, 0xFF000000.toInt(), 0xFF000000.toInt(), 0x00000000),
            floatArrayOf(
                0f,
                (featherTop / dst.height()).coerceIn(0f, 1f),
                1f - (featherBottom / dst.height()).coerceIn(0f, 1f),
                1f,
            ),
            Shader.TileMode.CLAMP,
        )
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    // Draw garment to an offscreen layer, punch the feather mask, then draw
    // the layer with the blend strength as alpha.
    val layer = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888)
    val layerCanvas = Canvas(layer)
    layerCanvas.drawBitmap(garment, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    layerCanvas.drawRect(0f, 0f, photo.width.toFloat(), photo.height.toFloat(), maskPaint)
    val alphaPaint = Paint().apply { alpha = (255 * blendStrength.coerceIn(0f, 1f)).toInt() }
    canvas.drawBitmap(layer, 0f, 0f, alphaPaint)
    layer.recycle()
}

/** Center-crop source rect of [bmp] to match [targetAspect] (w/h). */
private fun centerCropSrc(bmp: Bitmap, targetAspect: Float): Rect {
    val aspect = bmp.width.toFloat() / bmp.height
    return if (aspect > targetAspect) {
        val w = (bmp.height * targetAspect).toInt().coerceAtLeast(1)
        val x = ((bmp.width - w) / 2).coerceAtLeast(0)
        Rect(x, 0, min(x + w, bmp.width), bmp.height)
    } else {
        val h = (bmp.width / targetAspect).toInt().coerceAtLeast(1)
        val y = ((bmp.height - h) / 2).coerceAtLeast(0)
        Rect(0, y, bmp.width, min(y + h, bmp.height))
    }
}

/** Slight warm color grade so the composite reads as one photo, not a sticker. */
private fun applyWarmGrade(canvas: Canvas, photo: Bitmap) {
    val cm = ColorMatrix(
        floatArrayOf(
            1.04f, 0f, 0f, 0f, 4f,
            0f, 1.0f, 0f, 0f, 2f,
            0f, 0f, 0.96f, 0f, -2f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
    canvas.drawBitmap(photo, 0f, 0f, paint)
}
