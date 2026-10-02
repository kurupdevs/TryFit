package com.kurupdevs.tryfit.video

import java.io.File

/**
 * Try-On Transition Generator (research §0 — the viral #1 hook):
 * 1 photo + 1 try-on result → beat-synced before/after MP4, on-device
 * (MediaCodec/Muxer), watermarked, one-tap share to Reels/TikTok/WhatsApp.
 *
 * SEAM NOTE: Worker D owns the MediaCodec implementation. This interface +
 * [StubTransitionVideoGenerator] keep the try-on result screen compiling and
 * honest (the button explains "coming soon" instead of dead-ending) until
 * Worker D's implementation lands; AppContainer swaps the binding with zero
 * UI changes.
 */
interface TransitionVideoGenerator {
    /** False until the real on-device generator is bound. */
    val isAvailable: Boolean

    /**
     * Renders the transition video.
     * @return the MP4 file on success; failure carries a user-safe message.
     */
    suspend fun generate(
        beforePhoto: File,
        afterPhoto: File,
        output: File,
    ): Result<File>
}

/** v1 placeholder: reports unavailable so the UI shows "coming soon". */
class StubTransitionVideoGenerator : TransitionVideoGenerator {
    override val isAvailable: Boolean = false

    override suspend fun generate(
        beforePhoto: File,
        afterPhoto: File,
        output: File,
    ): Result<File> = Result.failure(
        UnsupportedOperationException("Transition video is coming soon — the on-device generator is still being built.")
    )
}
