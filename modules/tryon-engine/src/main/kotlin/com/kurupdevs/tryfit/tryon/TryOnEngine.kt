package com.kurupdevs.tryfit.tryon

import kotlinx.coroutines.flow.Flow

/** Daily free-try-on quota state, from `getQuota` (Edge Function contract in SPEC.md §7). */
data class QuotaInfo(
    val usedToday: Int,
    val limit: Int,
    /** Epoch millis when the quota resets (local midnight). */
    val resetsAtEpochMs: Long,
) {
    val remaining: Int get() = (limit - usedToday).coerceAtLeast(0)
    val isExhausted: Boolean get() = remaining == 0
}

/** Stream of updates for one try-on session. Terminal states are [Done] and [Failed]. */
sealed interface TryOnProgress {
    /** Pipeline advanced to [stage] ([stageIndex] is 1-based, out of [stageCount]). */
    data class Stage(
        val stage: ProcessingStage,
        val stageIndex: Int,
        val stageCount: Int,
    ) : TryOnProgress

    /** Pipeline finished successfully. */
    data class Done(val result: TryOnResult) : TryOnProgress

    /** Pipeline finished with a typed error. Never a raw exception — see [TryOnError]. */
    data class Failed(val error: TryOnError) : TryOnProgress
}

/**
 * Abstraction over the virtual try-on pipeline.
 *
 * Two implementations exist:
 * - [DemoTryOnEngine] — realistic simulated pipeline for dev/demo builds.
 * - Commercial engine (app module) — calls the Supabase Edge Function, which
 *   holds the provider API key server-side and talks to fal.ai / FASHN.
 *
 * Implementations must never throw for expected failures; surface them via
 * [TryOnProgress.Failed] instead. Coroutine cancellation ([kotlinx.coroutines.CancellationException])
 * propagates normally and is the one exception to that rule.
 */
interface TryOnEngine {
    /**
     * Creates a try-on session. Idempotent: repeating a call with the same
     * [idempotencyKey] returns the original session instead of creating a new
     * one (prevents double-billing on retried requests).
     *
     * @param sizeOverride optional size hint (e.g. "M"); null = product default.
     */
    suspend fun startSession(
        productId: String,
        photoBytes: ByteArray,
        sizeOverride: String? = null,
        idempotencyKey: String = java.util.UUID.randomUUID().toString(),
    ): TryOnSession

    /**
     * Streams progress for [sessionId]. Replays the terminal state immediately
     * if the session already finished. Emits [TryOnProgress.Failed] with
     * [TryOnError.PROVIDER_ERROR] for unknown session ids.
     */
    fun observeSession(sessionId: String): Flow<TryOnProgress>

    /** Requests cancellation. Allowed while queued/processing; unknown ids are a no-op. */
    suspend fun cancelSession(sessionId: String)

    /** Current daily quota. */
    suspend fun getQuota(): QuotaInfo
}
