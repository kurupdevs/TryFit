// TryFit shared edge-function utilities (Deno, std-only — zero imports).
// Talks to Supabase via the PostgREST REST API directly, so functions carry
// no third-party dependencies.

export const CODES = {
  UNAUTHORIZED: "UNAUTHORIZED",
  QUOTA_EXCEEDED: "QUOTA_EXCEEDED",
  TRYON_DISABLED: "TRYON_DISABLED",
  INVALID_PHOTO: "INVALID_PHOTO",
  MODERATION_REJECTED: "MODERATION_REJECTED",
  NOT_FOUND: "NOT_FOUND",
  FORBIDDEN: "FORBIDDEN",
  INVALID_STATE: "INVALID_STATE",
  PRODUCT_INACTIVE: "PRODUCT_INACTIVE",
} as const;

export type ErrorCode = (typeof CODES)[keyof typeof CODES];

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

export function errorResponse(code: string, message: string, status = 400): Response {
  return jsonResponse({ ok: false, code, message }, status);
}

export function getEnv(name: string, fallback = ""): string {
  return Deno.env.get(name) ?? fallback;
}

// ---------------- minimal Supabase REST client ----------------

export interface Db {
  /** GET /rest/v1/<table>?<query> — returns parsed JSON (array or object). */
  get(table: string, query: string, jwt?: string): Promise<unknown>;
  /** POST /rest/v1/<table> — insert; Prefer: return=representation. */
  insert(table: string, rows: unknown, jwt?: string): Promise<unknown>;
  /** PATCH /rest/v1/<table>?<query> — update. */
  update(table: string, query: string, body: unknown, jwt?: string): Promise<unknown>;
  /** DELETE /rest/v1/<table>?<query>. */
  del(table: string, query: string, jwt?: string): Promise<unknown>;
  /** POST /rest/v1/rpc/<fn>. */
  rpc(fn: string, params: unknown): Promise<unknown>;
  /** GET /auth/v1/user — validate caller's JWT via service role. */
  authUser(jwt: string): Promise<{ id: string }>;
  /** Storage: DELETE objects by name list. */
  storageDelete(bucket: string, names: string[]): Promise<void>;
  /** Config table read. */
  config(key: string): Promise<unknown>;
}

function headers(key: string, jwt?: string, extra: Record<string, string> = {}): Headers {
  const h = new Headers({
    apikey: key,
    "content-type": "application/json",
    ...extra,
  });
  if (jwt) h.set("Authorization", `Bearer ${jwt}`);
  return h;
}

export function dbClient(): Db {
  const url = getEnv("SUPABASE_URL");
  const service = getEnv("SUPABASE_SERVICE_ROLE_KEY");
  const anon = getEnv("SUPABASE_ANON_KEY");
  if (!url || !service || !anon) {
    throw new Error("Missing SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY / SUPABASE_ANON_KEY");
  }
  const rest = `${url}/rest/v1`;

  async function request(
    path: string,
    init: RequestInit,
    useService: boolean,
    jwt?: string,
  ): Promise<unknown> {
    const key = useService ? service : anon;
    const res = await fetch(`${rest}${path}`, {
      ...init,
      headers: headers(key, jwt, {
        Prefer: "return=representation",
        ...(init.headers as Record<string, string> ?? {}),
      }),
    });
    const text = await res.text();
    if (!res.ok) throw new Error(`supabase ${res.status}: ${text.slice(0, 300)}`);
    return text ? JSON.parse(text) : null;
  }

  return {
    get: (table, query, jwt) => request(`/${table}?${query}`, { method: "GET" }, false, jwt),
    insert: (table, rows, jwt) => request(`/${table}`, { method: "POST", body: JSON.stringify(rows) }, false, jwt),
    update: (table, query, body, jwt) => request(`/${table}?${query}`, { method: "PATCH", body: JSON.stringify(body) }, false, jwt),
    del: (table, query, jwt) => request(`/${table}?${query}`, { method: "DELETE" }, false, jwt),
    rpc: (fn, params) =>
      request(`/rpc/${fn}`, { method: "POST", body: JSON.stringify(params) }, true),
    authUser: async (jwt) => {
      const res = await fetch(`${url}/auth/v1/user`, {
        headers: { apikey: service, Authorization: `Bearer ${jwt}` },
      });
      if (!res.ok) throw new Error("invalid token");
      const u = await res.json();
      return { id: u.id as string };
    },
    storageDelete: async (bucket, names) => {
      const res = await fetch(`${url}/storage/v1/object/${bucket}`, {
        method: "DELETE",
        headers: { apikey: service, Authorization: `Bearer ${service}`, "content-type": "application/json" },
        body: JSON.stringify({ prefixes: names }),
      });
      if (!res.ok) throw new Error(`storage delete failed: ${res.status}`);
    },
    config: async (key) => {
      const rows = (await request(
        `/app_config?key=eq.${encodeURIComponent(key)}&select=value`,
        { method: "GET" },
        true,
      )) as Array<{ value: unknown }>;
      return rows[0]?.value ?? null;
    },
  };
}

// ---------------- auth / quota ----------------

/** Verify caller; throws a 401 Response on failure. Returns user id + jwt. */
export async function requireUser(req: Request, db: Db): Promise<{ id: string; jwt: string }> {
  const auth = req.headers.get("authorization") ?? "";
  const jwt = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  if (!jwt) throw errorResponse(CODES.UNAUTHORIZED, "Missing bearer token", 401);
  try {
    const { id } = await db.authUser(jwt);
    return { id, jwt };
  } catch {
    throw errorResponse(CODES.UNAUTHORIZED, "Invalid token", 401);
  }
}

/** True unless the app_config kill-switch says otherwise (default ON). */
export async function isTryOnEnabled(db: Db): Promise<boolean> {
  const v = await db.config("tryon_enabled");
  return v !== false;
}

export async function dailyQuotaLimit(db: Db): Promise<number> {
  const v = await db.config("daily_quota");
  return typeof v === "number" ? v : 5;
}

/**
 * Atomically increments the user's daily quota via the increment_quota RPC.
 * Throws 429 Response with QUOTA_EXCEEDED when over the limit.
 */
export async function getQuotaOrThrow(
  db: Db,
  userId: string,
): Promise<{ usedToday: number; limit: number }> {
  const limit = await dailyQuotaLimit(db);
  const row = (await db.rpc("increment_quota", { p_user_id: userId, p_limit: limit })) as {
    usedToday: number;
    limit: number;
    ok: boolean;
  };
  if (!row.ok) {
    throw errorResponse(CODES.QUOTA_EXCEEDED, `Daily try-on limit reached (${limit}/day)`, 429);
  }
  return { usedToday: row.usedToday, limit: row.limit };
}

/** Non-incrementing quota view for getQuota. */
export async function getQuotaView(
  db: Db,
  userId: string,
  jwt?: string,
): Promise<{ usedToday: number; limit: number; resetsAt: string }> {
  const limit = await dailyQuotaLimit(db);
  const rows = (await db.get("users", `id=eq.${userId}&select=quota`, jwt)) as Array<{
    quota?: { usedToday?: number; resetDate?: string };
  }>;
  const q = rows[0]?.quota ?? {};
  const today = new Date().toISOString().slice(0, 10);
  const usedToday = q.resetDate === today ? (q.usedToday ?? 0) : 0;
  const reset = new Date(`${today}T00:00:00Z`);
  reset.setUTCDate(reset.getUTCDate() + 1);
  return { usedToday, limit, resetsAt: reset.toISOString() };
}

/** Wrap a handler so thrown typed errors become JSON responses. */
export function serve(
  handler: (req: Request, db: Db) => Promise<Response>,
): (req: Request) => Promise<Response> {
  return async (req: Request) => {
    try {
      if (req.method !== "POST") {
        return errorResponse(CODES.INVALID_STATE, "POST only", 405);
      }
      return await handler(req, dbClient());
    } catch (err) {
      if (err instanceof Response) return err;
      console.error("edge-function error:", err);
      return errorResponse(CODES.INVALID_STATE, "Internal error", 500);
    }
  };
}

/** Fetch a single row (or null) by id-style query. */
export async function getRow(
  db: Db,
  table: string,
  query: string,
  jwt?: string,
): Promise<Record<string, unknown> | null> {
  const rows = (await db.get(table, `${query}&limit=1`, jwt)) as Array<Record<string, unknown>>;
  return rows[0] ?? null;
}
