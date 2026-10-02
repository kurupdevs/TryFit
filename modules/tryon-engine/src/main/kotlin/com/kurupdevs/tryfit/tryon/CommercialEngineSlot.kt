package com.kurupdevs.tryfit.tryon

/**
 * Configuration + HTTP plumbing for a commercial try-on provider
 * (fal.ai / FASHN class). The client never talks to the provider directly —
 * see the security note below.
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │ SECURITY — READ BEFORE TOUCHING THIS FILE                          │
 * │ There is NO API key field anywhere in this module, by design.      │
 * │ The provider key lives server-side in the Supabase Edge Function    │
 * │ (`createTryOnSession`), which signs provider requests and returns  │
 * │ only signed result URLs to the client. A key baked into the app     │
 * │ can be extracted from the APK in minutes and drained.              │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * Verified 2026-10-02 from fal.ai's own explore page:
 * - PRIMARY `fal-ai/kling/v1-5/kolors-virtual-try-on`: inputs
 *   `human_image_url` + `garment_image_url` (+ optional `sync_mode`),
 *   $0.07/generation, commercial use OK.
 * - FALLBACK `fashn/tryon/v1.6`: 864x1296 output, ~$0.075/generation.
 * - NOTE: fal.ai welcome-credit amounts are UNVERIFIED — verify at signup
 *   before promising any free tier in-app or budgeting around it.
 */

/** Cost constants + quota/budget math for the commercial path. */
object TryOnPricing {
    /** Verified 2026-10-02: kolors-virtual-try-on = $0.07/generation. */
    const val EST_COST_USD_PER_TRYON = 0.07

    /** Verified 2026-10-02: fashn/tryon v1.6 ≈ $0.075/generation. */
    const val FALLBACK_COST_USD_PER_TRYON = 0.075

    /** Display-only FX rate; do NOT use for billing. */
    const val USD_TO_INR_APPROX = 83.0

    /** Expected provider spend for [count] try-ons. */
    fun costForTryons(count: Int, costPerTryonUsd: Double = EST_COST_USD_PER_TRYON): Double =
        count * costPerTryonUsd

    /** How many try-ons a USD budget buys (rounds down). */
    fun maxTryonsForBudgetUsd(budgetUsd: Double, costPerTryonUsd: Double = EST_COST_USD_PER_TRYON): Int =
        (budgetUsd / costPerTryonUsd).toInt().coerceAtLeast(0)

    /** Approximate INR display value. */
    fun usdToInrApprox(usd: Double): Double = usd * USD_TO_INR_APPROX
}

/**
 * Provider endpoint configuration.
 *
 * NOTE: no `apiKey` field — see the security note at the top of this file.
 * The Edge Function injects the key server-side.
 */
data class ProviderConfig(
    val displayName: String,
    /** Queue base, e.g. `https://queue.fal.run`. */
    val baseUrl: String,
    /** Model id, e.g. `fal-ai/kling/v1-5/kolors-virtual-try-on`. */
    val modelId: String,
    /** Per-attempt deadline (SPEC.md §7: 120s/attempt). */
    val timeoutMs: Long = 120_000,
    /** Retries after the first attempt (SPEC.md §7: max 2 retries). */
    val maxRetries: Int = 2,
    /** Delay between status polls. */
    val pollIntervalMs: Long = 3_000,
    val costUsdPerTryon: Double = TryOnPricing.EST_COST_USD_PER_TRYON,
)

/** Known provider presets. */
object TryOnProviders {
    /** Primary. Commercial use OK. $0.07/generation (verified 2026-10-02). */
    val PRIMARY_KOLORS = ProviderConfig(
        displayName = "fal.ai — Kolors Virtual Try-On (kling v1.5)",
        baseUrl = "https://queue.fal.run",
        modelId = "fal-ai/kling/v1-5/kolors-virtual-try-on",
        costUsdPerTryon = TryOnPricing.EST_COST_USD_PER_TRYON,
    )

    /** Fallback. 864x1296 output, ~$0.075/generation (verified 2026-10-02). */
    val FALLBACK_FASHN = ProviderConfig(
        displayName = "FASHN Try-On v1.6 (fallback, 864x1296)",
        baseUrl = "https://queue.fal.run",
        modelId = "fashn/tryon/v1.6",
        costUsdPerTryon = TryOnPricing.FALLBACK_COST_USD_PER_TRYON,
    )

    // FUTURE — multi-garment in a single call (1–3 garment reference images):
    // val FUTURE_BRIA_MULTIGARMENT = ProviderConfig(
    //     displayName = "Bria fibo-edit-1.5 virtual try-on (multi-garment)",
    //     baseUrl = "https://queue.fal.run",
    //     modelId = "bria/fibo-edit-1.5/virtual-try-on",
    // )
    // When enabling: buildRequest gains `garmentUrls: List<String>` (max 3) and
    // the payload key becomes the provider's multi-image field; the rest of the
    // slot (queue URLs, polling, parseResult) is unchanged.
}

/**
 * Builds the provider request payload.
 *
 * Kolors v1-5 schema (verified 2026-10-02): `human_image_url` + `garment_image_url`,
 * with optional `sync_mode` (true = inline result instead of queue polling).
 *
 * @param sizeHint accepted for API stability but NOT sent — the kolors schema
 *   has no size field. Size is applied server-side (Edge Function) when routing
 *   to the fallback provider.
 */
fun buildRequest(
    photoUrl: String,
    garmentUrl: String,
    sizeHint: String? = null,
): Map<String, Any> = buildMap {
    put("human_image_url", photoUrl)
    put("garment_image_url", garmentUrl)
    // put("sync_mode", true) // optional: skip queue polling, get the result inline
}

/** fal.ai queue submit endpoint for [config]. */
fun submitUrl(config: ProviderConfig): String = "${config.baseUrl}/${config.modelId}"

/** fal.ai queue status endpoint for a submitted request. */
fun statusUrl(config: ProviderConfig, requestId: String): String =
    "${submitUrl(config)}/requests/$requestId/status"

/** fal.ai queue result endpoint for a completed request. */
fun responseUrl(config: ProviderConfig, requestId: String): String =
    "${submitUrl(config)}/requests/$requestId/response"

/** Extracts `request_id` from a queue-submit response body. Null when absent. */
fun parseRequestId(json: String): String? = extractJsonStringValue(json, "request_id", 0)

/**
 * Parses a provider result payload (queue `/response` body or webhook body —
 * same shape) and downloads the result image.
 *
 * Looks for the first `url` inside the `images` array, e.g.
 * `{"images":[{"url":"https://…","content_type":"image/jpeg"}]}`.
 * Best-effort tolerant parsing (no JSON lib on this module's classpath);
 * override in the app module if the provider changes its schema.
 *
 * @return [Result.success] with the raw image bytes, or [Result.failure] with
 *   the underlying [java.io.IOException]/[IllegalStateException]. Callers map
 *   failures to [TryOnError] via [mapHttpError] / [TryOnError.PROVIDER_ERROR].
 */
fun parseResult(json: String): Result<ByteArray> = runCatching {
    val url = extractFirstImageUrl(json)
        ?: throw IllegalStateException("No result image URL in provider response")
    downloadBytes(url)
}

/** First image URL inside the provider response's `images` array. Null when absent. */
fun extractFirstImageUrl(json: String): String? {
    val imagesAt = json.indexOf("\"images\"")
    val from = if (imagesAt >= 0) imagesAt else 0
    return extractJsonStringValue(json, "url", from)
}

/**
 * Maps a provider HTTP failure to a typed [TryOnError] matching the Edge
 * Function error codes (SPEC.md §7).
 */
fun mapHttpError(httpStatus: Int, body: String? = null): TryOnError {
    val lower = body?.lowercase().orEmpty()
    return when {
        httpStatus == 429 -> TryOnError(
            TryOnError.QUOTA_EXCEEDED,
            "The try-on service is rate-limited right now — please retry in a bit.",
            retryable = true,
        )
        httpStatus == 408 || httpStatus == 504 -> TryOnError(
            TryOnError.TIMEOUT,
            "The try-on took too long — tap retry.",
            retryable = true,
        )
        lower.contains("nsfw") || lower.contains("moderation") || lower.contains("inappropriate") ->
            TryOnError(
                TryOnError.MODERATION_REJECTED,
                "That photo didn't pass the safety check — try a different one.",
                retryable = false,
            )
        httpStatus in 400..499 -> TryOnError(
            TryOnError.INVALID_PHOTO,
            "The provider couldn't use that photo — try a clearer front-facing one.",
            retryable = true,
        )
        else -> TryOnError(
            TryOnError.PROVIDER_ERROR,
            "Try-on service hiccup — tap retry.",
            retryable = true,
        )
    }
}

private fun downloadBytes(url: String, timeoutMs: Int = 30_000): ByteArray {
    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    try {
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "image/*")
        val code = conn.responseCode
        if (code !in 200..299) throw java.io.IOException("Result image download failed: HTTP $code")
        return conn.inputStream.use { it.readBytes() }
    } finally {
        conn.disconnect()
    }
}

/**
 * Best-effort JSON string-value extractor (tolerant of whitespace and key
 * order; handles basic escapes). Enough for the known provider schemas —
 * not a general JSON parser.
 */
private fun extractJsonStringValue(json: String, key: String, fromIndex: Int): String? {
    val quoted = "\"$key\""
    var i = json.indexOf(quoted, fromIndex)
    while (i >= 0) {
        var j = i + quoted.length
        while (j < json.length && json[j].isWhitespace()) j++
        if (j < json.length && json[j] == ':') {
            j++
            while (j < json.length && json[j].isWhitespace()) j++
            if (j < json.length && json[j] == '"') {
                val sb = StringBuilder()
                j++
                while (j < json.length) {
                    val c = json[j]
                    if (c == '\\' && j + 1 < json.length) {
                        sb.append(json[j + 1])
                        j += 2
                        continue
                    }
                    if (c == '"') return sb.toString()
                    sb.append(c)
                    j++
                }
                return null
            }
        }
        i = json.indexOf(quoted, i + 1)
    }
    return null
}
