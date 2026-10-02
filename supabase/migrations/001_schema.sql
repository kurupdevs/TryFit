-- TryFit Supabase migration 001 — core schema
-- Spec: ~/workspace/virtual-tryon-app/SPEC.md sections 6 & 7
-- Conventions: app_config kill-switch/quota knobs read by edge functions.

-- ---------- Enums ----------
do $$ begin
  create type product_category as enum ('casual','jackets','shoes','bags','tops');
exception when duplicate_object then null; end $$;

do $$ begin
  create type body_build as enum ('slim','athletic','average','plus');
exception when duplicate_object then null; end $$;

do $$ begin
  create type tryon_status as enum ('queued','processing','done','failed','cancelled');
exception when duplicate_object then null; end $$;

do $$ begin
  create type notification_type as enum ('tryon_done','promo','system');
exception when duplicate_object then null; end $$;

-- ---------- Tables ----------
create table if not exists public.users (
  id          uuid primary key references auth.users(id) on delete cascade,
  name        text,
  email       text,
  phone       text,
  avatar_url  text,
  body_profile jsonb not null default '{}'::jsonb,   -- {heightCm, build, chestCm, waistCm}
  preferences jsonb not null default '{}'::jsonb,
  quota        jsonb not null default '{"usedToday":0,"resetDate":null}'::jsonb,
  created_at  timestamptz not null default now()
);

create table if not exists public.products (
  id          uuid primary key default gen_random_uuid(),
  brand       text not null,
  name        text not null,
  category    product_category not null,
  price       numeric(10,2) not null,
  currency    char(3) not null default 'INR',
  images      text[] not null default '{}',           -- images[1] = hero
  description text,
  sizes       text[] not null default '{}',
  tags        text[] not null default '{}',
  is_active   boolean not null default true,
  created_at  timestamptz not null default now()
);

create table if not exists public.wardrobe_items (
  user_id    uuid not null references public.users(id) on delete cascade,
  product_id uuid not null references public.products(id) on delete cascade,
  added_at   timestamptz not null default now(),
  notes      text,
  primary key (user_id, product_id)
);

create table if not exists public.tryon_sessions (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null references public.users(id) on delete cascade,
  product_id      uuid not null references public.products(id) on delete cascade,
  input_photo_url text not null,                       -- path inside user-uploads bucket
  garment_image_url text not null,
  status          tryon_status not null default 'queued',
  result_image_url text,                               -- path inside tryon-results bucket
  processing_ms   integer,
  error_message   text,
  size_override   text,
  demo            boolean not null default false,      -- client-driven demo progression flag
  cost_cents      integer,                             -- logged per-session spend (SPEC 7 cost guard)
  idempotency_key text unique,                         -- SPEC 7 job ledger
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);

create table if not exists public.notifications (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references public.users(id) on delete cascade,
  title      text not null,
  body       text,
  type       notification_type not null default 'system',
  read       boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.ai_chats (
  user_id    uuid primary key references public.users(id) on delete cascade,
  messages   jsonb not null default '[]'::jsonb,      -- [{role, text, productRef, ts}]
  updated_at timestamptz not null default now()
);

create table if not exists public.app_config (
  key   text primary key,
  value jsonb not null
);

-- Kill-switch + quota knobs (defaults per SPEC 7)
insert into public.app_config (key, value) values
  ('tryon_enabled',        'true'::jsonb),
  ('daily_quota',          '5'::jsonb),
  ('cost_alert_threshold', '20000'::jsonb)           -- cents; 2x-expected-spend alert input
on conflict (key) do nothing;

-- ---------- Indexes ----------
create index if not exists idx_wardrobe_user_added on public.wardrobe_items (user_id, added_at desc);
create index if not exists idx_tryon_user_created on public.tryon_sessions (user_id, created_at desc);
create index if not exists idx_tryon_status on public.tryon_sessions (status);
create index if not exists idx_products_category_active on public.products (category) where is_active;
create index if not exists idx_notifications_user_created on public.notifications (user_id, created_at desc);

-- ---------- Updated-at trigger ----------
create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin new.updated_at = now(); return new; end $$;

drop trigger if exists trg_tryon_updated_at on public.tryon_sessions;
create trigger trg_tryon_updated_at
  before update on public.tryon_sessions
  for each row execute function public.set_updated_at();

-- ---------- Atomic quota increment (server-side, race-safe) ----------
-- Returns the new usedToday count. Resets on date rollover (YYYY-MM-DD, UTC).
create or replace function public.increment_quota(p_user_id uuid, p_limit int)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_quota jsonb;
  v_used  int;
  v_date  text;
  v_today text := to_char(now() at time zone 'utc', 'YYYY-MM-DD');
begin
  select quota into v_quota from public.users where id = p_user_id for update;
  if not found then
    raise exception 'USER_NOT_FOUND';
  end if;
  v_used := coalesce((v_quota->>'usedToday')::int, 0);
  v_date := v_quota->>'resetDate';
  if v_date is distinct from v_today then
    v_used := 0;
    v_date := v_today;
  end if;
  if v_used >= p_limit then
    -- Do NOT increment; caller raises QUOTA_EXCEEDED.
    return jsonb_build_object('usedToday', v_used, 'limit', p_limit, 'ok', false);
  end if;
  v_used := v_used + 1;
  update public.users
    set quota = jsonb_build_object('usedToday', v_used, 'resetDate', v_date)
    where id = p_user_id;
  return jsonb_build_object('usedToday', v_used, 'limit', p_limit, 'ok', true);
end $$;

-- ---------- RLS ----------
alter table public.users           enable row level security;
alter table public.products        enable row level security;
alter table public.wardrobe_items  enable row level security;
alter table public.tryon_sessions  enable row level security;
alter table public.notifications   enable row level security;
alter table public.ai_chats        enable row level security;
alter table public.app_config      enable row level security;

-- users: own row only; insert on signup (id = auth.uid())
create policy "users_select_own" on public.users for select
  using (auth.uid() = id);
create policy "users_insert_own" on public.users for insert
  with check (auth.uid() = id);
create policy "users_update_own" on public.users for update
  using (auth.uid() = id) with check (auth.uid() = id);

-- products: public read where active
create policy "products_public_read" on public.products for select
  using (is_active = true);

-- wardrobe_items: own rows
create policy "wardrobe_all_own" on public.wardrobe_items for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- tryon_sessions: own rows
create policy "tryon_all_own" on public.tryon_sessions for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- notifications: own rows
create policy "notif_all_own" on public.notifications for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- ai_chats: own rows
create policy "aichat_all_own" on public.ai_chats for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- app_config: readable by anyone authenticated (feature flags),
-- writes reserved for service role / dashboard only.
create policy "appconfig_read" on public.app_config for select
  using (auth.role() = 'authenticated');

-- NOTE: users rows are created by a sign-up trigger (optional):
-- create or replace function public.handle_new_user() returns trigger ... 
-- insert into public.users(id, email) values (new.id, new.email);
-- Left out on purpose: client upserts its own user row on first login.
