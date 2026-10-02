// POST /functions/v1/getQuota
// Out: { usedToday, limit, resetsAt }
import { getQuotaView, requireUser, serve } from "../shared/_utils.ts";

export default {
  fetch: serve(async (req, db) => {
    const user = await requireUser(req, db);
    const quota = await getQuotaView(db, user.id, user.jwt);
    return new Response(JSON.stringify(quota), {
      headers: { "content-type": "application/json" },
    });
  }),
};
