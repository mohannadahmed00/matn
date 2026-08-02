# Contract: Retirement of the Superseded Delivery Model

FR-038 – FR-042 require deletion, not bypass. The constitution's deferred TODO is explicit:
*"Phase 13 must delete, not merely bypass, the superseded delivery stack … Leaving it in place would
contradict Principle III and the amended Principle VI simultaneously."*

This is the closing checklist. Every row is verifiable by grep, and SC-012 is satisfied only when
all of them are empty.

---

## 1. Files and modules deleted outright

| Path | Reason |
|---|---|
| `packs/matn_structured_sample/` (whole directory, incl. 4 `.mp3` assets) | FR-038 |
| `shared/src/androidMain/kotlin/com/giraffe/matn/delivery/PlayAssetDeliveryEngine.kt` | FR-039, FR-041 |
| `shared/src/iosMain/kotlin/com/giraffe/matn/delivery/OnDemandResourcesEngine.kt` | FR-039, FR-041 |
| `shared/src/jvmMain/kotlin/com/giraffe/matn/delivery/DesktopContentDeliveryEngine.kt` | FR-041; it also reports every pack installed, which is now a lie (research D3) |
| `shared/src/commonMain/kotlin/com/giraffe/matn/data/seed/` (`ContentSeedLoader`, `ContentSeedLoaderImpl`, `SeedContent`, `SeedMatnProjection`) | FR-038 — the download path replaces it entirely |
| Bundled audio under Compose resources (`files/audio/ajurrumiyya_*.mp3` and siblings) | FR-038, SC-005 |

## 2. Build configuration

| Location | Change |
|---|---|
| `settings.gradle.kts` | remove `include(":packs:matn_structured_sample")` |
| `androidApp/build.gradle.kts:56` | remove `assetPacks += listOf(":packs:matn_structured_sample")` |
| `gradle/libs.versions.toml:66` | remove `play-asset-delivery-ktx` |
| `gradle/libs.versions.toml:24` | remove `playAssetDelivery = "2.2.2"` |
| `shared/build.gradle.kts` | drop the `play-asset-delivery-ktx` androidMain dependency; add `kotlinx-io-core` to commonMain (research D4) |
| iOS project | remove On-Demand Resources tags |

## 3. Code paths deleted

| Symbol | Site | Replaced by |
|---|---|---|
| `bundledSampleMatns()` | `di/MatnKoinStarter.kt:190` | nothing — the library starts empty (FR-001) |
| the seed bootstrap call | `di/MatnKoinStarter.kt:174` | nothing |
| `isStarter` | `SeedContent.kt`, `SeedMatnProjection.kt`, `MatnDetails.kt`, `StorageUsage.kt`, `ContentPackRepositoryImpl.kt` (10 sites) | FR-040 |
| `ContentPackRepository.isStarterMatn()` | interface + impl + 3 call sites | FR-040 |
| starter branch in `AudioSourceResolverImpl.resolve()` | `data/audio/AudioSourceResolverImpl.kt:34` | `file://` under `downloads/{matnId}/audio/` for every matn, uniformly |
| starter guard in `InstallMatnContentUseCase` / `RemoveMatnContentUseCase` | 2 sites | FR-031 |
| starter-sparing in `removeAll()` | `ContentPackRepositoryImpl.kt:311` | FR-031 — "remove all" spares nothing |
| `isStarter` param in `ContentActionButton` | `presentation/common/ContentActionButton.kt` (+ its previews) | FR-040 |
| starter row label in `StorageUsageRow` | `presentation/common/StorageUsageRow.kt:53` | FR-031 — no row is marked non-removable |
| `DeliveryError.StarterMatnNotRemovable` | `domain/error/DeliveryError.kt` | FR-040 |
| `MatnStorageEntry.isStarter`, `StorageUsage.onDemandUsedBytes` | `domain/model/StorageUsage.kt` | data-model §2.5 |
| `DeliveryFailure.Evicted`, `DeliveryFailure.Unknown` | `domain/model/DeliveryFailure.kt` | data-model §2.3 |
| `DeliveryPhase.WAITING_FOR_NETWORK_POLICY`, `.REQUIRES_CONFIRMATION` | `domain/model/DeliveryProgress.kt` | data-model §2.4 |
| `RemovalOutcome.ReleasedPendingSystemReclaim` | `domain/model/` | delivery-contract §2 |
| `content_pack` table | `Content.sq` | `catalog_overview` (data-model §1.1) |

**Tests that must be rewritten, not deleted** — each is written against `ContentSeedLoaderImpl` and
will stop compiling when `data/seed/` goes. They encode behaviour that still holds; only their
setup changes to the download path:

`RemovalPreservesUserDataTest` (its "no edge to personal data" premise is what research D5
replaces — this becomes the SC-010 guard), `ContentPackRepositoryTest`,
`InstallMatnContentUseCaseTest`, `RemoveMatnContentUseCaseTest`, `ObserveStorageUsageUseCaseTest`,
`EnsureMatnPlayableUseCaseTest`.

## 4. Verification

SC-012 is met when all of the following return nothing outside `docs/`, `specs/`, and this file:

```bash
grep -rn "isStarter\|StarterMatn"            --include=*.kt .
grep -rn "bundledSampleMatns\|ContentSeedLoader" --include=*.kt .
grep -rn "assetPack\|asset-delivery\|AssetPackManager" --include=*.kt --include=*.kts --include=*.toml .
grep -rn "OnDemandResources\|ODR"            --include=*.kt --include=*.plist .
grep -rn "content_pack\|pack_id"             --include=*.sq --include=*.kt .
find packs -type d                            # must not exist
```

And, per FR-042, all three student clients plus `:teacherApp` build and their tests pass:

```bash
./gradlew :shared:allTests :androidApp:assembleDebug :desktopApp:build :teacherApp:build
```

## 5. Explicitly *not* deleted

| Kept | Why |
|---|---|
| `ContentDeliveryEngine` interface | the seam Principle I's rationale names; only its implementations change |
| The repository abstraction behind `ContentDeliveryEngine` | still the availability boundary, though **renamed** `ContentPackRepository` → `DownloadedContentRepository` (delivery-contract §3, data-model §3.2) — the role survives, the pack-era name does not |
| `DeviceStorage` | still the platform filesystem seam; gains one method |
| `ContentAvailability`, `DeliveryProgress`, `DeliveryFailure`, `StorageUsage` | shapes survive with the edits in data-model §2 |
| Phase 11's `PostgrestClient`, `StorageRestClient`, `MatnRow`, `SupabaseConfig` | reused as-is apart from the token change (student-read-contract §1.1) |
| `docs/ROADMAP.md` § Phase 8 | it already carries the "superseded by Phase 13" note; history stays readable |
