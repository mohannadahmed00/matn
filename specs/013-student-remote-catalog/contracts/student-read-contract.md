# Contract: The Student's Anonymous Read Surface

Extends Phase 11's `contracts/rest-contract.md` and `contracts/rls-policies.md`. **No server-side
change is required** beyond one column added to an existing projection — the policies and grants
Phase 11 shipped already permit everything below (research D1).

---

## 1. Authentication: none

Every request in this contract sends the project `apikey` header and **no `Authorization` header**.
The caller is the Postgres `anon` role.

```http
apikey: <SupabaseConfig.anonKey>
```

FR-027 forbids any student-side token, key exchange, or signed URL. `SupabaseConfig.anonKey`
identifies the *project*, not the caller, and is public by design — it is not a credential in the
sense FR-027 excludes.

### 1.1 Client change required

`PostgrestClient` and `StorageRestClient` currently hard-require a bearer token and fail before
issuing a request when none exists (research D2). Both take an `AccessTokenProvider` instead:

```kotlin
fun interface AccessTokenProvider {
    suspend fun currentAccessToken(): Resource<String?>
}
```

| Binding | Module | Returns |
|---|---|---|
| `TokenRefresher` (existing, adapted) | `:teacherApp` | the teacher's refreshed access token |
| `AnonymousAccessTokenProvider` | student clients | `Resource.Success(null)` |

When the provider yields `null`, the `Authorization` header is **omitted**. All other behaviour —
`RemoteErrorMapper`, `Resource` returns, `CancellationException` rethrow — is unchanged.

---

## 2. Catalog sync

```http
GET /rest/v1/matns
  ?select=id,title,author,description,cover_image_ref,structure_kind,published,
          audio_completeness,verse_count,declared_size_bytes,revision,updated_at
  &published=eq.true
  &order=updated_at.desc
```

- Projection is Phase 11's `MATN_OVERVIEW_COLUMNS` **plus `revision` and `structure_kind`**. Adding
  those two is the only edit this phase makes to Phase 11 code. `verses` and `chapters` are absent,
  which is how FR-003 is enforced — no verse text or audio reference can travel on a sync.
- `published=eq.true` is belt-and-braces: `matns_read` already filters unpublished rows out for
  `anon`, so an omitted filter would return the same set. Stating it keeps the intent legible and
  keeps the query on `matns_published_updated_at_idx`.
- One request, no pagination (research D8, Clarification 2).

**Response handling**

| Condition | Result |
|---|---|
| 200, non-empty array | reconcile per data-model §4.2; `last_success_at_millis = now`; `last_attempt_failed = 0` |
| 200, empty array | same, yielding zero overviews — this is FR-044's "reachable but empty" |
| any non-2xx | `RemoteErrorMapper.mapHttpError`; stored catalog untouched; `last_attempt_failed = 1` (FR-007) |
| unparseable body | `RemoteError.Decode`; treated exactly as a failure — never as a partial catalog |
| transport throw | `RemoteErrorMapper.mapThrowable`; typically `RemoteError.Network` → FR-044's "no connectivity" |

A sync **never** performs a partial reconciliation. The full remote set is decoded into memory
first; only a complete success reaches the database, in one transaction.

---

## 3. Per-matn download

### 3.1 Content

```http
GET /rest/v1/matns?select=id,revision,structure_kind,chapters,verses&id=eq.{matnId}
```

The full row, including the `verses` and `chapters` `jsonb`. Decoded with Phase 11's existing
`MatnRow` / `toMatnRow()` — the student reads exactly the shape the teacher writes.

- An empty array means the matn was unpublished between browsing and downloading. Resolves to
  `NotDownloaded(SourceUnavailable)` (spec edge case), never to a partial matn.
- The `revision` returned here is the one recorded in `downloaded_matn`, **not** the one from the
  catalog sync — this is what makes "verse text and recitations come from a single version, never a
  mix of two" hold when a revision lands mid-download (spec edge case).

### 3.2 Per-verse audio

For each `VerseRow.audio?.fileRef` (already a full object path, `matns/{matnId}/verses/{...}.mp3`):

```http
GET /storage/v1/object/matn-content/{fileRef}
```

Permitted anonymously by `matn_content_published_read`, which resolves the matn id as
`(storage.foldername(name))[2]` and requires `m.published` — the same gate as the row itself.

| Condition | Result |
|---|---|
| 200 | bytes written to the staging directory |
| 404 | that verse is recorded with no recitation; **the download continues** (FR-023) |
| other non-2xx / throw | the whole download fails to `NotDownloaded(Remote(...))` (FR-017) |

A verse whose `audio` is `null` is treated identically to a 404 — readable, marked as having no
recitation. This is the audio-incomplete matn the spec's Assumptions permit to be published.

### 3.3 Cover images

```http
GET /storage/v1/object/matn-content/{cover_image_ref}
```

Fetched lazily on first render, cached at `{contentRoot}/covers/{matnId}`, **never counted in
storage usage** (Clarification 3, FR-012). Any failure is swallowed to a placeholder and never
propagates (FR-012, User Story 1 scenario 6).

---

## 4. What this contract does not permit

| Not permitted | Why |
|---|---|
| Any write to `public.matns` from a student client | `grant select` only for `anon` (`20260731000400`); every write policy requires `private.is_teacher()` |
| Reading an unpublished matn | `matns_read`'s `using (published or private.is_teacher())` |
| Reading a draft's cover or audio | `matn_content_published_read`'s `exists (… and m.published)` |
| Reaching `public.teachers` | no grant to `anon`; RLS enabled with no policies |
| Transferring verse text during a sync | the §2 projection omits `verses` |

The first four are enforced by the database, not by client code, so FR-004 holds even against a
modified client.

---

## 5. Test doubles

`MockEngine` (already a test dependency, `ktor-client-mock`) backs every contract test. The suite
must cover, at minimum:

- an anonymous request carries `apikey` and **no** `Authorization` header
- a 200 with three published overviews reconciles to three rows
- a 500 mid-sync leaves a previously-synced catalog byte-identical and sets `last_attempt_failed`
- a 200-empty is distinguishable from a transport failure in `CatalogSyncState`
- a download whose third verse audio 404s completes, with that verse marked recitation-less
- a download whose matn row returns `[]` resolves to `NotDownloaded(SourceUnavailable)`
