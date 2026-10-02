package com.kurupdevs.tryfit.video

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

/**
 * On-device Try-On Transition Generator (FEATURE-RESEARCH §0 — the viral #1 hook).
 *
 * Input: before Bitmap (user photo) + after Bitmap (try-on result).
 * Output: 720×960, 30fps, 3.2s H.264 MP4 via MediaCodec + MediaMuxer.
 *
 * Timeline:
 * - 0–0.9s: before photo, subtle Ken Burns zoom (1.00→1.08)
 * - 0.9–1.7s: diagonal wipe revealing after, white 6px edge line
 * - 100BPM beats (every 0.6s): 120ms white flash + 1.04× punch scale
 * - 1.7–3.2s: after photo, slow zoom (1.00→1.06)
 * - "TryFit" watermark bottom-right throughout
 *
 * Frames are rendered with [Canvas] onto the codec input Surface. Frame
 * pacing: each frame is posted on a 1/30s wall-clock cadence so the muxer
 * timestamps (assigned at post time) play back at the right speed — no EGL
 * needed. ~3.2s encode time for 96 frames.
 *
 * All codec errors are caught and returned as [Result.failure]; the codec,
 * surface and muxer are always released.
 */
class MediaCodecTransitionVideoGenerator : TransitionVideoGenerator {

    override val isAvailable: Boolean = true

    override suspend fun generate(
        beforePhoto: File,
        afterPhoto: File,
        output: File
    ): Result<File> = withContext(Dispatchers.Default) {
        runCatching {
            val before = BitmapFactory.decodeFile(beforePhoto.absolutePath)
                ?: throw IOException("Cannot decode before photo: ${beforePhoto.name}")
            val after = BitmapFactory.decodeFile(afterPhoto.absolutePath)
                ?: throw IOException("Cannot decode after photo: ${afterPhoto.name}")
            try {
                renderToFile(before, after, output)
                output
            } finally {
                if (!before.isRecycled) before.recycle()
                if (!after.isRecycled) after.recycle()
            }
        }
    }

    /**
     * Bitmap-direct entry point (try-on result screen).
     * The output lands in `<cache>/videos/tryfit_transition_<ts>.mp4`.
     */
    suspend fun generate(
        before: Bitmap,
        after: Bitmap,
        context: Context,
        fileName: String = "tryfit_transition_${System.currentTimeMillis()}.mp4"
    ): Result<File> = withContext(Dispatchers.Default) {
        runCatching {
            val out = File(File(context.cacheDir, "videos").apply { mkdirs() }, fileName)
            renderToFile(before, after, out)
            out
        }
    }

    private fun renderToFile(before: Bitmap, after: Bitmap, out: File) {
        out.parentFile?.mkdirs()
        val format = MediaFormat.createVideoFormat(MIME, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
        }
        val codec = MediaCodec.createEncoderByType(MIME)
        var muxer: MediaMuxer? = null
        var surface: Surface? = null
        var muxerStarted = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = codec.createInputSurface()
            surface = inputSurface
            codec.start()
            val mux = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux

            val renderer = FrameRenderer(cover(before), cover(after))
            val info = MediaCodec.BufferInfo()
            var trackIndex = -1

            fun drain(endOfStream: Boolean): Boolean {
                var sawEos = false
                while (true) {
                    val idx = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
                    when {
                        idx == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                        idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            trackIndex = mux.addTrack(codec.outputFormat)
                            mux.start()
                            muxerStarted = true
                        }
                        idx >= 0 -> {
                            val buf = codec.getOutputBuffer(idx)
                            if (buf != null) {
                                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                    info.size = 0
                                }
                                if (info.size > 0 && muxerStarted) {
                                    buf.position(info.offset)
                                    buf.limit(info.offset + info.size)
                                    mux.writeSampleData(trackIndex, buf, info)
                                }
                            }
                            codec.releaseOutputBuffer(idx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                sawEos = true
                                break
                            }
                        }
                    }
                }
                return sawEos
            }

            // Paced posting: 1/30s wall-clock cadence → correct muxer timestamps.
            val startNs = System.nanoTime()
            val frameNs = 1_000_000_000L / FPS
            for (frame in 0 until FRAME_COUNT) {
                val canvas = inputSurface.lockCanvas(null)
                try {
                    renderer.drawFrame(canvas, frame)
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }
                drain(false)
                val targetNs = startNs + (frame + 1) * frameNs
                val nowNs = System.nanoTime()
                if (targetNs > nowNs) {
                    Thread.sleep((targetNs - nowNs) / 1_000_000)
                }
            }
            codec.signalEndOfInputStream()
            var eos = false
            var guard = 0
            while (!eos && guard++ < 300) {
                eos = drain(true)
            }
            if (!eos) throw IOException("Encoder did not signal end of stream")
            if (!muxerStarted) throw IOException("Encoder produced no output")
        } finally {
            runCatching { codec.stop() }
            codec.release()
            surface?.release()
            if (muxerStarted) runCatching { muxer?.stop() }
            muxer?.release()
        }
    }

    /** Scale-to-cover + center-crop to exactly 720×960, once per input. */
    private fun cover(src: Bitmap): Bitmap {
        val scale = maxOf(WIDTH / src.width.toFloat(), HEIGHT / src.height.toFloat())
        val sw = (src.width * scale).roundToInt().coerceAtLeast(WIDTH)
        val sh = (src.height * scale).roundToInt().coerceAtLeast(HEIGHT)
        val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
        val out = Bitmap.createBitmap(scaled, (sw - WIDTH) / 2, (sh - HEIGHT) / 2, WIDTH, HEIGHT)
        if (scaled !== out && !scaled.isRecycled) scaled.recycle()
        return out
    }

    companion object {
        const val WIDTH = 720
        const val HEIGHT = 960
        const val FPS = 30
        const val DURATION_SEC = 3.2f
        const val FRAME_COUNT = 96 // 3.2s × 30fps
        const val BITRATE = 4_000_000
        const val MIME = "video/avc"

        /** 100 BPM → a beat every 0.6s. */
        const val BEAT_SEC = 0.6f
        const val FLASH_SEC = 0.12f
    }
}

/**
 * Per-frame renderer. All values are precomputed per frame; bitmaps are
 * pre-scaled to 720×960 so each frame is a handful of draw calls.
 */
private class FrameRenderer(
    private val before: Bitmap,
    private val after: Bitmap
) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }
    private val flashPaint = Paint()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(8f, 2f, 3f, Color.argb(170, 0, 0, 0))
    }

    fun drawFrame(canvas: Canvas, frame: Int) {
        val t = frame / MediaCodecTransitionVideoGenerator.FPS.toFloat()
        val beatAge = if (t >= MediaCodecTransitionVideoGenerator.BEAT_SEC) {
            t - (t / MediaCodecTransitionVideoGenerator.BEAT_SEC).toInt() *
                MediaCodecTransitionVideoGenerator.BEAT_SEC
        } else {
            Float.MAX_VALUE
        }
        // 1.04× punch scale decaying over 120ms after each beat.
        val punch = if (beatAge < MediaCodecTransitionVideoGenerator.FLASH_SEC) {
            1f + 0.04f * (1f - beatAge / MediaCodecTransitionVideoGenerator.FLASH_SEC)
        } else {
            1f
        }

        canvas.drawColor(Color.BLACK)
        when {
            t < 0.9f -> {
                // Before photo, Ken Burns 1.00 → 1.08.
                drawZoomed(canvas, before, (1f + 0.08f * (t / 0.9f)) * punch)
            }
            t < 1.7f -> {
                // Diagonal wipe revealing after, white 6px edge line.
                drawZoomed(canvas, before, 1.08f * punch)
                val p = (t - 0.9f) / 0.8f
                val ax = 2f * (1f - p) * MediaCodecTransitionVideoGenerator.WIDTH
                val ay = 2f * (1f - p) * MediaCodecTransitionVideoGenerator.HEIGHT
                canvas.save()
                canvas.clipPath(
                    Path().apply {
                        val w = MediaCodecTransitionVideoGenerator.WIDTH.toFloat()
                        val h = MediaCodecTransitionVideoGenerator.HEIGHT.toFloat()
                        moveTo(ax, 0f)
                        lineTo(w, 0f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        lineTo(0f, ay)
                        close()
                    }
                )
                drawZoomed(canvas, after, punch)
                canvas.restore()
                canvas.drawLine(ax, 0f, 0f, ay, edgePaint)
            }
            else -> {
                // After photo, slow zoom 1.00 → 1.06.
                val progress = ((t - 1.7f) / 1.5f).coerceIn(0f, 1f)
                drawZoomed(canvas, after, (1f + 0.06f * progress) * punch)
            }
        }

        // Beat flash: white overlay fading over 120ms.
        if (beatAge < MediaCodecTransitionVideoGenerator.FLASH_SEC) {
            flashPaint.alpha =
                (110 * (1f - beatAge / MediaCodecTransitionVideoGenerator.FLASH_SEC)).toInt()
            canvas.drawRect(
                0f, 0f,
                MediaCodecTransitionVideoGenerator.WIDTH.toFloat(),
                MediaCodecTransitionVideoGenerator.HEIGHT.toFloat(),
                flashPaint
            )
        }

        // Watermark, bottom-right, every frame.
        val label = "TryFit"
        val tw = textPaint.measureText(label)
        canvas.drawText(
            label,
            MediaCodecTransitionVideoGenerator.WIDTH - tw - 36f,
            MediaCodecTransitionVideoGenerator.HEIGHT - 44f,
            textPaint
        )
    }

    private fun drawZoomed(canvas: Canvas, bitmap: Bitmap, zoom: Float) {
        val w = MediaCodecTransitionVideoGenerator.WIDTH
        val h = MediaCodecTransitionVideoGenerator.HEIGHT
        val dw = w * zoom
        val dh = h * zoom
        canvas.drawBitmap(
            bitmap,
            null,
            RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f),
            bitmapPaint
        )
    }
}

/**
 * Shares a generated transition video through the system share sheet
 * (TikTok / Reels / WhatsApp…) via FileProvider.
 */
fun shareVideo(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share transition video"))
}
