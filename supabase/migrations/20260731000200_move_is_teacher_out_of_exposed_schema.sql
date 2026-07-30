-- `public.is_teacher()` was reachable as `/rest/v1/rpc/is_teacher` because PostgREST exposes the
-- whole `public` schema (database linter 0028/0029). It is a policy helper, not an endpoint, so it
-- moves to an unexposed schema: RLS expressions still call it, PostgREST no longer sees it.
create schema if not exists private;
grant usage on schema private to anon, authenticated;

create function private.is_teacher()
    returns boolean
    language sql
    stable
    security definer
    set search_path = ''
as $$
    select exists (
        select 1 from public.teachers t where t.uid = (select auth.uid())
    );
$$;

revoke execute on function private.is_teacher() from public;
grant execute on function private.is_teacher() to anon, authenticated;

alter policy matns_read   on public.matns using (published or private.is_teacher());
alter policy matns_insert on public.matns with check (private.is_teacher());
alter policy matns_update on public.matns using (private.is_teacher()) with check (private.is_teacher());
alter policy matns_delete on public.matns using (private.is_teacher());

alter policy matn_content_teacher_read on storage.objects
    using (bucket_id = 'matn-content' and private.is_teacher());
alter policy matn_content_teacher_insert on storage.objects
    with check (bucket_id = 'matn-content' and private.is_teacher());
alter policy matn_content_teacher_update on storage.objects
    using (bucket_id = 'matn-content' and private.is_teacher())
    with check (bucket_id = 'matn-content' and private.is_teacher());
alter policy matn_content_teacher_delete on storage.objects
    using (bucket_id = 'matn-content' and private.is_teacher());

drop function public.is_teacher();
