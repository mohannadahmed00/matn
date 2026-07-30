-- Phase 11 teacher authoring, on Supabase.
--
-- Replaces the Firestore `matns`/`teachers` collections and `firebase/firestore.rules`. The
-- document shape is preserved rather than normalised: `chapters` and `verses` stay as `jsonb`
-- arrays on the row, because FR-033 requires a save to be one atomic full-body write — a matn is
-- never a mixture of two edits. Normalising verses into their own table would make a save a
-- multi-statement transaction the REST client cannot express.

-- ---------------------------------------------------------------------------
-- teachers: the authorisation marker, provisioned by hand (research D14).
-- ---------------------------------------------------------------------------

create table public.teachers (
    uid          uuid primary key references auth.users (id) on delete cascade,
    display_name text        not null default '',
    created_at   timestamptz not null default now()
);

alter table public.teachers enable row level security;

-- Deliberately no policies: with RLS on and nothing granted, no client — not even a teacher's own
-- session — can read or write this table. `is_teacher()` is SECURITY DEFINER, so its own read is
-- privileged and unaffected. Rows are inserted from the dashboard / service role only.

comment on table public.teachers is
    'Authorisation marker. No RLS policies by design: provisioned out of band, readable only through is_teacher().';

-- ---------------------------------------------------------------------------
-- is_teacher(): the SQL equivalent of the rules'
--   exists(/databases/$(database)/documents/teachers/$(request.auth.uid))
-- ---------------------------------------------------------------------------

create function public.is_teacher()
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

revoke execute on function public.is_teacher() from public;
grant execute on function public.is_teacher() to anon, authenticated;

-- ---------------------------------------------------------------------------
-- matns
-- ---------------------------------------------------------------------------

create table public.matns (
    id                  text primary key,
    title               text        not null default '',
    author              text        not null default '',
    description         text        not null default '',
    cover_image_ref     text,
    structure_kind      text        not null default 'SIMPLE',
    default_reciter_id  text        not null default '',
    published           boolean     not null default false,
    audio_completeness  text        not null default 'NONE',
    verse_count         integer     not null default 0,
    declared_size_bytes bigint      not null default 0,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    chapters            jsonb       not null default '[]'::jsonb,
    verses              jsonb       not null default '[]'::jsonb,
    -- The concurrency token (FR-043), replacing Firestore's server-assigned `updateTime`. A
    -- monotonic counter rather than a timestamp: an update filters on `revision=eq.<n>`, and an
    -- integer round-trips through a URL filter without any timestamp-formatting hazard.
    revision            bigint      not null default 1
);

-- The list query is `published`-filtered for anonymous callers and ordered by `updated_at`.
create index matns_published_updated_at_idx on public.matns (published, updated_at desc);

alter table public.matns enable row level security;

-- `revision` is server-owned: a client that sends its own value is overwritten here, so the token
-- can never be forged into agreeing with a stale read.
create function public.bump_matn_revision()
    returns trigger
    language plpgsql
    set search_path = ''
as $$
begin
    new.revision := old.revision + 1;
    return new;
end;
$$;

create trigger matns_bump_revision
    before update on public.matns
    for each row
execute function public.bump_matn_revision();

-- FR-040: published matns are readable by anyone; drafts only by a teacher. Unlike Firestore —
-- which had to deny unfiltered collection listing outright because it cannot filter a denied
-- document out of a result set — RLS filters row by row, so an anonymous `select` on the whole
-- table simply returns the published rows and nothing else.
create policy matns_read on public.matns
    for select to anon, authenticated
    using (published or public.is_teacher());

-- FR-041: every mutation requires the teacher marker.
create policy matns_insert on public.matns
    for insert to authenticated
    with check (public.is_teacher());

create policy matns_update on public.matns
    for update to authenticated
    using (public.is_teacher())
    with check (public.is_teacher());

create policy matns_delete on public.matns
    for delete to authenticated
    using (public.is_teacher());

-- ---------------------------------------------------------------------------
-- Storage: the `matn-content` bucket (cover images now, Phase 12 audio later).
-- ---------------------------------------------------------------------------

create policy matn_content_teacher_read on storage.objects
    for select to authenticated
    using (bucket_id = 'matn-content' and public.is_teacher());

create policy matn_content_teacher_insert on storage.objects
    for insert to authenticated
    with check (bucket_id = 'matn-content' and public.is_teacher());

-- `x-upsert: true` re-uploads a cover to the same deterministic path, which is an update.
create policy matn_content_teacher_update on storage.objects
    for update to authenticated
    using (bucket_id = 'matn-content' and public.is_teacher())
    with check (bucket_id = 'matn-content' and public.is_teacher());

create policy matn_content_teacher_delete on storage.objects
    for delete to authenticated
    using (bucket_id = 'matn-content' and public.is_teacher());
