-- TryFit Supabase migration 004 — shared looks ("Ask friends" vote links)
--
-- Backs the share/vote flow: a try-on result image + the product ids in the
-- look, shareable as https://tryfit.app/v/<id> (App Link planned; v1 copies
-- the link + image via the system share sheet).
--
-- NOTE on images: `image_url` may be a long-lived signed URL. The
-- `tryon-results` storage bucket is private; a public `shared-looks` bucket
-- (Worker B follow-up) will let this be a plain public URL in a later
-- migration.

create table if not exists public.shared_looks (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  image_url text not null,
  product_ids text[] not null default '{}',
  votes_a integer not null default 0,
  votes_b integer not null default 0,
  created_at timestamptz not null default now()
);

alter table public.shared_looks enable row level security;

-- Public read: anyone with the vote link can view (no sign-in wall).
create policy "shared_looks_public_read" on public.shared_looks for select
  using (true);

-- Owner write: only the creating user can insert/update/delete their rows.
create policy "shared_looks_insert_own" on public.shared_looks for insert
  with check (auth.uid() = user_id);

create policy "shared_looks_update_own" on public.shared_looks for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

create policy "shared_looks_delete_own" on public.shared_looks for delete
  using (auth.uid() = user_id);

create index if not exists shared_looks_user_id_idx on public.shared_looks (user_id);
create index if not exists shared_looks_created_at_idx on public.shared_looks (created_at desc);
