// POST /functions/v1/getTryOnStatus
// In:  { sessionId }
// Out: { status, resultImageUrl?, processingMs?, errorMessage? }
import { CODES, errorResponse, getRow, requireUser, serve } from "../shared/_utils.ts";

export default {
  fetch: serve(async (req, db) => {
    const user = await requireUser(req, db);
    const { sessionId } = (await req.json().catch(() => ({}))) as { sessionId?: string };
    if (!sessionId) throw errorResponse(CODES.INVALID_STATE, "sessionId required", 400);

    const row = await getRow(
      db,
      "tryon_sessions",
      `id=eq.${encodeURIComponent(sessionId)}&select=id,user_id,status,result_image_url,processing_ms,error_message,created_at`,
      user.jwt,
    );
    if (!row) throw errorResponse(CODES.NOT_FOUND, "Session not found", 404);
    if (row.user_id !== user.id) throw errorResponse(CODES.FORBIDDEN, "Not your session", 403);

    return new Response(
      JSON.stringify({
        status: row.status,
        resultImageUrl: row.result_image_url ?? undefined,
        processingMs: row.processing_ms ?? undefined,
        errorMessage: row.error_message ?? undefined,
      }),
      { headers: { "content-type": "application/json" } },
    );
  }),
};
