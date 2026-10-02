# tryon-engine

Pure-JVM Kotlin module for the TryFit virtual try-on pipeline. Zero Android
imports — image payloads travel as `ByteArray`, so the same code runs on the
JVM (tests, tooling) and inside the Android app.

Package: `com.kurupdevs.tryfit.tryon`

## Files

| File | What |
|---|---|
| `TryOnModels.kt` | `TryOnSession`, `TryOnStatus` (QUEUED/PROCESSING/DONE/FAILED/CANCELLED), `ProcessingStage` (7 stages), `TryOnResult`, `TryOnError` with codes `QUOTA_EXCEEDED` / `MODERATION_REJECTED` / `INVALID_PHOTO` / `PROVIDER_ERROR` / `TIMEOUT` / `CANCELLED` |
| `TryOnEngine.kt` | The `TryOnEngine` interface: `startSession` (idempotency-keyed), `observeSession(): Flow<TryOnProgress>` (`Stage`/`Done`/`Failed`), `cancelSession`, `getQuota`. Failures always surface as `Failed` — never raw exceptions |
| `PhotoQualityGate.kt` | Pure-logic pre-flight check on `PhotoAnalysis` (front-facing / well-lit / upper-body-visible + width/height/bytes). Fails fast and free before any paid provider call |
| `DemoTryOnEngine.kt` | Realistic simulated pipeline: ~14.2s across the 7 stages, cancellable, 8% friendly failures, terminal-state replay, rotating per-stage copy via `copyFor(stage)`. Bundles a pure-Kotlin PNG codec + demo compositor (no image libs needed) |
| `CommercialEngineSlot.kt` | fal.ai/FASHN-class HTTP slot: `ProviderConfig`, primary/fallback presets, `buildRequest()` payload builder, queue URL helpers, `parseResult()` response parser, `mapHttpError()` → typed `TryOnError`, `TryOnPricing` cost/quota math |

## The abstraction

The app talks only to `TryOnEngine`. Two implementations:

- **Demo** — `DemoTryOnEngine()`. Drop-in for dev builds, UI wiring, and screenshots. Deterministic when seeded: `DemoTryOnEngine(random = Random(42), failureRate = 0.0)`.
- **Commercial** — implemented in the app module against the Supabase Edge Function (`createTryOnSession` / `getTryOnStatus` / `cancelTryOnSession`, SPEC.md §7). Same interface, real billing.

## Swapping demo → commercial

1. Implement `TryOnEngine` in the app module backed by the Edge Functions.
2. Keep the UI on `observeSession()` — `Stage` emissions drive the progress screen, `Done` drives the reveal, `Failed` drives the friendly-error state. No UI changes needed.
3. Wire `copyFor(stage)` (or your own copy) to the 2.2s rotation timer.
4. Feed the quality gate with real MediaPipe output: map face-detection results to `PhotoAnalysis(isFrontFacing, isWellLit, upperBodyVisible, width, height)` and call `PhotoQualityGate().evaluate(...)` before `startSession`.
5. For a nicer demo composite without the provider, inject `demoCompositor = { photo, garment -> /* your Bitmap blend */ }` — the engine stays the same.

## Commercial provider notes (verified 2026-10-02)

- **Primary:** `fal-ai/kling/v1-5/kolors-virtual-try-on` — `human_image_url` + `garment_image_url` (+ optional `sync_mode`), **$0.07/generation**, commercial use OK.
- **Fallback:** `fashn/tryon/v1.6` — 864x1296, ~$0.075/generation.
- **Future multi-garment:** `bria/fibo-edit-1.5/virtual-try-on` accepts 1–3 garment reference images (commented stub in `TryOnProviders`).
- **Security:** no API key field exists anywhere in this module — the key lives server-side in the Supabase Edge Function; the client never holds it.
- **Welcome credits:** fal.ai welcome-credit amounts are UNVERIFIED — verify at signup before budgeting or promising free tier.
- Cost guard rails: `TryOnPricing.costForTryons()` / `maxTryonsForBudgetUsd()`; server enforces 5 free/day, 120s timeout, ≤2 retries, cancel while queued/processing (SPEC.md §7).

## Constraints

- Pure JVM Kotlin (`kotlin("jvm")`). No `android.*`, no `Bitmap`. Only stdlib + `kotlinx-coroutines-core` + `java.util.zip`/`java.net` (present on JVM and Android).
- The PNG codec handles non-interlaced 8-bit PNGs (gray/RGB/RGBA) — enough for the demo compositor. JPEG decode is intentionally out of scope; the app module's injected compositor handles real photos.
