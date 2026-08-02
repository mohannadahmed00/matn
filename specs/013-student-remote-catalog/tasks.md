---
description: "Task list for 013-student-remote-catalog"
---

# Tasks: Student Remote Catalog & Download

**Input**: Design documents from `/specs/013-student-remote-catalog/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: REQUIRED, not optional. Constitution Principle V is NON-NEGOTIABLE — "New or changed
domain/data behavior MUST land with tests in the same change."

## Format: `[ID] [P?] [Story] Description`

- **[P]**: safe to do in parallel — touches different files, no dependency on an unfinished task
- **[Story]**: US1–US5, matching the user stories in [spec.md](./spec.md)
- Every task names the exact file(s) it touches

---

## Rules for the implementer — read before starting

These are hard constraints. Violating one is a review failure, not a style opinion.

1. **Do the tasks in ID order.** `[P]` marks tasks that may be reordered *within their own phase*;
   phases themselves are strictly sequential.
2. **Never invent a file path.** Every path below exists or is explicitly marked `[NEW]`. If a path
   does not match reality, stop and report it rather than creating a parallel file.
3. **One task = one commit-sized change.** Do not bundle.
4. **`commonMain` only**, unless the task says `androidMain` / `iosMain` / `jvmMain`. Platform code
   holds no business logic (Constitution IV).
5. **No hard-coded colors, `.dp`, or `.sp` in composables.** Use the existing design tokens
   (Constitution VIII).
6. **Every new or changed state-rendering composable needs a `@Preview`** driven by hand-built
   sample state — no ViewModel, no DI (Constitution II). Missing preview = blocking failure.
7. **Never widen a `catch`.** Existing code rethrows `CancellationException` before catching
   `Throwable`; copy that shape exactly.
8. **Run `./gradlew :shared:allTests` after every phase.** Do not start the next phase on red.
9. **Arabic UI strings** go in the existing string resources, never inline in a composable.
10. **If a task's premise is wrong** (the code does not look as described), stop and report. Do not
    improvise a different design.

**Verification command used throughout:**

```bash
./gradlew :shared:allTests
```

**Full build check (used at phase boundaries):**

```bash
./gradlew :shared:allTests :androidApp:assembleDebug :desktopApp:build :teacherApp:build
```

---

## Sequencing note (deviation from plan.md)

[plan.md](./plan.md) § Implementation Sequencing puts the retirement sweep last. For a single
implementer the **platform-engine deletion moves into Phase 2**, because two `ContentDeliveryEngine`
implementations cannot both be bound in Koin without an ambiguous-binding failure. The final grep
sweep (Phase 8) still runs last. Everything else follows the plan's order.

---

## Phase 1: Setup

**Purpose**: dependencies and the one new platform capability, so later phases have somewhere to
write files.

- [X] T001 Add `kotlinx-io-core` to `gradle/libs.versions.toml`. **Do not guess the version.** First run `./gradlew :shared:dependencies --configuration commonMainResolvableDependenciesMetadata | grep kotlinx-io` to find the version Ktor 3.2.3 already resolves, then pin exactly that under `[versions]` as `kotlinxIo`, and add `kotlinx-io-core = { module = "org.jetbrains.kotlinx:kotlinx-io-core", version.ref = "kotlinxIo" }` under `[libraries]`. If the grep finds nothing, use the newest release compatible with Kotlin 2.4.10 and record which you chose in the PR
- [X] T002 Add `implementation(libs.kotlinx.io.core)` to the `commonMain.dependencies { }` block in `shared/build.gradle.kts` (the block that already lists `libs.ktor.client.core`)
- [X] T003 Add `suspend fun contentRootPath(): String` to the `DeviceStorage` interface in `shared/src/commonMain/kotlin/com/giraffe/matn/domain/delivery/DeviceStorage.kt`, with a KDoc line saying it returns the app-private directory that downloaded content lives under
- [X] T004 [P] Implement `contentRootPath()` in `shared/src/androidMain/kotlin/com/giraffe/matn/delivery/AndroidDeviceStorage.kt` returning `context.filesDir.absolutePath`
- [X] T005 [P] Implement `contentRootPath()` in `shared/src/iosMain/kotlin/com/giraffe/matn/delivery/IosDeviceStorage.kt` returning the `NSApplicationSupportDirectory` path for the user domain
- [X] T006 [P] Implement `contentRootPath()` in `shared/src/jvmMain/kotlin/com/giraffe/matn/delivery/DesktopDeviceStorage.kt` returning `${System.getProperty("user.home")}/.matn`
- [X] T007 Add a `contentRootPath()` override returning a settable fake path to `shared/src/commonTest/kotlin/com/giraffe/matn/delivery/FakeDeviceStorage.kt`

**Checkpoint**: `./gradlew :shared:allTests` is green. Nothing behavioural changed yet.

---

## Phase 2: Foundational (BLOCKING — no user story may start before this completes)

**Purpose**: the schema change, the anonymous read path, the domain-model reshaping, and the
deletion of the old delivery stack. Everything downstream assumes all four.

### 2A — Database schema (do these strictly in order: T008 → T009 → T010 → T011)

> **Why this is first and highest-risk**: today `bookmark`, `note`, `memorization`,
> `daily_practice` and `matn_session` cascade-delete from `verse`/`matn`. Once a download owns verse
> rows, removing a matn would delete the student's entire history. See [research.md](./research.md)
> D5.

- [X] T008 Edit `shared/src/commonMain/sqldelight/com/giraffe/matn/db/Content.sq` — **schema section only**: (a) delete the `content_pack` table and its leading comment; (b) add the three new tables exactly as written in [data-model.md](./data-model.md) §1.1, §1.2, §1.3 (`catalog_overview`, `catalog_sync_state`, `downloaded_matn`); (c) rewrite the five personal-data foreign keys per §1.5 — `bookmark.verse_id`, `note.verse_id`, `memorization.verse_id` become `TEXT NOT NULL UNIQUE`, `daily_practice.verse_id` becomes `TEXT NOT NULL`, `matn_session.matn_id` becomes `TEXT NOT NULL PRIMARY KEY`, each with `REFERENCES …` and `ON DELETE CASCADE` **removed**; (d) add `CREATE INDEX` on each of those columns
- [X] T009 Create `shared/src/commonMain/sqldelight/com/giraffe/matn/db/5.sqm` [NEW] following the exact style of the existing `4.sqm`. It must: recreate each of the five personal-data tables without their foreign key (`CREATE TABLE x_new` → `INSERT INTO x_new SELECT …` → `DROP TABLE x` → `ALTER TABLE x_new RENAME TO x`), `DROP TABLE content_pack`, `DELETE FROM audio_asset; DELETE FROM verse; DELETE FROM chapter; DELETE FROM matn;`, and create the three new tables. **Every `CREATE TABLE` text must be byte-identical to the same table in `Content.sq`** — this is what `4.sqm`'s comment requires
- [X] T010 Create `shared/src/commonTest/kotlin/com/giraffe/matn/db/MigrationV5Test.kt` [NEW] modelled on the existing `MigrationV4Test`: open a v5 database, insert a matn + verse + bookmark + note + memorization + matn_session, migrate to v6, then assert the bookmark, note, memorization and session rows all still exist and the content tables are empty
- [X] T011 Add SQLDelight queries to `Content.sq` for the three new tables: `selectAllCatalogOverviews`, `selectCatalogOverviewById`, `upsertCatalogOverview`, `deleteCatalogOverviewById`, `markCatalogOverviewWithdrawn`, `selectSyncState`, `upsertSyncState`, `selectDownloadedMatn`, `selectAllDownloadedMatns`, `insertDownloadedMatn`, `deleteDownloadedMatn`

### 2B — Repair queries broken by the FK removal

> Personal-data rows can now outlive their verse. Any read that surfaces personal data *with verse
> context* must hide orphans with an inner join. See [data-model.md](./data-model.md) §1.5.

- [X] T012 In `Content.sq`, change `selectAllBookmarksWithContext` and `selectAllNotesWithContext` to `INNER JOIN verse ON verse.id = bookmark.verse_id` (resp. `note.verse_id`) and `INNER JOIN matn ON matn.id = verse.matn_id`, so a bookmark/note whose matn is not downloaded is simply absent from the result
- [X] T013 In `Content.sq`, handle the `memorization` / `daily_practice` queries. **Five are already structurally safe — verify and leave them unchanged**: `selectMemorizedVerseIdsByMatn`, `selectMemorizedVerseIdsByChapter` and `selectNotedVerseIdsByMatn` already `JOIN verse`; `selectAllMatnProgress` and `selectMatnProgressById` are anchored `FROM matn LEFT JOIN verse LEFT JOIN memorization`, so an orphan cannot reach the count. **One needs a decision**: `selectDailyPracticeCount` is `SELECT COUNT(*) FROM daily_practice WHERE day_epoch = ?` and *will* keep counting rows whose verse was removed. **Leave it counting them** — practice is a historical fact and FR-026 forbids losing personal data — and record that intent in a comment above the query
- [ ] T014 Create `shared/src/commonTest/kotlin/com/giraffe/matn/db/OrphanPersonalDataTest.kt` [NEW]: insert a bookmark, note, memorization and daily_practice row, delete the verse row directly, then assert (a) all four personal-data rows still exist in their tables, (b) the `…WithContext` queries return zero rows, (c) `selectAllMatnProgress` does not report the orphan as memorized, and (d) `selectDailyPracticeCount` **still counts** the orphan, pinning T013's decision

### 2C — Anonymous read path

> Both REST clients currently fail before issuing a request when no teacher is signed in. See
> [research.md](./research.md) D2 and [contracts/student-read-contract.md](./contracts/student-read-contract.md) §1.1.

- [X] T015 Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/auth/AccessTokenProvider.kt` [NEW] containing `fun interface AccessTokenProvider { suspend fun currentAccessToken(): Resource<String?> }`
- [X] T016 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/auth/AnonymousAccessTokenProvider.kt` [NEW] — a class implementing `AccessTokenProvider` whose body is `= Resource.Success(null)`, with a KDoc line citing FR-027
- [X] T017 Make `TokenRefresher` in `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/auth/TokenRefresher.kt` implement `AccessTokenProvider`. Change its `currentAccessToken()` return type from `Resource<String>` to `Resource<String?>`. **Do not change any other behaviour** — it still returns `Resource.Failure(RemoteError.Unauthorized)` when there is no session
- [X] T018 In `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/postgrest/PostgrestClient.kt`, change the constructor parameter `tokenRefresher: TokenRefresher` to `tokenProvider: AccessTokenProvider`; change `private fun HttpRequestBuilder.standardHeaders(token: String)` to accept `token: String?` and append the `Authorization` header **only when token is non-null**; update `withToken` so a `null` token is a success path, not a failure
- [X] T019 In `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/storage/StorageRestClient.kt`, apply the same change to all four methods (`upload`, `listWithSizes`, `download`, `delete`): take `AccessTokenProvider`, allow a null token, omit `Authorization` when null. Keep the `apikey` header on every request
- [ ] T020 Create `shared/src/commonTest/kotlin/com/giraffe/matn/data/remote/AnonymousRequestTest.kt` [NEW] using `MockEngine`: assert that with `AnonymousAccessTokenProvider` a `PostgrestClient.select` request carries an `apikey` header and **no** `Authorization` header, and that with a stub provider returning a token it carries both
- [X] T021 Update `:teacherApp` DI so `PostgrestClient` and `StorageRestClient` receive the existing `TokenRefresher` as their `AccessTokenProvider`, then run `./gradlew :teacherApp:build` and confirm its tests still pass unchanged

### 2D — Domain model reshaping

- [X] T022 [P] In `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/ContentAvailability.kt`: rename `NotInstalled`→`NotDownloaded`, `Installing`→`Downloading`, `Installed`→`Downloaded`; add `data object Queued : ContentAvailability`; update the KDoc to cite FR-016 and Clarification 4 rather than the starter rule
- [X] T023 [P] In `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/DeliveryFailure.kt`: delete `Evicted` and `Unknown`; add `data class Remote(val error: RemoteError) : DeliveryFailure` and `data object SourceUnavailable : DeliveryFailure`; keep `NoConnectivity`, `InsufficientStorage`, `Cancelled`
- [X] T024 [P] In `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/DeliveryProgress.kt`: delete `DeliveryPhase.WAITING_FOR_NETWORK_POLICY` and `DeliveryPhase.REQUIRES_CONFIRMATION`, keeping `PENDING` and `TRANSFERRING`
- [X] T025 [P] In `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/RemovalOutcome.kt`: delete `ReleasedPendingSystemReclaim`, leaving only `Reclaimed(bytes)`
- [X] T026 [P] In `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/StorageUsage.kt`: delete `MatnStorageEntry.isStarter` and `StorageUsage.onDemandUsedBytes`; update KDoc to state that cached covers are excluded (Clarification 3)
- [X] T027 [P] In `shared/src/commonMain/kotlin/com/giraffe/matn/domain/error/DeliveryError.kt`: delete `StarterMatnNotRemovable`; rename `ContentNotInstalled` to `ContentNotDownloaded`
- [X] T028 Fix every compile error produced by T022–T027 across `shared/src`, mechanically — rename call sites only. **Do not change behaviour or delete a branch you do not understand**; if a branch is only reachable for the starter, leave it in place and add it to a list you carry forward to **T094**, which is the task that removes them

### 2E — Delete the old delivery stack

- [X] T029 [P] Delete `shared/src/androidMain/kotlin/com/giraffe/matn/delivery/PlayAssetDeliveryEngine.kt`
- [X] T030 [P] Delete `shared/src/iosMain/kotlin/com/giraffe/matn/delivery/OnDemandResourcesEngine.kt`
- [X] T031 [P] Delete `shared/src/jvmMain/kotlin/com/giraffe/matn/delivery/DesktopContentDeliveryEngine.kt`
- [X] T032 Delete the directory `packs/matn_structured_sample/` and remove `include(":packs:matn_structured_sample")` from `settings.gradle.kts`
- [X] T033 Remove `assetPacks += listOf(":packs:matn_structured_sample")` from `androidApp/build.gradle.kts` (line ~56), remove `implementation(libs.play.asset.delivery.ktx)` from the `androidMain.dependencies` block in `shared/build.gradle.kts`, and remove both `play-asset-delivery-ktx` and `playAssetDelivery` entries from `gradle/libs.versions.toml`
- [X] T034 **Verify, do not assume**: search `iosApp/` (including `iosApp.xcodeproj/project.pbxproj` and every `.plist`) for `OnDemandResource`, `ODRTag`, or `onDemandResourceTags`. A search at planning time found **none**, so the expected outcome is that iOS ODR tags were never configured and FR-039's iOS half is already satisfied. If that holds, record it in the PR and move on. If tags *are* found, delete them

### 2F — The new file store and engine skeleton

- [X] T035 Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/delivery/ContentFileStore.kt` [NEW] — a class taking `DeviceStorage`, using `kotlinx.io` and `kotlinx.io.files.SystemFileSystem`, exposing: `suspend fun writeFile(relativePath: String, bytes: ByteArray)`, `suspend fun deleteTree(relativePath: String)`, `suspend fun moveTree(from: String, to: String)`, `suspend fun exists(relativePath: String): Boolean`, `suspend fun sizeOfTree(relativePath: String): Long`. All paths are relative to `DeviceStorage.contentRootPath()`. Layout is fixed by [contracts/delivery-contract.md](./contracts/delivery-contract.md) §1
- [X] T036 Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/delivery/RemoteContentDeliveryEngine.kt` [NEW] implementing `ContentDeliveryEngine` with the member set in [contracts/delivery-contract.md](./contracts/delivery-contract.md) §2. In this task implement only `contentRootFor`, `isDownloaded`, `remove` and `cancel`; leave `download` and `observe` throwing `NotImplementedError()` — Phase 4 fills them in
- [X] T037 Update `shared/src/commonMain/kotlin/com/giraffe/matn/domain/delivery/ContentDeliveryEngine.kt` to the new member set: delete `querySize`, rename `install`→`download`, `locate`→`contentRootFor`, `isInstalled`→`isDownloaded`, and change every `packId: String` parameter to `matnId: String`
- [X] T038 Bind `RemoteContentDeliveryEngine` and `ContentFileStore` as `@Single` in `shared/src/commonMain/kotlin/com/giraffe/matn/di/ContentModule.kt`, and remove the `deliveryEngine` parameter from `initMatnKoin(...)` in `shared/src/commonMain/kotlin/com/giraffe/matn/di/MatnKoinStarter.kt` plus its three platform call sites (`androidApp`, `iosApp` via `MainViewController.kt`, `desktopApp`)
- [X] T039 Update `shared/src/commonTest/kotlin/com/giraffe/matn/delivery/FakeContentDeliveryEngine.kt` to the new member set, adding a settable queue so tests can script ordering

**Checkpoint**: run the full build check. All four modules build; `:shared:allTests` green. The old
delivery model is gone and the new one is a compiling skeleton. **Do not proceed on red.**

---

## Phase 3: User Story 1 — Browse what the teacher has published (P1) 🎯 MVP

**Goal**: a student with no downloads sees the published catalog, and still sees it offline after
one successful sync.

**Independent Test**: publish a matn from `:teacherApp`, open a fresh student install with
connectivity, confirm the card shows cover, title, author, description, verse count and download
size and reads as not downloaded; force-quit, disable the network, relaunch, confirm the same list
renders. No download performed.

### Domain & data

- [X] T040 [P] [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/CatalogOverview.kt` [NEW] with the data class in [data-model.md](./data-model.md) §2.1, reusing the existing `StructureKind` and `AudioCompleteness` types
- [X] T041 [P] [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/CatalogSyncState.kt` [NEW] with the data class in [data-model.md](./data-model.md) §2.6
- [X] T042 [US1] Add `"revision"` and `"structure_kind"` to `MATN_OVERVIEW_COLUMNS` in `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/postgrest/MatnRow.kt`, and add a `MatnRow.toCatalogOverview(): CatalogOverview` mapper next to the existing `toCatalogEntry()`
- [X] T043 [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/StudentCatalogRepository.kt` [NEW] — the interface in [data-model.md](./data-model.md) §3.1. Name it `StudentCatalogRepository`, **not** `CatalogRepository`: that name is already taken by the teacher-side interface in the same package
- [X] T044 [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/catalog/CatalogReconciler.kt` [NEW] — a pure function taking the remote overview list plus the local overview list plus the set of downloaded matn ids, returning the rows to upsert, delete and mark withdrawn, per the table in [data-model.md](./data-model.md) §4.2. No database access, no network: pure input → output so it is trivially unit-testable
- [X] T045 [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/catalog/StudentCatalogRepositoryImpl.kt` [NEW] implementing `StudentCatalogRepository` over `PostgrestClient` + `ContentDatabase` + `CatalogReconciler`. The sync request is exactly the one in [contracts/student-read-contract.md](./contracts/student-read-contract.md) §2 — **including the `published=eq.true` filter**, which is how FR-004 reaches the client. Add a comment noting that FR-004 is *also* enforced server-side by the `matns_read` RLS policy, so the filter is defence in depth, not the only gate ([research.md](./research.md) D1). Apply the reconciler's output in **one** SQLDelight transaction; on any failure write nothing and set `last_attempt_failed = 1`
- [X] T046 [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/SyncCatalogUseCase.kt` [NEW]: when `force` is false, return early without a request if `lastSuccessAtMillis` is within 1 hour of now (FR-006); when `force` is true always sync. Take the clock as an injected `() -> Long` so tests control it
- [X] T047 [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/ObserveCatalogUseCase.kt` [NEW] combining `observeCatalog()` with `observeLibraryAvailability()` into one list for the library grid
- [X] T048 [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/cover/CoverImageCache.kt` [NEW]: fetch `cover_image_ref` through `StorageRestClient.download`, cache under `covers/{matnId}` via `ContentFileStore`, return null on any failure. **Never throws, never counted in storage usage** (FR-012). It must only ever be called from the catalog/browse path — never from reading or playback, which T070 asserts
- [X] T049 [US1] Register `StudentCatalogRepositoryImpl`, `CatalogReconciler`, `CoverImageCache` and the new use cases in `shared/src/commonMain/kotlin/com/giraffe/matn/di/ContentModule.kt`

### Presentation

- [X] T050 [US1] Add `catalog`, `syncState` and `isSyncing` to `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/home/HomeUiState.kt`, and add three derived booleans distinguishing FR-044's three cases: `showConnectPrompt` (never synced), `showEmptyCatalog` (synced, zero overviews), `showNothingDownloaded` (overviews exist, none downloaded)
- [X] T051 [US1] Wire `SyncCatalogUseCase` and `ObserveCatalogUseCase` into `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/home/HomeViewModel.kt`; trigger a non-forced sync when the library is opened and a forced sync from a `Refresh` intent. Collect independently so a slow sync never gates the grid
- [X] T052 [US1] Render the three new states in `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/home/HomeScreen.kt`, each with Arabic copy and a retry action where applicable. **Check Stitch for an existing design for each state first** (Constitution VIII); if none exists, reuse the existing empty-state token pattern and say so in the PR
- [X] T053 [US1] Add `@Preview`s to `HomeScreen.kt` for: connect-prompt, empty-catalog, nothing-downloaded, and a populated catalog of undownloaded متون
- [X] T054 [US1] Update `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/details/MatnDetailsViewModel.kt` and `MatnDetailsUiState.kt` so an undownloaded matn renders its overview plus a download action and **no verse text** (US1 scenario 7), plus a `@Preview` for that state in `MatnDetailsScreen.kt`

### Tests

- [ ] T055 [P] [US1] Create `shared/src/commonTest/kotlin/com/giraffe/matn/catalog/CatalogReconcilerTest.kt` [NEW] covering all six rows of [data-model.md](./data-model.md) §4.2
- [ ] T056 [P] [US1] Create `shared/src/commonTest/kotlin/com/giraffe/matn/catalog/CatalogSyncTest.kt` [NEW] with `MockEngine`: 200-with-three-rows populates three overviews; 500 leaves a previously synced catalog byte-identical and sets `last_attempt_failed`; 200-empty is distinguishable from a transport failure in `CatalogSyncState`
- [ ] T057 [P] [US1] Create `shared/src/commonTest/kotlin/com/giraffe/matn/catalog/SyncCatalogUseCaseTest.kt` [NEW]: no request inside the 1-hour window, a request outside it, and a request on `force = true` regardless

**Checkpoint**: User Story 1 is independently demonstrable. Run scenario 1 in
[quickstart.md](./quickstart.md).

---

## Phase 4: User Story 2 — Download a matn and study it offline (P2)

**Goal**: a student downloads a matn and can study it with the network off, forever.

**Independent Test**: download one matn, watch progress complete, disable the network, then read,
play end to end, run a repetition count and an A–B loop, mark a verse memorized, restart the app and
confirm it still plays.

### Data & domain

- [X] T058 [US2] Update the repository contract per [contracts/delivery-contract.md](./contracts/delivery-contract.md) §3: delete `isStarterMatn`, `declaredSize`, `bestKnownSize`; rename `install`→`download`, `packRootFor`→`contentRootFor`. **Also rename the type itself** `ContentPackRepository` → `DownloadedContentRepository` (and `ContentPackRepositoryImpl` → `DownloadedContentRepositoryImpl`), because this phase deletes the `content_pack` table and the `pack_id` concept entirely ([research.md](./research.md) D6) and the old name would outlive its referent. This is a mechanical rename across 19 files: `domain/repository/ContentPackRepository.kt`, `data/delivery/ContentPackRepositoryImpl.kt`, `data/audio/AudioSourceResolverImpl.kt`, the eight use cases in `domain/usecase/` (`CancelInstallUseCase`, `EnsureMatnPlayableUseCase`, `GetMatnDetailsUseCase`, `InstallMatnContentUseCase`, `ObserveContentAvailabilityUseCase`, `ObserveLibraryAvailabilityUseCase`, `ObserveStorageUsageUseCase`, `RemoveAllContentUseCase`, `RemoveMatnContentUseCase`), and the seven tests under `commonTest/.../delivery/` plus `commonTest/.../presentation/MatnDetailsViewModelTest.kt`. Rename the files too
- [X] T059 [US2] Implement `download` and `observe` in `RemoteContentDeliveryEngine.kt` following the nine-step sequence in [contracts/delivery-contract.md](./contracts/delivery-contract.md) §4 exactly. Stage into `downloads/.tmp-{matnId}/`, move to `downloads/{matnId}/` only on full success, then insert rows in one transaction. **Files first, database second** — the order matters ([research.md](./research.md) D7)
- [X] T060 [US2] A verse whose `audio` is null, or whose audio object returns 404, is recorded as readable with no recitation and the download **continues** (FR-023). Any other non-2xx aborts the whole download
- [X] T061 [US2] Add the one-at-a-time FIFO queue to `DownloadedContentRepositoryImpl.kt`, guarded by a `Mutex`, emitting `ContentAvailability.Queued` for waiting matns. Preserve the existing race rules and add the queued case: duplicate download is a no-op; duplicate removal is a no-op; removal beats an in-flight download of the same matn; **removing a matn that is queued but not yet started drops it from the queue and transfers nothing** (FR-032, US2 scenario 11)
- [X] T062 [US2] Run the download queue in an application-scope coroutine so a transfer and the queue behind it keep advancing while the app is backgrounded, and reflect their true state on return (FR-018, US2 scenario 6). Use a `CoroutineScope(SupervisorJob() + Dispatchers.Default)` held by the repository singleton — the same shape `ContentModule.kt` already uses for `SessionStateRecorder` and `PracticeSignalRecorder` — **not** a ViewModel scope, which dies with the screen. A killed process is out of scope: FR-017 already prescribes that everything resolves to not-downloaded
- [X] T063 [US2] Rename `InstallMatnContentUseCase.kt` to `DownloadMatnUseCase.kt` in `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/`; delete its starter guard; keep the free-space precondition (FR-019) and connectivity precondition (FR-020), both of which must run **before** enqueuing
- [X] T064 [US2] In `shared/src/commonMain/kotlin/com/giraffe/matn/data/audio/AudioSourceResolverImpl.kt`, delete **both** content-resolution branches that predate this phase — the `isStarterMatn` branch and the `Res.getUri("files/audio/$fileRef")` Compose-resource fallback. Every matn now resolves to exactly one form: `file://{contentRoot}/downloads/{matnId}/audio/{fileRef}`. After this task the file has no reference to Compose resources at all (the matching asset files are deleted later, in T093)

### Presentation

- [X] T065 [US2] Render `Queued` distinctly from `Downloading` in `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/common/ContentActionButton.kt` (queued shows no progress bar), and add a `@Preview` for it. **Check Stitch for an existing design for the queued state first** (Constitution VIII); if none exists, reuse the existing progress-state token pattern and say so in the PR
- [X] T066 [US2] Ensure attempting to play an undownloaded matn shows an actionable download prompt (FR-021) — check `EnsureMatnPlayableUseCase` still routes correctly after the T027 rename, and that the UI renders the prompt rather than a silent no-op
- [X] T067 [US2] Map every failure the student can reach to a specific, localized Arabic message plus a next action (FR-043). Add the strings to the existing string resources and a single mapping function — suggested location `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/common/DeliveryFailureCopy.kt` [NEW]. Cover **every** `DeliveryFailure` value after T023 (`NoConnectivity`, `InsufficientStorage` — which must state required against available, `Cancelled`, `Remote`, `SourceUnavailable`), the catalog-sync failure notice (FR-007), and the unreachable-cover case (FR-012, which renders a placeholder and says nothing). **No raw error code and no silent no-op may reach the UI.** Add a `@Preview` showing each failure state

### Tests

- [X] T068 [P] [US2] Create `shared/src/commonTest/kotlin/com/giraffe/matn/delivery/DownloadQueueTest.kt` [NEW]: a second download observes `Queued` then `Downloading` after the first settles; cancelling a queued matn leaves the running one untouched; removing a queued matn drops it from the queue (T061's FR-032 clause); the queue keeps advancing on a scope that outlives the caller (T062's FR-018 clause)
- [ ] T069 [P] [US2] Create `shared/src/commonTest/kotlin/com/giraffe/matn/delivery/DownloadCommitTest.kt` [NEW]: a download aborted mid-transfer leaves no `downloads/{matnId}/` directory, no `.tmp-` directory, and no database rows; a download whose third verse audio 404s completes with that verse marked recitation-less; a matn row returning `[]` resolves to `NotDownloaded(SourceUnavailable)`; **and a matn whose `downloads/{matnId}/` directory is deleted behind the app's back reports `NotDownloaded` on the next read and offers download again** (FR-045, [contracts/delivery-contract.md](./contracts/delivery-contract.md) §6)
- [ ] T070 [P] [US2] Create `shared/src/commonTest/kotlin/com/giraffe/matn/delivery/OfflineGuaranteeTest.kt` [NEW] — **the SC-004 / Principle VI guard, and the most important test in this phase.** Install a `MockEngine` that throws on *any* request. With one matn already downloaded, exercise the full study surface: read verses, build the playback queue and resolve every audio source, run a repetition count, run an A–B loop, change speed, mark a verse memorized, add a bookmark, write a note, record a daily-practice signal, and resume from saved session state. Assert **zero requests were attempted** and that no operation failed. This is the one binding condition of the amended constitution Principle VI; a cover fetch leaking into the reading path is the specific regression it exists to catch
- [X] T071 [P] [US2] Rewrite `shared/src/commonTest/kotlin/com/giraffe/matn/delivery/InstallMatnContentUseCaseTest.kt` as `DownloadMatnUseCaseTest.kt` against the download path instead of `ContentSeedLoaderImpl`

**Checkpoint**: User Stories 1 AND 2 both work. Run quickstart scenarios 1–2.

---

## Phase 5: User Story 3 — Remove a matn to reclaim space (P3)

**Goal**: any matn can be removed, with personal data untouched and nothing exempt.

**Independent Test**: download two متون, bookmark/note/memorize in one, remove it confirming the
reclaimed-space figure, verify it reverts to a catalog entry with all personal data preserved, check
Settings lists no non-removable row, re-download and confirm playback resumes where it left off.

- [X] T072 [US3] Delete the starter guard from `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/RemoveMatnContentUseCase.kt`, and make removal delete the `matn`/`chapter`/`verse`/`audio_asset` rows plus the `downloaded_matn` row in one transaction, after `ContentFileStore.deleteTree("downloads/{matnId}")`
- [X] T073 [US3] Delete the starter-sparing branch from `removeAll()` in `DownloadedContentRepositoryImpl.kt` (currently `if (row.isStarter) continue`) — "remove all" now spares nothing (FR-031)
- [X] T074 [US3] Make `observeStorageUsage()` in `DownloadedContentRepositoryImpl.kt` measure `downloads/{matnId}/` via `ContentFileStore.sizeOfTree`, and drop every `isStarter` / `onDemandUsedBytes` reference
- [X] T075 [US3] Delete the `isStarter` branch from `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/common/StorageUsageRow.kt` (line ~53) so no row renders a "part of the app" label or suppresses its remove action
- [X] T076 [US3] Delete the `isStarter` parameter from `ContentActionButton.kt` and update its four previews
- [ ] T077 [US3] Update `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/settings/SettingsUiState.kt`, `SettingsViewModel.kt` and `SettingsScreen.kt` for the narrowed `StorageUsage` model, plus a `@Preview` for the populated and zero states. **Check Stitch for the storage section's design first** (Constitution VIII); if the removal of the non-removable row has no design, reuse the existing row pattern and say so in the PR
- [X] T078 [US3] Rewrite `shared/src/commonTest/kotlin/com/giraffe/matn/delivery/RemovalPreservesUserDataTest.kt` against the download path instead of `ContentSeedLoaderImpl`. **This is the SC-010 guard** — it must assert that after removal the bookmark, note, memorized mark, daily-practice row and saved session all survive, and that re-downloading resumes at the same verse and position
- [ ] T079 [P] [US3] Update `shared/src/commonTest/kotlin/com/giraffe/matn/delivery/ObserveStorageUsageUseCaseTest.kt` and `RemoveMatnContentUseCaseTest.kt` for the new model. Add two cases: `removeAll()` leaves zero bytes under `downloads/` and every cover under `covers/` intact (SC-009, FR-012); and the reported per-matn figure is **within 5% of the actual bytes** written by a download of known size (SC-008)

**Checkpoint**: User Stories 1–3 all work. Run quickstart scenario 3.

---

## Phase 6: User Story 4 — Keep up with what the teacher changes (P4)

**Goal**: new متون appear, revised ones are flagged, withdrawn ones stop being offered — and nothing
downloaded is ever taken away.

**Independent Test**: publish one new matn, revise a second and unpublish a third; refresh and
confirm the new one appears, the revised one is flagged, the withdrawn one is gone from the catalog
while a previously downloaded copy still reads and plays offline.

- [ ] T080 [US4] Implement the withdrawal branches of `CatalogReconciler`: absent remotely + not downloaded → delete the overview; absent remotely + downloaded → set `withdrawn = 1` and keep everything (FR-009)
- [ ] T081 [US4] Exclude `withdrawn = 1` overviews from anything offering a new download and from the catalog half of search, while keeping them fully renderable for their downloaded content
- [ ] T082 [US4] Add an `updateAvailable` derivation comparing `catalog_overview.revision` against `downloaded_matn.revision` (FR-010), surfaced in the library and details UI states
- [ ] T083 [US4] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/UpdateMatnUseCase.kt` [NEW]: remove the current content then download at the newer revision. Personal data survives automatically because of the T008 FK removal — **do not add any copy/restore step** (FR-011)
- [ ] T084 [US4] Render the update-available affordance and an explicit refresh control in `HomeScreen.kt` and `MatnDetailsScreen.kt`, with `@Preview`s for both states. **Check Stitch for an update-available design first** (Constitution VIII); if none exists, reuse the existing badge/action token pattern and say so in the PR
- [ ] T085 [P] [US4] Extend `CatalogReconcilerTest.kt` with the withdrawal and revision cases, and create `shared/src/commonTest/kotlin/com/giraffe/matn/catalog/UpdateMatnUseCaseTest.kt` [NEW] asserting bookmarks, notes, marks and progress survive an update for verses that still exist

**Checkpoint**: User Stories 1–4 work. Run quickstart scenario 4.

---

## Phase 7: User Story 5 — Find a matn when the library grows (P5)

**Goal**: titles and authors are searchable across the whole catalog; verse text within downloads
only; all offline.

**Independent Test**: with one matn downloaded and one not, offline — search the undownloaded
matn's title (catalog hit leading to its details screen), a word in the downloaded matn's verses
(verse hits), and a word only in the undownloaded matn's verses (no verse hits, with the scope
explained).

- [ ] T086 [US5] Add a `searchCatalogOverviews` query to `Content.sq` matching title and author, reusing the same diacritic-insensitive normalisation the existing verse search uses (FR-037)
- [ ] T087 [US5] Extend `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/SearchLibraryUseCase.kt` to merge catalog hits with verse hits, tagging each result with its origin. Verse search needs **no new predicate** — verse rows exist only for downloaded متون after Phase 2 ([research.md](./research.md) D12)
- [ ] T088 [US5] Route a catalog-origin result to the matn details screen rather than to a verse (FR-035) in `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/search/SearchResultRow.kt` and the search navigation
- [ ] T089 [US5] Add a persistent, localized note to `SearchScreen.kt` stating that verse search covers downloaded متون only (FR-036), with a `@Preview` showing it alongside results. **Check Stitch for the search screen's design first** (Constitution VIII); if the scope note has no design, reuse the existing helper-text token pattern and say so in the PR
- [ ] T090 [P] [US5] Create `shared/src/commonTest/kotlin/com/giraffe/matn/search/CatalogSearchTest.kt` [NEW] covering all five acceptance scenarios of User Story 5

**Checkpoint**: all five user stories work. Run quickstart scenario 5.

---

## Phase 8: Retirement sweep & polish

- [X] T091 Delete the directory `shared/src/commonMain/kotlin/com/giraffe/matn/data/seed/` (`ContentSeedLoader.kt`, `ContentSeedLoaderImpl.kt`, `SeedContent.kt`, `SeedMatnProjection.kt`)
- [X] T092 Delete `bundledSampleMatns()` (line ~190) and the seed bootstrap call (line ~174) from `shared/src/commonMain/kotlin/com/giraffe/matn/di/MatnKoinStarter.kt`, plus the now-unused imports
- [X] T093 Delete the bundled audio **asset files** from Compose resources (`files/audio/ajurrumiyya_*.mp3` and any sibling matn audio). The code that referenced them was already removed in T064, so this task deletes files only — if any code still references `files/audio/`, stop and report it
- [X] T094 Delete `isStarter` from `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/MatnDetails.kt` and from `GetMatnDetailsUseCase.kt`, and remove every branch on the list carried forward from T028
- [ ] T095 Run every grep in [contracts/retirement-contract.md](./contracts/retirement-contract.md) §4 and confirm each returns nothing outside `docs/` and `specs/`. Confirm `find packs -type d` finds nothing
- [X] T096 Confirm no audio ships in a client binary: `unzip -l androidApp/build/outputs/apk/debug/androidApp-debug.apk | grep -i '\.mp3'` must return nothing (FR-038, SC-005)
- [ ] T097 [P] Update `docs/ROADMAP.md` to mark Phase 13 complete, and `docs/PRODUCT-SPEC.md` where it still describes bundled content
- [ ] T098 [P] Verify RTL, dark mode and contrast on every new or changed state: T052 (three catalog states), T065 (queued), T067 (failure copy), T075/T077 (storage rows), T084 (update available), T089 (search scope note) — Constitution VII
- [ ] T099 Run the full [quickstart.md](./quickstart.md) — all six scenarios — against a real Supabase stack with at least one published matn
- [ ] T100 Run the full build check on all four modules and confirm green

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 (Setup)** — no dependencies.
- **Phase 2 (Foundational)** — depends on Phase 1. **BLOCKS every user story.** Within it:
  2A → 2B (queries need the new schema); 2C, 2D, 2E are independent of 2A/2B and of each other;
  2F depends on 2D and 2E.
- **Phase 3 (US1)** — depends on Phase 2 in full.
- **Phase 4 (US2)** — depends on Phase 3 (a download needs a catalog entry to download from).
- **Phase 5 (US3)** — depends on Phase 4 (nothing to remove until something downloads).
- **Phase 6 (US4)** — depends on Phase 3; independent of Phases 4–5 for its catalog half, but its
  update flow needs Phase 4.
- **Phase 7 (US5)** — depends on Phase 3; its verse half needs Phase 4.
- **Phase 8** — depends on all of the above.

### User story dependencies

Unlike a typical feature, these stories are **not** independent — the spec says so explicitly
(US2 "depends on User Story 1 existing"). US1 → US2 → US3 is a genuine chain. US4 and US5 both
depend on US1 and are independent of each other.

### Parallel opportunities

- T004, T005, T006 (three platform `actual`s)
- T016 alongside T015's consumers
- T022–T027 (six independent domain model files)
- T029, T030, T031 (three engine deletions)
- T040, T041 (two new domain models)
- T055, T056, T057 (three US1 test files)
- T068, T069, T070, T071 (four US2 test files)
- T097, T098 (docs and a11y sweep)

---

## Parallel Example: Phase 2D

```bash
# Six independent domain-model files, no shared edits:
Task: "T022 ContentAvailability rename + Queued"
Task: "T023 DeliveryFailure: drop Evicted/Unknown, add Remote/SourceUnavailable"
Task: "T024 DeliveryProgress: drop two phases"
Task: "T025 RemovalOutcome: drop ReleasedPendingSystemReclaim"
Task: "T026 StorageUsage: drop isStarter and onDemandUsedBytes"
Task: "T027 DeliveryError: drop StarterMatnNotRemovable"
# Then T028 alone — it fixes the call sites all six of them broke.
```

---

## Implementation Strategy

### MVP (User Story 1 only)

1. Phase 1 → Phase 2 → Phase 3.
2. **Stop and validate**: quickstart scenario 1, all five sub-steps.
3. At this point a student can browse the teacher's published catalog online and offline. Nothing
   downloads yet — which is exactly the slice the spec calls independently testable.

### Incremental delivery

Phase 3 (browse) → Phase 4 (download — the core promise) → Phase 5 (removal) → Phase 6 (sync
changes) → Phase 7 (search) → Phase 8 (retirement). Each phase ends at a checkpoint where the app
builds, tests pass, and the increment is demonstrable.

### Highest-risk tasks

| Task | Risk | Mitigation |
|---|---|---|
| **T008–T010** (schema + migration) | Getting the FK removal wrong silently destroys student data; SQLite cannot drop a FK in place | T010 is the guard, and it must fail before T008/T009 are written correctly. Keep every `CREATE TABLE` byte-identical between `Content.sq` and `5.sqm` |
| **T059** (download commit) | Wrong file/database ordering leaves rows claiming playable audio that is absent | Follow §4's nine steps literally; T069 asserts the abort path leaves nothing |
| **T070** (offline guarantee) | A cover fetch or a size lookup leaking into the reading/playback path silently breaks constitution Principle VI's one binding condition | The test fails on *any* attempted request, so the leak cannot pass review |
| **T018–T019** (optional token) | A mistake here makes every student read fail in a way that looks like a network error | T020 asserts the exact headers on both paths |
| **T028** (mechanical rename fixups) | Tempting to "clean up" a branch that turns out to be load-bearing | Rename only; carry anything else forward to T094 |

### Notes

- Commit after each task or each `[P]` group.
- Do not skip a checkpoint. A red `:shared:allTests` at a phase boundary means the next phase starts
  on a broken foundation.
- `:teacherApp` should behave identically throughout. If one of its tests changes behaviour, that is
  a bug in T017–T021, not an expected consequence.
- Four timing targets (SC-001 3 s catalog, SC-003 3 min to first verse, SC-007 3 s to an actionable
  offline state, SC-011 <1 s search) are validated by hand in T099 rather than by an automated test.
  That is deliberate — they are acceptance-time measurements, not buildable infrastructure.
