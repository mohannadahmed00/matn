# Phase 0 Research: Student Remote Catalog & Download

**Feature**: `013-student-remote-catalog` | **Date**: 2026-08-02

Every decision below resolves a `NEEDS CLARIFICATION` raised by the plan's Technical Context, or
records a non-obvious choice the design depends on. Five product-level ambiguities were already
settled in `spec.md` § Clarifications (2026-08-02) and are not re-litigated here; they are cited
where they constrain a decision.

---

## D1 — The student's read path needs no credential, and the server already allows it

**Decision**: Students read Postgres and Storage as the **`anon` role**, sending only the project
`apikey` header and no `Authorization` bearer token. **No new migration is required.**

**Rationale**: This was the outcome of Clarification 1 (fully public read), and Phase 11's schema
already implements it exactly:

| Existing object | Effect for an anonymous student |
|---|---|
| `matns_read` policy (`20260731000000`, amended `…000200`) | `for select to anon, authenticated using (published or private.is_teacher())` — an anonymous `select` on the whole table returns published rows and nothing else |
| `grant select on public.matns to anon` (`20260731000400`) | the GRANT layer permits the read before RLS filters it |
| `matn_content_published_read` (`20260731000100`) | `for select to anon` on `storage.objects` where `(storage.foldername(name))[2]` names a published matn |
| `matns_published_updated_at_idx` | the `(published, updated_at desc)` index the sync query wants already exists |

Both the row filter and the object filter key off the same `published` flag, so FR-004 ("unpublished
must be absent from every surface") is enforced by the database rather than by client-side
filtering — a student build physically cannot see a draft. FR-009 (a withdrawn matn stays usable)
is unaffected because withdrawal only removes the row from *future* reads; bytes already on the
device are untouched.

**Alternatives considered**:
- *A student-side anonymous sign-in* (Supabase `signInAnonymously`) — rejected: it mints and stores
  a per-device credential, which FR-027 now explicitly forbids ("no student-side token"), and it
  buys nothing since the RLS predicate is `published`, not `auth.uid()`.
- *Signed URLs per download* — rejected by Clarification 1; it also requires a token-issuing
  identity, reintroducing the credential FR-027 removed.

**Consequence for the client**: see D2.

---

## D2 — `PostgrestClient` and `StorageRestClient` must admit a token-less call path

**Decision**: Make the bearer token **optional** in both clients by injecting a
`AccessTokenProvider` (a `fun interface` returning `Resource<String?>`) in place of the concrete
`TokenRefresher`. The teacher app binds the existing `TokenRefresher`; the student clients bind an
`AnonymousAccessTokenProvider` that returns `Resource.Success(null)`. When the token is `null` the
request sends `apikey` only and omits `Authorization` entirely.

**Rationale**: This is the single largest *unexpected* finding of Phase 0. Both clients currently
open every method with

```kotlin
val tokenResult = tokenRefresher.currentAccessToken()
if (tokenResult is Resource.Failure) return Resource.Failure(tokenResult.error)
```

so with no signed-in teacher, **every student read fails before a request is made** — a failure that
looks like a network error but is a missing credential. The server is ready (D1); the client is not.
Injecting the provider keeps the change to the two `standardHeaders`/header blocks and leaves the
teacher path byte-identical.

Sending `Authorization: Bearer <anonKey>` would also work against Supabase (it treats the anon key
as an `anon`-role JWT), but omitting the header is the honest expression of "this caller has no
identity" and keeps the anon key in exactly one header rather than two.

**Alternatives considered**:
- *A separate `AnonymousPostgrestClient`* — rejected: duplicates the pagination, error mapping, and
  `Resource` plumbing that Principle III requires be shared.
- *Defaulting `TokenRefresher` to return a null token in student builds* — rejected: it overloads a
  type whose name and contract promise a refresh, and it would silently mask a genuine auth failure
  in the teacher app.

---

## D3 — One `RemoteContentDeliveryEngine` in `commonMain`, three platform engines deleted

**Decision**: Implement a single `RemoteContentDeliveryEngine : ContentDeliveryEngine` in
`commonMain`. Delete `PlayAssetDeliveryEngine` (androidMain), `OnDemandResourcesEngine` (iosMain),
and `DesktopContentDeliveryEngine` (jvmMain), the `packs/matn_structured_sample` module and its
`settings.gradle.kts` include, the `assetPacks` block in `androidApp/build.gradle.kts`, the
`play-asset-delivery-ktx` dependency and its `playAssetDelivery` version entry.

**Rationale**: FR-041 requires one common acquisition mechanism, and the constitution's Principle IV
prohibits duplicating logic across platform source sets when `commonMain` can hold it. Over HTTP
nothing is platform-specific: Ktor's CIO engine is published for JVM, Android and every iOS variant
(already relied on by Phase 11, `HttpClientFactory`). The `ContentDeliveryEngine` interface survives
unchanged in shape — this is precisely the seam swap Principle I's rationale names by example.

`DesktopContentDeliveryEngine` deserves specific mention: it reports `isInstalled = true` for every
pack unconditionally. Under a remote model that is not a simplification but a lie, and desktop would
claim playability for content that was never fetched (violating SC-006). It must go, not be adapted.

**Alternatives considered**:
- *Keep the platform engines and add a fourth remote one* — rejected outright by FR-039/FR-041 and
  by the constitution's deferred TODO, which requires Phase 13 to **delete, not bypass**, the
  superseded stack.

---

## D4 — File I/O lives in `commonMain` via `kotlinx-io`, not in three `actual`s

**Decision**: Add `org.jetbrains.kotlinx:kotlinx-io-core` as an explicit `commonMain` dependency and
write downloaded verse audio through it. `DeviceStorage` keeps its two existing methods
(`freeSpaceBytes`, `sizeOfDirectory`) and gains one more — `contentRootPath(): String` — because the
per-platform *location* of the app's content directory is genuinely platform knowledge.

**Rationale**: The download path needs create-directory, write-bytes, delete-tree and exists. Adding
those to `DeviceStorage` would mean writing the same four operations three times in
`androidMain`/`iosMain`/`jvmMain`, which Principle IV names as prohibited ("if both need it, it
belongs in `commonMain`"). `kotlinx-io` is already on the classpath transitively — Ktor 3.2.3's I/O
layer is built on it — so this declares an existing artifact rather than pulling in a new one, and
it is a JetBrains-maintained KMP library covering all five targets.

Splitting it this way keeps each `actual` at the "no business logic" bar the constitution sets: the
platform says *where* the directory is; `commonMain` decides *what* goes in it.

**Alternatives considered**:
- *Okio* — rejected: functionally equivalent for this use, but a genuinely new third-party
  dependency where a transitively-present JetBrains one does the same job.
- *Expand `DeviceStorage` with write methods* — rejected on Principle IV as above; recorded in the
  plan's Complexity Tracking as the rejected simpler-looking alternative.

---

## D5 — Removal must not cascade into personal data: the schema requires a migration

**Decision**: Add SQLDelight migration `5.sqm` (the repo already carries `1.sqm`–`4.sqm`; the file
is named for the version it migrates *from*, so this one takes the schema to v6) that **drops the
`ON DELETE CASCADE` from
`verse(id)` to `bookmark`, `note`, `memorization` and `daily_practice`, and from `matn(id)` to
`matn_session`**, replacing the foreign keys with plain indexed columns holding the stable verse /
matn UUID. Personal-data rows become orphan-tolerant.

**Rationale**: This is the second unexpected finding, and it is a hard correctness blocker. Today:

```sql
bookmark.verse_id      REFERENCES verse(id) ON DELETE CASCADE
note.verse_id          REFERENCES verse(id) ON DELETE CASCADE
memorization.verse_id  REFERENCES verse(id) ON DELETE CASCADE
daily_practice.verse_id REFERENCES verse(id) ON DELETE CASCADE
matn_session.matn_id   REFERENCES matn(id)  ON DELETE CASCADE
```

Under Phase 8 this was safe *because removal only ever deleted audio files* — the `verse` rows were
seeded into the binary and never deleted. Phase 8 knew it was relying on this: the existing
`RemovalPreservesUserDataTest` states the guarantee as **"structurally guaranteed … no edge runs
from `content_pack` to any personal-data table"**. Phase 13 removes exactly that structure. FR-013
brings verse text down on download, so FR-028's removal deletes verse rows, and every bookmark,
note, memorized mark, practice record and resume position for that matn would cascade away with
them.

That test is also the tripwire: it is written against `ContentSeedLoaderImpl`, which this phase
deletes, so it cannot silently keep passing on a stale premise — it must be rewritten against the
download path, and its assertions are exactly SC-010's. That directly violates
FR-029, FR-026, SC-010 and the edge case "personal data keyed to its verses is preserved and
reattaches if that matn is later downloaded", and it would also silently break Principle VI's
"no personal data may be lost when content is removed".

Dropping the FK is the correct fix rather than a workaround: Principle VI already mandates that
every entity carry a stable UUID identity "independent of display order or local row IDs", and
personal data surviving content is exactly the sync-safe shape that principle is designed for. The
UUIDs come from the teacher's published rows, so they are stable across removal, re-download, and
revision (spec Assumption: "verse identity is stable across revisions").

**Consequences the design must carry**:
- Every read that joins personal data to verses becomes an `INNER JOIN` that naturally hides orphans
  — a bookmark whose matn is not downloaded simply does not render, without a delete.
- Bookmark/note/memorization counts shown outside a matn must count *resolvable* rows only.
- `daily_practice` aggregates must tolerate a verse row that is absent.
- Orphan rows are never garbage-collected. At the stated scale (D8) that is a negligible cost and
  the price of FR-029.

**Alternatives considered**:
- *Keep verse rows forever, delete only audio* — rejected: it makes FR-028's reclaimed-space figure
  dishonest (verse text is part of what FR-013 downloads), leaves withdrawn-and-removed متون
  half-present in local queries, and contradicts FR-022's "determined from what is actually present".
- *Copy personal data to a shadow table before removal and restore on re-download* — rejected: two
  sources of truth for the same fact, and a restore step that can fail is exactly the fragility
  SC-010's 100% target forbids.

---

## D6 — Catalog overviews get their own table; `content_pack` is retired

**Decision**: Replace `content_pack` with a new `catalog_overview` table holding every published
matn's overview (FR-002) plus sync bookkeeping. `matn`/`chapter`/`verse`/`audio_asset` rows exist
**only for downloaded متون**. Drop the `is_starter` column with the table.

**Rationale**: The two lifetimes are now genuinely different — an overview persists from first sync
regardless of download state (FR-005), while content rows come and go with FR-013/FR-028. Keeping
them in one table would require a nullable half and would make "is this downloaded?" a column, which
FR-022 forbids (state must be derived from what is present, not a stored flag).

`content_pack`'s two survivors move: `declared_size_bytes` becomes `catalog_overview.download_size_bytes`
(now sourced from the teacher's published `declared_size_bytes` rather than authored locally), and
`pack_id` disappears entirely — the storage prefix `matns/{matnId}/` is derivable from the matn id,
so the `matnId ↔ packId` translation the roadmap expected to keep is simply no longer a translation.

**Alternatives considered**:
- *Keep `content_pack` and add columns* — rejected: the table's name, its `pack_id UNIQUE`, and its
  `is_starter` flag are all artifacts of the retired model; renaming a table is cheaper than
  explaining it forever.

---

## D7 — Download is a staged commit into a temp directory, then an atomic move

**Decision**: A download writes to `{contentRoot}/downloads/.tmp-{matnId}/`, and only when every
verse row and every available audio file has landed does it (a) move the directory to
`{contentRoot}/downloads/{matnId}/` and (b) insert the `matn`/`chapter`/`verse`/`audio_asset` rows in
one SQLDelight transaction. Any failure deletes the temp directory and writes nothing.

**Rationale**: FR-016 makes a download atomic from the student's point of view and FR-017 requires a
failure to release consumed space; SC-006 puts the target at 100% with zero resolving to a state
that claims playability. Deriving availability from the presence of `{contentRoot}/downloads/{matnId}/`
means a half-written transfer is invisible by construction — there is no partial state to
mis-report, which is also what makes FR-045 (files deleted outside the app) a free property rather
than a reconciliation job.

Ordering matters: files first, database last. A crash between the two leaves an orphan directory
with no rows, which reads as not-downloaded and is cleaned on next start; the reverse order would
leave rows pointing at absent audio, which reads as playable and is not.

**Alternatives considered**:
- *Resumable/partial downloads* — rejected: FR-017 explicitly requires released space and a return
  to not-downloaded, and the spec's Out of Scope forbids per-verse granularity. At tens of MB
  (D8) a restart is cheap.
- *Stream directly into the final directory* — rejected: an interrupted transfer would leave the
  path present, which is the exact "claims to be playable" state SC-006 counts as a failure.

---

## D8 — A whole-catalog fetch in one request; no pagination

**Decision**: Sync is a single
`GET /rest/v1/matns?select={overview columns}&published=eq.true&order=updated_at.desc`, using
Phase 11's existing `MATN_OVERVIEW_COLUMNS` projection unchanged. The response replaces the local
overview set transactionally.

**Rationale**: Clarification 2 fixed the ceiling at ~50 متون. `MATN_OVERVIEW_COLUMNS` already
excludes the `verses` and `chapters` `jsonb` blobs, so an overview row is a few hundred bytes and the
whole catalog is well under 50 KB — comfortably inside SC-001's 3 seconds, and satisfying FR-003
(a sync must not transfer verse text or audio) by projection rather than by convention.

Transactional replacement is what makes FR-007 hold: the new set is computed in memory from a
complete, successful response, then swapped in one transaction. A partial or failed response never
touches the stored catalog, so "a partial sync never replaces a good catalog with a truncated one"
is structural.

**Alternatives considered**:
- *Delta sync on `updated_at`* — rejected as premature at this scale: it cannot express deletion
  (an unpublished matn stops matching the filter rather than appearing as a change), so it would
  need a separate reconciliation pass to satisfy FR-008 anyway.

---

## D9 — Reconciliation, revision tracking, and the update flag

**Decision**: After a successful sync, reconcile by matn id: ids present remotely are upserted;
local overviews whose id is absent are **deleted if not downloaded** and **marked withdrawn if
downloaded**. Downloaded متون store the `revision` they were built from; the update flag is
`overview.revision > downloaded.revision`.

**Rationale**: FR-008 and FR-009 together mean withdrawal has two different outcomes depending on
download state, so the reconciliation must be state-aware rather than a blind replace. Keeping a
`withdrawn` overview row is what lets a downloaded-but-unpublished matn keep rendering its title,
author and cover offline (User Story 4, scenario 3) while being excluded from anything offering a
new download.

`revision` is Phase 11's server-owned monotonic counter, bumped by the `matns_bump_revision`
trigger and impossible for a client to forge. It is a better update signal than `updated_at`
because it is integral and monotone — no timestamp parsing, no clock skew. It is **not** currently
in `MATN_OVERVIEW_COLUMNS`; adding it there is a one-line change and the only schema-adjacent edit
Phase 13 needs on the Phase 11 side.

FR-011's preservation-on-update falls out of D5 for free: an update deletes and re-inserts verse
rows, and personal data no longer cascades, so marks for surviving verse UUIDs simply re-resolve.

---

## D10 — A one-at-a-time download queue owned by the repository

**Decision**: `DownloadedContentRepository` (renamed from `ContentPackRepository` — D6) owns a
`Mutex`-guarded FIFO queue keyed by matn id, running
exactly one transfer at a time (Clarification 4). Queue membership is in-memory only; a process
death resolves every queued and in-flight matn to not-downloaded, which is already the correct
FR-017 outcome.

**Rationale**: The repository already serialises per-pack operations ("serialised per `packId` so a
remove racing an in-flight install is deterministic (removal wins)"), so the queue extends an
existing discipline rather than inventing one. Keeping it in the data layer means the new
`ContentAvailability.Queued` state is derived and observed exactly like the others, and the
ViewModels need no queue awareness.

FR-018 requires the queue to keep advancing while backgrounded. On Android the transfer runs in the
shared application-scope coroutine that already survives Activity destruction; a killed process is
FR-017's territory, not FR-018's.

**Alternatives considered**:
- *Persist the queue* — rejected: it would resurrect transfers the student cannot see pending, and
  FR-022 forbids trusting a stored flag over what is on disk.

---

## D11 — Cover images: a separate, never-counted cache

**Decision**: Covers are fetched lazily on first render into `{contentRoot}/covers/{matnId}`, keyed
by the overview's `cover_image_ref`, and are excluded from `StorageUsage` entirely (Clarification 3,
FR-012, FR-030). A fetch failure is swallowed to a placeholder.

**Rationale**: Counting them would put bytes into a per-matn storage row that removal is forbidden
to reclaim, breaking SC-009's "0 bytes" claim. Storing them outside `downloads/` means the removal
path — "delete `downloads/{matnId}/`" — cannot touch them by accident, so the exclusion is
structural rather than a filter someone must remember to apply.

---

## D12 — Search: two sources, one result list

**Decision**: `SearchLibraryUseCase` queries `catalog_overview` for title/author matches (whole
catalog) and `verse` for text matches (downloaded only, by construction — undownloaded متون have no
verse rows), merges, and tags each result with its origin so the UI can route per FR-035 and state
the scope per FR-036.

**Rationale**: FR-034's "downloaded only" needs no predicate at all once D6 splits the tables —
verse rows exist exactly for downloaded متون, so the existing verse query is already correctly
scoped. This is the narrowest possible change to shipped Phase 6 behaviour, which is what the
roadmap's "one behavioural change to spec, not discover" asks for. Diacritic-insensitive matching
(FR-037) is untouched; the catalog query reuses the same normalisation the verse query uses.

---

## Deferred, deliberately

- **Observability** — flagged as Deferred by `/speckit-clarify` and still out of scope. The app
  emits no telemetry today; adding it is neither required by a functional requirement nor blocked by
  anything here.
- **Cover cache eviction** — covers are small and bounded by catalog size (D8). A cache with no
  eviction policy is acceptable at ~50 entries; revisit if the catalog ceiling moves.
