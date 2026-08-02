# Phase 1 Data Model: Student Remote Catalog & Download

**Feature**: `013-student-remote-catalog` | **Date**: 2026-08-02

Entities are grouped by lifetime, because that is the distinction Phase 13 introduces: an
**overview** persists from first sync regardless of download state, while **content** exists only
while a matn is downloaded, and **personal data** outlives both.

---

## 1. Local schema (SQLDelight, `Content.sq`)

### 1.1 `catalog_overview` — NEW, replaces `content_pack`

One row per matn the student has ever seen in a sync. Survives removal (FR-029) and withdrawal
(FR-009). Carries no verse text and no audio (FR-003).

```sql
CREATE TABLE catalog_overview (
    matn_id             TEXT NOT NULL PRIMARY KEY,
    title               TEXT NOT NULL,
    author              TEXT NOT NULL,
    description         TEXT NOT NULL,
    cover_image_ref     TEXT,
    structure_kind      TEXT NOT NULL,
    verse_count         INTEGER NOT NULL,
    download_size_bytes INTEGER NOT NULL,
    audio_completeness  TEXT NOT NULL,
    revision            INTEGER NOT NULL,
    withdrawn           INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX catalog_overview_by_title ON catalog_overview(title);
```

| Column | Source | Notes |
|---|---|---|
| `matn_id` | `matns.id` | the stable UUID; also the storage prefix (`matns/{matn_id}/`), so no `pack_id` survives |
| `download_size_bytes` | `matns.declared_size_bytes` | always present, so a size renders offline (spec Assumption) and FR-019 never waits on a lookup |
| `revision` | `matns.revision` | Phase 11's server-owned counter; drives the update flag (FR-010) |
| `withdrawn` | derived at sync | `1` when the matn vanished from the source **and** is downloaded (research D9). A `withdrawn` row is excluded from anything offering a new download and from search's catalog half, but still renders for its downloaded content |

**Deleted with `content_pack`**: `pack_id` (no longer a translation — research D6) and `is_starter`
(FR-040 — the concept leaves the product).

### 1.2 `catalog_sync_state` — NEW

Exactly one row (`id = 1`). Backs FR-044's three-way distinction and FR-006's staleness window.

```sql
CREATE TABLE catalog_sync_state (
    id                     INTEGER NOT NULL PRIMARY KEY CHECK (id = 1),
    last_success_at_millis INTEGER,
    last_attempt_failed    INTEGER NOT NULL DEFAULT 0
);
```

`last_success_at_millis IS NULL` is "never synced" — the only state that may show the
connect-to-browse prompt. A non-null value with zero overviews is "reachable but empty" (FR-044).

### 1.3 `downloaded_matn` — NEW

Present iff a matn's content is on the device. Holds the revision the content was built from, so
FR-010's flag is a comparison against `catalog_overview.revision`.

```sql
CREATE TABLE downloaded_matn (
    matn_id            TEXT NOT NULL PRIMARY KEY,
    revision           INTEGER NOT NULL,
    downloaded_at_millis INTEGER NOT NULL
);
```

> This row is bookkeeping, **not** the availability flag. FR-022 requires availability to come from
> what is actually on disk; a row whose `downloads/{matnId}/` directory is missing resolves to
> `NotDownloaded` and the row is reaped (FR-045).

### 1.4 `matn` / `chapter` / `verse` / `audio_asset` — lifetime change, no shape change

Columns are unchanged. What changes is **when rows exist**: previously seeded at first launch from
`bundledSampleMatns()`, now inserted by a download and deleted by a removal. The seeding call site
is deleted (FR-038).

### 1.5 Personal-data tables — **foreign keys dropped** (research D5)

| Table | Was | Becomes |
|---|---|---|
| `bookmark.verse_id` | `REFERENCES verse(id) ON DELETE CASCADE` | plain `TEXT NOT NULL UNIQUE`, indexed |
| `note.verse_id` | `REFERENCES verse(id) ON DELETE CASCADE` | plain `TEXT NOT NULL UNIQUE`, indexed |
| `memorization.verse_id` | `REFERENCES verse(id) ON DELETE CASCADE` | plain `TEXT NOT NULL UNIQUE`, indexed |
| `daily_practice.verse_id` | `REFERENCES verse(id) ON DELETE CASCADE` | plain `TEXT NOT NULL`, indexed |
| `matn_session.matn_id` | `REFERENCES matn(id) ON DELETE CASCADE` | plain `TEXT NOT NULL PRIMARY KEY` |

**Why**: removal now deletes `verse` rows, and every cascade above would take the student's
bookmarks, notes, memorized marks, practice history and resume position with it — violating FR-026,
FR-029, SC-010 and Principle VI. The UUIDs are stable and teacher-owned, so a re-download re-resolves
every orphan automatically (spec edge case: "reattaches if that matn is later downloaded").

**Consequences for queries** (each is a task, not an afterthought):
- Reads that surface personal data with verse context become `INNER JOIN verse` — orphans are hidden
  without being deleted.
- Counts shown outside a matn (bookmark totals, memorized totals) must count **resolvable** rows.
- `daily_practice` aggregates must tolerate an absent verse row.
- Orphans are never garbage-collected; at ~50 متون (research D8) the cost is negligible and it is
  what FR-029 buys.

### 1.6 Migration `5.sqm`

The repo already carries `1.sqm`–`4.sqm`, so this is **`5.sqm`** (named for the version it migrates
*from*, per the SQLDelight convention the existing files document) and it bumps
`ContentDatabase.Schema.version` to 6. Every `CREATE TABLE` text in it must stay byte-identical to
the matching definition in `Content.sq`, exactly as `3.sqm`/`4.sqm` require.

SQLite cannot drop a foreign key in place, so each affected table is recreated:
`PRAGMA foreign_keys=OFF` → `CREATE …_new` → `INSERT … SELECT` → `DROP` → `ALTER … RENAME` →
`PRAGMA foreign_keys=ON`, then `DROP TABLE content_pack`, then the three new tables. Content tables
(`matn`, `chapter`, `verse`, `audio_asset`) are **emptied**, not migrated: the spec's Assumption
records that the app has not shipped, so after upgrade nothing reports as downloaded and the student
downloads what they want. Personal-data rows are carried across verbatim.

---

## 2. Domain models

### 2.1 `CatalogOverview` — NEW

```kotlin
data class CatalogOverview(
    val matnId: String,
    val title: String,
    val author: String,
    val description: String,
    val coverImageRef: String?,
    val structureKind: StructureKind,
    val verseCount: Int,
    val downloadSizeBytes: Long,
    val audioCompleteness: AudioCompleteness,
    val revision: Long,
    val withdrawn: Boolean,
)
```

Reuses `StructureKind` and `AudioCompleteness` from Phase 11's domain rather than redeclaring them —
the same types the teacher publishes are the ones the student reads.

### 2.2 `ContentAvailability` — one state added, one removed

```kotlin
sealed interface ContentAvailability {
    data class NotDownloaded(val failure: DeliveryFailure? = null) : ContentAvailability
    data object Queued : ContentAvailability                        // NEW — Clarification 4
    data class Downloading(val progress: DeliveryProgress) : ContentAvailability
    data class Downloaded(val occupiedBytes: Long) : ContentAvailability
}
```

`Queued` carries no progress because nothing has transferred; that is precisely what distinguishes
it from `Downloading(0f)` on screen (FR-015). The `Installed`/`Installing`/`NotInstalled` names are
renamed to the spec's vocabulary; `Installed.occupiedBytes`'s three-step priority collapses to one
step now that the starter branch is gone (FR-040): **the measured size of `downloads/{matnId}/`**.

### 2.3 `DeliveryFailure` — one value removed, one added

| Value | Change |
|---|---|
| `NoConnectivity` | kept (FR-020) |
| `InsufficientStorage(requiredBytes, availableBytes)` | kept (FR-019) |
| `Cancelled` | kept (FR-017) |
| `Evicted` | **removed** — an OS-evicted asset pack is not a thing that can happen any more. Files deleted outside the app surface as plain `NotDownloaded` (FR-045), which is what `Evicted` already rendered as |
| `Unknown(code: Int)` | **replaced** by `Remote(error: RemoteError)` — Phase 11's mapped error carries a meaning FR-043 can render, where a raw platform int could not |
| `SourceUnavailable` | **new** — the matn was withdrawn or its objects are missing mid-transfer (spec edge case) |

### 2.4 `DeliveryProgress` — unchanged shape, narrowed phases

`DeliveryPhase` loses `WAITING_FOR_NETWORK_POLICY` and `REQUIRES_CONFIRMATION`: both described Play
Asset Delivery parking states with no HTTP equivalent. `PENDING` and `TRANSFERRING` remain.

### 2.5 `StorageUsage` / `MatnStorageEntry` — starter fields removed

`MatnStorageEntry.isStarter` is deleted (FR-031: no item may be exempt), and with it
`StorageUsage.onDemandUsedBytes` — with nothing exempt, it was always equal to `totalUsedBytes`.
`bytes` counts verse text and audio only; cached covers are excluded by construction, since they
live outside `downloads/` (research D11, Clarification 3).

### 2.6 `CatalogSyncState` — NEW

```kotlin
data class CatalogSyncState(
    val lastSuccessAtMillis: Long?,
    val lastAttemptFailed: Boolean,
) {
    val hasEverSynced: Boolean get() = lastSuccessAtMillis != null
}
```

### 2.7 `DeliveryError` — starter value removed

`StarterMatnNotRemovable` is deleted (FR-040/FR-031). `ContentNotInstalled` is renamed
`ContentNotDownloaded` and keeps its role as FR-021's play-a-missing-matn error.

---

## 3. Repository & use-case surface

### 3.1 `CatalogRepository` (student-side) — NEW

Named `StudentCatalogRepository` to avoid colliding with Phase 11's teacher-side
`domain/catalog/CatalogRepository`.

| Member | Purpose |
|---|---|
| `observeCatalog(): Flow<List<CatalogOverview>>` | the browsable library, downloaded or not (FR-005) |
| `observeSyncState(): Flow<CatalogSyncState>` | drives FR-044's three-way distinction |
| `sync(force: Boolean): Resource<Unit>` | `force=false` honours the 1-hour window (FR-006); `force=true` is explicit refresh |
| `overview(matnId): CatalogOverview?` | details screen, offline-safe |

### 3.2 `ContentPackRepository` → `DownloadedContentRepository`

The abstraction survives; the **name does not**. This phase deletes the `content_pack` table and the
`pack_id` concept outright (§1.1, research D6), so "Pack" would outlive its referent. Renamed to
`DownloadedContentRepository` (impl: `DownloadedContentRepositoryImpl`) — a mechanical rename across
19 files, enumerated in `tasks.md` T058.

> The roadmap's Phase 13 entry anticipated keeping the name on the grounds that "`packId` simply
> becomes a Supabase Storage path prefix". That premise does not survive research D6: the prefix
> `matns/{matnId}/` is derivable from the matn id, so there is no translation left to own.

Retained members: `observeAvailability`, `observeLibraryAvailability`, `observeStorageUsage`,
`install` (renamed `download`), `cancel`, `remove`, `removeAll`, `packRootFor` (renamed
`contentRootFor`).

**Deleted**: `isStarterMatn()` (FR-040), `declaredSize()` / `bestKnownSize()` (the overview always
carries the size — spec Assumption — so the two-source dance goes away), and `removeAll()`'s
starter-sparing branch (FR-031).

### 3.3 Use cases

| Use case | Change |
|---|---|
| `SyncCatalogUseCase` | **new** — staleness window, reconciliation, FR-007 failure semantics |
| `ObserveCatalogUseCase` | **new** — merges overviews with availability for the library grid |
| `DownloadMatnUseCase` | renamed from `InstallMatnContentUseCase`; keeps the free-space (FR-019) and connectivity (FR-020) preconditions, drops the starter guard |
| `UpdateMatnUseCase` | **new** — FR-011; delete-and-refetch at the newer revision |
| `RemoveMatnContentUseCase` | drops the starter guard; now also deletes verse rows |
| `RemoveAllContentUseCase` | drops starter-sparing (FR-031) |
| `ObserveContentAvailabilityUseCase` | unchanged contract, new states |
| `ObserveStorageUsageUseCase` | unchanged contract, narrowed model (2.5) |
| `SearchLibraryUseCase` | extended to search `catalog_overview` titles/authors alongside verse text (FR-033/FR-034), tagging result origin (FR-035/FR-036) |
| `GetMatnDetailsUseCase` | reads the overview when not downloaded (FR-007 of User Story 1, scenario 7), full content when downloaded |

---

## 4. State transitions

### 4.1 Per-matn download state

```
                 ┌───────────────────────── cancel ─────────────────────────┐
                 │                                                          │
NotDownloaded ──download──▶ Queued ──slot free──▶ Downloading ──commit──▶ Downloaded
      ▲                        │                      │                      │
      │                        └──── cancel ──────────┤                      │
      │                                               │                      │
      └──────── fail / cancel / files vanish ─────────┴───── remove ─────────┘
```

- Entry to `Queued` requires the FR-019 space check and FR-020 connectivity check to pass first, so a
  doomed download never occupies the queue.
- `Downloading → NotDownloaded` always releases the temp directory (FR-017), and always carries a
  `DeliveryFailure` the UI can act on (FR-043).
- `Downloaded → NotDownloaded` also occurs without any student action when the directory disappears
  (FR-045); that transition carries **no** failure, because the student did nothing wrong.
- There is no arc into a partial state. FR-016.

### 4.2 Catalog reconciliation, per matn id

| Remote | Local overview | Downloaded | Outcome |
|---|---|---|---|
| present | absent | — | insert (FR-008) |
| present | present, same `revision` | — | no-op |
| present | present, newer `revision` | no | update overview in place |
| present | present, newer `revision` | yes | update overview; matn now flags an available update (FR-010), existing copy untouched (FR-011) |
| absent | present | no | delete overview (FR-008) |
| absent | present | yes | mark `withdrawn = 1`; content stays fully usable (FR-009) |

A sync that fails at any point performs **none** of the above (FR-007, research D8).

---

## 5. Validation rules

| Rule | Source | Enforced at |
|---|---|---|
| A sync response missing any overview column is a decode failure, not a partial catalog | FR-007 | `MatnRow` decode → `RemoteError.Decode` |
| `download_size_bytes` must be > 0 before the free-space check is meaningful; 0 means "unknown", and the download proceeds without the FR-019 guard | FR-019 | `DownloadMatnUseCase` |
| A verse whose `audio` is null downloads as readable-without-recitation, never a failure | FR-023 | download commit |
| A verse whose audio object 404s is treated identically to a null `audio` | FR-023, edge case | download commit |
| A cover that fails to fetch never fails anything else | FR-012 | cover cache, swallowed |
| An unpublished matn cannot reach the client at all | FR-004 | RLS `matns_read` (server-side — research D1) |
| Content expressed as offsets into a shared recording is not representable | FR-024, Constitution | `audio_asset` is per-verse by schema; no offset column exists |
