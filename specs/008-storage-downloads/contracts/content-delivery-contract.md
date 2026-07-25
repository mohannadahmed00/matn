# Contract: Content Delivery & Storage

The platform seam, the repository above it, and the use cases the presentation layer consumes.
Types referenced here are defined in [data-model.md](../data-model.md).

## 1. `ContentDeliveryEngine` (domain interface, platform-implemented)

`domain/delivery/ContentDeliveryEngine.kt` — the only place the delivery platform is visible. Same
shape as the existing `AudioEngine`/`WakeLock` seams: an interface in `commonMain`, one thin actual
per platform, no business logic in either (Principle IV).

```kotlin
interface ContentDeliveryEngine {
    /** Live size from the platform, or null when unavailable (offline, or iOS — research D4). */
    suspend fun querySize(packId: String): Long?

    /** Starts or rejoins a transfer. Idempotent per packId (duplicate taps are a no-op). */
    suspend fun install(packId: String): Resource<Unit>

    /** Progress for one pack. Emits on every platform state change; completes never. */
    fun observe(packId: String): Flow<DeliveryProgress>

    /** Best-effort abort; partial bytes are released by the platform. */
    suspend fun cancel(packId: String)

    /** Android: deletes and reports reclaimed bytes. iOS: releases the tag (research D2). */
    suspend fun remove(packId: String): Resource<RemovalOutcome>

    /** Filesystem root of an installed pack, or null when not present. */
    suspend fun locate(packId: String): String?

    /** True when the pack's content is present and complete right now. */
    suspend fun isInstalled(packId: String): Boolean
}
```

**Implementation notes**

| | Android (`PlayAssetDeliveryEngine`) | iOS (`OnDemandResourcesEngine`) |
|---|---|---|
| `querySize` | `requestPackStates().packStates()[pack]?.totalBytesToDownload()` | always `null` — no such API |
| `install` | `requestFetch(listOf(pack))` | `NSBundleResourceRequest(tags:).beginAccessingResources` |
| `observe` | `AssetPackStateUpdateListener` | KVO on `request.progress.fractionCompleted` |
| `cancel` | `cancel(listOf(pack))` | release the request object |
| `remove` | `requestRemovePack(pack)` → `Reclaimed` | `endAccessingResources()` + lowest preservation priority → `ReleasedPendingSystemReclaim` |
| `locate` | `getPackLocation(pack)?.assetsPath()` | tag directory resolved from the retained request's bundle |
| `isInstalled` | `AssetPackStatus.COMPLETED` | `conditionallyBeginAccessingResources` reports available |

**Rules**

- Neither actual decides anything: no free-space checks, no connectivity policy, no ordering, no
  formatting. Those live in the use cases below.
- Both actuals must survive process death — state is always re-read, never cached in the adapter (D5).
- Play statuses `WAITING_FOR_WIFI` and `REQUIRES_USER_CONFIRMATION` map to
  `DeliveryProgress.phase`, not to failures (D11).

## 2. `DeviceStorage` (domain interface, platform-implemented)

```kotlin
interface DeviceStorage {
    suspend fun freeSpaceBytes(): Long
    suspend fun sizeOfDirectory(path: String): Long
}
```

Android: `StatFs` + recursive walk. iOS: `NSFileManager.attributesOfFileSystemForPath` + enumeration.
Faked in `commonTest` (D8).

## 3. `ContentPackRepository` (domain interface, data-implemented)

```kotlin
interface ContentPackRepository {
    fun observeAvailability(matnId: String): Flow<ContentAvailability>
    fun observeLibraryAvailability(): Flow<Map<String, ContentAvailability>>
    fun observeStorageUsage(): Flow<StorageUsage>

    suspend fun declaredSize(matnId: String): Long
    suspend fun bestKnownSize(matnId: String): Long      // live ?: declared (D4)

    suspend fun install(matnId: String): Resource<Unit>
    suspend fun cancel(matnId: String)
    suspend fun remove(matnId: String): Resource<RemovalOutcome>
    suspend fun removeAll(): Resource<List<RemovalOutcome>>   // spares the starter
}
```

`ContentPackRepositoryImpl` composes: SQLDelight (`content_pack` rows) + `ContentDeliveryEngine`
(truth about presence and progress) + `DeviceStorage` (occupied and free bytes). It owns the
in-memory per-pack progress `StateFlow` map and the `matnId ↔ packId` translation, so no layer above
it ever sees a pack id.

**Concurrency rules the repository enforces**

- **Duplicate install** for a pack already `Installing` or `Installed`: no-op, never a second transfer.
- **Duplicate remove** for a pack already `NotInstalled`: no-op, returns `Reclaimed(0)`.
- **Remove racing an in-flight install of the same matn**: **removal wins.** The repository cancels
  the transfer first, then removes; the install resolves to `NotInstalled(Cancelled)`. Serialise per
  `packId` so the two can never interleave (spec edge case "Removing content while it is being
  installed").
- **`occupiedBytes` resolution** follows data-model §2.1's three-step priority, including the
  starter matn's measured-catalog-size rule.

## 4. Use cases

| Use case | Contract | Requirements |
|---|---|---|
| `ObserveContentAvailabilityUseCase` | `FlowUseCase<String, ContentAvailability>` | FR-002 |
| `ObserveLibraryAvailabilityUseCase` | `FlowUseCase<Unit, Map<String, ContentAvailability>>` | FR-002 |
| `InstallMatnContentUseCase` | `UseCase<String, Resource<Unit>>` | FR-004, FR-007, FR-015 |
| `CancelInstallUseCase` | `UseCase<String, Resource<Unit>>` | FR-005, FR-006 |
| `RemoveMatnContentUseCase` | `UseCase<String, Resource<RemovalOutcome>>` | FR-017, FR-021, FR-022, FR-027 |
| `RemoveAllContentUseCase` | `UseCase<Unit, Resource<List<RemovalOutcome>>>` | FR-029 |
| `ObserveStorageUsageUseCase` | `FlowUseCase<Unit, StorageUsage>` | FR-025, FR-026, FR-030 |
| `EnsureMatnPlayableUseCase` | `UseCase<String, Resource<Unit>>` | FR-011, FR-012 |

**`InstallMatnContentUseCase` — ordered preconditions**

1. Starter matn → `Failure(AppError.Validation)`; it is never an install target.
2. Already `Installed` or `Installing` → success no-op (duplicate-tap edge case).
3. No connectivity → `Failure(NoConnectivity)`, nothing started, no partial state (FR-015).
4. `freeSpaceBytes() < bestKnownSize()` → `Failure(InsufficientStorage)` carrying required and
   available bytes so the message can state both (FR-007).
5. Otherwise delegate to the engine.

**`RemoveMatnContentUseCase` — ordered effects**

1. Starter matn → `Failure(AppError.Validation)` (FR-027).
2. If this matn is the active playback session, stop playback first and leave the player in its
   defined stopped state (FR-021) — the stop precedes deletion, never races it.
3. Delegate to the engine; return the `RemovalOutcome` unchanged so the caller can render honest copy.
4. Touch no personal data — structurally guaranteed (data-model §4).

**`EnsureMatnPlayableUseCase`** returns `Failure(AppError.ContentNotInstalled(matnId))` when the matn
is not `Installed`. `PlaybackController.startSession` consults it once per session (D9); the UI
consults it before rendering a play affordance so the install prompt replaces it (FR-011).

## 5. Playback integration

- `AudioSourceResolver.resolve(fileRef)` → **`resolve(matnId, fileRef)`** (D6). The impl returns a
  Compose-resources URI for the starter matn and a file URI beneath `locate(packId)` otherwise.
- `BuildPlaybackQueueUseCase` — the only caller — passes the `matnId` it already holds. Its existing
  "verse with no audio row is silently omitted" behaviour is unchanged; absence of *content* is now
  caught earlier by the gate, so the queue builder never sees a half-available matn.
- `PlaybackController` gains one guard in `startSession`. No change to `moveToVerse`, `applyCursor`,
  or the gapless transition path (Principle VII).
- Continue Learning: `ObserveContinueLearningUseCase`'s entry is joined with availability so the Home
  card renders a reinstall offer instead of a resume that would fail (FR-022). The saved session row
  is never deleted, so reinstalling resumes exactly where it left off (FR-023).

## 6. Test obligations (`commonTest`, device-free)

`FakeContentDeliveryEngine` scripts every path; `FakeDeviceStorage` scripts space.

- Install: happy path; offline refusal; insufficient-space refusal stating both figures; duplicate
  request is a no-op; two متون install independently and one failing leaves the other alone.
- Progress: transferring → completed; parked in `WaitingForNetworkPolicy` then resuming; cancel
  releases and returns to `NotInstalled`.
- Interruption: engine reports not-installed after a simulated kill → availability is `NotInstalled`,
  never `Installed`; no orphaned bytes counted in the total.
- **Connectivity lost mid-transfer** (FR-016): failure injected *after* progress has advanced →
  availability is `NotInstalled(NoConnectivity)` with a retry available, never `Installed`, and no
  partial bytes remain counted.
- **Remove racing an install** of the same matn: removal wins; the install resolves to
  `NotInstalled(Cancelled)`; final state is consistent regardless of call ordering.
- **Duplicate remove** taps: second call is a no-op returning `Reclaimed(0)`, not a second deletion.
- **Zero-audio matn** (`declared_size_bytes == 0`): reports a defined "nothing to install" state, is
  not offered as an install target, and contributes 0 to the storage total without distorting it.
- **Starter `occupiedBytes`**: equals its measured `declared_size_bytes` and is included in
  `totalUsedBytes` (data-model §2.1 rule 1, SC-003).
- Eviction: engine flips an installed pack to absent → availability follows, personal data intact,
  reinstall offered.
- Removal: reclaims and updates usage; starter refused; active session stopped before deletion;
  bookmarks/notes/memorization/saved-session all unchanged afterwards; reinstall resumes at the same
  verse and position.
- Removal outcomes: both `Reclaimed` and `ReleasedPendingSystemReclaim` propagate to the ViewModel
  unchanged.
- Storage usage: total = Σ entries including starter; `onDemandUsedBytes` drives the zero state;
  ordering is size-descending; "remove all" spares the starter and leaves it playable.
- Sizes: declared size renders with the engine offline; live size supersedes when available;
  occupied size supersedes once installed.
- Gate: `EnsureMatnPlayableUseCase` fails for a not-installed matn and passes for the starter;
  `PlaybackController.startSession` does not start a session when the gate fails.
- Migration: `MigrationV4Test` — v4 data survives v4 → v5 and every matn gains a `content_pack` row.
