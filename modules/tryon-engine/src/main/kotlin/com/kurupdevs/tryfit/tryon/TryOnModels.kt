package com.kurupdevs.tryfit.tryon

/**
 * Core domain models for the TryFit virtual try-on pipeline.
 *
 * Pure JVM Kotlin — no Android imports. Image payloads travel as [ByteArray]
 * so the module stays platform-agnostic (the app module decodes to Bitmap
 * only where it actually needs to render).
 */

/** Lifecycle of a try-on session. Mirrors the `tryon_sessions.status` column in Supabase. */
enum class TryOnStatus {
    QUEUED,
    PROCESSING,
    DONE,
    FAILED,
    CANCELLED,
}

/**
 * Fine-grained processing stages surfaced to the UI progress screen.
 * Order matters — the demo engine walks these in declaration order.
 */
enum class ProcessingStage {
    UPLOADING,
    QUALITY_CHECK,
    QUEUED,
    STITCHING,
    FITTING,
    RENDERING,
    FINALIZING,
}

/** A single try-on job, from creation through its terminal state. */
data class TryOnSession(
    val id: String,
    val productId: String,
    val status: TryOnStatus,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

/** Successful output of a try-on session. */
data class TryOnResult(
    val sessionId: String,
    /** Encoded result image (PNG from the demo engine, JPEG from the commercial path). */
    val resultImage: ByteArray,
    /** Wall-clock time from pipeline start to result, in milliseconds. */
    val processingMs: Long,
)

/**
 * Typed try-on failure. Codes match the Supabase Edge Function contracts
 * in SPEC.md §7 — the UI switches friendly copy off these, never raw text.
 */
data class TryOnError(
    val code: String,
    val message: String,
    /** True when tapping "retry" is meaningful (transient failure). */
    val retryable: Boolean = false,
) {
    companion object {
        const val QUOTA_EXCEEDED = "QUOTA_EXCEEDED"
        const val MODERATION_REJECTED = "MODERATION_REJECTED"
        const val INVALID_PHOTO = "INVALID_PHOTO"
        const val PROVIDER_ERROR = "PROVIDER_ERROR"
        const val TIMEOUT = "TIMEOUT"
        const val CANCELLED = "CANCELLED"
    }
}
