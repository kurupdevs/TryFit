// POST /functions/v1/toggleWardrobe
// In:  { productId, notes? } -> { saved: bool }
// Toggles the (user, product) row. Validates product exists & is active.
import { CODES, errorResponse, getRow, requireUser, serve } from "../shared/_utils.ts";

export default {
  fetch: serve(async (req, db) => {
    const user = await requireUser(req, db);
    const { productId, notes } = (await req.json().catch(() => ({}))) as {
      productId?: string;
      notes?: string;
    };
    if (!productId) throw errorResponse(CODES.INVALID_STATE, "productId required", 400);

    const product = await getRow(
      db,
      "products",
      `id=eq.${encodeURIComponent(productId)}&select=id,is_active`,
      user.jwt,
    );
    if (!product) throw errorResponse(CODES.NOT_FOUND, "Product not found", 404);
    if (product.is_active !== true) {
      throw errorResponse(CODES.PRODUCT_INACTIVE, "Product is no longer available", 410);
    }

    const existing = await getRow(
      db,
      "wardrobe_items",
      `user_id=eq.${user.id}&product_id=eq.${encodeURIComponent(productId)}&select=user_id`,
      user.jwt,
    );

    let saved: boolean;
    if (existing) {
      await db.del(
        "wardrobe_items",
        `user_id=eq.${user.id}&product_id=eq.${encodeURIComponent(productId)}`,
        user.jwt,
      );
      saved = false;
    } else {
      await db.insert(
        "wardrobe_items",
        { user_id: user.id, product_id: productId, notes: notes ?? null },
        user.jwt,
      );
      saved = true;
    }

    return new Response(JSON.stringify({ saved }), {
      headers: { "content-type": "application/json" },
    });
  }),
};
