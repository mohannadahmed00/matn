# Implementation Plan: Student Remote Catalog & Download

**Branch**: `013-student-remote-catalog` | **Date**: 2026-08-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-student-remote-catalog/spec.md`

## Summary

Replace the student app's content channel. Today content arrives compiled into the binary and
through store-delivered asset packs; after this phase it arrives only from what the teacher
published, over anonymous HTTP.

Two halves, as the spec frames them. **Added**: a catalog sync that pulls published *overviews* into
SQLDelight, a per-matn download that stages verse text and per-verse audio onto disk and commits
them atomically, a one-at-a-time download queue, and honest empty/offline states. **Retired**: the
`packs/` module, `play-asset-delivery-ktx`, the three platform `ContentDeliveryEngine`
implementations, the iOS ODR tags, `bundledSampleMatns()`, and the entire `isStarter` path.

Phase 0 turned up two things the spec could not have known, and they drive the shape of the work:

1. **The server is already correct; the client is not.** Phase 11's RLS policies and grants already
   permit exactly the anonymous published-only read this phase needs — no migration required. But
   `PostgrestClient` and `StorageRestClient` both demand a bearer token before issuing any request,
   so with no signed-in teacher every student read fails before it starts. Fixed by making the token
   optional (research D2, student-read-contract §1.1).
2. **Removal would silently destroy personal data.** `bookmark`, `note`, `memorization`,
   `daily_practice` and `matn_session` all hang off `verse`/`matn` with `ON DELETE CASCADE`. That was
   safe while verse rows were bundled and never deleted; once a download owns them, FR-028's removal
   cascades the student's entire history away — breaking FR-026, FR-029, SC-010 and Principle VI.
   Fixed by a SQLDelight migration that drops those foreign keys (research D5, data-model §1.5).

Neither is optional, and the second is the single highest-risk item in the phase.

## Technical Context

**Language/Version**: Kotlin 2.4.10, Kotlin Multiplatform

**Primary Dependencies**: Ktor 3.2.3 (CIO, existing — Phase 11), SQLDelight 2.0.2, Compose
Multiplatform 1.11.1, Koin 4.2.1 (annotations), kotlinx-serialization 1.7.3, kotlinx-coroutines
1.11.0. **One addition**: `kotlinx-io-core` for `commonMain` file I/O — already on the classpath
transitively via Ktor 3, declared explicitly (research D4, Complexity Tracking below).

**Storage**: SQLDelight (local, source of truth) + Supabase Postgres via PostgREST and Supabase
Storage via REST (remote, read-only for students)

**Testing**: `commonTest` with `kotlin.test`, Ktor `MockEngine` for the read surface, existing
`FakeContentDeliveryEngine` / `FakeDeviceStorage` for the delivery seam. No device or emulator
required for any domain or data test (Principle V).

**Target Platform**: `androidApp` (minSdk 24), `iosApp`, `desktopApp` (JVM) — the three student
clients. `:teacherApp` is touched only where it shares the two REST clients.

**Project Type**: Kotlin Multiplatform application; `shared` holds domain + data + presentation, one
module per client.

**Performance Goals**: catalog visible ≤3 s on a fresh install (SC-001); first verse audible ≤3 min
from fresh install (SC-003); search results <1 s offline (SC-011); storage figures within ±5% of
actual (SC-008).

**Constraints**: zero network requests for any study feature on downloaded content (SC-004); no
student credential of any kind (FR-027); no content in any client binary (FR-038, SC-005); download
atomic per matn, never partially available (FR-016, SC-006).

**Scale/Scope**: ≤~50 متون, each up to a few hundred verses and tens of MB (spec Clarification 2).
Single whole-catalog fetch; no pagination, no delta sync (research D8).

**Unknowns**: none outstanding. Five product ambiguities were resolved in `spec.md` §Clarifications
(2026-08-02); the technical decisions they left open are recorded in
[research.md](./research.md) D1–D12.

## Constitution Check

*GATE: evaluated against constitution v2.0.0 before Phase 0, re-evaluated after Phase 1 design.*

| Principle | Assessment | Verdict |
|---|---|---|
| **I. Clean Architecture & Layer Boundaries** | This phase is the worked example in the principle's own rationale — the data layer swaps its content source behind the unchanged `ContentDeliveryEngine` seam. The new `StudentCatalogRepository` is a domain interface with a data-layer impl; use cases stay the only path from presentation. | ✅ PASS |
| **II. MVVM Presentation** | New states (`Queued`, catalog-empty, never-synced, update-available) land on existing screens' immutable UI-state classes. Each new or changed state-rendering composable needs a `@Preview` for its key states — a blocking review item, carried into tasks. | ✅ PASS (with explicit preview obligation) |
| **III. DRY via Base Abstractions** | Phase 11's `PostgrestClient`/`StorageRestClient` are extended, not forked, for the anonymous path (research D2). The paginated-listing helper stays shared. `Resource`/`AppError` used throughout. | ✅ PASS |
| **IV. Shared-First Multiplatform** | Strengthened, not strained: three platform engines collapse into one `commonMain` implementation (FR-041), and file I/O goes to `commonMain` via kotlinx-io rather than being written three times. `DeviceStorage` keeps only genuinely platform facts. | ✅ PASS — net reduction in `actual` code |
| **V. Test-First & Testable Design** | Every new behaviour is device-free testable: `MockEngine` for reads, fakes for the delivery seam and storage. The migration in particular needs a test that personal data survives a removal (SC-010) — the failure it guards against is silent. | ✅ PASS |
| **VI. Offline-First & Future-Proof Data** | This phase *implements* amended VI. Acquisition requires connectivity; everything downloaded works offline forever (FR-025, SC-004). The FK removal (research D5) is Principle VI's stable-UUID mandate being cashed in — personal data keyed to teacher-owned UUIDs now genuinely outlives content, which is also what makes later cross-device sync possible. | ✅ PASS — moves *toward* the principle |
| **VII. Experience Fidelity** | Gapless playback, RTL, dark mode and a11y are untouched for downloaded content. New surfaces (queued, offline, empty, update-available) must meet the same contrast and RTL bar and carry Arabic copy. | ✅ PASS (carried as a review item) |
| **VIII. Design Fidelity & Reusable Composables** | Library, details, settings and search all already have Stitch designs; new states must be fetched from Stitch before implementation, not invented. `ContentActionButton` and `StorageUsageRow` are edited in place (losing their `isStarter` parameter) rather than duplicated. Tokens only — no literals. | ⚠️ CONDITIONAL — see below |

**Condition on VIII**: the catalog-empty, never-synced, queued and update-available states may not
have existing Stitch screens. Principle VIII requires the design be fetched before implementing a
screen that has one, and requires conflicts be raised rather than silently implemented. The tasks
phase must, for each new state, either cite the Stitch screen or record that none exists and the
state reuses an existing token-driven pattern. This is a gate on the UI tasks, not on this plan.

**No violations requiring justification.** One dependency addition is recorded in Complexity
Tracking per the constitution's "Adding a new third-party dependency requires justification against
a simpler alternative".

### Post-Design Re-evaluation

Re-checked after the Phase 1 artifacts. No principle moved to FAIL. Two observations worth
recording:

- The FK removal (data-model §1.5) means personal-data reads become `INNER JOIN`s against content
  tables. That is a correctness obligation spread across several existing queries, not a new
  abstraction — it does not create duplication, but it does need per-query test coverage
  (Principle V), which the tasks phase must enumerate rather than assume.
- Deleting `ContentSeedLoader` removes the only path that ever wrote content rows outside a
  download. Nothing else depended on it, so no seam is lost.

## Project Structure

### Documentation (this feature)

```text
specs/013-student-remote-catalog/
├── plan.md                              # This file
├── research.md                          # Phase 0 — D1–D12
├── data-model.md                        # Phase 1 — schema, domain models, transitions
├── quickstart.md                        # Phase 1 — end-to-end validation guide
├── contracts/
│   ├── student-read-contract.md         # anonymous PostgREST + Storage surface
│   ├── delivery-contract.md             # engine, repository, on-device layout
│   └── retirement-contract.md           # the deletion inventory (FR-038–FR-042)
├── checklists/requirements.md           # spec quality gate (16/16)
├── spec.md                              # /speckit-specify + /speckit-clarify output
└── tasks.md                             # /speckit-tasks output — NOT created here
```

### Source Code (repository root)

```text
shared/src/commonMain/kotlin/com/giraffe/matn/
├── domain/
│   ├── catalog/            StudentCatalogRepository, CatalogOverview, CatalogSyncState   [NEW]
│   ├── delivery/           ContentDeliveryEngine (kept), DeviceStorage (+contentRootPath)
│   ├── model/              ContentAvailability (+Queued), DeliveryFailure, StorageUsage  [EDIT]
│   ├── error/              DeliveryError (−StarterMatnNotRemovable)                      [EDIT]
│   └── usecase/            SyncCatalogUseCase, ObserveCatalogUseCase, UpdateMatnUseCase  [NEW]
│                           DownloadMatnUseCase, Remove*, SearchLibraryUseCase            [EDIT]
├── data/
│   ├── delivery/           RemoteContentDeliveryEngine, ContentFileStore,            [NEW]
│                           DownloadedContentRepositoryImpl (was ContentPack…)        [RENAME]
│   ├── catalog/            StudentCatalogRepositoryImpl, CatalogReconciler               [NEW]
│   ├── cover/              CoverImageCache                                               [NEW]
│   ├── remote/             PostgrestClient, StorageRestClient (+AccessTokenProvider)     [EDIT]
│   ├── audio/              AudioSourceResolverImpl (−starter branch)                     [EDIT]
│   └── seed/               ──────────────────────────────────────────────── DELETED
├── presentation/
│   ├── home/ details/ settings/ search/   new states, previews                           [EDIT]
│   └── common/             ContentActionButton, StorageUsageRow (−isStarter)             [EDIT]
└── sqldelight/…/Content.sq  +catalog_overview, +catalog_sync_state, +downloaded_matn,
                             −content_pack, FK removals; new 5.sqm (v5→v6)                [EDIT]

shared/src/androidMain/…/delivery/   PlayAssetDeliveryEngine ───────────── DELETED
                                     AndroidDeviceStorage (+contentRootPath)              [EDIT]
shared/src/iosMain/…/delivery/       OnDemandResourcesEngine ────────────── DELETED
                                     IosDeviceStorage (+contentRootPath)                  [EDIT]
shared/src/jvmMain/…/delivery/       DesktopContentDeliveryEngine ───────── DELETED
                                     DesktopDeviceStorage (+contentRootPath)              [EDIT]

packs/matn_structured_sample/ ──────────────────────────────────────────── DELETED
```

**Structure Decision**: no new module. The whole feature lands in `shared` (with three one-method
`actual` edits and build-file deletions in `androidApp`), because FR-041 requires exactly one
acquisition mechanism and Principle IV puts it in `commonMain`. `:teacherApp` is touched only by the
`AccessTokenProvider` refactor, which leaves its behaviour byte-identical.

## Implementation Sequencing

Ordered so each step is independently testable and nothing is left non-building (Constitution
§Phased, Incremental Delivery).

1. **Schema migration first** (`5.sqm`, FK removal, new tables). Highest risk, and everything else
   assumes it. Ships with the test that proves personal data survives content deletion.
2. **Anonymous read path** (`AccessTokenProvider`, both REST clients). Unblocks every network task;
   `:teacherApp` regression-tested here.
3. **Catalog sync** (repository, reconciler, sync state) → User Story 1 is demonstrable.
4. **Download** (engine, staging/commit, queue) → User Story 2, the phase's core promise.
5. **Removal & storage** (starter path deleted, `removeAll` spares nothing) → User Story 3.
6. **Reconciliation & update flag** → User Story 4.
7. **Search narrowing** → User Story 5.
8. **Retirement sweep** ([retirement-contract.md](./contracts/retirement-contract.md)) → SC-012.

Steps 1–4 are strictly ordered. Steps 5–7 may be reordered. Step 8 lands last so the old path stays
available as a reference while the new one is built — but it is **not** optional, and FR-042/SC-012
are unmet until it completes.

> **Amended during task breakdown**: [tasks.md](./tasks.md) moves the *platform-engine* deletion
> (the three `ContentDeliveryEngine` implementations, the `packs/` module, `play-asset-delivery-ktx`,
> the ODR tags) forward into step 2, because two `ContentDeliveryEngine` implementations cannot both
> be bound in Koin without an ambiguous-binding failure — keeping the old one "available as a
> reference" is not actually possible. The rest of the retirement (the seed loader, the starter path,
> the bundled audio, the grep sweep) still lands last.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| New dependency: `org.jetbrains.kotlinx:kotlinx-io-core` | The download path needs create-dir, write-bytes, delete-tree and exists in `commonMain`. Already present transitively via Ktor 3.2.3's I/O layer, so this declares an artifact that already ships rather than adding one. | *Expanding `DeviceStorage` with write methods* would mean writing the same four filesystem operations three times across `androidMain`/`iosMain`/`jvmMain` — which Principle IV names as prohibited when `commonMain` can hold it. *Okio* is functionally equivalent but is a genuinely new third-party artifact where a transitively-present JetBrains one does the same job (research D4). |
| Dropping foreign keys from five personal-data tables | Removal now deletes `verse`/`matn` rows; every `ON DELETE CASCADE` would take the student's bookmarks, notes, memorized marks, practice history and resume position with it, violating FR-026, FR-029, SC-010 and Principle VI. | *Keeping verse rows forever and deleting only audio* makes FR-028's reclaimed-space figure dishonest and contradicts FR-022's "determined from what is actually present". *Shadowing personal data and restoring on re-download* creates two sources of truth for one fact and adds a restore step that can fail, which SC-010's 100% target forbids (research D5). |

> Neither entry is a constitution violation in the "unjustified complexity" sense — the first is the
> dependency justification the constitution requires in the PR description, recorded here so it is
> not lost; the second is a deliberate schema decision whose rejected alternatives are worth keeping
> on the record.

## Notes

- **No Supabase migration is added by this phase.** Phase 11's `matns_read`,
  `matn_content_published_read` and the `20260731000400` grants already express exactly the
  anonymous published-only access FR-027 and FR-004 require (research D1). The only server-adjacent
  edit is adding `revision` and `structure_kind` to `MATN_OVERVIEW_COLUMNS`, which is client-side.
- **Agent context script**: `.specify/scripts/powershell/` contains no `update-agent-context`
  script in this repo, so that step of the plan workflow was skipped rather than substituted.
- **Deferred from clarification**: observability (no telemetry, logging or crash reporting exists or
  is added) and cover-cache eviction. Both recorded in [research.md](./research.md) §Deferred.
