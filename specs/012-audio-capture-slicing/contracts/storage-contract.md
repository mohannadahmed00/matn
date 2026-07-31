# Contract: Storage Surface & Policies

Extends Phase 11's `contracts/rest-contract.md` §5 and `contracts/rls-policies.md`. Everything here
is additive; no existing method or policy changes meaning.

## 1. New `StorageRestClient` methods

All three follow the existing shape: bearer token from `TokenRefresher`, `apikey` header,
`Resource<T>` return, `RemoteErrorMapper` on failure, `CancellationException` rethrown.

| Method | Request | Returns | Used by |
|--------|---------|---------|---------|
| `download(objectPath): Resource<ByteArray>` | `GET /object/{bucket}/{path}` | object bytes | preview cache, profile checks |
| `delete(objectPath): Resource<Unit>` | `DELETE /object/{bucket}/{path}` | — | step 4 of the commit ordering |
| `listWithSizes(prefix): Resource<Map<String, Long>>` | `POST /object/list/{bucket}` paginated, as `totalUsageBytes` already does | object name → byte size | the resume predicate |

`listWithSizes` and the existing `totalUsageBytes` share one paginated listing helper rather than
duplicating the offset loop (Principle III).

`upload` is unchanged. Its `x-upsert: true` header stays for covers; for audio the path is
content-tagged, so an upsert only ever rewrites byte-identical content.

**Notable failure mappings**

| HTTP | `RemoteError` | Teacher-facing meaning |
|------|---------------|------------------------|
| 413 / bucket limit | `Quota` | "this recording is larger than the 10 MB limit" |
| 415 | `Server` | mime type not allowed — should be unreachable, the client checks first |
| 404 on `download` | `Server` | a referenced object is missing — surfaced as a broken verse in preview (FR-025) |
| 507 / storage full | `Quota` | "storage is full", named as a storage problem, not a generic failure |

## 2. Bucket migration

`supabase/migrations/20260801000000_matn_content_audio_limits.sql`:

```sql
update storage.buckets
set file_size_limit = 10 * 1024 * 1024,
    allowed_mime_types = array['image/png', 'image/jpeg', 'image/webp', 'audio/mpeg']
where id = 'matn-content';
```

- **10 MB** matches FR-004's per-verse ceiling, satisfying FR-004b's "server at least as permissive
  as the tool".
- The **300 MB continuous ceiling needs no server counterpart** — the source recording is never
  uploaded (FR-018). The largest object this bucket can ever receive is one verse.
- The cover-image consequence is recorded in the plan's Complexity Tracking: server-side covers may
  now be up to 10 MB; FR-016's 5 MB check remains client-side, and the mime allowlist still keeps an
  audio file out of a cover path.

## 3. RLS — no new policies, extended coverage

Phase 11's storage policies are path-agnostic and already cover audio objects:

| Policy | Effect on `verses/*.mp3` |
|--------|--------------------------|
| `matn_content_teacher_read/insert/update/delete` | teacher-only writes and reads of drafts (FR-038) |
| `matn_content_published_read` | anonymous read exactly when the matn is published |

`matn_content_published_read` resolves the matn id as `(storage.foldername(name))[2]`. For
`matns/{matnId}/verses/{verseId}-{tag}.mp3` that element is still `{matnId}` — the deeper nesting
does not shift it, because element 1 is `matns`. **This must be asserted, not assumed**: a test in
the RLS matrix reads an audio object at the deeper path, published and unpublished.

## 4. RLS matrix delta (FR-038)

Added to Phase 11's `RlsPolicyTest`:

| # | Actor | Operation | Object | Expected |
|---|-------|-----------|--------|----------|
| A1 | anon | `GET` | audio object of a **published** matn | 200 |
| A2 | anon | `GET` | audio object of a **draft** matn | 403/404 |
| A3 | anon | `POST` (upload) | any audio path | denied |
| A4 | anon | `DELETE` | any audio path | denied |
| A5 | authenticated non-teacher | `POST` | any audio path | denied |
| A6 | teacher | `POST`/`DELETE`/`GET` | any audio path | allowed |
| A7 | anon | `POST` | audio path with a spoofed `owner_id` | denied |

A2 is the case Phase 11's own migration notes flag as easy to get wrong — draft covers were world-
readable under the old Firebase rules until the backend swap. Audio must not regress to that.

Like Phase 11's matrix, these run against a local `supabase start` stack and are skipped when
`SUPABASE_TEST_URL` is unset.

## 5. Storage-usage reporting

The portal's storage row already sums the whole `matns/` prefix via `totalUsageBytes`, so audio is
included automatically once uploaded — no change. The figure now moves visibly as a matn is
recorded, which is the behaviour the teacher expects when approaching a quota (spec edge case:
"remaining storage is exhausted mid-upload" reports a storage problem, mapped from §1).
