package com.kurupdevs.tryfit.tryon

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Compositor used by the demo engine to fake a try-on result image. */
typealias DemoCompositorFn = suspend (photo: ByteArray, garment: ByteArray) -> ByteArray

/**
 * Realistic simulated try-on pipeline for dev/demo builds.
 *
 * Walks the 7 [ProcessingStage]s with production-like latencies (~14.2s total,
 * inside the 15–60s async window from SPEC.md §3.4), runs the real
 * [PhotoQualityGate], fails ~8% of runs with a friendly retryable error, and
 * produces a result image through the injectable [demoCompositor].
 *
 * Guarantees:
 * - Fully cancellable — [cancelSession] (or collector cancellation) stops the
 *   pipeline; the flow then emits [TryOnProgress.Failed] with code CANCELLED.
 * - Never throws for expected failures — everything surfaces as [TryOnProgress.Failed].
 *   (Coroutine [CancellationException] propagates normally, as with any suspend code.)
 * - Idempotent [startSession]: same idempotency key → same session, no double run.
 * - Terminal states are replayed to late collectors (rotation-safe).
 *
 * @param demoCompositor produces the result image. Default ([DemoCompositor.blend])
 *   does a real pixel blend for PNG inputs and a clearly-labelled synthetic
 *   placeholder otherwise. The app module injects a Bitmap compositor that
 *   handles JPEG for production-quality demos.
 * @param garmentProvider resolves garment image bytes for a product id. Default
 *   synthesizes a simple garment PNG so the demo works with zero backend.
 * @param random injectable for deterministic tests (seed it, set [failureRate] = 0.0).
 * @param failureRate probability of a simulated mid-pipeline provider failure.
 * @param dailyQuota demo quota (5/day, matching the v1 product rule).
 */
class DemoTryOnEngine(
    private val demoCompositor: DemoCompositorFn = { photo, garment -> DemoCompositor.blend(photo, garment) },
    private val garmentProvider: suspend (productId: String) -> ByteArray = { productId ->
        DemoCompositor.synthesizeGarment(productId)
    },
    private val random: Random = Random.Default,
    private val failureRate: Double = DEFAULT_FAILURE_RATE,
    private val dailyQuota: Int = DEFAULT_DAILY_QUOTA,
    private val qualityGate: PhotoQualityGate = PhotoQualityGate(),
) : TryOnEngine {

    private data class SessionRecord(
        val session: TryOnSession,
        val productId: String,
        val photoBytes: ByteArray,
        val sizeOverride: String?,
        val status: AtomicReference<TryOnStatus> = AtomicReference(TryOnStatus.QUEUED),
        val cancelled: AtomicBoolean = AtomicBoolean(false),
        val terminal: AtomicReference<TryOnProgress?> = AtomicReference(null),
    )

    private val sessions = ConcurrentHashMap<String, SessionRecord>()
    private val idempotencyIndex = ConcurrentHashMap<String, String>()
    private val quotaUsed = AtomicInteger(0)
    @Volatile private var quotaDayStartMs: Long = dayStartMs()

    override suspend fun startSession(
        productId: String,
        photoBytes: ByteArray,
        sizeOverride: String?,
        idempotencyKey: String,
    ): TryOnSession {
        idempotencyIndex[idempotencyKey]?.let { sid ->
            sessions[sid]?.let { return snapshot(it) }
        }
        maybeResetQuota()
        val record = when {
            photoBytes.isEmpty() -> failedRecord(
                productId, photoBytes, sizeOverride,
                TryOnError(
                    TryOnError.INVALID_PHOTO,
                    "That photo looks empty — pick a valid photo and try again.",
                    retryable = false,
                ),
            )
            quotaUsed.get() >= dailyQuota -> failedRecord(
                productId, photoBytes, sizeOverride,
                TryOnError(
                    TryOnError.QUOTA_EXCEEDED,
                    "You've used all $dailyQuota free try-ons for today — fresh ones land at midnight.",
                    retryable = false,
                ),
            )
            else -> {
                quotaUsed.incrementAndGet()
                val session = TryOnSession(
                    id = "tryon_${UUID.randomUUID()}",
                    productId = productId,
                    status = TryOnStatus.QUEUED,
                )
                SessionRecord(
                    session = session,
                    productId = productId,
                    photoBytes = photoBytes,
                    sizeOverride = sizeOverride,
                )
            }
        }
        sessions[record.session.id] = record
        idempotencyIndex[idempotencyKey] = record.session.id
        return snapshot(record)
    }

    private fun failedRecord(
        productId: String,
        photoBytes: ByteArray,
        sizeOverride: String?,
        error: TryOnError,
    ): SessionRecord {
        val session = TryOnSession(
            id = "tryon_${UUID.randomUUID()}",
            productId = productId,
            status = TryOnStatus.FAILED,
        )
        return SessionRecord(
            session = session,
            productId = productId,
            photoBytes = photoBytes,
            sizeOverride = sizeOverride,
            status = AtomicReference(TryOnStatus.FAILED),
            terminal = AtomicReference<TryOnProgress?>(TryOnProgress.Failed(error)),
        )
    }

    override fun observeSession(sessionId: String): Flow<TryOnProgress> = flow {
        val record = sessions[sessionId]
        if (record == null) {
            emit(
                TryOnProgress.Failed(
                    TryOnError(
                        TryOnError.PROVIDER_ERROR,
                        "Session not found — it may have expired.",
                        retryable = false,
                    ),
                ),
            )
            return@flow
        }
        record.terminal.get()?.let { emit(it); return@flow }
        runPipeline(record)
    }

    private suspend fun FlowCollector<TryOnProgress>.runPipeline(record: SessionRecord) {
        val startedAt = System.currentTimeMillis()
        record.status.set(TryOnStatus.PROCESSING)
        val failAtStage = if (random.nextDouble() < failureRate) FAILABLE_STAGES.random(random) else null
        val stages = ProcessingStage.entries
        for ((index, stage) in stages.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (record.cancelled.get()) {
                return finish(
                    record, TryOnStatus.CANCELLED,
                    TryOnError(TryOnError.CANCELLED, "Try-on cancelled.", retryable = false),
                )
            }
            emit(TryOnProgress.Stage(stage, index + 1, stages.size))
            delay(STAGE_DELAYS_MS.getValue(stage))
            if (stage == ProcessingStage.QUALITY_CHECK) {
                val verdict = qualityGate.evaluate(analyzePhoto(record.photoBytes))
                if (verdict is QualityVerdict.Fail) {
                    val reasons = verdict.reasons.joinToString(" ") { it.userMessage }
                    return finish(
                        record, TryOnStatus.FAILED,
                        TryOnError(TryOnError.INVALID_PHOTO, reasons, retryable = true),
                    )
                }
            }
            if (stage == failAtStage) {
                return finish(
                    record, TryOnStatus.FAILED,
                    TryOnError(TryOnError.PROVIDER_ERROR, FRIENDLY_FAILURE_MESSAGE, retryable = true),
                )
            }
        }
        currentCoroutineContext().ensureActive()
        val resultBytes: ByteArray? = try {
            val garment = garmentProvider(record.productId)
            demoCompositor(record.photoBytes, garment)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (resultBytes == null || record.cancelled.get()) {
            val cancelled = record.cancelled.get()
            return finish(
                record,
                if (cancelled) TryOnStatus.CANCELLED else TryOnStatus.FAILED,
                if (cancelled) TryOnError(TryOnError.CANCELLED, "Try-on cancelled.", retryable = false)
                else TryOnError(TryOnError.PROVIDER_ERROR, FRIENDLY_FAILURE_MESSAGE, retryable = true),
            )
        }
        val done = TryOnProgress.Done(
            TryOnResult(record.session.id, resultBytes, System.currentTimeMillis() - startedAt),
        )
        record.status.set(TryOnStatus.DONE)
        record.terminal.set(done)
        emit(done)
    }

    private suspend fun FlowCollector<TryOnProgress>.finish(
        record: SessionRecord,
        status: TryOnStatus,
        error: TryOnError,
    ) {
        val failed = TryOnProgress.Failed(error)
        record.status.set(status)
        record.terminal.set(failed)
        emit(failed)
    }

    override suspend fun cancelSession(sessionId: String) {
        // Unknown ids are a no-op: double-cancel or a stale id must never throw.
        sessions[sessionId]?.cancelled?.set(true)
    }

    override suspend fun getQuota(): QuotaInfo {
        maybeResetQuota()
        return QuotaInfo(
            usedToday = quotaUsed.get(),
            limit = dailyQuota,
            resetsAtEpochMs = quotaDayStartMs + 86_400_000L,
        )
    }

    private fun snapshot(record: SessionRecord): TryOnSession =
        record.session.copy(status = record.status.get())

    /**
     * Demo-side photo analysis. The demo assumes a sane input photo; the app
     * module replaces the three detection flags with real MediaPipe output.
     */
    private fun analyzePhoto(photoBytes: ByteArray): PhotoAnalysis {
        val decoded = PngCodec.decode(photoBytes)
        return PhotoAnalysis(
            isFrontFacing = true,
            isWellLit = true,
            upperBodyVisible = true,
            width = decoded?.width ?: 1024,
            height = decoded?.height ?: 1024,
            byteSize = photoBytes.size.toLong(),
        )
    }

    private fun maybeResetQuota() {
        val start = dayStartMs()
        if (start != quotaDayStartMs) {
            synchronized(this) {
                if (start != quotaDayStartMs) {
                    quotaDayStartMs = start
                    quotaUsed.set(0)
                }
            }
        }
    }

    private fun dayStartMs(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * Playful rotating copy for the progress screen — the UI cycles one variant
     * every ~2.2s while its stage is active (SPEC.md §3.4).
     */
    fun copyFor(stage: ProcessingStage): List<String> = STAGE_COPY.getValue(stage)

    companion object {
        /** Simulated provider failure probability (8%). */
        const val DEFAULT_FAILURE_RATE = 0.08

        /** Demo daily quota, matching the v1 product rule (5 free/day). */
        const val DEFAULT_DAILY_QUOTA = 5

        /**
         * Per-stage simulated latency. Totals ≈ 14.2s — inside the 15–60s async
         * window the UX is designed around (SPEC.md §1, §3.4).
         */
        val STAGE_DELAYS_MS: Map<ProcessingStage, Long> = mapOf(
            ProcessingStage.UPLOADING to 1_200L,
            ProcessingStage.QUALITY_CHECK to 1_500L,
            ProcessingStage.QUEUED to 1_000L,
            ProcessingStage.STITCHING to 3_000L,
            ProcessingStage.FITTING to 3_000L,
            ProcessingStage.RENDERING to 3_000L,
            ProcessingStage.FINALIZING to 1_500L,
        )

        /** Stages where a simulated provider failure may strike (never before real work starts). */
        private val FAILABLE_STAGES = listOf(
            ProcessingStage.STITCHING,
            ProcessingStage.FITTING,
            ProcessingStage.RENDERING,
            ProcessingStage.FINALIZING,
        )

        const val FRIENDLY_FAILURE_MESSAGE =
            "The AI tripped on a pixel — no charge, tap retry and we'll run it again."

        val STAGE_COPY: Map<ProcessingStage, List<String>> = mapOf(
            ProcessingStage.UPLOADING to listOf(
                "Uploading your photo…",
                "Packing up the pixels…",
                "Sending your look to the studio…",
            ),
            ProcessingStage.QUALITY_CHECK to listOf(
                "Checking the lighting…",
                "Making sure we can see you clearly…",
                "Inspecting every detail…",
            ),
            ProcessingStage.QUEUED to listOf(
                "Grabbing your spot in line…",
                "Warming up the GPUs…",
                "Almost your turn…",
            ),
            ProcessingStage.STITCHING to listOf(
                "Stitching the fabric…",
                "Threading the needle…",
                "Sewing the seams together…",
            ),
            ProcessingStage.FITTING to listOf(
                "Checking the fit…",
                "Pinning the shoulders…",
                "Adjusting the drape…",
            ),
            ProcessingStage.RENDERING to listOf(
                "Warming up the pixels…",
                "Painting in the shadows…",
                "Rendering your new look…",
            ),
            ProcessingStage.FINALIZING to listOf(
                "Adding the finishing touches…",
                "Polishing the details…",
                "Wrapping it all up…",
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Demo image support: pure-Kotlin PNG codec + compositor.
// Uses only kotlin-stdlib + java.util.zip (both present on JVM and Android),
// so the demo pipeline needs zero image libraries.
// ---------------------------------------------------------------------------

/** Minimal PNG codec: decodes non-interlaced 8-bit PNGs (gray/RGB/RGBA), encodes RGBA PNGs. */
internal object PngCodec {
    private val SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    /** Pixels packed 0xAARRGGBB. */
    data class Image(val width: Int, val height: Int, val rgba: IntArray)

    fun decode(bytes: ByteArray): Image? {
        if (bytes.size < 8 || !bytes.copyOfRange(0, 8).contentEquals(SIGNATURE)) return null
        var pos = 8
        var width = 0
        var height = 0
        var bitDepth = 0
        var colorType = -1
        var interlace = 1
        val idat = ArrayList<Byte>()
        while (pos + 8 <= bytes.size) {
            val length = getBe32(bytes, pos)
            if (length < 0) return null
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            val dataStart = pos + 8
            if (dataStart.toLong() + length + 4 > bytes.size) return null
            when (type) {
                "IHDR" -> {
                    if (length < 13) return null
                    width = getBe32(bytes, dataStart)
                    height = getBe32(bytes, dataStart + 4)
                    bitDepth = bytes[dataStart + 8].toInt() and 0xFF
                    colorType = bytes[dataStart + 9].toInt() and 0xFF
                    interlace = bytes[dataStart + 12].toInt() and 0xFF
                }
                "IDAT" -> for (i in 0 until length) idat.add(bytes[dataStart + i])
                "IEND" -> break
            }
            pos = dataStart + length + 4
        }
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192) return null
        if (bitDepth != 8 || interlace != 0 || colorType !in setOf(0, 2, 6)) return null
        val bpp = when (colorType) {
            0 -> 1
            2 -> 3
            else -> 4
        }
        val inflated: ByteArray = try {
            val inflater = java.util.zip.Inflater()
            inflater.setInput(idat.toByteArray())
            val out = ByteArrayOutputStream()
            val buf = ByteArray(65_536)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) break
                out.write(buf, 0, n)
            }
            inflater.end()
            out.toByteArray()
        } catch (e: Exception) {
            return null
        }
        val stride = width * bpp
        if (inflated.size < height * (stride + 1)) return null
        // Unfilter scanlines.
        val raw = ByteArray(height * stride)
        var p = 0
        for (y in 0 until height) {
            val filter = inflated[p++].toInt() and 0xFF
            if (filter !in 0..4) return null
            val rowStart = y * stride
            for (x in 0 until stride) {
                val filt = inflated[p++].toInt() and 0xFF
                val a = if (x >= bpp) raw[rowStart + x - bpp].toInt() and 0xFF else 0
                val b = if (y > 0) raw[rowStart - stride + x].toInt() and 0xFF else 0
                val c = if (x >= bpp && y > 0) raw[rowStart - stride + x - bpp].toInt() and 0xFF else 0
                val recon = when (filter) {
                    0 -> filt
                    1 -> filt + a
                    2 -> filt + b
                    3 -> filt + (a + b) / 2
                    else -> filt + paeth(a, b, c)
                }
                raw[rowStart + x] = (recon and 0xFF).toByte()
            }
        }
        val rgba = IntArray(width * height)
        var rp = 0
        for (i in rgba.indices) {
            val r: Int
            val g: Int
            val b: Int
            val a: Int
            when (colorType) {
                0 -> {
                    r = raw[rp++].toInt() and 0xFF; g = r; b = r; a = 255
                }
                2 -> {
                    r = raw[rp++].toInt() and 0xFF
                    g = raw[rp++].toInt() and 0xFF
                    b = raw[rp++].toInt() and 0xFF
                    a = 255
                }
                else -> {
                    r = raw[rp++].toInt() and 0xFF
                    g = raw[rp++].toInt() and 0xFF
                    b = raw[rp++].toInt() and 0xFF
                    a = raw[rp++].toInt() and 0xFF
                }
            }
            rgba[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Image(width, height, rgba)
    }

    fun encode(width: Int, height: Int, rgba: IntArray): ByteArray {
        require(width > 0 && height > 0 && rgba.size == width * height) {
            "Pixel buffer size ${rgba.size} does not match ${width}x$height"
        }
        val out = ByteArrayOutputStream()
        fun writeU32(v: Int) {
            out.write(v ushr 24); out.write(v ushr 16); out.write(v ushr 8); out.write(v)
        }
        fun writeChunk(type: String, data: ByteArray) {
            writeU32(data.size)
            val typeBytes = type.toByteArray(Charsets.US_ASCII)
            out.write(typeBytes)
            out.write(data)
            val crc = java.util.zip.CRC32()
            crc.update(typeBytes)
            crc.update(data)
            writeU32(crc.value.toInt())
        }
        out.write(SIGNATURE)
        val ihdr = ByteArrayOutputStream()
        fun ByteArrayOutputStream.u32(v: Int) {
            write(v ushr 24); write(v ushr 16); write(v ushr 8); write(v)
        }
        ihdr.u32(width)
        ihdr.u32(height)
        ihdr.write(8) // bit depth
        ihdr.write(6) // color type: RGBA
        ihdr.write(0) // compression
        ihdr.write(0) // filter
        ihdr.write(0) // interlace
        writeChunk("IHDR", ihdr.toByteArray())
        val raw = ByteArrayOutputStream(height * (1 + width * 4))
        var i = 0
        for (y in 0 until height) {
            raw.write(0) // filter type 0 (None)
            for (x in 0 until width) {
                val px = rgba[i++]
                raw.write(px ushr 16 and 0xFF) // R
                raw.write(px ushr 8 and 0xFF) // G
                raw.write(px and 0xFF) // B
                raw.write(px ushr 24 and 0xFF) // A
            }
        }
        val deflater = java.util.zip.Deflater(6)
        deflater.setInput(raw.toByteArray())
        deflater.finish()
        val comp = ByteArrayOutputStream()
        val buf = ByteArray(65_536)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            comp.write(buf, 0, n)
        }
        deflater.end()
        writeChunk("IDAT", comp.toByteArray())
        writeChunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun getBe32(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or
            ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or
            (b[o + 3].toInt() and 0xFF)

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = abs(p - a)
        val pb = abs(p - b)
        val pc = abs(p - c)
        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
    }
}

/**
 * Default demo image compositor.
 *
 * [blend]: center-crops the garment to 3:4, scales it to the photo width,
 * overlays it on the lower two-thirds of the photo with a feathered top edge,
 * and returns a PNG. Inputs must be PNG (the codec above); anything else
 * falls back to [placeholder], a clearly synthetic side-by-side BMP — always
 * valid image bytes, never garbage.
 *
 * This is DEMO-grade on purpose: the app module injects a Bitmap compositor
 * (JPEG-aware, nicer masking) for anything user-facing.
 */
object DemoCompositor {

    fun blend(photo: ByteArray, garment: ByteArray): ByteArray {
        val photoImg = PngCodec.decode(photo) ?: return placeholder(photo, garment)
        val garmentImg = PngCodec.decode(garment) ?: return placeholder(photo, garment)
        val pw = photoImg.width
        val ph = photoImg.height
        // Center-crop garment to a 3:4 aspect.
        val gw = garmentImg.width
        val gh = garmentImg.height
        val targetAspect = 3f / 4f
        val aspect = gw.toFloat() / gh
        val cx: Int
        val cy: Int
        val cw: Int
        val ch: Int
        if (aspect > targetAspect) {
            cw = (gh * targetAspect).toInt().coerceAtLeast(1)
            ch = gh
            cx = ((gw - cw) / 2).coerceAtLeast(0)
            cy = 0
        } else {
            cw = gw
            ch = (gw / targetAspect).toInt().coerceAtLeast(1)
            cx = 0
            cy = ((gh - ch) / 2).coerceAtLeast(0)
        }
        val sw = pw
        val sh = (ph * 2 / 3).coerceAtLeast(1)
        val yOff = ph - sh
        val featherRows = (sh * 0.06f).toInt().coerceAtLeast(1)
        val out = photoImg.rgba.copyOf()
        for (y in 0 until sh) {
            val gy = (cy + y * ch / sh).coerceIn(0, gh - 1)
            val feather = (y.toFloat() / featherRows).coerceIn(0f, 1f)
            for (x in 0 until sw) {
                val gx = (cx + x * cw / sw).coerceIn(0, gw - 1)
                val src = garmentImg.rgba[gy * gw + gx]
                val alpha = ((src ushr 24) / 255f) * feather
                if (alpha <= 0f) continue
                val di = (yOff + y) * pw + x
                out[di] = blendPixel(src, out[di], alpha)
            }
        }
        return PngCodec.encode(pw, ph, out)
    }

    /**
     * Synthesizes a simple flat-lay tee PNG (512x683) in a color derived from
     * [seed], so the demo pipeline works with zero backend or assets.
     */
    fun synthesizeGarment(seed: String): ByteArray {
        val w = 512
        val h = 683
        val bg = 0xFFF5F5F7.toInt()
        val px = IntArray(w * h) { bg }
        val palette = intArrayOf(
            0xFFFF6B6B.toInt(), // coral
            0xFF4ECDC4.toInt(), // teal
            0xFF355070.toInt(), // deep navy
            0xFFF4A259.toInt(), // mustard
            0xFF9B5DE5.toInt(), // lavender
        )
        val color = palette[(seed.hashCode() and Int.MAX_VALUE) % palette.size]
        val edge = darken(color)
        // Body + sleeves.
        fillRect(px, w, h, 172, 150, 340, 600, color)
        fillRect(px, w, h, 92, 160, 172, 300, color)
        fillRect(px, w, h, 340, 160, 420, 300, color)
        // Neckline scoop + collar rib.
        fillEllipse(px, w, h, 256, 150, 52, 34, bg)
        strokeEllipse(px, w, h, 256, 150, 52, 34, edge, 6)
        // Outlines + hem.
        strokeRect(px, w, h, 172, 150, 340, 600, edge, 5)
        strokeRect(px, w, h, 92, 160, 172, 300, edge, 5)
        strokeRect(px, w, h, 340, 160, 420, 300, edge, 5)
        fillRect(px, w, h, 172, 584, 340, 592, edge)
        return PngCodec.encode(w, h, px)
    }

    /**
     * Last-resort result: a valid 640x480 BMP whose two halves are pastel
     * gradients seeded from the input hashes (left = photo, right = garment).
     * Obviously synthetic — that's the point.
     */
    fun placeholder(photo: ByteArray, garment: ByteArray): ByteArray {
        val w = 640
        val h = 480
        val hueA = (photo.contentHashCode() and Int.MAX_VALUE) % 360
        val hueB = (garment.contentHashCode() and Int.MAX_VALUE) % 360
        val rowSize = (w * 3 + 3) / 4 * 4
        val pixels = ByteArray(rowSize * h)
        for (y in 0 until h) {
            val shade = y.toFloat() / h
            val (ra, ga, ba) = pastelRgb(hueA, shade)
            val (rb, gb, bb) = pastelRgb(hueB, shade)
            for (x in 0 until w) {
                val left = x < w / 2
                var r = if (left) ra else rb
                var g = if (left) ga else gb
                var b = if (left) ba else bb
                if (abs(x - w / 2) <= 2) {
                    r = 255; g = 255; b = 255 // white split line
                }
                val off = (h - 1 - y) * rowSize + x * 3
                pixels[off] = b.toByte()
                pixels[off + 1] = g.toByte()
                pixels[off + 2] = r.toByte()
            }
        }
        val out = ByteArrayOutputStream(54 + pixels.size)
        out.write(byteArrayOf(0x42, 0x4D)) // "BM"
        out.writeLe32(54 + pixels.size) // file size
        out.writeLe32(0) // reserved
        out.writeLe32(54) // pixel data offset
        out.writeLe32(40) // DIB header size
        out.writeLe32(w)
        out.writeLe32(h)
        out.writeLe16(1) // planes
        out.writeLe16(24) // bits per pixel
        out.writeLe32(0) // compression: none
        out.writeLe32(pixels.size) // image size
        out.writeLe32(0) // x pixels/meter
        out.writeLe32(0) // y pixels/meter
        out.writeLe32(0) // palette colors
        out.writeLe32(0) // important colors
        out.write(pixels)
        return out.toByteArray()
    }

    private fun blendPixel(src: Int, dst: Int, alpha: Float): Int {
        val sr = (src ushr 16) and 0xFF
        val sg = (src ushr 8) and 0xFF
        val sb = src and 0xFF
        val dr = (dst ushr 16) and 0xFF
        val dg = (dst ushr 8) and 0xFF
        val db = dst and 0xFF
        val r = (sr * alpha + dr * (1 - alpha)).toInt().coerceIn(0, 255)
        val g = (sg * alpha + dg * (1 - alpha)).toInt().coerceIn(0, 255)
        val b = (sb * alpha + db * (1 - alpha)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun pastelRgb(hue: Int, shade: Float): Triple<Int, Int, Int> {
        val h = (hue % 360) / 60f
        val s = 0.45f
        val v = 0.97f - 0.18f * shade
        val c = v * s
        val x = c * (1 - abs(h % 2 - 1))
        val (r1, g1, b1) = when (h.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        return Triple(
            ((r1 + m) * 255).toInt().coerceIn(0, 255),
            ((g1 + m) * 255).toInt().coerceIn(0, 255),
            ((b1 + m) * 255).toInt().coerceIn(0, 255),
        )
    }

    private fun darken(color: Int): Int {
        val a = color ushr 24
        val r = ((color ushr 16) and 0xFF) * 3 / 4
        val g = ((color ushr 8) and 0xFF) * 3 / 4
        val b = (color and 0xFF) * 3 / 4
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun fillRect(px: IntArray, w: Int, h: Int, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        for (y in y0.coerceAtLeast(0) until y1.coerceAtMost(h)) {
            for (x in x0.coerceAtLeast(0) until x1.coerceAtMost(w)) {
                px[y * w + x] = color
            }
        }
    }

    private fun strokeRect(
        px: IntArray, w: Int, h: Int,
        x0: Int, y0: Int, x1: Int, y1: Int,
        color: Int, t: Int,
    ) {
        fillRect(px, w, h, x0, y0, x1, y0 + t, color)
        fillRect(px, w, h, x0, y1 - t, x1, y1, color)
        fillRect(px, w, h, x0, y0, x0 + t, y1, color)
        fillRect(px, w, h, x1 - t, y0, x1, y1, color)
    }

    private fun fillEllipse(
        px: IntArray, w: Int, h: Int,
        cx: Int, cy: Int, rx: Int, ry: Int, color: Int,
    ) {
        for (y in (cy - ry).coerceAtLeast(0) until (cy + ry).coerceAtMost(h)) {
            val dy = (y - cy).toDouble() / ry
            val dx = rx * sqrt((1 - dy * dy).coerceAtLeast(0.0))
            for (x in (cx - dx).toInt().coerceAtLeast(0) until (cx + dx).toInt().coerceAtMost(w)) {
                px[y * w + x] = color
            }
        }
    }

    private fun strokeEllipse(
        px: IntArray, w: Int, h: Int,
        cx: Int, cy: Int, rx: Int, ry: Int, color: Int, t: Int,
    ) {
        var a = 0.0
        while (a < Math.PI * 2) {
            val ex = (cx + rx * cos(a)).toInt()
            val ey = (cy + ry * sin(a)).toInt()
            fillRect(px, w, h, ex - t / 2, ey - t / 2, ex + t / 2 + 1, ey + t / 2 + 1, color)
            a += 0.02
        }
    }
}

private fun ByteArrayOutputStream.writeLe16(v: Int) {
    write(v and 0xFF)
    write((v ushr 8) and 0xFF)
}

private fun ByteArrayOutputStream.writeLe32(v: Int) {
    write(v and 0xFF)
    write((v ushr 8) and 0xFF)
    write((v ushr 16) and 0xFF)
    write((v ushr 24) and 0xFF)
}
