# Contract: Delivery Engine, Repository & On-Device Layout

Supersedes Phase 8's `specs/008-storage-downloads/contracts/content-delivery-contract.md`. The
`ContentDeliveryEngine` seam survives; everything behind it is replaced.

---

## 1. On-device layout

```
{contentRoot}/
├── downloads/
│   ├── .tmp-{matnId}/          staging; existence implies nothing
│   │   └── audio/{fileRef}
│   └── {matnId}/               existence IS "downloaded" (FR-022)
│       └── audio/{fileRef}
└── covers/
    └── {matnId}                never counted, never removed (FR-012, Clarification 3)
```

`contentRoot` comes from `DeviceStorage.contentRootPath()` — the one genuinely platform-specific
fact (research D4). Everything else is `commonMain` over `kotlinx-io`.

**Invariants**

1. `downloads/{matnId}/` exists **iff** the matn's content is fully present. There is no partial
   directory under `downloads/` — staging happens under `.tmp-{matnId}/` and is moved into place
   only on success (research D7). FR-016.
2. `covers/` is outside `downloads/`, so the removal path cannot touch it by accident.
3. Availability is computed by asking the filesystem, never by reading a flag. FR-022, FR-045.

---

## 2. `ContentDeliveryEngine` — one shared implementation

`RemoteContentDeliveryEngine` in `commonMain`, replacing `PlayAssetDeliveryEngine` (androidMain),
`OnDemandResourcesEngine` (iosMain) and `DesktopContentDeliveryEngine` (jvmMain). FR-041.

| Member | Was | Becomes |
|---|---|---|
| `querySize(packId): Long?` | live platform size, null on iOS | **deleted** — the overview always carries the size (spec Assumption), so there is no second source to reconcile |
| `install(packId)` | `download(matnId): Resource<Unit>` | fetches the row (§3.1 of the read contract), stages, commits |
| `observe(packId)` | `observe(matnId): Flow<DeliveryProgress>` | emits from the staging writer; byte counts are real, not platform-reported |
| `cancel(packId)` | `cancel(matnId)` | cancels the coroutine, deletes `.tmp-{matnId}/` |
| `remove(packId)` | `remove(matnId): Resource<RemovalOutcome>` | deletes `downloads/{matnId}/`, reports bytes reclaimed |
| `locate(packId)` | `contentRootFor(matnId): String?` | `downloads/{matnId}/` when present |
| `isInstalled(packId)` | `isDownloaded(matnId): Boolean` | directory presence |

`RemovalOutcome` collapses to a single `Reclaimed(bytes)` value: `ReleasedPendingSystemReclaim`
existed only to describe iOS ODR's deferred reclamation, which no longer occurs. Deleting files is
immediate on every platform, so SC-008's ±5% and SC-009's 0 bytes are directly observable.

**The engine holds no policy.** Free-space checks (FR-019), connectivity checks (FR-020), the
staleness window (FR-006) and queue ordering (FR-015) all live above it, per Principle I.

---

## 3. `DownloadedContentRepository` — queue owner

Keeps its role as the availability boundary, but **not** its old name: `ContentPackRepository` is
renamed here, because this phase deletes the `content_pack` table and the `pack_id` concept entirely
(data-model §3.2). Additions and deletions:

**Added**
- A `Mutex`-guarded FIFO queue, one active transfer (Clarification 4, research D10). Membership is
  in-memory; process death resolves every queued and in-flight matn to `NotDownloaded`, which FR-017
  already prescribes.
- `ContentAvailability.Queued` emitted for queued matns.

**Deleted**
- `isStarterMatn()` — FR-040.
- `declaredSize()` / `bestKnownSize()` — the overview is the single size source.
- `removeAll()`'s starter-sparing branch — FR-031: nothing is spared.

**Ordering guarantees** (carried forward from Phase 8's Assumptions, still binding)

| Race | Outcome |
|---|---|
| duplicate `download(matnId)` while queued or downloading | no-op |
| duplicate `remove(matnId)` | no-op |
| `remove` racing an in-flight `download` of the same matn | removal wins; transfer abandoned, `.tmp-` deleted (FR-032) |
| `remove` on a queued matn | dropped from the queue, nothing transferred (FR-032, US2 scenario 11) |

---

## 4. Download sequence

```
1. precondition: connectivity            → else NotDownloaded(NoConnectivity)          FR-020
2. precondition: freeSpace > size        → else NotDownloaded(InsufficientStorage)     FR-019
3. enqueue                               → Queued                                      FR-015
4. await slot                            → Downloading(0)
5. GET matn row                          → [] ⇒ NotDownloaded(SourceUnavailable)
6. for each verse with audio: GET object → 404 ⇒ mark recitation-less, continue        FR-023
                                         → other error ⇒ abort
7. move .tmp-{matnId}/ → {matnId}/
8. one transaction: insert matn/chapter/verse/audio_asset + downloaded_matn
9. Downloaded(measuredBytes)
```

Steps 7 and 8 are ordered files-then-database deliberately (research D7): a crash between them
leaves an orphan directory that reads as not-downloaded and is reaped on next start, whereas the
reverse leaves rows claiming playable audio that is not there.

Any abort at steps 5–8 deletes `.tmp-{matnId}/` before emitting the failure. FR-017, SC-006.

---

## 5. `DeviceStorage` — one method added

```kotlin
interface DeviceStorage {
    suspend fun freeSpaceBytes(): Long              // unchanged
    suspend fun sizeOfDirectory(path: String): Long // unchanged
    suspend fun contentRootPath(): String           // NEW
}
```

Three `actual`s, each returning the platform's app-private directory (`context.filesDir`,
`NSFileManager` application-support, `System.getProperty("user.home")`-anchored on desktop). No
business logic in any of them — Principle IV.

---

## 6. Test doubles

`FakeContentDeliveryEngine` and `FakeDeviceStorage` already exist in `commonTest` and keep their
role. Both are updated to the new member set; the fake engine gains a scriptable queue so the
one-at-a-time ordering (Clarification 4) is provable without a network or a filesystem.

Required coverage:

- two downloads requested → second observes `Queued`, then `Downloading` after the first settles
- cancelling a queued matn leaves the running one untouched
- an aborted download leaves no directory and reports the space released (SC-006)
- `removeAll()` leaves zero bytes under `downloads/` and every cover intact (SC-009, FR-012)
- a `downloaded_matn` row whose directory was deleted externally reads as `NotDownloaded` (FR-045)
