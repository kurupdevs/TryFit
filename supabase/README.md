# TryFit Supabase backend

Backend for the "TryFit" AI virtual try-on shopping app (SPEC §6–7).
Everything here deploys to a single Supabase project; no real credentials are committed.

## Layout

```
supabase/
  migrations/001_schema.sql    # enums, tables, app_config, increment_quota RPC, indexes, RLS
  migrations/002_storage.sql   # buckets + storage RLS + 30-day purge job (pg_cron)
  migrations/003_seed.sql      # 12 sample products (fictional brands)
  functions/shared/_utils.ts   # zero-dependency REST client, auth, quota, error codes
  functions/<name>/index.ts    # 7 edge functions
```

### Edge functions

| Function | Contract |
|---|---|
| `createTryOnSession` | `{productId, photoPath, sizeOverride?, idempotencyKey?}` → `{sessionId, demo, quota}`; errors `TRYON_DISABLED` / `INVALID_PHOTO` / `PRODUCT_INACTIVE` / `QUOTA_EXCEEDED` / `MODERATION_REJECTED` |
| `getTryOnStatus` | `{sessionId}` → `{status, resultImageUrl?, processingMs?, errorMessage?}` |
| `cancelTryOnSession` | `{sessionId}` → `{ok}` (owner only, queued/processing only, refunds quota) |
| `toggleWardrobe` | `{productId, notes?}` → `{saved}` |
| `deleteTryOnSession` | `{sessionId}` → `{ok}` (owner only; deletes storage files too) |
| `askStylist` | `{message, contextProductIds?}` → `{reply, suggestedProductIds[]}` (v1 stub; SSE streaming TODO) |
| `getQuota` | → `{usedToday, limit, resetsAt}` |

Typed error codes are shared in `functions/shared/_utils.ts` (`CODES`).
Client maps them 1:1 — never change the strings without a client release.

## Setup

### 1. Create the project
1. https://supabase.com → New project (free tier is enough for v1).
2. Note **Project URL** and the **anon** + **service_role** keys (Project Settings → API).
3. Install the CLI: `npm i -g supabase` and `supabase login`.

### 2. Link and push migrations
```bash
cd ~/workspace/virtual-tryon-app/supabase
supabase link --project-ref <project-ref>
supabase db push        # runs 001, 002, 003 in order
```
If `db push` is unavailable, run each `migrations/*.sql` in the SQL editor in order.

### 3. Deploy functions
```bash
supabase functions deploy createTryOnSession
supabase functions deploy getTryOnStatus
supabase functions deploy cancelTryOnSession
supabase functions deploy toggleWardrobe
supabase functions deploy deleteTryOnSession
supabase functions deploy askStylist
supabase functions deploy getQuota
# or: supabase functions deploy   (deploys all)
```

### 4. Environment variables (functions → secrets, never in the client)
```bash
supabase secrets set TRYON_PROVIDER=demo            # "demo" | "fashn" | "replicate"
supabase secrets set TRYON_API_KEY=<key>            # server-side only; blank in demo mode
# SUPABASE_URL / SUPABASE_ANON_KEY / SUPABASE_SERVICE_ROLE_KEY are injected by the platform
```

- **Demo mode** (`TRYON_PROVIDER=demo`, default): `createTryOnSession` inserts a
  `queued` row with `demo=true`; the *client* is allowed to drive demo progression
  locally (queued → processing → done with a bundled canned result) for UI development.
  Real builds must never ship demo mode.
- **Commercial mode**: a server-side worker (Supabase pg_net cron or a scheduled
  edge function polling `status='queued'`) picks up rows, calls the try-on API with
  `TRYON_API_KEY`, uploads `tryon-results/{uid}/{sid}.jpg`, flips `status` to
  `done`/`failed` (with quota refund on failure), then sends the FCM push.
  The client only polls `getTryOnStatus` or subscribes to the row via Realtime.

### 5. FCM push ("Your try-on is ready!")
No middleman service needed — FCM HTTP v1 directly:
1. Firebase Console → Project settings → Service accounts → generate a service-account JSON.
2. Store it as a function secret (never in the repo): `supabase secrets set FCM_SERVICE_ACCOUNT='<json>'`.
3. Client registers its FCM token on `users.preferences.fcmToken` at login.
4. The try-on worker mints an OAuth2 access token from the service account,
   POSTs to `https://fcm.googleapis.com/v1/projects/<project>/messages:send`,
   and inserts a `notifications` row (`type='tryon_done'`) for the in-app list.

### 6. Storage lifecycle — 30-day auto-delete of raw uploads
`002_storage.sql` already schedules a nightly pg_cron job (`tryfit-purge-old-uploads`,
03:00 UTC) that deletes `storage.objects` in `user-uploads` older than 30 days.
If your project lacks pg_cron, replicate it from the dashboard:
- Database → Cron Jobs → New job, schedule `0 3 * * *`, SQL:
  ```sql
  delete from storage.objects
  where bucket_id = 'user-uploads' and created_at < now() - interval '30 days';
  ```
- Verify: `select * from cron.job;`

### 7. Kill-switch & quota knobs (no deploy needed)
```sql
update app_config set value = 'false' where key = 'tryon_enabled';  -- disable try-on
update app_config set value = '3'     where key = 'daily_quota';     -- 3 free/day
update app_config set value = '20000' where key = 'cost_alert_threshold'; -- cents
```

## Conventions the client must follow

- Product seed images are `asset://product_<n>` placeholders — the Android app
  resolves them to bundled drawables; real admin uploads use public
  `product-images/<pid>/<variant>.jpg` URLs.
- Photo upload path: `user-uploads/{uid}/photos/{ts}.jpg` (≤5MB, JPEG/PNG/WebP).
- `tryon_sessions` are server-authoritative — no offline creation; manual Retry
  creates a NEW session (audit trail preserved).
- `wardrobe_items` toggles are optimistic UI + write-through; queue on reconnect.
