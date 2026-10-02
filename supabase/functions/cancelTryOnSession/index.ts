// POST /functions/v1/cancelTryOnSession
// In:  { sessionId }  -> { ok: true }
// Owner only; only queued/processing may be cancelled. Cancelling refunds
// the quota slot (usedToday decremented, never below 0).
import { CODES, errorResponse, getRow, requireUser, serve } from "../shared/_utils.ts";

export default {
  fetch: serve(async (req, db) => {
    const user = await requireUser(req, db);
    const { sessionId } = (await req.json().catch(() => ({}))) as { sessionId?: string };
    if (!sessionId) throw errorResponse(CODES.INVALID_STATE, "sessionId required", 400);

    const row = await getRow(
      db,
      "tryon_sessions",
      `id=eq.${encodeURIComponent(sessionId)}&select=id,user_id,status`,
      user.jwt,
    );
    if (!row) throw errorResponse(CODES.NOT_FOUND, "Session not found", 404);
    if (row.user_id !== user.id) throw errorResponse(CODES.FORBIDDEN, "Not your session", 403);
    if (row.status !== "queued" && row.status !== "processing") {
      throw errorResponse(CODES.INVALID_STATE, `Cannot cancel a ${row.status} session`, 409);
    }

    await db.update(
      "tryon_sessions",
      `id=eq.${encodeURIComponent(sessionId)}`,
      { status: "cancelled" },
      user.jwt,
    );

    // Refund one quota slot (reset-date aware).
    const today = new Date().toISOString().slice(0, 10);
    const urows = (await db.get("users", `id=eq.${user.id}&select=quota`, user.jwt)) as Array<{
      quota?: { usedToday?: number; resetDate?: string };
    }>;
    const q = urows[0]?.quota ?? {};
    if (q.resetDate === today && (q.usedToday ?? 0) > 0) {
      await db.update(
        "users",
        `id=eq.${user.id}`,
        { quota: { usedToday: q.usedToday! - 1, resetDate: today } },
        user.jwt,
      );
    }

    return new Response(JSON.stringify({ ok: true }), {
      headers: { "content-type": "application/json" },
    });
  }),
};
