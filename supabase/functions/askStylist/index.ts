// POST /functions/v1/askStylist
// In:  { message, contextProductIds?: string[] }
// Out: { reply, suggestedProductIds: string[] }
// STUB for v1: rule-based canned reply + tag-matched product suggestions.
// Real LLM wiring: replace canned() with a server-side model call
// (TRYON_PROVIDER-style env e.g. STYLIST_MODEL), keeping the same contract.
// SSE streaming: add a ?stream=1 mode returning text/event-stream — TODO.
import { CODES, errorResponse, requireUser, serve } from "../shared/_utils.ts";

interface Body {
  message?: string;
  contextProductIds?: string[];
}

const OCCASION_RULES: Array<{ keys: string[]; tags: string[]; line: string }> = [
  {
    keys: ["date", "dinner", "evening", "party", "night"],
    tags: ["evening", "party", "silk", "premium"],
    line: "For a night out, go for rich textures and a clean silhouette.",
  },
  {
    keys: ["office", "work", "interview", "formal"],
    tags: ["office", "formal", "work", "minimal"],
    line: "For work, keep it structured: solid colours, no loud prints.",
  },
  {
    keys: ["rain", "monsoon", "trek", "travel", "outdoor"],
    tags: ["rain", "travel", "outdoor"],
    line: "Wet weather? Prioritise water resistance and quick-dry fabrics.",
  },
  {
    keys: ["gym", "run", "workout", "sport", "jog"],
    tags: ["running", "athleisure", "knit"],
    line: "For workouts, breathable knits and a snug fit win.",
  },
  {
    keys: ["summer", "hot", "beach", "vacation"],
    tags: ["summer", "linen", "sandals", "comfort"],
    line: "Hot weather: linen, light knits and open footwear.",
  },
  {
    keys: ["winter", "cold", "wedding", "festive"],
    tags: ["winter", "premium"],
    line: "Cold or festive: layer up — an overcoat changes everything.",
  },
];

export default {
  fetch: serve(async (req, db) => {
    const user = await requireUser(req, db);
    const body = (await req.json().catch(() => ({}))) as Body;
    const message = (body.message ?? "").trim().slice(0, 500);
    if (!message) throw errorResponse(CODES.INVALID_STATE, "message required", 400);

    const lower = message.toLowerCase();
    const rule = OCCASION_RULES.find((r) => r.keys.some((k) => lower.includes(k)));

    // Tag-match products (active only) to the rule's tags; fall back to bestsellers.
    const tags = rule?.tags ?? ["bestseller", "everyday", "basics"];
    const tagFilter = tags.map((t) => `tags.cs.{${t}}`).join(",");
    let rows = (await db.get(
      "products",
      `is_active=eq.true&or=(${tagFilter})&select=id,brand,name,price&limit=3`,
      user.jwt,
    )) as Array<{ id: string; brand: string; name: string; price: number }>;

    if (rows.length === 0) {
      rows = (await db.get(
        "products",
        "is_active=eq.true&order=created_at.desc&select=id,brand,name,price&limit=3",
        user.jwt,
      )) as Array<{ id: string; brand: string; name: string; price: number }>;
    }

    const picks = rows.map((r) => `${r.brand} ${r.name} (₹${r.price})`).join(", ");
    const reply = rule
      ? `${rule.line} Try these: ${picks}. Tap any card to try it on yourself.`
      : `Here's what I'd pull from the rack for that: ${picks}. Tell me the occasion for sharper picks.`;

    return new Response(
      JSON.stringify({
        reply,
        suggestedProductIds: rows.map((r) => r.id),
      }),
      { headers: { "content-type": "application/json" } },
    );
  }),
};
