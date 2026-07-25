# Implementation Plan: Storage & Downloads

**Branch**: `feature/008-storage-downloads` | **Date**: 2026-07-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/008-storage-downloads/spec.md`

## Summary

Move per-matn audio out of the app binary and behind each platform's on-demand delivery mechanism —
Play Asset Delivery asset packs on Android, On-Demand Resources on iOS — behind one `commonMain`
`ContentDeliveryEngine` seam, mirroring the existing `AudioEngine`/`WakeLock` pattern. One matn
(الأجرومية) stays bundled install-time as the starter, so a fresh offline install is still a working
app. A new `content_pack` table (additive migration `4.sqm`, schema v4 → v5) holds the matn → pack
mapping, an always-offline **declared size**, and the starter flag; availability itself is
**never persisted** but read through to the platform, so OS eviction is self-healing. Availability is
per matn and atomic — three states, no partial — which lets playback be gated once per session in
`PlaybackController.startSession` instead of per verse transition. `AudioSourceResolver` widens to
`resolve(matnId, fileRef)` so a file resolves either to Compose resources (starter) or beneath a
delivered pack root. The Settings tab gets its first real screen: total used, device free space, a
size-ordered per-matn breakdown with the starter marked non-removable, and "remove all downloaded
content". Removal deletes bytes only — no schema edge runs from `content_pack` to any personal-data
table, so bookmarks, notes, memorization, and saved sessions survive structurally.

**One platform conflict is carried, not hidden**: iOS exposes no API to force-purge ODR content, so
removal there releases the tag and the OS reclaims later. `RemovalOutcome` makes that explicit at
every call site rather than letting the UI claim a reclaimed figure that is false. See Complexity
Tracking.

## Technical Context

**Language/Version**: Kotlin 2.4.10, Compose Multiplatform 1.11.1 (Material 3), coroutines/Flow.

**Primary Dependencies**: SQLDelight 2.0.2 (`ContentDatabase`/`Content.sq`), Koin 4.0
(`di/ContentModule.kt`), Jetbrains `navigation-compose`, Media3/ExoPlayer (Android),
AVQueuePlayer (iOS). **One new dependency**: `com.google.android.play:asset-delivery-ktx`
(androidMain only; justified in Complexity Tracking). iOS uses platform `Foundation`
(`NSBundleResourceRequest`) — no new dependency.

**Storage**: SQLDelight — one new table (`content_pack`) via additive migration `4.sqm` (v4 → v5).
No existing table is modified. Delivered audio lives in platform-managed pack storage, outside the
database entirely.

**Testing**: `kotlin.test` + `kotlinx-coroutines-test` in `commonTest`, driven by
`FakeContentDeliveryEngine` and `FakeDeviceStorage` (repositories via the in-memory driver per
`TestDatabase.kt`; use cases and ViewModels device-free). Migration guarded by `MigrationV4Test`
mirroring `MigrationV3Test`. A `@Preview` per Principle II for every new state-rendering composable.
Real delivery is verified manually via `bundletool --local-testing` (Android) and Xcode-hosted ODR
(iOS) — see [quickstart.md](./quickstart.md).

**Target Platform**: Android + iOS via the KMP `shared` module. **Android distribution changes**:
Play Asset Delivery requires an Android App Bundle served through Google Play; asset packs do not
resolve from a standalone APK.

**Project Type**: Mobile app (KMP) — single `shared` module holding domain/data/presentation, plus
one Gradle asset-pack module per on-demand matn.

**Performance Goals**: SC-004 — removal updates the Settings figures within 2 s (Android; see the
iOS caveat). SC-007 — a blocked play action opens the install prompt within one frame, since the
gate is a single per-matn check, not a per-verse lookup. SC-010 — a ~100-verse matn installs within
30 s on broadband, which is the platform's transfer rate, not app overhead.

**Constraints**: Offline for everything already installed (FR-012, SC-011); connectivity required
only to acquire content, which Constitution Principle VI explicitly permits as of v1.5.0, subject to
the bundled-starter and offline-thereafter conditions this phase satisfies. Atomic per-matn availability, never per verse (FR-001). Availability derived,
never persisted (research D5). No notifications (FR-005). Removal never touches personal data
(FR-020) — enforced structurally. All new surfaces native RTL on Phase 10 tokens.

**Scale/Scope**: 1 new table + 1 migration; 2 new platform interfaces (`ContentDeliveryEngine`,
`DeviceStorage`) with 2 actuals each; 1 new repository; 8 use cases; 1 new screen (Settings)
replacing the last `ComingSoonScreen` route; 6 shared components; 1 signature change
(`AudioSourceResolver`) with a single call site; 1 guard in `PlaybackController.startSession`;
Home/details/carousel/Continue-Learning wiring; per-matn asset-pack Gradle modules plus iOS ODR tags.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Clean Architecture & Layer Boundaries** — PASS. `ContentDeliveryEngine`, `DeviceStorage`,
  `ContentPackRepository`, the availability models and the use cases are pure Kotlin in
  `commonMain/domain`; the SQLDelight + engine composition lives in `data`; screens reach domain only
  through use cases injected via Koin. Platform delivery code sits at the outermost edge, behind an
  interface, exactly like `Media3AudioEngine` today.
- **II. MVVM Presentation** — PASS by construction. `SettingsViewModel` exposes one immutable state
  object via `StateFlow` through `BaseViewModel`; the Settings screen ships a stateless content
  composable plus a thin stateful holder, with `@Preview`s for loading / zero / populated /
  confirmation / released-pending-reclaim states (enumerated in contracts/storage-ui-contract.md).
- **III. DRY via Base Abstractions** — PASS. New use cases implement the existing
  `UseCase`/`FlowUseCase` contracts with `Resource`; the availability badge, progress indicator,
  action button, storage row, confirmation dialog and install prompt are each extracted once and
  shared across library / details / Settings rather than copy-pasted (Principle VIII second-use rule);
  `formatBytes` joins the existing `DurationFormatter` as a shared formatter.
- **IV. Shared-First Multiplatform** — PASS. Every decision — free-space precondition, connectivity
  precondition, starter protection, ordering, zero-state rule, gating, formatting — lives in
  `commonMain`. The four actuals are thin adapters that translate platform status codes into domain
  types and contain no business logic (contracts §1 spells out the mapping table). No logic is
  duplicated between `androidMain` and `iosMain`.
- **V. Test-First & Testable Design** — PASS. Both platform seams are interfaces, not
  `expect`/`actual` functions, precisely so `FakeContentDeliveryEngine`/`FakeDeviceStorage` can script
  every path — including process death, eviction, Wi-Fi parking, and both removal outcomes — with no
  device. The untestable-in-CI surface is reduced to two thin adapters per platform, verified by the
  documented manual pass.
- **VI. Offline-First & Future-Proof Data** — PASS **against constitution v1.5.0**. The principle was
  amended on 2026-07-25 (resolving `/speckit-analyze` finding D1) to scope the offline guarantee to
  content already on the device and to permit connectivity for *acquisition* under two binding
  conditions. This phase satisfies both: FR-014 ships a bundled starter matn so a network-less fresh
  install is a working app, and FR-012 keeps everything already installed fully usable offline. No
  personal data depends on a network round-trip. Schema stays sync-safe: `content_pack` is keyed by
  the matn's stable UUID and carries an authored, stable `pack_id` never derived from a title.
- **VII. Experience Fidelity: Audio, RTL & Accessibility** — PASS with gates. The gapless transition
  path is untouched: the availability check is one guard at `startSession`, never inside
  `moveToVerse`/`applyCursor` (research D9). All new surfaces render native RTL, including size
  figures and progress direction. A parked transfer must render as an explained wait, never a frozen
  bar (SC-007).
- **VIII. Design Fidelity & Reusable Composables** — PASS with gates. Implementers MUST fetch
  *Home / Library* (`618643f891144557b5a4ddf4bbad0c03`) and *Matn Details (Refined)*
  (`472161bbcdee47adb26d94a1fefcc3a8`) from the Stitch MCP server before UI work, and reuse the
  Home card's existing status-icon slot for the availability affordance rather than inventing a
  region. The registry has **no Settings screen and no install/remove states** — those are original
  compositions from the Phase 10 token set, which MUST be recorded in
  `specs/008-storage-downloads/design-notes.md` before merge (the Phase 6 / 7 precedent). Zero raw
  hex / `.dp` / `.sp` literals.

**Post-design re-check (after Phase 1)**: PASS on all eight principles, with no outstanding
deviations. Phase 1 introduced no violations. Two items remain in Complexity Tracking — the new
Android dependency, and the iOS ODR reclamation limit from research D2 — neither of which is a
principle conflict: the first is the constitution's standard new-dependency justification, and the
second is a platform limitation now reflected in SC-004 itself rather than contradicting it.

## Project Structure

### Documentation (this feature)

```text
specs/008-storage-downloads/
├── plan.md              # This file
├── research.md          # Phase 0 output — D1–D13
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── content-delivery-contract.md  # engine + storage seams, repository, use cases, playback integration
│   └── storage-ui-contract.md        # Settings screen, shared components, modified surfaces, previews
├── checklists/
│   └── requirements.md  # spec quality checklist (16/16)
└── tasks.md             # Phase 2 output (/speckit-tasks — not created by /speckit-plan)
```

### Source Code (repository root)

```text
settings.gradle.kts                         # MODIFY — include one asset-pack module per on-demand matn
androidApp/build.gradle.kts                 # MODIFY — android { assetPacks += … }; PAD dependency wiring
packs/matn_structured_sample/               # NEW — worked example asset-pack module (com.android.asset-pack)
├── build.gradle.kts                        #        packName + dynamicDelivery { deliveryType = "on-demand" }
└── src/main/assets/audio/…                 #        the matn's per-verse mp3s, moved out of composeResources
iosApp/iosApp.xcodeproj                     # MODIFY — ODR tags per matn, matching the pack_id slugs

shared/src/commonMain/
├── composeResources/files/audio/           # MODIFY — starter matn's files stay; others move to packs
├── sqldelight/com/giraffe/matn/db/
│   ├── Content.sq                          # MODIFY — content_pack table + queries
│   └── 4.sqm                               # NEW — v4→v5 additive migration (content_pack)
└── kotlin/com/giraffe/matn/
    ├── domain/
    │   ├── delivery/
    │   │   ├── ContentDeliveryEngine.kt    # NEW — platform seam (install/observe/cancel/remove/locate)
    │   │   └── DeviceStorage.kt            # NEW — free space + directory size seam
    │   ├── model/
    │   │   ├── ContentAvailability.kt      # NEW — NotInstalled | Installing | Installed (atomic)
    │   │   ├── DeliveryProgress.kt         # NEW — bytes/fraction/phase
    │   │   ├── DeliveryFailure.kt          # NEW — retryable reasons
    │   │   ├── RemovalOutcome.kt           # NEW — Reclaimed | ReleasedPendingSystemReclaim (D2)
    │   │   └── StorageUsage.kt             # NEW — total/onDemand/free + MatnStorageEntry
    │   ├── audio/AudioSourceResolver.kt    # MODIFY — resolve(matnId, fileRef) (D6)
    │   ├── repository/ContentPackRepository.kt  # NEW
    │   └── usecase/
    │       ├── ObserveContentAvailabilityUseCase.kt   # NEW
    │       ├── ObserveLibraryAvailabilityUseCase.kt   # NEW
    │       ├── InstallMatnContentUseCase.kt           # NEW — connectivity + free-space preconditions
    │       ├── CancelInstallUseCase.kt                # NEW
    │       ├── RemoveMatnContentUseCase.kt            # NEW — stops playback first; starter refused
    │       ├── RemoveAllContentUseCase.kt             # NEW — spares the starter
    │       ├── ObserveStorageUsageUseCase.kt          # NEW — ordering + zero-state rule
    │       └── EnsureMatnPlayableUseCase.kt           # NEW — the single playback gate (D9)
    ├── data/
    │   ├── audio/AudioSourceResolverImpl.kt  # MODIFY — Compose resources for starter, pack root otherwise
    │   ├── delivery/ContentPackRepositoryImpl.kt  # NEW — SQLDelight + engine + DeviceStorage
    │   └── seed/ContentSeedLoader*.kt        # MODIFY — seed content_pack rows (pack id, size, starter)
    ├── playback/PlaybackController.kt        # MODIFY — one availability guard in startSession
    ├── di/ContentModule.kt                   # MODIFY — register repository, use cases, engine, storage
    ├── di/MatnKoinStarter.kt                 # MODIFY — accept platform engine + storage like audioEngine
    └── presentation/
        ├── common/
        │   ├── ContentAvailabilityBadge.kt   # NEW
        │   ├── InstallProgressIndicator.kt   # NEW
        │   ├── ContentActionButton.kt        # NEW
        │   ├── StorageUsageRow.kt            # NEW
        │   ├── ConfirmRemovalDialog.kt       # NEW
        │   ├── InstallPromptSheet.kt         # NEW
        │   ├── ByteFormatter.kt              # NEW — formatBytes, RTL-correct
        │   ├── MatnCard.kt                   # MODIFY — availability badge in the status slot
        │   └── ContinueLearningCard.kt       # MODIFY — reinstall offer (FR-022)
        ├── settings/
        │   ├── SettingsScreen.kt             # NEW — stateless content + thin holder
        │   ├── SettingsUiState.kt            # NEW
        │   └── SettingsViewModel.kt          # NEW
        ├── navigation/
        │   ├── MatnNavHost.kt                # MODIFY — settings route → real screen
        │   ├── NavigationTab.kt              # MODIFY — doc comment
        │   └── ComingSoonScreen.kt           # MODIFY — drop the Settings preview
        ├── home/{HomeUiState,HomeViewModel}.kt      # MODIFY — per-card availability
        ├── details/{MatnDetailsUiState,…ViewModel,…Screen}.kt  # MODIFY — header action + dialog
        └── player/{ReadingCarousel,PlayerBar}.kt    # MODIFY — install prompt replaces play

shared/src/androidMain/kotlin/com/giraffe/matn/
├── delivery/PlayAssetDeliveryEngine.kt      # NEW — AssetPackManager adapter
└── delivery/AndroidDeviceStorage.kt         # NEW — StatFs + recursive size

shared/src/iosMain/kotlin/com/giraffe/matn/
├── delivery/OnDemandResourcesEngine.kt      # NEW — NSBundleResourceRequest adapter
└── delivery/IosDeviceStorage.kt             # NEW — NSFileManager

shared/src/commonTest/kotlin/com/giraffe/matn/
├── delivery/
│   ├── FakeContentDeliveryEngine.kt         # NEW — scripts every status path
│   ├── FakeDeviceStorage.kt                 # NEW
│   ├── ContentPackRepositoryTest.kt         # NEW — availability, progress, eviction, usage, ordering
│   ├── InstallMatnContentUseCaseTest.kt     # NEW — offline / no-space / duplicate / independence
│   ├── RemoveMatnContentUseCaseTest.kt      # NEW — starter refused, playback stopped, data preserved
│   └── EnsureMatnPlayableUseCaseTest.kt     # NEW — gate behaviour
├── playback/PlaybackControllerTest.kt       # MODIFY — no session starts when the gate fails
└── presentation/
    ├── SettingsViewModelTest.kt             # NEW — zero/populated/ordering/remove-all/outcomes
    ├── HomeViewModelTest.kt                 # MODIFY — availability + Continue Learning reinstall
    └── MatnDetailsViewModelTest.kt          # MODIFY — header action + confirmation gate

shared/src/androidHostTest/kotlin/com/giraffe/matn/db/
└── MigrationV4Test.kt                       # NEW — v4 data survives v4→v5 (mirrors MigrationV3Test)
```

**Structure Decision**: Single `shared` KMP module, mirroring the layering already in place
(`domain` / `data` / `presentation` + `playback` + `di`). A new `presentation/settings` package sits
beside `home`/`details`/`goals`/`notes`/`search`; a new `domain/delivery` + `data/delivery` pair sits
beside the existing `audio` packages, which is the closest existing precedent. The one structural
addition outside `shared` is a Gradle asset-pack module per on-demand matn under `packs/` — required
by Play Asset Delivery, which resolves packs by module name.

## Complexity Tracking

> Two items require justification: one new dependency, and one platform limitation that scopes a
> spec success criterion to Android. **No principle deviation remains** — the Principle VI conflict
> this table previously carried was resolved by amending the constitution to v1.5.0 on 2026-07-25.

| Item | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| **New dependency** — `com.google.android.play:asset-delivery-ktx` (androidMain only) | The only supported way to fetch, observe, cancel, remove, and locate Play asset packs. There is no platform-neutral alternative and no way to reimplement it. | Dynamic feature modules carry executable code and are the wrong tool for pure assets. Rolling our own downloader means hosting, resume logic, and integrity checks — far more code and a server bill, and it forfeits Play's delivery guarantees. |
| **iOS ODR cannot guarantee reclamation on removal** — SC-004 is scoped to Android | iOS exposes no API to force-purge ODR content: `endAccessingResources()` only releases the app's claim and `setPreservationPriority` is a documented hint, so the OS reclaims when it needs space (research D2). `RemovalOutcome.ReleasedPendingSystemReclaim` propagates this to the UI so the confirmation copy stays truthful instead of promising a figure that is false. **SC-004 was amended on 2026-07-25** to state the Android guarantee and the iOS behaviour separately, so spec and platform now agree. | Showing the Android copy on both platforms would state something untrue and let SC-003's total drift from the device's real footprint with no way to reconcile. Switching iOS to a self-hosted downloader would restore an identical guarantee but is a substantially larger scope change; it is recorded in the spec as future scope, and the `ContentDeliveryEngine` seam makes it a one-implementation swap if it is ever wanted. |
