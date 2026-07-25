# Contract: Storage & Downloads UI

Screens, shared components, states, and preview obligations. Every composable here is built from the
Phase 10 tokens (`presentation/theme/{Color,Type,Shape,Spacing}.kt`) with **zero** raw hex, `.dp`, or
`.sp` literals (Principle VIII).

## 1. Design source and its gap

`docs/DESIGN-SOURCE.md` registers no Settings screen and no captured install / progress / remove
states. Before UI work the implementer MUST still fetch *Home / Library* (`618643f8…`) and *Matn
Details (Refined)* (`472161bb…`) from the Stitch MCP server to place the availability affordance
inside the existing card and header layouts. The Settings screen and every install/remove state are
**original compositions built from the existing token set** — the same fallback Phases 6 and 7 used.
Record the deviations in `specs/008-storage-downloads/design-notes.md` before merge.

The Home/Library card already carries a sync-status icon slot in the fetched design (noted in
DESIGN-SOURCE issue 8). That slot is the natural home for the availability affordance — reuse it
rather than adding a new region.

## 2. New screen — Settings

`presentation/settings/` — `SettingsScreen.kt`, `SettingsUiState.kt`, `SettingsViewModel.kt`.
Replaces the `ComingSoonScreen` stub on the `settings` route (FR-024). Stateless content composable
plus thin stateful holder, per Principle II.

```
SettingsUiState
├── isLoading: Boolean
├── totalUsedBytes: Long
├── onDemandUsedBytes: Long
├── freeSpaceBytes: Long
├── entries: List<MatnStorageEntry>        -- size DESC
├── pendingRemoval: RemovalTarget?          -- drives the confirmation dialog
└── lastOutcome: RemovalOutcome?            -- drives honest post-removal messaging
```

Intents: `OnRemoveMatn(matnId)`, `OnRemoveAll`, `OnConfirmRemoval`, `OnDismissRemoval`.

**Layout**: storage section at the top of the screen so SC-008 (find the figure within 10 s, without
scrolling past unrelated preferences) holds — total used and free space as a header pair, then the
breakdown list, then the "remove all downloaded content" action.

**States requiring `@Preview`**: loading; zero (`onDemandUsedBytes == 0`, starter row only, invitation
to install); populated (several متون, size-ordered, starter row marked non-removable); confirmation
dialog open; post-removal `ReleasedPendingSystemReclaim` copy.

## 3. Shared components (`presentation/common/`)

Each is stateless, parameterized, previewed, and extracted at second use (Principle VIII).

| Component | Used by | Notes |
|---|---|---|
| `ContentAvailabilityBadge` | `MatnCard`, `MatnDetailsScreen` | renders the three states + declared size when not installed |
| `InstallProgressIndicator` | badge, details header | determinate on `fraction`; distinct treatment for `WaitingForNetworkPolicy` so a parked transfer never reads as stalled |
| `ContentActionButton` | details header, Settings row | install / cancel / remove, driven by availability |
| `StorageUsageRow` | Settings | title + size; `isStarter` renders "part of the app" and no remove action |
| `ConfirmRemovalDialog` | details, Settings | states bytes to be reclaimed; copy switches on `RemovalOutcome` semantics per platform |
| `InstallPromptSheet` | reading/playback entry points | the actionable prompt FR-011 requires |

`formatBytes(Long)` joins `DurationFormatter` in `presentation/common/` — locale-appropriate units,
correct under RTL (FR-031).

## 4. Modified surfaces

| File | Change | Requirement |
|---|---|---|
| `common/MatnCard.kt` | availability badge + declared size in the existing status slot | FR-002, FR-003 |
| `home/HomeUiState.kt`, `HomeViewModel.kt` | collect `ObserveLibraryAvailabilityUseCase`; expose per-matn availability | FR-002 |
| `common/ContinueLearningCard.kt` | reinstall offer when the referenced matn is not installed | FR-022 |
| `details/MatnDetailsUiState.kt`, `…ViewModel.kt`, `…Screen.kt` | availability in the header; install / cancel / remove intents; confirmation dialog | FR-004, FR-005, FR-017, FR-018 |
| `player/ReadingCarousel.kt`, `PlayerBar.kt` | play affordance replaced by the install prompt when not installed | FR-011 |
| `navigation/MatnNavHost.kt` | `settings` route → real `SettingsScreen` | FR-024 |
| `navigation/NavigationTab.kt` | doc comment: Settings now has a real screen | — |
| `navigation/ComingSoonScreen.kt` | drop the Settings preview; the stub now has no live route | — |

## 5. Interaction rules

- **Never a silent failure** (SC-007): every blocked play action opens `InstallPromptSheet`; every
  refused install shows why (offline / not enough space, stating required vs available) with a retry.
- **Confirmation before removal** (FR-018): the dialog states the bytes at stake. On iOS, where the
  outcome is `ReleasedPendingSystemReclaim`, the copy says the space is released and reclaimed by the
  system when needed — it must not promise an immediate figure (research D2).
- **Progress is honest**: `WaitingForNetworkPolicy` and `RequiresConfirmation` render as distinct,
  explained waits, never as a frozen bar.
- **No notifications** anywhere in this phase (FR-005).
- **Backgrounding**: on resume the screen must show the transfer's true state, not the state it held
  when it went away (FR-005). **This is satisfied by construction, not by a resume callback**: every
  availability surface renders from a `Flow` collected in the ViewModel, and the repository derives
  availability by reading through to the platform on every emission (research D5) — so re-collection
  after resume yields current truth with no extra machinery. What this forbids is caching an
  availability snapshot in composable state or in a `remember` block. Each ViewModel test listed in
  §6 must include a case proving a mid-flight availability change reaches the state object.
- **RTL**: sizes, progress direction, and the breakdown list are laid out natively RTL (FR-031).

## 6. ViewModel test obligations (`commonTest`)

- `SettingsViewModelTest` — loading → populated; zero state driven by `onDemandUsedBytes`; ordering;
  starter row non-removable; remove updates total without a restart; "remove all" spares the starter;
  both removal outcomes render their own copy.
- `MatnDetailsViewModelTest` (extend) — availability drives the header action; install refused
  offline; cancel returns to not-installed; removal confirmation gate.
- `HomeViewModelTest` (extend) — per-card availability wiring; Continue Learning offers reinstall
  when its matn is not installed.
- Every new state-rendering composable carries at least one `@Preview` — a blocking review item
  (Principle II).
