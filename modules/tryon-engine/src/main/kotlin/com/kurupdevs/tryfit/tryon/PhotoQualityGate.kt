package com.kurupdevs.tryfit.tryon

/**
 * Input to the photo quality gate.
 *
 * The three booleans come from face detection (MediaPipe in the app module —
 * see SPEC.md §7 "photo-quality gate"). This module only consumes the result;
 * real MediaPipe wiring lives in the app module and is injected here as data.
 */
data class PhotoAnalysis(
    val isFrontFacing: Boolean,
    val isWellLit: Boolean,
    val upperBodyVisible: Boolean,
    val width: Int,
    val height: Int,
    val byteSize: Long,
)

/** One concrete reason a photo failed the quality gate, with user-facing copy. */
enum class QualityIssue(val userMessage: String) {
    IMAGE_EMPTY("That photo looks empty — please pick a valid photo."),
    IMAGE_TOO_LARGE("Photo is bigger than 5 MB — let the app compress it or pick a smaller one."),
    IMAGE_TOO_SMALL("Photo resolution is too low — use a clearer photo (at least 480 px on the short side)."),
    NOT_FRONT_FACING("Face the camera straight on — no side profiles or tilted heads."),
    POOR_LIGHTING("Move somewhere brighter, with even light on your face."),
    UPPER_BODY_NOT_VISIBLE("Step back so your shoulders and upper body are fully in frame."),
}

/** Verdict of the quality gate: [Pass], or [Fail] with every issue found. */
sealed interface QualityVerdict {
    data object Pass : QualityVerdict
    data class Fail(val reasons: List<QualityIssue>) : QualityVerdict
}

/**
 * Pure-logic photo quality gate. Runs BEFORE any paid provider call so bad
 * inputs fail fast and free instead of burning $0.07 on a doomed generation.
 *
 * Thresholds mirror the client rules in SPEC.md §6 (photo ≤ 5 MB, compressed
 * to ≤ 2048 px). All checks are data-driven — no image decoding here.
 */
class PhotoQualityGate(
    private val maxBytes: Long = MAX_PHOTO_BYTES,
    private val minDimensionPx: Int = MIN_DIMENSION_PX,
) {
    fun evaluate(analysis: PhotoAnalysis): QualityVerdict {
        val issues = mutableListOf<QualityIssue>()
        if (analysis.byteSize <= 0L) {
            issues += QualityIssue.IMAGE_EMPTY
        } else if (analysis.byteSize > maxBytes) {
            issues += QualityIssue.IMAGE_TOO_LARGE
        }
        if (minOf(analysis.width, analysis.height) < minDimensionPx) {
            issues += QualityIssue.IMAGE_TOO_SMALL
        }
        if (!analysis.isFrontFacing) issues += QualityIssue.NOT_FRONT_FACING
        if (!analysis.isWellLit) issues += QualityIssue.POOR_LIGHTING
        if (!analysis.upperBodyVisible) issues += QualityIssue.UPPER_BODY_NOT_VISIBLE
        return if (issues.isEmpty()) QualityVerdict.Pass else QualityVerdict.Fail(issues)
    }

    companion object {
        /** Matches the 5 MB client upload cap (SPEC.md §6). */
        const val MAX_PHOTO_BYTES: Long = 5L * 1024 * 1024

        /** Short-side floor — below this, try-on models degrade visibly. */
        const val MIN_DIMENSION_PX: Int = 480
    }
}
