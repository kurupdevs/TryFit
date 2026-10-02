// POST /functions/v1/createTryOnSession
// In:  { productId, photoPath, sizeOverride? }
// Out: { sessionId }
// Errors: UNAUTHORIZED, TRYON_DISABLED, QUOTA_EXCEEDED, INVALID_PHOTO,
//         PRODUCT_INACTIVE, MODERATION_REJECTED
//
// Server-authoritative: inserts a queued row; the client must NOT drive
// progression itself (except in DEMO mode — see below).
//   - TRYON_PROVIDER=demo (default): inserts the row with demo=true.
//     The client is permitted to advance demo sessions locally
//     (queued -> processing -> done with a canned result asset) for UI dev.
//     Real builds must never use the demo path.
//   - TRYON_PROVIDER=<commercial>: a server-side worker (Supabase pg_net / a
//     scheduled function watching queued rows) picks up the row, calls the
//     try-on API (TRYON_API_KEY — server-side only, never in the client),
//     stores tryon-results/{uid}/{sid}.jpg, flips status, sends FCM.
import {
  CODES,
  errorResponse,
  getEnv,
  getQuotaOrThrow,
  getRow,
  isTryOnEnabled,
  requireUser,
  serve,
} from "../shared/_utils.ts";

interface Body {
  productId?: string;
  photoPath?: string;
  sizeOverride?: string;
  idempotencyKey?: string;
}

export default {
  fetch: serve(async (req, db) => {
    const user = await requireUser(req, db);

    if (!(await isTryOnEnabled(db))) {
      throw errorResponse(CODES.TRYON_DISABLED, "Try-on is temporarily disabled", 503);
    }

    const body = (await req.json().catch(() => ({}))) as Body;
    const { productId, photoPath, sizeOverride, idempotencyKey } = body;

    // --- validate photo path: must be the caller's own upload ---
    if (!photoPath || typeof photoPath !== "string" || !photoPath.startsWith(`${user.id}/photos/`)) {
      throw errorResponse(
        CODES.INVALID_PHOTO,
        "photoPath must be your own upload under user-uploads/{uid}/photos/",
        400,
      );
    }
    if (!/^[a-zA-Z0-9][a-zA-Z0-9/_.\-]{0,180}\.(jpe?g|png|webp)$/i.test(photoPath)) {
      throw errorResponse(CODES.INVALID_PHOTO, "Unsupported photo path", 400);
    }

    // --- idempotency: same key returns the existing session ---
    if (idempotencyKey) {
      const existing = await getRow(
        db,
        "tryon_sessions",
        `idempotency_key=eq.${encodeURIComponent(idempotencyKey)}&select=id`,
        user.jwt,
      );
      if (existing) {
        return new Response(JSON.stringify({ sessionId: existing.id }), {
          status: 200,
          headers: { "content-type": "application/json" },
        });
      }
    }

    // --- validate product ---
    const product = await getRow(
      db,
      "products",
      `id=eq.${encodeURIComponent(productId ?? "")}&select=id,is_active,images`,
      user.jwt,
    );
    if (!product) {
      throw errorResponse(CODES.NOT_FOUND, "Product not found", 404);
    }
    if (product.is_active !== true) {
      throw errorResponse(CODES.PRODUCT_INACTIVE, "Product is no longer available", 410);
    }
    const images = (product.images as string[]) ?? [];
    const garmentImageUrl = images[0] ?? "";

    // --- quota (atomic) ---
    const quota = await getQuotaOrThrow(db, user.id);

    // --- moderation (commercial providers: hook your SafeSearch-class API here;
    //     this stub rejects nothing but reserves the MODERATION_REJECTED code) ---
    // const verdict = await moderate(photoPath);
    // if (verdict.nsfw) { await refundQuota(db, user.id); throw errorResponse(CODES.MODERATION_REJECTED, "Photo did not pass moderation", 422); }

    const demo = getEnv("TRYON_PROVIDER", "demo") === "demo";

    const rows = (await db.insert("tryon_sessions", {
      user_id: user.id,
      product_id: productId,
      input_photo_url: photoPath,
      garment_image_url: garmentImageUrl,
      status: "queued",
      size_override: sizeOverride ?? null,
      demo,
      idempotency_key: idempotencyKey ?? null,
    }, user.jwt)) as Array<{ id: string }>;
    const session = rows[0];
    if (!session?.id) throw errorResponse(CODES.INVALID_STATE, "Failed to create session", 500);

    return new Response(
      JSON.stringify({ sessionId: session.id, demo, quota }),
      { status: 201, headers: { "content-type": "application/json" } },
    );
  }),
};
