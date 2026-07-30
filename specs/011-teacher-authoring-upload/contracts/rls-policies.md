# Contract: Row-Level Security Policies

**Feature**: `specs/011-teacher-authoring-upload` | Satisfies FR-033, FR-039, FR-040, FR-041, FR-042

FR-039 makes these policies the **only** gate on student visibility, and FR-042 requires them
tested rather than merely written. This file is the policy text and the test matrix that proves it.

Supersedes the retired `security-rules.md`. The DDL lives in `supabase/migrations/`; this document
is the reasoning and the matrix.

---

## 0. What changed from Firestore rules, and why it matters

Firestore evaluates a rule against **the query**; Postgres evaluates a policy against **each row**.
Two consequences run through everything below:

1. Where a Firestore rule *refused* (403), an RLS policy *filters* (200, row absent). No data leaks
   either way, but the assertions differ — see §3.
2. Firestore had to deny an unconstrained collection listing outright, because it cannot filter a
   denied document out of a result set. RLS has no such problem, so the anonymous catalog query
   needs no `published=eq.true` filter and cannot be got wrong by forgetting one.

---

## 1. The teacher predicate

```sql
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
```

**Why a marker table and not a JWT claim**: a marker row needs no Admin SDK, is visible in the
dashboard, and makes FR-013's "must not block multiple teachers" true at the cost of one row.
Research D14 records the alternatives. It is the direct translation of the Firestore rules'
`exists(/databases/$(db)/documents/teachers/$(request.auth.uid))`.

**Why `SECURITY DEFINER`**: `public.teachers` has RLS on and no policies, so an ordinary read of it
returns nothing. The definer context is what lets the policies consult the marker while no client
can.

**Why the `private` schema**: PostgREST exposes every function in `public` as
`/rest/v1/rpc/<name>`. This is a policy helper, not an endpoint; the unexposed schema is the
documented remediation for database-linter warnings 0028/0029.

**Why `published` is a boolean column**: policies compare it directly. A text column would work but
invites `'true'` / `'TRUE'` drift in the field that is the entire security boundary.

---

## 2. Policies

### 2.1 `public.matns`

```sql
alter table public.matns enable row level security;

-- FR-040: published matns are readable by anyone; drafts only by a teacher.
create policy matns_read on public.matns
    for select to anon, authenticated
    using (published or private.is_teacher());

-- FR-041: every mutation requires the teacher marker. Anonymous callers get nothing.
create policy matns_insert on public.matns
    for insert to authenticated with check (private.is_teacher());

create policy matns_update on public.matns
    for update to authenticated
    using (private.is_teacher()) with check (private.is_teacher());

create policy matns_delete on public.matns
    for delete to authenticated using (private.is_teacher());
```

`anon` appears on the read policy only. With RLS enabled, anything not matched by a policy is
denied — the Firestore rules' explicit `match /{document=**} { allow read, write: if false; }`
catch-all has no counterpart to write, because it is the default.

### 2.2 `public.teachers`

RLS enabled, **no policies**. Provisioned with the service-role key (research D14). See
[postgres-schema.md](postgres-schema.md) §3.

### 2.3 Storage — the `matn-content` bucket

```sql
-- FR-040 for binary objects: a cover is anonymously readable exactly when its matn is published.
-- Object paths are `matns/{matnId}/cover.{ext}`, so element 2 of the folder path is the matn id.
create policy matn_content_published_read on storage.objects
    for select to anon, authenticated
    using (
        bucket_id = 'matn-content'
        and exists (
            select 1 from public.matns m
            where m.id = (storage.foldername(name))[2] and m.published
        )
    );

-- FR-041, one policy per verb. `x-upsert: true` re-uploads to the same deterministic path, which
-- is an update, so both `insert` and `update` are needed.
create policy matn_content_teacher_read   on storage.objects for select to authenticated
    using (bucket_id = 'matn-content' and private.is_teacher());
create policy matn_content_teacher_insert on storage.objects for insert to authenticated
    with check (bucket_id = 'matn-content' and private.is_teacher());
create policy matn_content_teacher_update on storage.objects for update to authenticated
    using (bucket_id = 'matn-content' and private.is_teacher())
    with check (bucket_id = 'matn-content' and private.is_teacher());
create policy matn_content_teacher_delete on storage.objects for delete to authenticated
    using (bucket_id = 'matn-content' and private.is_teacher());
```

**This is stricter than the Firebase Storage rules it replaces.** Those made every cover
world-readable, including an unpublished matn's, on the argument that a UUID path is unguessable and
a cover is not the content. RLS can express the published-only check as a cheap `exists` in the same
statement — no extra round trip, which was the original objection — so the draft cover is now
protected too.

FR-016's size and content-type limits are bucket properties rather than policy predicates:

```sql
update storage.buckets
set file_size_limit = 5 * 1024 * 1024,
    allowed_mime_types = array['image/png', 'image/jpeg', 'image/webp']
where id = 'matn-content';
```

They enforce FR-016 server-side as well as in the client, so a malformed upload cannot land even if
the client check is bypassed. Phase 12 extends `allowed_mime_types` with the audio types.

---

## 3. Test matrix (FR-042)

Every row is one test in `RlsPolicyTest`. Run in `jvmTest` against a local Supabase stack, driven
through this project's own Ktor client (research D9), so the request shape under test is the one the
app actually sends. Tests skip when `SUPABASE_TEST_URL` is unset.

"**Filtered**" below means the request succeeds and the row is absent — the RLS equivalent of a
Firestore refusal, per §0.

### 3.1 Reads

| # | Caller | Target | Expected | Requirement |
|---|--------|--------|----------|-------------|
| R1 | Anonymous | `matns` row with `published = true` | **Allow** | FR-040 |
| R2 | Anonymous | `matns` row with `published = false` | **Filtered** | FR-040 |
| R3 | Anonymous | Unfiltered listing of `matns` | **Published rows only** | §0 |
| R5 | Teacher | `matns` row with `published = false` | **Allow** | FR-036 |
| R6 | Authenticated non-teacher | `matns` row with `published = false` | **Filtered** | FR-013 forward-compat |
| R7 | Anonymous *and* teacher | `teachers` | **Filtered** — no policy exists | §2.2 |

R4 from the Firestore matrix ("unfiltered listing is denied") is retired: it tested a Firestore
limitation, not a requirement. R3 replaces it and asserts the stronger property directly.

### 3.2 Writes

| # | Caller | Operation | Expected | Requirement |
|---|--------|-----------|----------|-------------|
| W1 | Anonymous | Insert into `matns` | **Deny** — 401/403 | FR-041 |
| W2 | Anonymous | Update an existing published matn | **No row changed** | FR-041 |
| W3 | Anonymous | Flip `published` false → true | **No row changed** | FR-041, FR-039 |
| W5 | Anonymous | Delete from `matns` | **No row deleted** | FR-041 |
| W6 | Authenticated non-teacher | Insert into `matns` | **Deny** — 403, SQLSTATE `42501` | FR-013 forward-compat |
| W7 | Teacher | Insert, update, publish, unpublish | **Allow**, revision bumped each time | FR-032, FR-034, FR-038 |
| W8 | Teacher | Update with a stale `revision` filter | **No row changed** → `RemoteError.Conflict` | FR-043 |
| W9 | Anonymous *and* authenticated non-teacher | Insert into `teachers` (self-promotion) | **Deny** | §2.2 |

W4 (anonymous unpublish) is folded into W2/W3 — all three are the same `update` policy on the same
row, and W3 already proves the direction that matters for FR-039.

W9 is the one that matters most: without a locked-down `teachers` table, a caller who could insert
their own marker row would grant themselves write access to the entire catalog.

### 3.3 Storage

Covered by the bucket constraints in §2.3 rather than by test cases. The Firestore-era S1–S7 rows
were retired when Storage first moved to Supabase (`design-notes.md`); the size and content-type
limits are now bucket properties that Supabase enforces before any policy runs.

---

## 4. Running the tests

```bash
# One-off: install the Supabase CLI (not a project dependency)
npm install -g supabase

# From the repo root
supabase start
eval "$(supabase status -o env)"
SUPABASE_TEST_URL="$API_URL" \
SUPABASE_TEST_ANON_KEY="$ANON_KEY" \
SUPABASE_TEST_SERVICE_ROLE_KEY="$SERVICE_ROLE_KEY" \
  ./gradlew :shared:jvmTest --tests 'com.giraffe.matn.remote.RlsPolicyTest'
```

`supabase start` applies everything in `supabase/migrations/` to the local stack, so the policies
under test are exactly the committed ones. With the env vars unset the tests report as skipped and
the ordinary `./gradlew test` stays green on a machine with no Supabase CLI.

**CI**: `.github/workflows/rls-policy-tests.yml` runs the command above on any change to
`supabase/**` or the remote layer. Without it these tests exist but never execute, and FR-042 is
satisfied on paper only.

---

## 5. Deployment

```bash
supabase db push
```

Policies are tracked in the repo as migrations (research D13) — they are code, they are tested, and
a policy change is a reviewable diff. Only project configuration is gitignored, and no service-role
key exists in this design outside the test environment.
