# Virtual Try-On Shopping App — Build Spec

**App:** AI-powered virtual try-on e-commerce ("Ecom with AI Powered Virtual Try-On")
**Reference mockup:** `~/workspace/user/media_library/image/4c/4c02bae96dba6f456e7c0ec864db9373187c99086f0e58d5cf5782f7b2bcb264.webp`
**Date:** 2026-10-02 · **Status:** spec only — no build authorized yet
**Mandate from user:** same UI as the mockup, fully functional online app, fully smooth.

Synthesized from 4 parallel research agents: UI teardown, motion/interaction, stack decision, data model + backend architecture.

---

## 1. Tech Stack (decided)

| Component | Chosen | Why |
|---|---|---|
| Client | **Native Android Kotlin + Jetpack Compose** | Matches all other apps' stack (shared camera/auth/storage modules, one CI, one signing flow); CameraX + ML Kit are Kotlin-first; smaller APK than Flutter (matters for India-market installs); Kotlin Multiplatform/Compose Multiplatform keeps an iOS door open later |
| Backend | **Supabase** | Free-tier file storage with no billing card (Firebase Storage now requires Blaze pay-as-you-go — a liability for a photo-heavy v1); Postgres + RLS beats Firestore for catalog filtering/joins; portable (self-hostable), no vendor lock-in |
| Try-on engine | **Commercial try-on API (FASHN / fal.ai class) primary, Replicate CatVTON-class as fallback** | Zero infra, ship in weeks; commercial-safe licensing. ⚠️ IDM-VTON is CC BY-NC-SA (non-commercial) — DO NOT ship on it. Budget ~$0.05/try-on (estimate); 15–60s latency per result — design for async |
| Product catalog | **Curated seed catalog (100–300 garments, royalty-free images, fictional brands like "Urbanco") + admin CSV/image upload from day one** | Legally clean (no scraping brand sites/Google Images — copyrighted product photos are a lawsuit); merchant onboarding path built in from v1 |
| Auth | **Google sign-in primary + anonymous try-first** | One-tap, zero SMS cost (phone OTP bills per SMS); let users try 2–3 garments anonymously, prompt sign-in at the value moment |
| Currency | **₹ (INR) throughout** | Indian market; decide now — mixed currency kills trust at checkout |
| Push | **FCM direct** (via FCM HTTP v1 from Supabase Edge Functions) | Free, no middleman (OneSignal adds cost for no v1 benefit) |
| Crash/analytics | **Firebase Crashlytics + Analytics** (standalone, no other Firebase needed) | Best free Android crash reporting, Play Console integration |

### Biggest risks + mitigations
1. **Try-on cost spiral** ($0.02–0.07/try-on est.) → hard per-user daily quota (5 free/day v1), server-side job ledger with idempotency keys, cost dashboard alert at 2× expected spend, app kill-switch flag.
2. **Quality failure on the core promise** (diffusion try-on degrades on non-Western garments, bad poses, low-light selfies) → photo-quality gate before submission (MediaPipe: front-facing, well-lit, upper-body visible), pre-launch test set of 50+ Indian garments, honest "AI preview" framing.
3. **Photo privacy/trust** → private Storage buckets + RLS (own photos only), auto-delete raw uploads after 30 days, no photo ever leaves pipeline to ad/analytics SDKs, clear in-app privacy copy.

---

## 2. Design Tokens

### Colors
| Token | Hex | Usage |
|---|---|---|
| `bg-screen` | `#FFFFFF` | Home/wardrobe screen background |
| `bg-canvas` | `#F5F5F7` | Light-gray canvas behind cards |
| `surface-card` | `#FFFFFF` | Product/grid card surface |
| `surface-chip-selected` | `#EDEDF0` | "All" selected chip fill (unselected = transparent, text-only) |
| `surface-overlay` | `rgba(255,255,255,0.55)` + backdrop blur | Glassmorphic hero-card info overlay |
| `surface-thumb` | `#F1F1F3` | Thumbnail strip tiles |
| `surface-grid-img` | `#FAFAFA` | Grid card image wells |
| `nav-bg` | `#141416` | Dark floating bottom nav |
| `text-primary` | `#111111` | Headlines, names, CTA text |
| `text-secondary` | `#6E6E73` | Greeting, chip labels |
| `text-on-photo` | `#FFFFFF` + text shadow | Overlay brand/name/price |
| `accent-badge` | `#FF3B30` | Bell red dot |
| `orb-gradient` | radial: `#F5EFFF` (hot center) → `#A855F7` → `#EC4899` → `#3B82F6` (edge), glow halo `rgba(168,85,247,0.45)` | Center AI orb |

### Radii
- Product hero card: **28–32dp** · Glass overlay: **16–18dp** · Grid cards: **20–22dp** (image well 14dp inner)
- Category chips, CTA pill, "Add to wardrobe" pill, "Try-On" pill, bottom nav bar: **full pill**
- Thumbnail tiles: **10–12dp** · Avatar: circular

### Shadows
- Hero card: `0 8 24, rgba(0,0,0,0.08)` · Grid cards: `0 4 12, rgba(0,0,0,0.06)`
- Floating bottom nav: `0 10 28, rgba(0,0,0,0.28)` — always inset 16–20dp sides, never edge-docked
- Pills over photos: `0 2 8, rgba(0,0,0,0.15)` · CTA: `0 6 16, rgba(0,0,0,0.14)`

### Typography
- **Headline (onboarding):** serif, Playfair Display-class, 34–36sp, 600 semi-bold ("Virtual Try-On" italic 500), leading 1.15, letter-spacing −0.5
- **Body/UI:** Inter/SF-class sans. Greeting 13–14sp/400 gray · Name 19–21sp/700 · Chips 13–14sp/500-400 · Overlay brand 13sp/500, product 17–18sp/700, price 14sp/600 (all white + shadow) · CTA 15–16sp/600 · Pill labels 11–12sp/500–600

### Spacing (8dp base)
Screen padding 16–20dp · header→chips 16dp · chip gaps 8dp · hero↔next card 16dp · overlay insets 12dp · thumbnail tiles 56–64dp w/ 8dp gaps · grid 2-col, 12–16dp gutters · bottom nav 64–68dp tall, 16–20dp side margins, 12dp above home indicator.

---

## 3. Screen Inventory + Layouts

### 3.1 Onboarding
Full-bleed fashion photo (bright at top so dark status icons work); serif headline top-center ("Ecom with AI Powered" roman / "Virtual Try-On" italic), ~12–15% from top; white full-width-minus-32dp pill CTA "Ready? Let's Go!" (52–56dp tall) ~90dp above bottom; home indicator below. No scroll. **Build additions:** "Skip" link top-right, camera/photos permission sheets before first try-on.

### 3.2 Home feed
```
Status bar · "Good Morning! ☀️" / "Hello, {name}"  🔔(red dot)  avatar(36–40dp, white ring)
[All]  Casual  Jackets  Shoes            ← chip row (All = #EDEDF0 pill)
┌ Hero card (4:5, 28–32dp radius, ~62% viewport) ─┐
│ model photo full-bleed, bottom vignette        │
│                          [Add to wardrobe 🎒]  │ ← top-right pill
│ ┌ glass overlay (12dp insets) ───────────────┐ │
│ │ Urbanco / Casual Shirt / $35.00  [Try-On ✨]│ │
│ └────────────────────────────────────────────┘ │
└────────────────────────────────────────────────┘
[thumb][thumb][thumb][thumb]  ← 56–64dp tiles, scrollable
┌ second hero card (peeking ~15%) ────────────────┐  ← feed scrolls vertically
════════ dark floating bottom nav ════════
```
Content scrolls under floating nav (bottom padding ~100dp). Cards peek next card ~72dp to signal scroll.

### 3.3 Product detail (implied, not in mockup)
Image gallery (pager + dots, pinch/double-tap zoom 1–4x), brand/name/price, size selector chips, description/tags, **Try-On** + **Add to wardrobe** CTAs, share. Entered via shared-element transition on hero image (340ms, title/price crossfade not shared).

### 3.4 Try-on flow (implied)
1. **Source picker:** camera / gallery / "use last photo" (bottom sheet, slides up 280ms)
2. **Garment confirm:** photo + garment side-by-side, size override, "Start try-on"
3. **Processing:** full-bleed photo + shimmer sweep + pulsing AI orb, gradient progress bar, rotating playful copy every 2.2s ("Stitching the fabric…", "Checking the fit…"), cancel X (appears after 1s), "notify me" background option. 15–60s expected.
4. **Result:** 3-stage reveal (~900ms): fade-out → diagonal clip-path wipe w/ white edge line + haptic tick → spring to 1.0 + before/after slider + one subtle confetti burst (24 particles, no loop). Actions: Save to wardrobe, Share, Retry, rate result. Failures: friendly error, never a stranded spinner.

### 3.5 Wardrobe
Same header + chips + nav as home. Tabs: **Items** (2-col grid, 3:4 cards, name/price/remove/try-on actions in the white zone) / **History** (try-on sessions w/ thumbnails + status). Empty state: hanger illustration + "Browse products" CTA.

### 3.6 AI assistant (center orb)
Orb press → ripple + orb stretches into bottom sheet (320ms) → chat: message list, quick-prompt chips ("Style this for a date"), suggestion cards deep-linking Try-On. Orb idle: breathing scale 1.0↔1.06 (2600ms) + 8s gradient rotation + glow pulse; loading: 1000ms conic sweep + pulse.

### 3.7 Notifications / Profile & Settings (implied)
Bell → list w/ unread dots, deep-link on "try-on ready". Settings: avatar, name, **body profile** (height/build/measurements — improves try-on), preferences, privacy (delete photos, export data), logout, version.

### Missing states to build
Shimmer skeletons (pixel-matched to cards, shared animation clock), image-load failure tiles w/ retry, network-error banner, filter-empty state, notification-empty, guest avatar + sign-in prompt.

---

## 4. Motion Spec (Compose)

### Transitions
- Onboarding→Home: photo fade+scale→1.06 (320ms), content fade+slideUp 24dp staggered +40ms (350ms); CTA pill morphs to circle (240ms) then navigate. `FastOutSlowIn` throughout.
- Bottom-nav tabs: no animation on icon press; content crossfade 200ms `LinearOutSlowIn`; per-tab back stacks; re-tap active tab = scroll-to-top (400ms), 80ms bounce if already top.
- Product→detail: `SharedTransitionLayout` + `sharedElement` on image, 340ms `FastOutSlowIn`.
- Try-on flow screens: push from right 260ms `FastOutLinearIn`, old screen parallaxes 0.92x + fade.
- ≤3 animated properties per transition; durations 180–400ms.

### Micro-interactions
- Chips: `animateColorAsState` 180ms + indicator slide; selected chip padding 12dp vs 8dp.
- Add-to-wardrobe: press scale 0.96 (80ms) → spring back (damping 0.4); snackbar w/ thumbnail slides up 220ms; optional fly-to-cart thumbnail arc 450ms to wardrobe icon pop (scale 1.25→1.0 spring).
- Try-On button: state machine idle→loading (label→dots, `animateContentSize` 200ms, width locked, disabled against double-fire).
- Nav tab press: icon pop 1.0→1.18→1.0 (spring 0.5), indicator slide 240ms; hide nav on scroll-down (threshold 12dp), show on scroll-up.
- Pull-to-refresh: Material3 `PullToRefreshBox`, 600ms min spinner. Thumbnail strip: `LazyRow`, selected thumb 2dp gradient border + 1.06 scale, auto-scroll into view.
- Home entrance: first-load stagger fade+slideUp 24dp, 60ms stagger, once only.

### Haptics (`LocalHapticFeedback`)
Tab/chip/heart-like/slider-snap: light tick · Add-to-wardrobe/refresh-release/orb-open/processing-start: medium · Reveal midpoint: single tick · Complete: double-tick 120ms apart · Error: REJECT. Respect system haptic settings; never vibrate in idle loops.

### Accessibility
48dp touch targets (orb 64dp, slider grab zone 48dp wide) · content descriptions on all images · TalkBack on result announce + slider percentage (throttled) · reduced-motion: confetti/shimmer/stagger off, wipe→150ms crossfade, springs→tweens, all durations ≤150ms · contrast ≥4.5:1 on pill text.

---

## 5. 60fps Performance Rules
1. Coil: memory cache 25% app memory + 250MB disk; explicit `.size()` per view (thumbs ≤400px, detail ≤1080px, `Scale.FILL`); blur-up thumbnails; crossfade 250ms; never spinner-over-image.
2. Stable data classes, `key()` on lazy items, `derivedStateOf` for scroll/slider-driven values; ONE shared `rememberInfiniteTransition` per screen for shimmer/orb loops (not per card).
3. Animate via `graphicsLayer` (GPU); no `Modifier.blur` in loops; pre-render static orb gradients; ≤3 simultaneous animated gradients per screen; max 2x overdraw (verify w/ Debug GPU Overdraw in QA).
4. `LazyColumn`/`LazyVerticalGrid` everywhere; `contentType` set; fixed 3:4 image aspects to prevent reflow.
5. Release: R8 full mode + resource shrinking; Baseline Profiles shipped (`startup + scroll` Macrobenchmark journeys); cold start: splash→home first frame <800ms on mid-range; validate on a Moto-class device w/ 120Hz off.

---

## 6. Data Model

Postgres tables (Supabase) — RLS: users own their rows; products public-read where `is_active`.

- **users** (`id` uuid PK = auth UID, `name`, `email`/`phone` nullable, `avatar_url`, `body_profile` jsonb {heightCm, build enum, chestCm, waistCm}, `preferences` jsonb, `quota` jsonb {usedToday, resetDate}, `created_at`)
- **products** (`id` uuid PK, `brand`, `name`, `category` enum: casual/jackets/shoes/bags/tops, `price` numeric(10,2), `currency` char(3) = 'INR', `images` text[] (images[1] = hero), `description`, `sizes` text[], `tags` text[], `is_active` bool)
- **wardrobe_items** (`user_id` FK, `product_id` FK, `added_at`, `notes` nullable; PK (user_id, product_id))
- **tryon_sessions** (`id` uuid PK, `user_id` FK, `product_id` FK, `input_photo_url`, `garment_image_url`, `status` enum: queued/processing/done/failed, `result_image_url` nullable, `processing_ms`, `error_message`, `created_at`)
- **notifications** (`id` uuid PK, `user_id` FK, `title`, `body`, `type` enum: tryon_done/promo/system, `read` bool, `created_at`)
- **ai_chats** (`user_id` PK, `messages` jsonb [{role, text, productRef, ts}], `updated_at`)

Indexes: `wardrobe_items(user_id, added_at DESC)` · `tryon_sessions(user_id, created_at DESC)` · `products(category) WHERE is_active` · `notifications(user_id, created_at DESC)`.

### Storage layout
```
user-uploads/{uid}/photos/{ts}.jpg   # inputs, auto-delete after 30d (lifecycle)
tryon-results/{uid}/{sessionId}.jpg  # kept until user deletes
product-images/{pid}/{variant}.jpg   # admin uploads
avatars/{uid}.jpg
```
Client compresses to 2048px max / JPEG q80 (<1.5MB); limits: photo ≤5MB, avatar ≤2MB; MIME allowlist jpeg/png/webp. Thumbnails via Supabase Image Transform on read.

---

## 7. Try-On Pipeline (backend)

```
App: compress photo → upload user-uploads/{uid}/photos/… → createTryOnSession(productId, photoPath)
Edge Function createTryOnSession:
  auth + quota check (5 free/day v1, atomic increment; else 429 QUOTA_EXCEEDED)
  → moderation check on input photo (SafeSearch-class API; reject NSFW → MODERATION_REJECTED)
  → insert tryon_sessions row status=queued → return sessionId
Worker/trigger on queued:
  status=processing → POST commercial try-on API {personImage, garmentImage}
  → poll/webhook, deadline 120s → on success: store tryon-results/{uid}/{sid}.jpg,
    status=done, processingMs; on failure: ≤2 retries (10s/30s backoff)
    → still failing: status=failed + refund quota
  → FCM push "Your try-on is ready!" + notifications row
App: realtime subscription on session row (or poll getTryOnStatus) → result screen
```
- **Timeouts/retries:** 120s/attempt, max 2 retries; manual Retry creates a NEW session (audit trail); cancel allowed while queued/processing (owner only).
- **Cost guard:** per-user daily quota server-enforced; per-session cost logged; kill-switch flag.
- **Abuse:** 1 concurrent session/user; repeated NSFW flags → block.
- **Privacy:** input auto-delete 30d; delete session cascades files; export endpoint (GDPR-style).

### API contracts (Edge Functions, auth required, typed error codes)
| Function | In → Out |
|---|---|
| `createTryOnSession` | `{productId, photoPath, sizeOverride?}` → `{sessionId}` (throws `QUOTA_EXCEEDED`/`MODERATION_REJECTED`/`INVALID_PHOTO`) |
| `getTryOnStatus` | `{sessionId}` → `{status, resultImageUrl?, processingMs?, errorMessage?}` |
| `cancelTryOnSession` | `{sessionId}` → `{ok}` |
| `toggleWardrobe` | `{productId, notes?}` → `{saved: bool}` |
| `deleteTryOnSession` | `{sessionId}` → `{ok}` (cascades) |
| `askStylist` | `{message, contextProductIds?}` → `{reply, suggestedProductIds[]}` (SSE stream) |
| `getQuota` | → `{usedToday, limit, resetsAt}` |

### Offline + sync
Offline-capable: product catalog (Room mirror), Coil disk cache, wardrobe grid, body profile, last 50 notifications. Network-required: auth, try-on, AI chat, uploads, quota. Wardrobe toggles: optimistic UI + write-through, queued on reconnect. Try-on sessions are server-authoritative (no offline creation).

---

## 8. Build Phases

- **Phase 0 — Setup:** repo, design tokens, 5-tab nav graph + floating nav, screen stubs, DI + Supabase client skeleton. *Exit:* builds, navigates stubs.
- **Phase 1 — Catalog storefront:** home feed, chips filter, product detail (gallery/zoom/sizes/CTAs), seed 100–300 products, shimmer/empty/error states. *Exit:* browse→detail fully working, offline-capable.
- **Phase 2 — Auth + Wardrobe:** Google sign-in + anonymous-then-upgrade, profile/settings shell, wardrobe backend (toggle/grid/notes), body profile form, notifications screen. *Exit:* sign-in works; wardrobe survives reinstall.
- **Phase 3 — Try-on E2E:** source picker (CameraX/gallery), garment confirm, processing screen, result reveal (wipe + before/after), save/share/retry, quota + moderation + FCM, history tab, photo-quality gate (MediaPipe). *Exit:* real photo → real result → saved, on device.
- **Phase 4 — AI orb + polish:** stylist chat sheet w/ suggestion cards deep-linking try-on, motion polish pass, all missing states, privacy controls, reduced-motion. *Exit:* zero dead buttons; every mockup element live.
- **Phase 5 — Beta + release:** perf pass (Baseline Profiles, overdraw, cold start), Crashlytics, Play Console listing, closed beta → production. *Exit:* release build in Play Console.

---

## 9. Open Questions (need user call before build)
1. **Currency:** spec assumes ₹ (Indian market) though mockup shows $ — confirm ₹.
2. **Try-on API key/budget:** commercial API (FASHN/fal.ai-class) needs an API key + spend cap — whose key, what monthly cap?
3. **Brand name + package:** app name / `com.*` package id for the project.
4. **Seed catalog images:** generate/buy 100–300 garment shots, or start smaller (~30) for the MVP feed?
