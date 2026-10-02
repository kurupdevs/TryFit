package com.kurupdevs.tryfit.ui.screens.tryon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurupdevs.tryfit.R
import com.kurupdevs.tryfit.di.AppContainer
import com.kurupdevs.tryfit.tryon.AndroidDemoCompositor
import com.kurupdevs.tryfit.tryon.GarmentSlot
import com.kurupdevs.tryfit.tryon.PhotoAnalysis
import com.kurupdevs.tryfit.tryon.PhotoQualityGate
import com.kurupdevs.tryfit.tryon.QualityIssue
import com.kurupdevs.tryfit.tryon.QualityVerdict
import com.kurupdevs.tryfit.tryon.QuotaInfo
import com.kurupdevs.tryfit.tryon.TryOnError
import com.kurupdevs.tryfit.tryon.TryOnProgress
import com.kurupdevs.tryfit.tryon.TryOnResult
import com.kurupdevs.tryfit.tryon.UploadGuardFlag
import com.kurupdevs.tryfit.tryon.ProcessingStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** One garment selected for the try-on (catalog product or screenshot crop). */
data class SelectedGarment(
    val productId: String?,
    val name: String,
    val slot: GarmentSlot,
    val imageBytes: ByteArray,
)

/** Steps of the try-on flow (SPEC §3.4). */
sealed interface FlowStep {
    data object Source : FlowStep
    data object Camera : FlowStep
    data object CropScreenshot : FlowStep
    data object QualityCoach : FlowStep
    data object Confirm : FlowStep
    data object Processing : FlowStep
    data object Result : FlowStep
}

/**
 * Try-on flow state machine.
 *
 * Owns: photo bytes, selected garments (1–3), size override, quality-gate
 * verdict, engine session lifecycle, progressive preview, history + quota.
 * The UI is a pure function of this state.
 */
class TryOnFlowViewModel(val container: AppContainer) : ViewModel() {

    var step by mutableStateOf<FlowStep>(FlowStep.Source)
        private set

    var photoBytes by mutableStateOf<ByteArray?>(null)
        private set

    val garments = mutableStateListOf<SelectedGarment>()

    var sizeOverride by mutableStateOf<String?>(null)

    var qualityIssues by mutableStateOf<List<QualityIssue>>(emptyList())
        private set

    var guardMessage by mutableStateOf<String?>(null)
        private set

    var sessionId by mutableStateOf<String?>(null)
        private set

    var stage by mutableStateOf<TryOnProgress.Stage?>(null)
        private set

    var restoring by mutableStateOf(false)
        private set

    var result by mutableStateOf<TryOnResult?>(null)
        private set

    /** Input photo file for the before/after slider (restored from history when deep-linked). */
    var inputFile by mutableStateOf<File?>(null)
        private set

    var resultFile by mutableStateOf<File?>(null)
        private set

    var error by mutableStateOf<TryOnError?>(null)
        private set

    var wasCancelled by mutableStateOf(false)
        private set

    /** Early lighter-blend preview shown at RENDERING (progressive loading). */
    var previewBytes by mutableStateOf<ByteArray?>(null)
        private set

    var notifyMe by mutableStateOf(false)
    private var _rating by mutableStateOf<Int?>(null)
    val rating: Int? get() = _rating

    var quota by mutableStateOf<QuotaInfo?>(null)
        private set

    private val engine get() = container.tryOnEngine
    private val qualityGate = PhotoQualityGate()

    init {
        refreshQuota()
    }

    fun refreshQuota() {
        viewModelScope.launch {
            quota = runCatching { engine.getQuota() }.getOrNull()
        }
    }

    // ------------------------------------------------------------------
    // Photo intake
    // ------------------------------------------------------------------

    /**
     * Accepts raw photo bytes from any source (camera file, gallery uri,
     * avatar, last photo). Runs the upload guard + quality gate, then routes
     * to [FlowStep.QualityCoach] or [FlowStep.Confirm].
     */
    fun setPhoto(context: Context, rawBytes: ByteArray) {
        guardMessage = null
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.Default) { compressForUpload(rawBytes) }
            if (bytes.isEmpty()) {
                guardMessage = "That photo couldn't be read — try a different one."
                return@launch
            }
            // Upload guard (bloat-trap #12): block suggestive / no-person uploads.
            val flags = container.photoAnalyzer.guardFlags(bytes)
            if (UploadGuardFlag.SUGGESTIVE_RISK in flags) {
                guardMessage = "That photo didn't pass the safety check — please use a different one."
                return@launch
            }
            if (UploadGuardFlag.NO_PERSON_DETECTED in flags) {
                guardMessage = "We couldn't find a person in that photo — try a full-body photo of you."
                return@launch
            }
            // Quality gate (research v1 item 4): coach tips instead of silent failure.
            val analysis = container.photoAnalyzer.analyze(bytes).getOrNull()
            val verdict = qualityGate.evaluate(
                analysis ?: PhotoAnalysis(true, true, true, 1024, 1024, bytes.size.toLong())
            )
            photoBytes = bytes
            rememberLastPhoto(context, bytes)
            if (verdict is QualityVerdict.Fail) {
                qualityIssues = verdict.reasons
                step = FlowStep.QualityCoach
            } else {
                qualityIssues = emptyList()
                step = FlowStep.Confirm
            }
        }
    }

    /** Quality-coach fallback: "try on a model shaped like you" (research §1.4). */
    fun useSilhouetteFallback(context: Context) {
        viewModelScope.launch {
            photoBytes = withContext(Dispatchers.Default) { silhouettePhotoBytes(context) }
            qualityIssues = emptyList()
            step = FlowStep.Confirm
        }
    }

    fun retakePhoto() {
        qualityIssues = emptyList()
        step = FlowStep.Source
    }

    fun goToCamera() { step = FlowStep.Camera }
    fun goToSource() { step = FlowStep.Source }
    fun goToCrop() { step = FlowStep.CropScreenshot }

    fun setScreenshotBytes(bytes: ByteArray) {
        viewModelScope.launch {
            photoBytes = withContext(Dispatchers.Default) { compressForUpload(bytes) }
            step = FlowStep.Source
        }
    }

    // ------------------------------------------------------------------
    // Garments
    // ------------------------------------------------------------------

    fun addGarment(garment: SelectedGarment) {
        if (garments.size >= 3) return
        // One garment per slot — replacing keeps the outfit coherent.
        garments.removeAll { it.slot == garment.slot }
        garments.add(garment)
        if (garments.size > 3) garments.removeAt(0)
    }

    fun removeGarment(garment: SelectedGarment) {
        garments.remove(garment)
    }

    // ------------------------------------------------------------------
    // Session lifecycle
    // ------------------------------------------------------------------

    /** Starts the try-on pipeline (SPEC §3.4 step 3). */
    fun startTryOn(context: Context) {
        val photo = photoBytes ?: return
        val outfit = garments.toList()
        if (outfit.isEmpty()) return
        viewModelScope.launch {
            error = null
            wasCancelled = false
            result = null
            resultFile = null
            previewBytes = null
            stage = null
            _rating = null

            // Hand the full outfit to the engine's demo compositor (consumed once).
            container.pendingOutfit.set(outfit.map { it.imageBytes to it.slot })

            val first = outfit.first()
            val session = engine.startSession(
                productId = first.productId ?: "custom",
                photoBytes = photo,
                sizeOverride = sizeOverride,
                idempotencyKey = UUID.randomUUID().toString(),
            )
            sessionId = session.id
            val input = saveInputPhoto(context, session.id, photo)
            inputFile = input
            container.historyRepository.recordStarted(
                sessionId = session.id,
                productId = first.productId,
                productName = first.name,
                inputPath = input?.absolutePath,
            )
            step = FlowStep.Processing
            engine.observeSession(session.id).collect { progress ->
                when (progress) {
                    is TryOnProgress.Stage -> {
                        stage = progress
                        maybeRenderPreview(photo, first.imageBytes, progress.stage)
                    }
                    is TryOnProgress.Done -> onDone(context, progress.result)
                    is TryOnProgress.Failed -> onFailed(progress.error)
                }
            }
        }
    }

    /** Manual retry creates a NEW session (SPEC §7 audit trail). */
    fun retry(context: Context) = startTryOn(context)

    fun cancel() {
        viewModelScope.launch {
            sessionId?.let { runCatching { engine.cancelSession(it) } }
        }
    }

    /** Deep-link restore: replays the terminal state of a finished session. */
    fun restoreSession(context: Context, id: String) {
        viewModelScope.launch {
            restoring = true
            error = null
            wasCancelled = false
            sessionId = id
            step = FlowStep.Processing
            // Restore the input photo for the before/after slider from history.
            val entry = withContext(Dispatchers.IO) {
                runCatching { container.historyRepository.byId(id) }.getOrNull()
            }
            inputFile = entry?.inputPath?.let { File(it).takeIf { f -> f.exists() } }
            engine.observeSession(id).collect { progress ->
                when (progress) {
                    is TryOnProgress.Stage -> stage = progress
                    is TryOnProgress.Done -> {
                        restoring = false
                        onDone(context, progress.result, skipHistoryWrite = true)
                    }
                    is TryOnProgress.Failed -> {
                        restoring = false
                        onFailed(progress.error)
                    }
                }
            }
        }
    }

    fun setRating(value: Int?) {
        _rating = value?.coerceIn(1, 2)
        val id = sessionId ?: return
        viewModelScope.launch { container.historyRepository.rate(id, _rating) }
    }

    private suspend fun onDone(context: Context, tryOnResult: TryOnResult, skipHistoryWrite: Boolean = false) {
        result = tryOnResult
        val file = saveResultPhoto(context, tryOnResult.sessionId, tryOnResult.resultImage)
        resultFile = file
        val id = tryOnResult.sessionId
        if (!skipHistoryWrite && file != null) {
            container.historyRepository.recordDone(id, file.absolutePath)
        }
        if (notifyMe) {
            container.notificationsRepository.notifyTryOnReady(
                id, garments.firstOrNull()?.name
            )
        }
        refreshQuota()
        step = FlowStep.Result
    }

    private fun onFailed(err: TryOnError) {
        if (err.code == TryOnError.CANCELLED) {
            wasCancelled = true
            sessionId?.let { id ->
                viewModelScope.launch { container.historyRepository.recordCancelled(id) }
            }
        } else {
            error = err
            sessionId?.let { id ->
                viewModelScope.launch { container.historyRepository.recordFailed(id) }
            }
        }
        refreshQuota()
        if (step == FlowStep.Processing) {
            // Stay on the processing step; the UI swaps to the error/cancelled card.
        }
    }

    /**
     * Progressive preview (research v1 item 3): at RENDERING, composite early
     * with a lighter blend so the user sees something real instead of a dead
     * spinner while the engine refines.
     */
    private fun maybeRenderPreview(photo: ByteArray, garment: ByteArray, stage: ProcessingStage) {
        if (stage != ProcessingStage.RENDERING || previewBytes != null) return
        viewModelScope.launch(Dispatchers.Default) {
            previewBytes = runCatching {
                AndroidDemoCompositor(photo, garment, blendStrength = 0.55f)
            }.getOrNull()
        }
    }

    // ------------------------------------------------------------------
    // IO helpers
    // ------------------------------------------------------------------

    private suspend fun saveInputPhoto(context: Context, sessionId: String, bytes: ByteArray): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, "tryon_results").apply { mkdirs() }
                File(dir, "input_$sessionId.jpg").also { it.writeBytes(bytes) }
            }.getOrNull()
        }

    private suspend fun saveResultPhoto(context: Context, sessionId: String, bytes: ByteArray): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, "tryon_results").apply { mkdirs() }
                File(dir, "result_$sessionId.jpg").also { it.writeBytes(bytes) }
            }.getOrNull()
        }

    private suspend fun rememberLastPhoto(context: Context, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, "uploads").apply { mkdirs() }
                File(dir, "last_photo.jpg").writeBytes(bytes)
            }
        }

    fun readLastPhoto(context: Context): ByteArray? =
        runCatching {
            File(context.filesDir, "uploads/last_photo.jpg")
                .takeIf { it.exists() }?.readBytes()
        }.getOrNull()

    fun readAvatarBytes(context: Context): ByteArray? =
        runCatching {
            File(context.filesDir, "avatar/avatar.jpg")
                .takeIf { it.exists() }?.readBytes()
        }.getOrNull()
}

/** Compresses to ≤2048px long side / JPEG q80 (SPEC §6 client contract). */
fun compressForUpload(bytes: ByteArray): ByteArray {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    if (opts.outWidth <= 0 || opts.outHeight <= 0) return bytes
    val longSide = maxOf(opts.outWidth, opts.outHeight)
    val sample = (longSide / 2048).coerceAtLeast(1).takeHighestOneBit()
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return bytes
    return try {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        out.toByteArray()
    } finally {
        bmp.recycle()
    }
}

/** Rasterizes the neutral silhouette drawable for the quality-gate fallback. */
fun silhouettePhotoBytes(context: Context): ByteArray {
    val drawable = ContextCompat.getDrawable(context, R.drawable.ic_body_silhouette)
        ?: return ByteArray(0)
    val size = 768
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(canvas)
    return try {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    } finally {
        bmp.recycle()
    }
}
