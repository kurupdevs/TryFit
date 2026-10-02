// POST /functions/v1/deleteTryOnSession
// In:  { sessionId } -> { ok: true }
// Owner only. Deletes the row AND cascades storage files (input photo in
// user-uploads and result in tryon-results). No offline creation is possible —
// sessions are server-authoritative per SPEC 7.
import { CODES, errorResponse, getRow, requireUser, serve } from "../shared/_utils.ts";

export default {
  fetch: serve(async (req, db) => {
    const user = await requireUser(req, db);
    const { sessionId } = (await req.json().catch(() => ({}))) as { sessionId?: string };
    if (!sessionId) throw errorResponse(CODES.INVALID_STATE, "sessionId required", 400);

    const row = await getRow(
      db,
      "tryon_sessions",
      `id=eq.${encodeURIComponent(sessionId)}&select=id,user_id,input_photo_url,result_image_url`,
      user.jwt,
    );
    if (!row) throw errorResponse(CODES.NOT_FOUND, "Session not found", 404);
    if (row.user_id !== user.id) throw errorResponse(CODES.FORBIDDEN, "Not your session", 403);

    // Cascade storage files BEFORE row delete (service role bypasses RLS).
    if (row.input_photo_url) {
      await db.storageDelete("user-uploads", [row.input_photo_url as string]).catch((e) =>
        console.warn("input photo delete failed:", e)
      );
    }
    if (row.result_image_url) {
      await db.storageDelete("tryon-results", [row.result_image_url as string]).catch((e) =>
        console.warn("result image delete failed:", e)
      );
    }

    await db.del(
      "tryon_sessions",
      `id=eq.${encodeURIComponent(sessionId)}`,
      user.jwt,
    );

    return new Response(JSON.stringify({ ok: true }), {
      headers: { "content-type": "application/json" },
    });
  }),
};
