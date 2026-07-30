-- The migrations so far relied on Supabase's `pg_default_acl` in the `public` schema to give
-- `anon`/`authenticated` their table privileges. That is an environment default, not something this
-- schema states, so a fresh local stack produced tables no client could touch — every write was
-- refused at the GRANT layer before RLS was ever consulted, which reads as `Forbidden` and looks
-- exactly like a broken policy. Stated explicitly here so the schema is self-contained.
--
-- Also narrower than the defaults were: `anon` held INSERT/UPDATE/DELETE on both tables and was kept
-- off them by RLS alone. RLS is still the boundary, but there is no reason to hand an anonymous
-- caller write privileges that every policy then has to deny.

revoke all on public.matns from anon, authenticated;
grant select on public.matns to anon, authenticated;
grant insert, update, delete on public.matns to authenticated;
grant all on public.matns to service_role;

-- `public.teachers` is reachable only through `private.is_teacher()`, which is SECURITY DEFINER and
-- runs as the owner. No client role needs any privilege on it at all, so "RLS enabled with no
-- policies" is now backed by the absence of a grant as well — a caller is refused before RLS runs,
-- rather than filtered to zero rows by it.
revoke all on public.teachers from anon, authenticated;
grant all on public.teachers to service_role;
