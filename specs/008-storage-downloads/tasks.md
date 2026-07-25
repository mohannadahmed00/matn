# Tasks: Storage & Downloads (Phase 8)

**Input**: Design documents from `specs/008-storage-downloads/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED â€” Constitution Principle V (test-first) is NON-NEGOTIABLE: every new
domain/data behavior lands with `commonTest` coverage in the same change.

**Organization**: Grouped by user story from spec.md â€” US1 Install a matn (P1), US2 Remove a matn
(P2), US3 Settings storage section (P3).

---

## HOW TO EXECUTE THIS FILE (read once, then follow literally)

1. Do tasks **in numeric order**. Do not skip ahead. Do not batch unrelated tasks.
2. Every task names its **exact file path** and either the **exact code** or a **file to copy the
   pattern from**. When a task says "copy the shape of `X.kt`", open `X.kt` first and mirror its
   structure, imports, and KDoc style.
3. **Never invent behavior.** If a task says "per content-delivery-contract.md Â§ 4", that section is
   authoritative. If something is genuinely undefined, STOP and ask â€” do not guess.
4. After each task, run `./gradlew :shared:allTests`. It must be **green** before the next task
   (except where a task explicitly says "this test must fail first").
5. Commit after each task or tight pair (a test + its implementation).
6. If a task's described code does not compile because the surrounding code differs from what the
   task assumed, **STOP and report the mismatch** rather than redesigning.
7. Tasks marked **MANUAL** cannot be done by an LLM (Xcode UI, device install). Do everything else,
   then report which MANUAL tasks remain.

**Path shorthand used below** (expand it yourself when creating files):

- `KOTLIN` = `shared/src/commonMain/kotlin/com/giraffe/matn`
- `TEST` = `shared/src/commonTest/kotlin/com/giraffe/matn`
- `SQL` = `shared/src/commonMain/sqldelight/com/giraffe/matn/db`
- `ANDROID` = `shared/src/androidMain/kotlin/com/giraffe/matn`
- `IOS` = `shared/src/iosMain/kotlin/com/giraffe/matn`

**Three rules that cause blocking review failures if broken**:

- **Stateless/stateful split** (Constitution II): each screen = a stateless `XxxContent(state, onâ€¦)`
  composable holding ALL rendering + a thin `Xxx(viewModel, â€¦)` holder that only collects the
  ViewModel `StateFlow` and forwards intents. Every state-rendering composable needs â‰¥1 `@Preview`
  built from hand-written sample state (no ViewModel, no DI, no database in previews).
- **Tokens only** (Constitution VIII): use `MaterialTheme.colorScheme` / `MaterialTheme.typography`
  and the token objects in `KOTLIN/presentation/theme/` (`MatnSpacing`, `MatnShapes`). Raw hex
  colors (`Color(0xFFâ€¦)`) or magic `.dp`/`.sp` numbers inside screen composables are forbidden.
  (Exception, already used in this repo: a glyph's `size: Dp = 22.dp` default parameter.)
- **No business logic in `androidMain`/`iosMain`** (Constitution IV): the platform files created in
  this phase are *adapters*. They translate platform status codes into domain types and nothing
  else. No free-space checks, no connectivity policy, no ordering, no formatting â€” those live in
  `commonMain` use cases.

**The one architectural invariant of this phase**: availability is **per matn and atomic** â€” three
states only (`NotInstalled`, `Installing`, `Installed`). There is no per-verse presence tracking and
no partial state anywhere. If you find yourself writing a per-verse availability check, you have
misread the spec â€” STOP.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different file, no dependency on an unfinished task)
- **[Story]**: US1 / US2 / US3 â€” user-story phases only

---

## Phase 1: Setup

**Purpose**: Green baseline, the new dependency, the asset-pack module, and the Constitution VIII
design gate.

- [X] T001 Verify a green baseline: run `./gradlew :shared:allTests` and confirm every existing test passes BEFORE changing anything. If the baseline is red, STOP and report â€” never build on a broken baseline. (You are already on branch `feature/008-storage-downloads`; do not create another branch.)
- [X] T002 Add the Play Asset Delivery dependency (research.md D1 â€” the only new dependency in this phase). In `gradle/libs.versions.toml`: add `playAssetDelivery = "2.2.2"` under `[versions]`, and `play-asset-delivery-ktx = { module = "com.google.android.play:asset-delivery-ktx", version.ref = "playAssetDelivery" }` under `[libraries]`. In `shared/build.gradle.kts`, inside `androidMain.dependencies { â€¦ }`, add `implementation(libs.play.asset.delivery.ktx)` next to the existing `implementation(libs.media3.exoplayer)` line. Run `./gradlew :shared:allTests`. **If resolution fails**, try `2.2.1` then `2.1.0`; if none resolve, STOP and report â€” do not hand-roll a downloader.
- [X] T003 Create the asset-pack Gradle module for the structured sample matn. NEW file `packs/matn_structured_sample/build.gradle.kts` containing exactly: `plugins { id("com.android.asset-pack") }` then `assetPack { packName.set("matn_structured_sample"); dynamicDelivery { deliveryType.set("on-demand") } }`. Create the empty directory `packs/matn_structured_sample/src/main/assets/audio/`.
- [X] T004 Register the pack module. In `settings.gradle.kts`, add `include(":packs:matn_structured_sample")` below the existing `include(":shared")` line.
- [X] T005 Wire the pack into the app. In `androidApp/build.gradle.kts`, inside the `android { â€¦ }` block, add `assetPacks += listOf(":packs:matn_structured_sample")`.
- [X] T006 Move the on-demand audio out of the app binary: move the five files `structured_verse_001.mp3`, `structured_verse_002.mp3`, `structured_verse_004.mp3`, `structured_verse_005.mp3` (and `structured_verse_003.mp3` if present) from `shared/src/commonMain/composeResources/files/audio/` to `packs/matn_structured_sample/src/main/assets/audio/`. **Leave the four `ajurrumiyya_verse_00*.mp3` files and `README.txt` exactly where they are** â€” Ø§Ù„Ø£Ø¬Ø±ÙˆÙ…ÙŠØ© is the bundled starter matn (research D7). Then **measure and write down two byte totals you will need in T021**: (a) the sum of the file sizes now in `packs/matn_structured_sample/src/main/assets/audio/`, and (b) the sum of the four `ajurrumiyya_verse_00*.mp3` files still in composeResources. Use `ls -l` / `Get-ChildItem | Measure-Object -Property Length -Sum`. These are real measured figures â€” SC-002 requires the declared size to be within 5% of actual, so do NOT estimate. Run `./gradlew :shared:allTests`; existing playback tests use fakes, so they must stay green.
- [ ] T007 [P] **MANUAL (Xcode)** In `iosApp/iosApp.xcodeproj`, add the same five structured-sample audio files as On-Demand Resources tagged `matn_structured_sample` â€” OUTSTANDING MANUAL STEP (cannot perform Xcode UI from this session; nothing in this file depends on it; record in PR description). (Xcode: select the files â†’ File Inspector â†’ "On Demand Resource Tags"). The tag string MUST equal the `pack_id` used in T009. If you cannot open Xcode, record this as an outstanding manual step in the PR description and continue â€” nothing else in this file depends on it.
- [X] T008 [P] Fetch the Stitch designs required by Constitution VIII before any UI work: *Home / Library* (`618643f891144557b5a4ddf4bbad0c03`) and *Matn Details (Refined)* (`472161bbcdee47adb26d94a1fefcc3a8`) via the `stitch` MCP server's `get_screen` tool. Write findings into a NEW file `specs/008-storage-downloads/design-notes.md`, following the format of `specs/007-progress-daily-goals/design-notes.md`: where the card's status-icon slot sits, the details-header action area, and an explicit record that **the registry has no Settings screen and no install/remove states**, so those are original compositions from the Phase 10 token set. **If the `stitch` MCP server is unavailable**, write that fact in `design-notes.md`, build from contracts/storage-ui-contract.md, and flag it in the PR description.

**Checkpoint**: project builds, on-demand audio is out of the base app, starter audio remains bundled.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, domain types, platform seams, repository, fakes, DI. **No user story can start
until this phase is complete.**

### Schema

- [X] T009 Add the `content_pack` table and its queries to `SQL/Content.sq`. Append the table definition from data-model.md Â§1.1 verbatim (`matn_id` TEXT NOT NULL PRIMARY KEY REFERENCES matn(id) ON DELETE CASCADE, `pack_id` TEXT NOT NULL UNIQUE, `declared_size_bytes` INTEGER NOT NULL, `is_starter` INTEGER NOT NULL DEFAULT 0). Then add these named queries in the file's existing style: `selectAllContentPacks:` (SELECT * FROM content_pack), `selectContentPackByMatn:` (SELECT * FROM content_pack WHERE matn_id = ?), `insertContentPack:` (INSERT OR REPLACE INTO content_pack VALUES (?, ?, ?, ?)).
- [X] T010 Create the migration `SQL/4.sqm` (v4 â†’ v5). Copy the header-comment style of `SQL/3.sqm`. Body = the `CREATE TABLE content_pack (â€¦)` statement, **byte-identical** to the definition you added to `Content.sq` in T009. SQLDelight names migrations for the version they migrate FROM, so `4.sqm` bumps `ContentDatabase.Schema.version` to 5.
- [X] T011 Create `shared/src/androidHostTest/kotlin/com/giraffe/matn/db/MigrationV4Test.kt` by copying `MigrationV3Test.kt` in the same directory and adapting it: seed a v4 database with a matn + verses, migrate to v5, assert the pre-existing rows survive and that `content_pack` exists and is queryable. This test must be green before continuing.

### Domain models (all parallel â€” separate new files)

- [X] T012 [P] Create `KOTLIN/domain/model/ContentAvailability.kt`: `sealed interface ContentAvailability` with `data class NotInstalled(val failure: DeliveryFailure? = null)`, `data class Installing(val progress: DeliveryProgress)`, `data class Installed(val occupiedBytes: Long)`. KDoc must state: exactly three states, atomic per matn, no partial state (FR-001, spec Clarifications 2026-07-25).
- [X] T013 [P] Create `KOTLIN/domain/model/DeliveryProgress.kt`: `data class DeliveryProgress(val bytesTransferred: Long, val totalBytes: Long, val phase: DeliveryPhase)` with a derived `val fraction: Float get() = if (totalBytes <= 0L) 0f else (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f)`, plus `enum class DeliveryPhase { PENDING, TRANSFERRING, WAITING_FOR_NETWORK_POLICY, REQUIRES_CONFIRMATION }`.
- [X] T014 [P] Create `KOTLIN/domain/model/DeliveryFailure.kt`: `sealed interface DeliveryFailure` with `data object NoConnectivity`, `data class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long)`, `data object Cancelled`, `data object Evicted`, `data class Unknown(val code: Int)`. KDoc: every value is retryable from the UI; `Evicted` renders as plain "not installed", not as an error.
- [X] T015 [P] Create `KOTLIN/domain/model/RemovalOutcome.kt`: `sealed interface RemovalOutcome` with `data class Reclaimed(val bytes: Long)` and `data class ReleasedPendingSystemReclaim(val bytes: Long)`. KDoc must explain research D2: iOS exposes no API to force-purge On-Demand Resources, so the UI must not promise an immediate figure there.
- [X] T016 [P] Create `KOTLIN/domain/model/StorageUsage.kt`: `data class MatnStorageEntry(val matnId: String, val title: String, val bytes: Long, val isStarter: Boolean)` and `data class StorageUsage(val entries: List<MatnStorageEntry>, val totalUsedBytes: Long, val onDemandUsedBytes: Long, val freeSpaceBytes: Long)`. KDoc: `entries` is ordered by `bytes` DESC; `totalUsedBytes` includes the starter; `onDemandUsedBytes == 0L` is what drives the Settings zero state.
- [X] T017 [P] Create `KOTLIN/domain/error/DeliveryError.kt` following the exact shape of `KOTLIN/domain/error/NoteError.kt`: `sealed interface DeliveryError : AppError` with `data class ContentNotInstalled(val matnId: String)`, `data object StarterMatnNotRemovable`, and `data class DeliveryFailed(val failure: DeliveryFailure)`.

### Platform seams (interfaces only â€” implementations come later)

- [X] T018 [P] Create `KOTLIN/domain/delivery/ContentDeliveryEngine.kt` â€” copy the exact interface from content-delivery-contract.md Â§1 (`querySize`, `install`, `observe`, `cancel`, `remove`, `locate`, `isInstalled`). Model the KDoc on `KOTLIN/domain/audio/AudioEngine.kt`, which is the same kind of platform seam.
- [X] T019 [P] Create `KOTLIN/domain/delivery/DeviceStorage.kt` â€” `interface DeviceStorage { suspend fun freeSpaceBytes(): Long; suspend fun sizeOfDirectory(path: String): Long }` per content-delivery-contract.md Â§2.
- [X] T020 Create `KOTLIN/domain/repository/ContentPackRepository.kt` â€” copy the exact interface from content-delivery-contract.md Â§3. Model the KDoc on `KOTLIN/domain/repository/ProgressRepository.kt`.

### Seed data

- [X] T021 Extend seeding so every matn gets a `content_pack` row. In `KOTLIN/data/seed/SeedContent.kt` add to `SeedMatn` the fields `packId: String`, `declaredSizeBytes: Long`, `isStarter: Boolean = false`. In `KOTLIN/data/seed/ContentSeedLoaderImpl.kt`, insert the `content_pack` row inside the same transaction that writes the matn. In `KOTLIN/di/MatnKoinStarter.kt`'s `bundledSampleMatns()`, set the Ø§Ù„Ø£Ø¬Ø±ÙˆÙ…ÙŠØ© entry to `packId = "matn_ajurrumiyya", declaredSizeBytes = <measurement (b) from T006>, isStarter = true` and the structured entry to `packId = "matn_structured_sample", declaredSizeBytes = <measurement (a) from T006>, isStarter = false`. **Use the real measured byte totals from T006 â€” never a placeholder or a round number.** The starter's figure is not cosmetic: it is the only size available for its contribution to the Settings total (data-model.md Â§2.1 rule 1), so `0L` would silently under-report the total and break SC-003. Add a KDoc line above `bundledSampleMatns()` stating that declared sizes are measured from the audio files and MUST be re-measured whenever a matn's audio changes. The pack ids MUST match T003/T007 exactly.

### Test fakes (build these before the repository â€” everything below depends on them)

- [X] T022 [P] Create `TEST/delivery/FakeContentDeliveryEngine.kt` implementing `ContentDeliveryEngine` with scriptable behavior: a `MutableMap<String, ContentAvailability>` of current state, settable `var liveSize: Long?`, `var installedRoot: String?`, `var nextRemovalOutcome: RemovalOutcome`, `var failInstallWith: DeliveryFailure?`, plus helper methods `emitProgress(packId, bytes, total, phase)`, `completeInstall(packId, occupiedBytes)`, `simulateEviction(packId)`, `simulateProcessDeathMidInstall(packId)`. `observe(packId)` returns a `MutableStateFlow`-backed flow per pack. No real IO.
- [X] T023 [P] Create `TEST/delivery/FakeDeviceStorage.kt` implementing `DeviceStorage` with `var freeSpace: Long = Long.MAX_VALUE` and a `MutableMap<String, Long>` of directory sizes.

### Repository

- [X] T024 Create `KOTLIN/data/delivery/ContentPackRepositoryImpl.kt` implementing `ContentPackRepository`. Constructor: `(private val db: ContentDatabase, private val engine: ContentDeliveryEngine, private val storage: DeviceStorage)`. Copy the SQLDelight + Flow style of `KOTLIN/data/repository/DailyGoalRepositoryImpl.kt`. Rules: it owns the `matnId â†” packId` translation so no layer above ever sees a pack id; availability is computed per read from `engine` + the `content_pack` row and is **never persisted** (research D5); `bestKnownSize` returns `engine.querySize(packId) ?: declaredSizeBytes`; `removeAll()` skips starter rows. **Resolve `Installed.occupiedBytes` by data-model.md Â§2.1's three-step priority**: (1) starter row â†’ always `Installed(declaredSizeBytes)`, because its audio is inside the app binary and has no directory to measure; (2) installed on-demand â†’ `storage.sizeOfDirectory(engine.locate(packId))`; (3) platform says installed but `locate` returns null â†’ treat as `NotInstalled(Evicted)`, never a zero-byte install. **Enforce the concurrency rules in content-delivery-contract.md Â§3**: serialise per `packId` (a `Mutex` per pack); duplicate install is a no-op; duplicate remove returns `Reclaimed(0)`; a remove racing an in-flight install of the same matn **wins** â€” cancel first, then remove, leaving the install at `NotInstalled(Cancelled)`.
- [X] T025 Create `TEST/delivery/ContentPackRepositoryTest.kt` covering, per content-delivery-contract.md Â§6: availability reflects the engine; progress flows through; eviction flips `Installed` â†’ `NotInstalled`; process death mid-install never yields `Installed`; `bestKnownSize` prefers live over declared and falls back when the engine returns null; `removeAll` spares the starter. Use `TestDatabase` (see `TEST/db/`) plus the two fakes. **Also cover these six cases, each of which closes a specific gap** â€” (a) the starter always reads `Installed` and its `occupiedBytes` equals its measured `declaredSizeBytes`, never 0 (FR-025/SC-003); (b) connectivity lost *after* progress has advanced â†’ `NotInstalled(NoConnectivity)` with retry, no partial bytes counted (FR-016 â€” set `failInstallWith` mid-progress, not before); (c) a remove issued while the same matn is installing â†’ removal wins, install ends `NotInstalled(Cancelled)`, and the final state is the same whichever order the two calls are made; (d) a second remove on an already-removed matn is a no-op returning `Reclaimed(0)`; (e) a matn with `declaredSizeBytes == 0` reports "nothing to install" and contributes 0 to the total without distorting it; (f) platform reports installed but `locate` returns null â†’ `NotInstalled(Evicted)`, never `Installed(0)`.

### Platform adapters

- [X] T026 [P] Create `ANDROID/delivery/AndroidDeviceStorage.kt` â€” `actual`-style adapter implementing `DeviceStorage` using `android.os.StatFs` on the app's files directory for `freeSpaceBytes()` and a recursive `File.walkTopDown().sumOf { it.length() }` for `sizeOfDirectory(path)`. Takes `context: android.content.Context` in its constructor, like `ANDROID/data/db/DatabaseDriverFactory.android.kt`. No business logic.
- [X] T027 [P] Create `IOS/delivery/IosDeviceStorage.kt` â€” implements `DeviceStorage` using `NSFileManager.defaultManager.attributesOfFileSystemForPath` (key `NSFileSystemFreeSize`) and a recursive enumeration for directory size. Copy the interop style of `IOS/audio/AvQueueAudioEngine.kt`. No business logic.
- [X] T028 Create `ANDROID/delivery/PlayAssetDeliveryEngine.kt` implementing `ContentDeliveryEngine` via `AssetPackManagerFactory.getInstance(context)`. Map exactly per content-delivery-contract.md Â§1's table: `querySize` â†’ `requestPackStates(listOf(packId)).packStates()[packId]?.totalBytesToDownload()`; `install` â†’ `requestFetch(listOf(packId))`; `observe` â†’ an `AssetPackStateUpdateListener` bridged to a `callbackFlow`; `cancel` â†’ `cancel(listOf(packId))`; `remove` â†’ `requestRemovePack(packId)` returning `RemovalOutcome.Reclaimed`; `locate` â†’ `getPackLocation(packId)?.assetsPath()`; `isInstalled` â†’ status `COMPLETED`. Map `AssetPackStatus.WAITING_FOR_WIFI` â†’ `DeliveryPhase.WAITING_FOR_NETWORK_POLICY` and `REQUIRES_USER_CONFIRMATION` â†’ `DeliveryPhase.REQUIRES_CONFIRMATION` â€” these are **waits, not failures** (research D11). No decisions of any kind in this file.
- [X] T029 Create `IOS/delivery/OnDemandResourcesEngine.kt` implementing `ContentDeliveryEngine` via `NSBundleResourceRequest(tags = setOf(packId))`. `querySize` returns `null` (no such API on iOS â€” research D4). `install` â†’ `beginAccessingResources`; `observe` â†’ KVO on `request.progress.fractionCompleted`; `cancel` â†’ release the retained request; `remove` â†’ `endAccessingResources()` plus `NSBundle.mainBundle.setPreservationPriority(0.0, forTags = setOf(packId))`, returning `RemovalOutcome.ReleasedPendingSystemReclaim`; `isInstalled` â†’ `conditionallyBeginAccessingResources` reports available. Keep one retained request per active tag in a map.

### Dependency injection

- [X] T030 Register the new graph. In `KOTLIN/di/ContentModule.kt` add: `single<ContentPackRepository> { ContentPackRepositoryImpl(get(), get(), get()) }`. In `KOTLIN/di/MatnKoinStarter.kt`, extend `initMatnKoin(...)` with two new parameters `deliveryEngine: ContentDeliveryEngine` and `deviceStorage: DeviceStorage`, registered in the same inline `module { â€¦ }` block that already registers `audioEngine` and `wakeLock` (`single<ContentDeliveryEngine> { deliveryEngine }`, `single<DeviceStorage> { deviceStorage }`).
- [X] T031 Update both platform shells to pass the new dependencies to `initMatnKoin`: the Android `MainActivity` (in `androidApp/src/main/â€¦`) constructs `PlayAssetDeliveryEngine(applicationContext)` and `AndroidDeviceStorage(applicationContext)`; `IOS/MainViewController.kt` constructs `OnDemandResourcesEngine()` and `IosDeviceStorage()`. Find the existing `initMatnKoin(` call sites with a project-wide search and update every one.

### Shared formatter

- [X] T032 [P] Create `KOTLIN/presentation/common/ByteFormatter.kt` with `fun formatBytes(bytes: Long): String` producing locale-appropriate human-readable units (B / KB / MB / GB, one decimal place above KB, e.g. `2.4 MB`). Model it on the existing `KOTLIN/presentation/common/DurationFormatter.kt`. Create `TEST/presentation/ByteFormatterTest.kt` covering 0, 999, 1_024, 2_400_000, and a multi-GB value.

**Checkpoint**: schema migrated, domain types exist, both platform seams have adapters and fakes, DI
wired, everything green. User stories can now begin.

---

## Phase 3: User Story 1 - Install a matn's audio before studying it (Priority: P1) ðŸŽ¯ MVP

**Goal**: A student sees each matn's availability and size, installs one, watches progress, and
plays it â€” offline afterwards. Playback of a not-installed matn shows an install prompt instead of
failing.

**Independent Test**: On a fresh install, the starter matn plays with no network; every other matn
shows "not installed" plus a declared size; installing one completes and makes it playable; playing
a not-installed matn opens the install prompt.

### Use cases + tests

- [X] T033 [P] [US1] Create `KOTLIN/domain/usecase/ObserveContentAvailabilityUseCase.kt` â€” `FlowUseCase<String, ContentAvailability>`, pure delegation to `ContentPackRepository.observeAvailability`. Copy the shape of `KOTLIN/domain/usecase/ObserveContinueLearningUseCase.kt`.
- [X] T034 [P] [US1] Create `KOTLIN/domain/usecase/ObserveLibraryAvailabilityUseCase.kt` â€” `FlowUseCase<Unit, Map<String, ContentAvailability>>`, pure delegation to `observeLibraryAvailability`.
- [X] T035 [P] [US1] Create `KOTLIN/domain/usecase/CancelInstallUseCase.kt` â€” `UseCase<String, Unit>`, delegates to `ContentPackRepository.cancel`.
- [X] T036 [US1] Create `KOTLIN/domain/usecase/InstallMatnContentUseCase.kt` â€” `UseCase<String, Unit>` implementing the **ordered preconditions** in content-delivery-contract.md Â§4 exactly: (1) starter matn â†’ `Resource.Failure(DeliveryError.StarterMatnNotRemovable)`; (2) already `Installed`/`Installing` â†’ `Resource.Success(Unit)` no-op; (3) no connectivity â†’ `Failure(DeliveryError.DeliveryFailed(NoConnectivity))`; (4) `storage.freeSpaceBytes() < repository.bestKnownSize(matnId)` â†’ `Failure(DeliveryError.DeliveryFailed(InsufficientStorage(required, available)))`; (5) otherwise delegate to the repository. Connectivity is determined by attempting the engine call and mapping its failure â€” do NOT add a network-state API.
- [X] T037 [US1] Create `TEST/delivery/InstallMatnContentUseCaseTest.kt` covering: happy path; starter refused; duplicate request is a no-op (engine `install` called once); offline refusal carries `NoConnectivity`; insufficient-space refusal carries both the required and available byte figures; two Ù…ØªÙˆÙ† install independently and one failing leaves the other's state untouched.
- [X] T038 [P] [US1] Create `KOTLIN/domain/usecase/EnsureMatnPlayableUseCase.kt` â€” `UseCase<String, Unit>` returning `Resource.Failure(DeliveryError.ContentNotInstalled(matnId))` unless availability is `Installed`. Create `TEST/delivery/EnsureMatnPlayableUseCaseTest.kt`: fails for not-installed, passes for installed, passes for the starter.

### Playback integration

- [ ] T039 [US1] Widen the audio seam (research D6). In `KOTLIN/domain/audio/AudioSourceResolver.kt` change `suspend fun resolve(fileRef: String): String` to `suspend fun resolve(matnId: String, fileRef: String): String`. In `KOTLIN/data/audio/AudioSourceResolverImpl.kt`, inject `ContentPackRepository` + `ContentDeliveryEngine`: for the starter matn return today's `Res.getUri("files/audio/$fileRef")`; otherwise return `"file://" + engine.locate(packId) + "/audio/" + fileRef`. Update the Koin registration in `ContentModule.kt` for the new constructor.
- [ ] T040 [US1] Update the single call site: in `KOTLIN/domain/usecase/BuildPlaybackQueueUseCase.kt`, change `audioSourceResolver.resolve(asset.fileRef)` to `audioSourceResolver.resolve(params.matnId, asset.fileRef)`. Then fix every `FakeAudioSource`/resolver fake in `TEST/` to match the new signature. Run `./gradlew :shared:allTests` â€” all existing playback tests must go green again before continuing.
- [ ] T041 [US1] Add the single playback gate (research D9). In `KOTLIN/playback/PlaybackController.kt`, inject `EnsureMatnPlayableUseCase` and call it once at the top of the private `startSession(...)`; when it fails, publish the existing notice mechanism and return **without** starting a session. Do NOT add checks to `moveToVerse`, `applyCursor`, or the transition path â€” the gapless path must stay untouched (Constitution VII). Update `TEST/playback/PlaybackControllerTest.kt` with a case proving no session starts when the gate fails.

### UI components

- [ ] T042 [P] [US1] Create `KOTLIN/presentation/common/ContentAvailabilityBadge.kt` â€” stateless `@Composable fun ContentAvailabilityBadge(availability: ContentAvailability, declaredSizeBytes: Long, modifier: Modifier = Modifier)`. Renders: not installed â†’ download glyph + `formatBytes(declaredSizeBytes)`; installing â†’ `InstallProgressIndicator`; installed â†’ a checked/downloaded glyph. **Special case**: when `declaredSizeBytes == 0L` and the matn is not installed, render "nothing to install" rather than "0 B" and offer no install affordance (spec edge case "Matn with no audio at all"). Add `@Preview`s for all three states plus the zero-audio case. Place the glyphs in `KOTLIN/presentation/common/` alongside the existing `NavGlyphs.kt`/`PlaybackGlyphs.kt` â€” follow their hand-drawn `Canvas` style.
- [ ] T043 [P] [US1] Create `KOTLIN/presentation/common/InstallProgressIndicator.kt` â€” stateless, determinate on `progress.fraction`; when `phase` is `WAITING_FOR_NETWORK_POLICY` or `REQUIRES_CONFIRMATION` it renders a distinct explained wait, never a frozen bar (SC-007). `@Preview` for transferring and for waiting.
- [ ] T044 [P] [US1] Create `KOTLIN/presentation/common/ContentActionButton.kt` â€” stateless; given `ContentAvailability` plus `onInstall`, `onCancel`, `onRemove` lambdas and an `isStarter: Boolean`, renders install / cancel / remove / nothing-for-starter. `@Preview` per state.
- [ ] T045 [P] [US1] Create `KOTLIN/presentation/common/InstallPromptSheet.kt` â€” stateless bottom sheet: matn title, size, install action, dismiss. This is the actionable prompt FR-011 requires. `@Preview`.

### Screen wiring

- [ ] T046 [US1] Modify `KOTLIN/presentation/common/MatnCard.kt`: add parameter `availability: ContentAvailability? = null` and `declaredSizeBytes: Long = 0L`; render `ContentAvailabilityBadge` in the card's existing status-icon slot (see design-notes.md from T008). Keep the existing `progressFraction` parameter and all current previews working; add one preview showing a not-installed card.
- [ ] T047 [US1] Wire the library. In `KOTLIN/presentation/home/HomeUiState.kt` add `val availability: Map<String, ContentAvailability> = emptyMap()`. In `HomeViewModel.kt`, collect `ObserveLibraryAvailabilityUseCase` with the existing `collectInto` helper and pass the map through to the cards in `HomeScreen.kt`. Update `MatnNavHost.kt`'s `HomeViewModel { â€¦ }` construction with the new use case.
- [ ] T048 [US1] Extend `TEST/presentation/HomeViewModelTest.kt` with two cases: per-card availability reaches the state map; and a **mid-flight availability change** (emit a new value on the fake's flow after the ViewModel is constructed) reaches the state object. The second case is what proves the backgrounding requirement is satisfied by Flow re-collection rather than a cached snapshot (storage-ui-contract.md Â§5, FR-005). Never cache availability in composable `remember` state.
- [ ] T049 [US1] Wire the details screen. In `KOTLIN/presentation/details/MatnDetailsUiState.kt` add `availability`, `declaredSizeBytes`, and `isStarter`. In `MatnDetailsViewModel.kt` collect `ObserveContentAvailabilityUseCase` and add `onInstall()` / `onCancelInstall()` intents calling the use cases via `runUseCase`, mapping failures to a user-facing message that states the reason (offline / required-vs-available bytes). In `MatnDetailsScreen.kt` render `ContentActionButton` in the header. Update the `MatnDetailsViewModel { â€¦ }` construction in `MatnNavHost.kt`.
- [ ] T050 [US1] Extend `TEST/presentation/MatnDetailsViewModelTest.kt`: availability drives the header action; install refused offline surfaces the reason; cancel returns the state to not-installed; and a mid-flight availability change reaches the state object (same rationale as T048 â€” storage-ui-contract.md Â§5).
- [ ] T051 [US1] Gate the play affordances. In `KOTLIN/presentation/player/ReadingCarousel.kt` and `PlayerBar.kt`, when the matn is not installed, replace the play action with an affordance that opens `InstallPromptSheet` instead of starting playback. No silent no-ops (SC-007).
- [ ] T052 [US1] Register the US1 use cases in `KOTLIN/di/ContentModule.kt`: `factory { ObserveContentAvailabilityUseCase(get()) }`, `factory { ObserveLibraryAvailabilityUseCase(get()) }`, `factory { InstallMatnContentUseCase(get(), get()) }`, `factory { CancelInstallUseCase(get()) }`, `factory { EnsureMatnPlayableUseCase(get()) }`.

**Checkpoint**: US1 is independently testable â€” install, progress, cancel, offline playback of
installed content, and the install prompt all work. This is the MVP.

---

## Phase 4: User Story 2 - Remove a matn to reclaim space (Priority: P2)

**Goal**: Removal with a confirmation stating the bytes at stake, preserving every piece of personal
data, stopping playback first, and keeping Continue Learning coherent.

**Independent Test**: Install a matn, bookmark a verse, write a note, mark verses memorized, remove
it, confirm the matn is still listed/readable/searchable and all personal data survives, then
reinstall and confirm resume lands on the same verse and position.

- [ ] T053 [US2] Create `KOTLIN/domain/usecase/RemoveMatnContentUseCase.kt` â€” `UseCase<String, RemovalOutcome>` implementing the **ordered effects** in content-delivery-contract.md Â§4: (1) starter â†’ `Failure(DeliveryError.StarterMatnNotRemovable)`; (2) if this matn is the active playback session, call `PlaybackController.stop()` **before** deleting; (3) delegate to the repository and return the `RemovalOutcome` unchanged; (4) touch no personal data. Inject `PlaybackController`.
- [ ] T054 [P] [US2] Create `KOTLIN/domain/usecase/RemoveAllContentUseCase.kt` â€” `UseCase<Unit, List<RemovalOutcome>>` delegating to `ContentPackRepository.removeAll()`, which already spares the starter.
- [ ] T055 [US2] Create `TEST/delivery/RemoveMatnContentUseCaseTest.kt`: starter refused; active session stopped before the engine's `remove` is called (assert ordering); outcome propagates unchanged for both `Reclaimed` and `ReleasedPendingSystemReclaim`; repeated remove taps on the same matn call the engine once and the second returns `Reclaimed(0)` (spec edge case "Repeated taps on install/remove"); `removeAll` spares the starter and leaves it playable.
- [ ] T056 [US2] Create `TEST/delivery/RemovalPreservesUserDataTest.kt` â€” the FR-020 guard. Seed a matn with a bookmark, a note, memorized verses and a saved session; remove its content; assert every one of those rows is unchanged and the verse text is still readable and searchable; then reinstall and assert the saved session still resolves to the same verse and position (FR-023, SC-005).
- [ ] T057 [P] [US2] Create `KOTLIN/presentation/common/ConfirmRemovalDialog.kt` â€” stateless; states the bytes to be reclaimed via `formatBytes`. **The copy switches on platform semantics**: when the expected outcome is `ReleasedPendingSystemReclaim`, the text says the space is released and reclaimed by the system when needed, and must NOT promise an immediate figure (research D2). `@Preview` for both variants.
- [ ] T058 [US2] Add removal to the details screen: a remove intent in `MatnDetailsViewModel.kt` that opens `ConfirmRemovalDialog` and only calls `RemoveMatnContentUseCase` on confirmation; render the dialog in `MatnDetailsScreen.kt`. Extend `TEST/presentation/MatnDetailsViewModelTest.kt` to prove removal does not fire without confirmation.
- [ ] T059 [US2] Modify `KOTLIN/presentation/common/ContinueLearningCard.kt` to accept `isContentInstalled: Boolean` and render a reinstall offer instead of a resume action when false (FR-022). Add a `@Preview` for the not-installed variant.
- [ ] T060 [US2] Wire it: in `HomeViewModel.kt` join the Continue Learning entry with the availability map already collected in T047 and pass `isContentInstalled` to the card. Extend `TEST/presentation/HomeViewModelTest.kt` proving the card offers reinstall when its matn is not installed and never produces a resume that would fail.
- [ ] T061 [US2] Register the US2 use cases in `KOTLIN/di/ContentModule.kt`: `factory { RemoveMatnContentUseCase(get(), get()) }`, `factory { RemoveAllContentUseCase(get()) }`.

**Checkpoint**: US1 and US2 both work independently. Removal is safe and fully reversible.

---

## Phase 5: User Story 3 - See and manage total storage use in Settings (Priority: P3)

**Goal**: The Settings tab becomes a real screen with total used, free space, a size-ordered
per-matn breakdown, a non-removable starter row, and "remove all downloaded content".

**Independent Test**: Install two Ù…ØªÙˆÙ† of different sizes, open Settings, confirm the total, the
size-ordered breakdown and the starter row's "part of the app" treatment; remove one from that
screen and watch the total update without restarting.

- [ ] T062 [US3] Create `KOTLIN/domain/usecase/ObserveStorageUsageUseCase.kt` â€” `FlowUseCase<Unit, StorageUsage>` delegating to `ContentPackRepository.observeStorageUsage()`. All the ordering and totalling logic lives in the repository (T024); if it is not there yet, add it there, not here.
- [ ] T063 [US3] Create `TEST/delivery/ObserveStorageUsageUseCaseTest.kt`: entries ordered by bytes DESC; `totalUsedBytes` == sum of all entries **including** the starter, whose contribution is its measured `declaredSizeBytes` and is asserted to be non-zero (SC-003); `onDemandUsedBytes` excludes the starter and is `0L` when nothing on-demand is installed; a zero-audio matn contributes 0 and does not appear as an install target or distort the total; `freeSpaceBytes` comes from `DeviceStorage`.
- [ ] T064 [P] [US3] Create `KOTLIN/presentation/common/StorageUsageRow.kt` â€” stateless row: title + `formatBytes(bytes)` + a remove action, except when `isStarter` is true, where it renders a "part of the app" label and **no** remove action (FR-027). `@Preview` for both.
- [ ] T065 [P] [US3] Create `KOTLIN/presentation/settings/SettingsUiState.kt` â€” exactly the data class in storage-ui-contract.md Â§2 (`isLoading`, `totalUsedBytes`, `onDemandUsedBytes`, `freeSpaceBytes`, `entries`, `pendingRemoval`, `lastOutcome`).
- [ ] T066 [US3] Create `KOTLIN/presentation/settings/SettingsViewModel.kt` extending `BaseViewModel<SettingsUiState>`. Collect `ObserveStorageUsageUseCase`; implement intents `onRemoveMatn(matnId)` (sets `pendingRemoval`), `onConfirmRemoval` (calls `RemoveMatnContentUseCase`, stores `lastOutcome`), `onDismissRemoval`, `onRemoveAll` (confirmation then `RemoveAllContentUseCase`). Copy the structure of `KOTLIN/presentation/goals/GoalsViewModel.kt`.
- [ ] T067 [US3] Create `KOTLIN/presentation/settings/SettingsScreen.kt` with the mandatory split: stateless `SettingsContent(state, onRemoveMatn, onConfirmRemoval, onDismissRemoval, onRemoveAll)` holding all rendering, plus a thin `SettingsScreen(viewModel)` holder. Layout per storage-ui-contract.md Â§2 â€” storage section at the very top (SC-008): total used + free space header pair, then the breakdown list, then "remove all downloaded content". `@Preview` for loading, zero, populated, confirmation-open, and released-pending-reclaim states.
- [ ] T068 [US3] Create `TEST/presentation/SettingsViewModelTest.kt`: loading â†’ populated; zero state driven by `onDemandUsedBytes == 0L`; ordering preserved; starter row non-removable; removal updates the total without a restart; "remove all" spares the starter; both removal outcomes reach `lastOutcome`.
- [ ] T069 [US3] Route it. In `KOTLIN/presentation/navigation/MatnNavHost.kt`, change the `Routes.SETTINGS` composable from `ComingSoonScreen` to the real `SettingsScreen`, constructing `SettingsViewModel` with `viewModel { â€¦ }` and Koin-resolved use cases, exactly as the `goals` route does.
- [ ] T070 [P] [US3] Delete the stub. Settings was `ComingSoonScreen`'s **last live route** â€” Goals got its real screen in Phase 7 and Notes in Phase 6 â€” so after T069 the whole file is dead code. Delete `KOTLIN/presentation/navigation/ComingSoonScreen.kt` entirely, then fix the fallout: remove its import from `MatnNavHost.kt`, update that file's route KDoc (line ~66) which still says Settings "is still routed to the shared ComingSoonScreen", and update `NavigationTab.kt`'s doc comment (line ~19) which still says SETTINGS routes there "until Phase 8/9". Confirm with a project-wide search for `ComingSoonScreen` that only the two KDoc mentions in `GoalsScreen.kt` and `NotesTabScreen.kt` remain â€” those are historical references in comments and are fine. If the build breaks on a reference you did not anticipate, STOP and report rather than reinstating the file.
- [ ] T071 [US3] Register the US3 use case in `KOTLIN/di/ContentModule.kt`: `factory { ObserveStorageUsageUseCase(get()) }`.

**Checkpoint**: all three user stories work independently. The Settings tab is real; no
`ComingSoonScreen` route remains.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T072 [P] Finalise `specs/008-storage-downloads/design-notes.md`: record every deviation from the Stitch set â€” the Settings screen and all install/remove/progress states are original compositions with no captured design, and the availability affordance reuses the Home card's existing status-icon slot. Follow the Phase 6/7 precedent.
- [ ] T073 [P] Update `docs/DESIGN-SOURCE.md`: add a "Phase 8 â€” Storage & Downloads" note recording the same gap, and update open issue 6 to say the Settings tab now has a real screen (no tab routes to `ComingSoonScreen` any more).
- [ ] T074 Audit every file this phase created or modified, and fix what you find. **(a) Constitution VIII**: zero raw hex colors, zero magic `.dp`/`.sp` literals in screen composables, every new state-rendering composable has â‰¥1 `@Preview`, repeated units extracted as shared components rather than copy-pasted. **(b) FR-032 forward compatibility**: grep the whole `presentation/`, `domain/usecase/`, and `playback/` trees for `packId`, `AssetPack`, `NSBundleResourceRequest`, and `asset-delivery` â€” **there must be zero hits**. Only `ContentPackRepositoryImpl` and the two platform adapters may name a pack id or a platform delivery type; every layer above them speaks in `matnId` and domain types. This is the property that makes a future remote-catalog swap a one-implementation change instead of a rewrite. **(c) Constitution IV**: re-read `ANDROID/delivery/*` and `IOS/delivery/*` and confirm they contain no free-space checks, connectivity policy, ordering, or formatting â€” translation only.
- [ ] T075 Verify RTL: every new surface (Settings, badges, progress, dialogs, prompt sheet) lays out correctly right-to-left, including byte figures and progress direction (FR-031).
- [ ] T076 Run the full suite: `./gradlew :shared:allTests`. Everything green, including `MigrationV4Test`.
- [ ] T077 **MANUAL (device)** Run the Android walkthrough in `quickstart.md` Â§2 using `bundletool build-apks --local-testing`. Asset packs do not resolve from a plain debug APK, so a normal `installDebug` will NOT exercise this feature.
- [ ] T078 **MANUAL (Xcode)** Run the iOS walkthrough in `quickstart.md` Â§3, and confirm the documented removal difference: iOS reports `ReleasedPendingSystemReclaim` and the total may not drop immediately. Record it as expected behavior â€” this is a platform limitation, not a bug.
- [ ] T079 In the PR description, note the two governance items a reviewer needs to know about, both already resolved and written up: the constitution was amended to **v1.5.0** on 2026-07-25 to scope Principle VI's offline guarantee to on-device content (this phase depends on that amendment), and SC-004 was split into SC-004 (Android) / SC-004a (iOS) to match what the platforms can actually guarantee. Link `plan.md` Â§ Complexity Tracking for the dependency and iOS-limitation rationale.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies â€” start immediately.
- **Foundational (Phase 2)**: depends on Phase 1. **BLOCKS all user stories.**
- **US1 (Phase 3)**: depends on Phase 2 only.
- **US2 (Phase 4)**: depends on Phase 2; reuses US1's availability wiring in T060, so run it after US1.
- **US3 (Phase 5)**: depends on Phase 2; reuses US2's `RemoveMatnContentUseCase` in T066, so run it after US2.
- **Polish (Phase 6)**: depends on all stories you intend to ship.

### Critical path inside Phase 2

T009 â†’ T010 â†’ T011 (schema first) â†’ T012â€“T020 (types, parallel) â†’ T021 (seed) â†’ T022â€“T023 (fakes)
â†’ T024 â†’ T025 (repository) â†’ T026â€“T029 (adapters, parallel) â†’ T030 â†’ T031 (DI).

### Parallel Opportunities

- Phase 2 domain models T012â€“T019 are all separate new files â€” fully parallel.
- Phase 2 platform adapters T026â€“T029 are separate new files â€” fully parallel.
- US1 components T042â€“T045 are separate new files â€” fully parallel.
- The three use-case creations T033/T034/T035 are independent of each other.
- Phase 6 documentation tasks T072/T073 are parallel.

**Not parallel** (same file, must be sequential): T046 â†’ T047 (MatnCard then HomeScreen wiring),
T049 â†’ T058 (details ViewModel touched twice), T030 â†’ T052 â†’ T061 â†’ T071 (all edit `ContentModule.kt`).

---

## Parallel Example: Phase 2 domain models

```bash
Task: "Create ContentAvailability.kt in KOTLIN/domain/model/"
Task: "Create DeliveryProgress.kt in KOTLIN/domain/model/"
Task: "Create DeliveryFailure.kt in KOTLIN/domain/model/"
Task: "Create RemovalOutcome.kt in KOTLIN/domain/model/"
Task: "Create StorageUsage.kt in KOTLIN/domain/model/"
Task: "Create DeliveryError.kt in KOTLIN/domain/error/"
Task: "Create ContentDeliveryEngine.kt in KOTLIN/domain/delivery/"
Task: "Create DeviceStorage.kt in KOTLIN/domain/delivery/"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1: Setup (T001â€“T008)
2. Phase 2: Foundational (T009â€“T032) â€” **critical, blocks everything**
3. Phase 3: User Story 1 (T033â€“T052)
4. **STOP and VALIDATE**: run `quickstart.md` Â§2's install rows. Installing and playing a matn, and
   the install prompt for a not-installed one, all work.

### Incremental Delivery

1. Setup + Foundational â†’ foundation ready
2. + US1 â†’ install works â†’ **MVP**
3. + US2 â†’ removal is safe and reversible
4. + US3 â†’ Settings storage management
5. + Polish â†’ design notes, RTL/preview audit, manual passes

---

## Notes

- [P] = different file, no dependency on an unfinished task.
- Commit after each task or tight pair (a test plus its implementation).
- Run `./gradlew :shared:allTests` after every task; never build on a red baseline.
- The two `MANUAL` tasks (T077, T078) and the Xcode step (T007) cannot be done by an LLM â€” report
  them as outstanding rather than faking them.
- Avoid: per-verse availability checks (the spec abolished partial state), business logic in
  `androidMain`/`iosMain`, and persisting availability (it is always read through to the platform).
