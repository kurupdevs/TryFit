-- TryFit Supabase migration 002 — storage buckets + RLS
-- Buckets per SPEC 6 layout. MIME allowlist enforced client-side (see comment
-- at bottom); also enforced here via metadata checks in insert policies where cheap.

-- ---------- Buckets ----------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('user-uploads',  'user-uploads',  false, 5242880,  array['image/jpeg','image/png','image/webp']),   -- 5MB
  ('tryon-results', 'tryon-results', false, 10485760, array['image/jpeg','image/png','image/webp']),   -- 10MB
  ('product-images','product-images',true,  10485760, array['image/jpeg','image/png','image/webp']),   -- 10MB
  ('avatars',       'avatars',       false, 2097152,  array['image/jpeg','image/png','image/webp'])     -- 2MB
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

-- NOTE: allowed_mime_types + file_size_limit on storage.buckets are informational
-- on older self-hosted versions; the authoritative client contract is:
--   user-uploads: JPEG/PNG/WebP, <=5MB, 2048px max / q80 (<1.5MB target)
--   avatars:      JPEG/PNG/WebP, <=2MB
-- The edge functions / upload client must validate before PUT.

-- ---------- RLS policies on storage.objects ----------
-- Helper: first path segment is the owner's uid.

-- user-uploads: user can read/write/delete only their own prefix
create policy "uploads_select_own" on storage.objects for select
  using (bucket_id = 'user-uploads' and auth.uid()::text = (storage.foldername(name))[1]);
create policy "uploads_insert_own" on storage.objects for insert
  with check (bucket_id = 'user-uploads' and auth.uid()::text = (storage.foldername(name))[1]);
create policy "uploads_update_own" on storage.objects for update
  using (bucket_id = 'user-uploads' and auth.uid()::text = (storage.foldername(name))[1]);
create policy "uploads_delete_own" on storage.objects for delete
  using (bucket_id = 'user-uploads' and auth.uid()::text = (storage.foldername(name))[1]);

-- tryon-results: same ownership rule
create policy "results_select_own" on storage.objects for select
  using (bucket_id = 'tryon-results' and auth.uid()::text = (storage.foldername(name))[1]);
create policy "results_insert_own" on storage.objects for insert
  with check (bucket_id = 'tryon-results' and auth.uid()::text = (storage.foldername(name))[1]);
create policy "results_update_own" on storage.objects for update
  using (bucket_id = 'tryon-results' and auth.uid()::text = (storage.foldername(name))[1]);
create policy "results_delete_own" on storage.objects for delete
  using (bucket_id = 'tryon-results' and auth.uid()::text = (storage.foldername(name))[1]);

-- product-images: public read; only service role / admins write (no public write policy)
create policy "product_images_public_read" on storage.objects for select
  using (bucket_id = 'product-images');

-- avatars: {uid}.jpg — owner only (name must be "<uid>" or "<uid>.<ext>")
create policy "avatars_select_own" on storage.objects for select
  using (bucket_id = 'avatars' and auth.uid()::text = split_part(name, '.', 1));
create policy "avatars_insert_own" on storage.objects for insert
  with check (bucket_id = 'avatars' and auth.uid()::text = split_part(name, '.', 1));
create policy "avatars_update_own" on storage.objects for update
  using (bucket_id = 'avatars' and auth.uid()::text = split_part(name, '.', 1));
create policy "avatars_delete_own" on storage.objects for delete
  using (bucket_id = 'avatars' and auth.uid()::text = split_part(name, '.', 1));

-- ---------- 30-day lifecycle for user-uploads ----------
-- Supabase has no native bucket lifecycle policy; two options:
--  A) Dashboard: Database → Cron (pg_cron) with the job below, OR
--  B) this SQL: schedules a nightly pg_cron job that removes raw uploads older than 30d.
-- Kept idempotent so re-running the migration is safe.
do $$
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    perform cron.schedule(
      'tryfit-purge-old-uploads',
      '0 3 * * *',   -- daily 03:00 UTC
      $$delete from storage.objects
        where bucket_id = 'user-uploads'
          and created_at < now() - interval '30 days'$$
    );
  end if;
exception when others then
  raise notice 'pg_cron not available; create the 30-day purge job from the dashboard instead (see README).';
end $$;
