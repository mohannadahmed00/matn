# Contract: Postgres Schema

**Feature**: `specs/011-teacher-authoring-upload` | Satisfies FR-007, FR-009, FR-010, FR-011, FR-012, FR-013

Domain types are in [../data-model.md](../data-model.md); this is the wire shape they map to.

Supersedes the retired `firestore-schema.md`. The whole backend moved from Firebase to Supabase
(`design-notes.md`); the DDL that creates everything below lives in `supabase/migrations/`.

---

## 1. Tables

| Table | Purpose | Written by | Read by |
|-------|---------|-----------|---------|
| `public.matns` | One row per matn — metadata, chapters, verses | Teacher only | Teacher (all), anonymous (published only) |
| `public.teachers` | Authorization marker; a row's existence = may write (research D14) | Service role, by hand | `private.is_teacher()` only |

`matns.id` is the domain UUID (FR-008) as `text`, so the row identity is stable across
unpublish/republish.

`chapters` and `verses` stay as `jsonb` documents on the row rather than becoming child tables.
That is deliberate: FR-033 requires a save to be one atomic full-row write, and normalised verses
would make a save a multi-statement transaction the REST client cannot express.

---

## 2. `public.matns`

Column names are snake_case, the PostgREST convention. Field names *inside* the two `jsonb` columns
stay camelCase, mirroring `DraftChapter`/`DraftVerse` one-for-one. Where a counterpart exists the
names still match `SeedMatn`'s (FR-009), so Phase 13's sync is a projection.

### 2.1 Scalar columns — the catalog overview (FR-012)

Everything a list row needs is a scalar column, so a reader selects those columns and never
transfers verse text.

| Column | Type | Domain source | Notes |
|--------|------|---------------|-------|
| `id` | `text` primary key | `MatnDraft.id` | |
| `title` | `text not null` | `MatnDraft.title` | Required, non-blank |
| `author` | `text not null` | `MatnDraft.author` | Required, non-blank |
| `description` | `text not null` | `MatnDraft.description` | May be empty |
| `cover_image_ref` | `text` | `MatnDraft.coverImageRef` | Storage object path, not a download URL. Nullable |
| `structure_kind` | `text not null` | `MatnDraft.structureKind.name` | `'SIMPLE'` \| `'STRUCTURED'` — the exact strings `ContentSeedLoaderImpl` compares against |
| `default_reciter_id` | `text not null` | `MatnDraft.defaultReciterId` | The FR-007a constant; never empty |
| `published` | `boolean not null` | `publicationState == PUBLISHED` | **The visibility gate (FR-039).** The RLS `select` policy reads it |
| `audio_completeness` | `text not null` | derived | `'NONE'` \| `'PARTIAL'` \| `'COMPLETE'`. Always `'NONE'` in Phase 11 |
| `verse_count` | `integer not null` | `verses.size` | Derived, so an overview read needs no verse array |
| `declared_size_bytes` | `bigint not null` | derived | Text-only this phase |
| `created_at` | `timestamptz not null` | `MatnDraft.createdAt` | Set once |
| `updated_at` | `timestamptz not null` | `MatnDraft.updatedAt` | Set on every write |
| `revision` | `bigint not null default 1` | — | **Server-owned.** The concurrency token (FR-043) |

`revision` replaces Firestore's server-assigned `updateTime`. It is a counter, not a timestamp: an
update filters on `revision=eq.<n>`, and an integer round-trips through a URL filter with none of a
timestamp's formatting hazards. A `before update` trigger sets `new.revision := old.revision + 1`,
so a client cannot forge agreement with a stale read by sending its own value — the write payload
omits the column entirely.

### 2.2 `chapters` — `jsonb` array

| Field | JSON type | Notes |
|-------|-----------|-------|
| `id` | string | UUID |
| `title` | string | Non-blank |
| `order` | number | Distinct within the matn |

`'[]'::jsonb` for a `SIMPLE` matn.

### 2.3 `verses` — `jsonb` array

| Field | JSON type | Notes |
|-------|-----------|-------|
| `id` | string | UUID |
| `chapterId` | string \| null | Non-null iff `STRUCTURED` |
| `displayNumber` | number | `1..n`, no gaps |
| `arabicText` | string | Verbatim, diacritics preserved |
| `durationMs` | number | `0` in Phase 11 |
| `audio` | object \| null | **Always `null` in Phase 11** (FR-045). Shape reserved: `{ id, fileRef, durationMs }` |

Array order is the authoritative verse order; `displayNumber` mirrors it and is what the app reads.

---

## 3. `public.teachers`

A row's existence is the whole contract. Columns are informational only and no policy reads them.

| Column | Type | Notes |
|--------|------|-------|
| `uid` | `uuid` primary key → `auth.users(id)` | The Supabase Auth user |
| `display_name` | `text not null` | Convenience for a human browsing the dashboard |
| `created_at` | `timestamptz not null` | |

RLS is enabled with **no policies at all**, so no client — not even the teacher whose row it is —
can read or write it. `private.is_teacher()` is `SECURITY DEFINER`, which is what lets the policies
consult it anyway.

Provisioning is manual (Assumptions, research D14): insert a row with the service-role key after
creating the account. A second teacher later is a second row — which is how FR-013 stays true
without a schema change.

---

## 4. Reads

### 4.1 Overview list (FR-035, and Phase 13's catalog sync)

```http
GET /rest/v1/matns
      ?select=id,title,author,description,cover_image_ref,published,
              audio_completeness,verse_count,declared_size_bytes,updated_at
      &order=updated_at.desc
```

The column list is what makes FR-012 real: the `verses` and `chapters` jsonb are never transferred
for a listing.

**An anonymous reader needs no `published=eq.true` filter.** This is the one place the Postgres
model is simpler than the Firestore one it replaces: RLS filters row by row, so an unconstrained
listing returns exactly the published rows. Firestore had to refuse an unconstrained collection
listing outright, because it evaluates rules against the query rather than the results.

### 4.2 Full row (opening a matn for editing, FR-036)

```http
GET /rest/v1/matns?id=eq.{matnId}&select=*
```

The response's `revision` is retained as `MatnDraft.remoteRevision` — the concurrency token
(research D4).

---

## 5. Writes

Every write is a single-row statement, which is atomic in Postgres with no explicit transaction.
That is what discharges FR-033.

### 5.1 Save

**First save** — the row does not exist yet (`remoteRevision == null`):

```http
POST /rest/v1/matns
Prefer: return=representation
```

A duplicate primary key answers `409` with SQLSTATE `23505` → `RemoteError.Conflict`.

**Subsequent saves** — the revision from the last read is the precondition:

```http
PATCH /rest/v1/matns?id=eq.{matnId}&revision=eq.{remoteRevision}
Prefer: return=representation
```

- Full-row body — no partial-column updates, so the row is never a mixture of two edits.
- `return=representation` brings the newly bumped `revision` back on the same round trip; refetching
  it would reopen the race the revision exists to close.
- Publish and unpublish are the same call with `published` flipped; they are not separate endpoints,
  so they inherit the same atomicity and the same conflict check.

**The one ambiguity to know about.** PostgREST reports both "the revision precondition failed" and
"row-level security hid this row" identically: `200` with an empty array. `SupabaseCatalogRepository`
disambiguates by reading the row back — visible means the revision simply moved on
(`RemoteError.Conflict`); invisible means the `select` policy refuses it too
(`RemoteError.Forbidden`).

### 5.2 Ordering against the cover image (research D3)

Cover image uploads to Storage **first**, then the row is written with its path. A failure between
the two leaves an unreferenced Storage object — inert, and overwritten by the next attempt at the
same deterministic path. The reverse order would leave a row pointing at nothing.

---

## 6. Indexes

| Index | Why |
|-------|-----|
| `matns_pkey` on `id` | Every read and write filters on it |
| `matns_published_updated_at_idx` on `(published, updated_at desc)` | The catalog listing filters on visibility and orders by recency |

No index on `chapters` or `verses`. Firestore indexed every array element by default and needed an
explicit exemption to stop; Postgres indexes `jsonb` only when told to, so the exemption has no
counterpart here — the columns are simply left unindexed.

---

## 7. Size budget

| Component | 500-verse estimate |
|-----------|--------------------|
| Verse text (~120 Arabic chars ≈ 240 B UTF-8) | ~120 KB |
| Verse ids (36 B) + `chapterId` (36 B) + numeric fields | ~45 KB |
| `jsonb` key overhead | ~25 KB |
| Chapters, scalar columns | ~2 KB |
| **Phase 11 total** | **~190 KB** |
| Phase 12 adds an `audio` object + duration per verse | ~+60 KB |
| **Projected Phase 12 total** | **~250 KB** |

Firestore's hard 1 MiB per-document limit is gone — Postgres TOASTs a large `jsonb` value and the
practical ceiling is far above anything this feature produces. `ValidateMatn` still refuses to
publish above ~900 KB, which is now a **self-imposed sanity check** rather than a platform limit: an
implausibly large matn should fail with a clear message (research D2), and keeping the threshold
avoids a behaviour change with no benefit.

---

## 8. What is deliberately absent

| Absent | Why |
|--------|-----|
| A schema-version column | Flagged as a Deferred item in the clarify session. Phase 12 adds audio fields and Phase 13 reads them; the additive-only shape above means an old reader ignores new columns. Worth adding before a *breaking* change, which this is not |
| `pack_id` | A Phase 8 delivery slug. Phase 13 derives it from the Storage path prefix; the authoring tool never invents delivery values |
| `is_starter` | The starter path is deleted outright in Phase 13 |
| Ownership / `author_uid` | Single-teacher scope. Adding it later is additive and does not invalidate existing rows (FR-013) |
| Normalised `chapters` / `verses` tables | See §1: a save must be one atomic statement |
