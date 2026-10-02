package com.kurupdevs.tryfit.tryon

import com.kurupdevs.tryfit.tryon.PhotoAnalysis

/**
 * On-device photo analysis behind an interface.
 *
 * WHY AN INTERFACE: ML Kit (SelfieSegmentation / face detection) and CameraX
 * may not resolve in every build flavor or offline environment, and the real
 * analyzer needs runtime permissions + Play-services-backed models. Nothing in
 * the try-on flow may import ML Kit directly — everything goes through this
 * interface, so a missing ML Kit dependency can never break compilation of
 * the flow, and tests can inject fakes.
 *
 * Production implementation (pending): `MlKitPhotoAnalyzer` using
 * `com.google.mlkit:segmentation-selfie` + `play-services-mlkit-face-detection`,
 * returning real [PhotoAnalysis] (front-facing via face Euler angles,
 * well-lit via luminance histogram, upper-body via segmentation mask
 * coverage). Until then [StubPhotoAnalyzer] is the default.
 */
interface PhotoAnalyzer {
    /** Analyzes raw image bytes (JPEG/PNG/WebP). Never throws — returns [Result]. */
    suspend fun analyze(photoBytes: ByteArray): Result<PhotoAnalysis>

    /**
     * Client-side upload guard flags. The stub cannot detect anything and
     * returns empty — this is honest, not a bypass: the REAL guardrails are
     * server-side (Edge Function moderation hook -> MODERATION_REJECTED,
     * SPEC §7), which run before any paid provider call.
     *
     * BLOCKLIST NOTE (bloat-trap #12 / research §3): the server blocklist must
     * reject explicit/suggestive uploads, public figures, and non-consensual
     * use of other people's photos. Client flags here are a first-pass UX
     * courtesy only, never the enforcement point.
     */
    suspend fun guardFlags(photoBytes: ByteArray): Set<UploadGuardFlag> = emptySet()
}

/** First-pass client upload-guard flags (courtesy UX; enforcement is server-side). */
enum class UploadGuardFlag {
    /** Possible explicit/suggestive content — block with friendly copy. */
    SUGGESTIVE_RISK,

    /** No detectable person in frame — likely not a try-on photo. */
    NO_PERSON_DETECTED,
}

/**
 * Default analyzer used until the ML Kit implementation lands.
 *
 * Assumes a sane input (front-facing, lit, upper-body visible) and derives
 * only what it can measure without ML: byte size + decoded dimensions.
 * Everything else is passed through as "unknown-but-allowed" so the demo
 * flow never dead-ends on analysis it cannot perform.
 */
class StubPhotoAnalyzer : PhotoAnalyzer {

    override suspend fun analyze(photoBytes: ByteArray): Result<PhotoAnalysis> =
        runCatching {
            val opts = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size, opts)
            val w = opts.outWidth.takeIf { it > 0 } ?: 1024
            val h = opts.outHeight.takeIf { it > 0 } ?: 1024
            PhotoAnalysis(
                isFrontFacing = true,
                isWellLit = true,
                upperBodyVisible = true,
                width = w,
                height = h,
                byteSize = photoBytes.size.toLong(),
            )
        }

    override suspend fun guardFlags(photoBytes: ByteArray): Set<UploadGuardFlag> {
        // The stub has no model to run: only the trivially-measurable empty
        // check. Real detection lives in MlKitPhotoAnalyzer (pending) and the
        // server moderation hook (SPEC §7).
        return if (photoBytes.isEmpty()) setOf(UploadGuardFlag.NO_PERSON_DETECTED)
        else emptySet()
    }
}
