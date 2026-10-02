# Virtual Try-On Shopping App — FEATURE RESEARCH
**Compiled:** 2026-10-02 · **Method:** 5 parallel research agents — Reddit mining, IG/TikTok/X sentiment, competitor matrix, 1–2★ review mining, backend/infra pricing. Research only; nothing built.

**User directive:** maximum features ("jitna jyada ho sake daal de") — so this roadmap is EXPANSIVE (v1 / v1.5 / later), with a hard BLOAT TRAPS list for genuinely bad ideas. Judgment applied, but erring toward inclusion.

---

## 0. THE VIRAL #1 HOOK (all 5 agents converged here)

**"Screenshot any outfit → see it on YOUR body → get a moving before/after transition video → one tap to post."**

Evidence stack:
- TikTok's Sept 2026 "AI ceiling fan" trend proves one-photo outfit-transition videos are the current viral darling (The Tab, Sept 28 2026).
- Doppl proved screenshot-to-try-on ("influencer's outfit or thrift-store find") is the magic input; motion is the shareability multiplier over static images (Tom's Guide, 3dshoes).
- Aesty founder's real product data (9,324 try-ons, May–Jul 2026): users average **~34 try-ons/quarter** — *"closer to a mirror than to a checkout button."* Try-on is a daily PLAY habit, not a checkout feature. Fewer than 1 in 10 ever download the result.
- "Tap and transform" demo clips pull 1,400–2,400 comments each (@tik_tech6); Nano Banana outfit-swap tutorials hit 15.9K likes / 3,038 comments.

**Concrete v1 feature:** in-app **Try-On Transition Generator** — 1 photo + 1 outfit screenshot → beat-synced before/after video with app watermark + trending audio, one tap to post to TikTok/Reels. This single feature is simultaneously the viral format, the acquisition loop, and the demo. Everything else mounts on top.

---

## 1. PRIORITIZED FEATURE ROADMAP (maximal)

### V1 — viral core + trust (ship the hook, don't break trust)

**Try-on engine**
1. One-photo avatar: upload once (full-body), reuse for every try-on forever — zero re-upload (Genlook's winning UX).
2. **Try-On button on every product card** (per mockup) + "try anything from anywhere": paste a link or upload a screenshot of ANY outfit (Doppl/Perplexity pattern).
3. Sub-10s renders with **progressive loading** — low-res preview that refines, never a dead spinner. (Aesty: **73–86% of people who start a try-on never see the result** — they leave during loading, errors only 2–3%.)
4. Photo-quality coach before rendering ("step back, better light") + graceful fallback "try on a model shaped like you" (Walmart lesson) instead of failing silently.
5. Before/after slider on results; **Regenerate** for new variations.
6. **Transition-video generator** (the viral hook above): beat-synced before/after video, watermarked, trending audio, one-tap post.
7. Identity lock: face/body proportions frozen — no slimming, no smoothing, no face drift. Advertise that you do this.
8. Garment fidelity: preserve print/logo/text/color — or label output "style impression."
9. Honest inline labeling: **"style preview, not a fit guarantee"** (Google/OpenAI both do this; overclaiming fit is the trust-killer).

**Feed / shopping (per mockup)**
10. Product feed + category chips (All/Casual/Jackets/Shoes + ethnic categories) + bottom nav; ₹ pricing; affiliate deep-links (Myntra / Flipkart / Amazon.in / Ajio / Meesho).
11. "Add to wardrobe" on every card → personal wardrobe grid (the retention engine).
12. "Styled for You" auto-collages from tried-on/saved items (Pinterest's 2×-save mechanic).

**Social / growth**
13. One-tap share to WhatsApp/IG/Stories with watermark + app link.
14. "Ask friends" vote link — send a try-on to the group chat for A/B voting (Walmart promised this in 2021 and never shipped it; nobody owns it).

**Trust / onboarding**
15. Privacy screen BEFORE the camera: on-device-first, auto-delete timer (7 days), "never used for training," one-tap photo delete (Genlook playbook — the #1 objection in every review corpus).
16. No-signup instant trial: 2–3 free try-ons before any account wall.
17. Honest free tier: several free try-ons daily, no card required. In-app billing only, 2-tap cancel, every charge confirmed.

**India-specific**
18. Ethnic-wear categories day one (kurtis, sherwanis, kurta sets — an Indian Acloset reviewer literally begged for kurtis).
19. Hindi + Hinglish UI option; Indian skin-tone/size-diverse default models.

### V1.5 — deepen the loop (stylist intelligence + social engine)

**Try-on depth**
20. **Multi-garment full-outfit try-on in one render** — "try this whole fit on me." Every competitor does one item at a time (DressX Mix & Match is the only partial exception, luxury-only). This is the #1 structural gap.
21. Video try-on: selfie → full-body avatar → short moving clip (Google's Nano Banana update proved the "no perfect photo needed" convenience).
22. Occasion rendering: "this outfit at a wedding / office / streetwear night" — nobody productized this.
23. Accessories try-on: shoes (foot-photo AR like Wanna Kicks), bags, sunglasses, watches (Doppl's gap).
24. Body-honesty toggle — "show my real proportions" mode; never slim by default.

**Fit truth (separate from the render)**
25. Fit-intent + measurement profile: height/weight/optional measurements + "how do you like your fit?" (slim/regular/oversized) → per-brand size suggestion. Kept VISUALLY SEPARATE from the try-on render (appearance ≠ fit — every mainstream VTO transfers appearance, not fit).
26. Crowdsourced "runs small/large" signals per product.

**Stylist brain**
27. AI color analysis: personal palette from face scan (Dressly's most-loved feature).
28. Outfit scorer: color harmony, coordination, proportion, trend fit → score + actionable tips.
29. AI stylist chat: "style me for a wedding under ₹5,000" → assembled looks from wardrobe + catalog, answered in try-on visuals not text (Gensmo pattern).
30. Weather + occasion-aware daily suggestions — only if actually accurate (Acloset got mocked for wrong weather picks; it's a request AND a complaint).
31. Visual search: upload screenshot/IG photo → find shoppable matches → try on.
32. Natural-language search: "red kurta under 1000." Virtual-mannequin attribute filter (YesPlz pattern: tap neckline/sleeve instead of typing).

**Wardrobe intelligence**
33. "Try with my stuff": new item rendered WITH your saved wardrobe pieces (exists in GitHub spec docs, not in any shipped consumer app).
34. Closet digitizing: camera + auto background-removal + auto-categorize, with MANUAL override (Acloset's most-praised feature; lack of override was a 1-star complaint).
35. Outfit calendar/planner (beloved in Acloset reviews); trip/packing mode.
36. Cost-per-wear tracker (Acloset-praised); "haven't worn in 90 days" nudges.
37. Spending/gift metadata on items (mark gifts, hide price nags).

**Social / creator**
38. In-app try-on challenges ("2026 outfit challenge" style) with leaderboards.
39. Duet/stitch-ready exports — built-in "duet your reaction" prompt.
40. Private friend-circle polls ("which of these two?") — private feedback beats public judgment (Dripmatiq thesis).
41. Creator templates + tutorial feed (feed the @drlaw-style teachers with branded templates instead of competing with them).
42. Menswear + modest/hijab-aware modes (under-served, India-relevant; most try-on marketing skews women's).

**Money mechanics**
43. Favorites/wishlist folders + **price-drop alerts** ("ping me under ₹999") — merges the two highest-converting shopping features; nobody does it.
44. Price history + "lowest price across stores" per item.

### LATER — moat, breadth, monetization

45. AR live mirror mode for shoes/accessories (Wanna Kicks' best feature when it works).
46. Animated/video walk-around try-on (Reels-ready) — retention candy, heavy GPU cost, keep optional.
47. Group/couple/squad fit checks.
48. Gamification: streaks, style-levels, weekly style battles.
49. Community: public lookbooks, creator profiles, head-to-head look battles.
50. **Creator affiliate program**: try-on content with buy links, revenue share (the "carrboxyl/vikram_rizz rate-my-fit" economy has no try-on-native home; Myntra Glam Clan proves the India pipeline — 6–8M signups, 500K creators/mo).
51. "Style my own closet": digitize owned clothes → daily outfit suggestions from things you own (Aesty's thesis — *"the wardrobe graph is the thing you cannot buy, cannot scrape, and cannot fake"*).
52. Hairstyle + makeup try-on tabs (YouCam pattern).
53. Look-builder collage canvas + AI "evaluate my look."
54. Style inspiration feed + "shop the look" from reference photos.
55. Outfit history timeline (prevents repeat looks at events).
56. Secondhand/resale tab — ONLY with proper escrow/buyer protection (Acloset's unsecured P2P feels "junky" per reviews).
57. Brand/retailer catalog partnerships + direct checkout (affiliate revenue instead of user paywalls).
58. Festival/season lookbooks + regional trend radar ("rising in Indore this week").
59. White-label / B2B try-on widget for small Indian D2C boutiques (Genlook playbook: free self-serve → paid quotas — the single proven indie revenue line in this space; Genlook: 600+ stores, 5.0★ Shopify rating).
60. Optional **one-time "Pro" unlock** (HD renders, video try-on, unlimited looks/day) — Clozzie reviewers beg for one-time payment; NEVER paywall core try-on (DressX's $4.99/wk wall is the anti-pattern).
61. Kids'/family profiles with parental controls.
62. Low-data "lite" mode for 2G/3G + low-end Android (zero competitors design for this — huge for India).
63. UPI-native checkout handoff; COD/return-policy info per listing.
64. Rewards-for-posting loop (test carefully; one unverified "Doppy" claim of 12K views vs 240 on TikTok — treat as experiment, not plan).
65. Sustainability framing: "wear what you own" stats, no-buy challenges (resonates with frugal-fashion subs).
66. Taste graph from saves/tries → compounding personalization moat.
67. Fit-feedback loop ("how did it actually fit?" post-purchase) → fit-profile that improves per user.

---

## 2. BLOAT TRAPS — explicitly REFUSE (judgment applied)

1. **"This size WILL fit you" claims from the render.** Technically infeasible — Google: *"we don't promise fit."* Claiming it is scam-adjacent and will generate the exact r/google backlash ("it just assumes a good fit… don't expect much beyond that").
2. **Body-slimming / beautifying filters (default or otherwise).** Doppl got roasted for mirror-selfie slimming; NPR-documented body-image harm. Trust-killing.
3. **Weekly subscriptions / paywall-after-upload / instant-charge "free trials."** The #1 one-star factory in the category (AI Mirror "charged instantly" review: 900 helpful votes; Pureple $6.99/wk → 2.5★ Google Play). Free core + one-time Pro + affiliate instead.
4. **Training on or retaining user body photos; sharing images with third parties.** Peer-reviewed study: 65% of VTO sites send images to servers (57% to third parties), 11% violate their own policies. MAC/e.l.f. face BIPA lawsuits (motion to dismiss DENIED June 2026). Auto-delete by default.
5. **Fake loading screens to serve ads / ad-choked screens / dead buttons / cluttered UI.** Literal 1-star Acloset review: "fake loading screen so you can get an ad." If a feature doesn't work, remove the button.
6. **Unsecured P2P marketplace.** Reviews call it "junky" and risky — only with escrow, else skip.
7. **Mandatory ethnicity/age pickers.** A fashion app's 1-star: "Biased and Backwsrd." Optional + "prefer not to say" or skip entirely.
8. **Email-to-cancel, off-store billing (PayPal/bank direct), no-confirmation micro-charges.** Every mention is a 1-star; off-store billing removes chargeback path and reads as scam.
9. **Fake 5-star review seeding.** Threads spot non-native-English 5-stars vs real-usage 1-stars → instant "scam" verdict.
10. **Head-on render-quality war with Google/Gensmo.** Capital game you'll lose; the wardrobe graph + India localization is the moat.
11. **Shrinking free tiers** (Clozzie cut free items 150→25 → "SUPER disappointed"). Set caps honestly at launch and hold them.
12. **Undress-adjacent anything / try-on of other people's photos without consent.** X currently runs AI-undress ads (HN, Jan 2026) — guilt by association is real. Hard guardrails (block explicit/suggestive uploads, public figures) are mandatory AND a selling point.
13. **Weather/occasion suggestions done badly.** Requested and mocked simultaneously — ship only if accurate.
14. **Public feed of strangers' outfits as the home tab.** Same "junky" complaint as unsecured P2P; keep the closet personal, social opt-in.

---

## 3. TRUST & PRIVACY — handle before launch

- **Photo pipeline:** explicit per-upload consent → on-device preprocessing (MediaPipe segmentation/crop/face-check, photo never leaves phone for this half) → secure upload → try-on → auto-delete (7-day timer like Genlook) → never train on photos → one-tap delete anytime. Only claim "never stored" if the provider's retention policy actually supports it — verify per provider.
- **No biometric overreach:** never require ethnicity/age; no face-geometry measurement without explicit written consent (BIPA tail risk is real — MAC Cosmetics motion to dismiss denied June 2026).
- **Honest framing everywhere:** "style preview, not a fitting room" inline — not buried in ToS.
- **Misuse guardrails:** block explicit/suggestive uploads, public figures, and non-consensual use of others' photos; name these guardrails publicly (Doppl-style) — it's a selling point in a space running AI-undress ads.
- **Monetization honesty:** free tier does something real; no card for free; in-app billing only; 2-tap cancel; developer replies to every 1–2★ review within 48h (a praised pattern).

---

## 4. TRY-ON TECH RECOMMENDATION (zero/low budget, honest numbers)

**Verdict: cloud API required.** Full diffusion try-on on-device is NOT feasible on mid-range Android in 2026 (CatVTON needs ~6GB VRAM @1024×768; phones share 6–8GB total; the only mobile port is iOS-research-grade). What IS feasible on-device: MediaPipe selfie-segmentation + face check for input validation/cropping — free, private, fast.

**Model legality trap (critical):** the two best open models — **CatVTON** (FID ~5.4, <8GB VRAM) and **IDM-VTON** (KAIST, "best-in-class in the wild") — are **CC BY-NC-SA 4.0: NON-COMMERCIAL. Legally unusable for a shopping app.** OOTDiffusion is Apache 2.0 ✅ (commercial OK) but measurably weaker (FID ~9.3).

**Recommended stack:**
- **Primary: fal.ai → Kling Kolors v1.5** — **$0.07/try-on**, commercial use cleared, simplest integration (2 image URLs in, 1 image out).
- **Fallback chain:** FASHN v1.6 via fal ($0.075, best-rated quality) → Segmind SegFit v1.2 (~$0.05–0.09 EST, cheapest paid) → Pollinations ($0, degraded, labeled "experimental").
- **Scale path:** self-hosted **OOTDiffusion (Apache 2.0 ✅)** on serverless GPU (Modal $30/mo free tier / RunPod ~$0.25/hr EST) — at ~10s/image ≈ **$0.0007/image → ~$7 per 10,000 try-ons**. This is the ONLY path that keeps 10k try-ons inside a $10 budget.
- **Privacy config:** prefer `return_base64: true` (FASHN supports it — output never lands on a CDN); never log/store photos longer than needed.

**Cost math (USD):**

| Try-ons/mo | Kolors $0.07 | FASHN $0.075 | Self-host OOTDiffusion |
|---|---|---|---|
| 100 | $7.00 | $7.50 | ~$0.07 |
| 1,000 | $70 | $75 | ~$0.70 |
| 10,000 | $700 | $750 | ~$7 |

**$10/month reality:** buys ~140 Kolors try-ons. So: (a) per-user quotas (e.g., 5 free/day), (b) burn fal.ai's $10–20 welcome credits first (~140–280 free generations), (c) migrate to self-hosted OOTDiffusion before usage explodes.

**Latency budget:** 10–30s per try-on across all options (FASHN ~7s best case; Replicate IDM-VTON ~19s + cold start). Nothing is real-time. This is why progressive loading is a v1 feature, not polish.

**Quality ceiling (honest):** at the $0.07 tier expect convincing fabric/drape on clean product shots for tops/dresses, front-facing, well-lit. Known failure modes across ALL diffusion try-on: printed text/logos distort; long→short sleeve conversions leave arm artifacts; faces can drift slightly; side/back/extreme poses degrade; lower-body garments weaker than tops. It is a visualization, not a fit predictor. DressX/Google win on identity preservation via proprietary models + bigger data + human retouching — don't race them; out-experience them.

**Open items for build phase:** verify fal.ai's current welcome-credit amount; verify RunPod/Modal $/hr; A/B test SegFit v1.2 vs Kolors quality on real photos before committing as fallback; confirm Leffa weight license if considered.

---

## 5. WHY THIS WINS (the wedge, in one paragraph)

Nobody owns the consumer experience in the mockup (feed + Try-On + wardrobe + social). Big-retailer try-on is a checkout widget, B2B sells to brands invisibly, and consumer apps are either fun-toys with no real shopping or luxury-only. The unclaimed space: **AI try-on of REAL, AFFORDABLE, multi-retailer catalog items on YOUR body + personal wardrobe graph + social/share loop, built for India** (₹ pricing, ethnic wear, Myntra/Flipkart/Amazon.in/Ajio/Meesho affiliate, low-end Android, Hindi/Hinglish). Monetize like Alta (free unlimited try-on + affiliate cut = 4.9★, ~14.7k ratings) — never like DressX ($4.99/wk wall). The wardrobe graph compounds; Google can't scrape it.

---

## 6. SOURCE NOTES & CAVEATS

- Reddit threads are poorly indexed by the search backend — no direct reddit.com thread URLs were retrievable; Reddit voices came via a press article quoting the r/google Doppl launch thread verbatim + secondary summaries of r/femalefashionadvice / r/malefashionadvice. Flagged honestly in the Reddit agent's report.
- App Store/Play block direct review scraping — 1–2★ quotes come from search-engine crawls of listing pages + aggregators (WorldsApps, AppBrain, Trustpilot) + press quoting reviews.
- Prices in USD; items marked EST are estimates. fal.ai welcome-credit amounts ($10–20) are widely reported but unconfirmed officially.
- Key sources: Aesty founder dataset (Medium, Aug 2026); TechCrunch/Tom's Guide/The Verge Doppl coverage (2025–26); Salt Agency + 3dshoes Doppl analyses; The Tab ceiling-fan trend (Sept 28 2026); fal.ai/Segmind/Replicate/FASHN pricing pages; CatVTON paper (arXiv:2407.15886); Concordia/Springer "Try On, Spied On?" privacy study; Overnight Glasses return-rate study (June 2026, n=1,882); Myntra Glam Clan affiliate (Sept 2026); gadgetbond Google Photos AI Wardrobe India rollout.
