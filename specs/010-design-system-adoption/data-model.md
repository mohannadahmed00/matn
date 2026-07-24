# Data Model: Design System Adoption

This phase adds **no domain entities and no persisted data** (spec Assumptions; Constitution
Check "VI. Offline-First & Future-Proof Data" = N/A). What follows are the new **presentation-layer
state shapes** introduced to drive the reworked screens — plain immutable `data class`/`enum`
types living in `commonMain`, consumed by existing or lightly-extended ViewModels, never touching
the data or domain layers.

## `ReadingCarouselUiState`

Drives the 3-verse carousel (`presentation/player/ReadingCarousel.kt`).

| Field | Type | Notes |
|---|---|---|
| `previousVerse` | `VerseDisplay?` | `null` when the active verse is the matn's first verse (Edge Case) |
| `activeVerse` | `VerseDisplay` | Always present while a matn is open |
| `nextVerse` | `VerseDisplay?` | `null` when the active verse is the matn's last verse |
| `verseLabel` | `String` | Localized "verse N" label shown under the active verse |

`VerseDisplay` (Arabic text, verse number, loop-boundary marker if any) is derived from the
existing verse list already loaded by `MatnDetailsViewModel` — no new fetch, just a windowed view
of data that's already in memory.

## `RepetitionSetupUiState`

Drives the consolidated bottom sheet (`presentation/player/RepetitionSetupSheet.kt`).

| Field | Type | Notes |
|---|---|---|
| `abLoopEnabled` | `Boolean` | Drives whether start/end verse selectors are active |
| `startVerse` / `endVerse` | `Int?` | Verse indices bounding the A–B range; `null` outside A-B mode |
| `verseRepeatCount` | `RepeatCount` | **Reused from `domain/model/RepeatCount.kt`** (specs/004) — not redefined |
| `segmentRepeatCount` | `RepeatCount` | Same reused type |
| `isInfiniteVerseRepeat` / `isInfiniteSegmentRepeat` | `Boolean` | Mirrors `RepeatCount`'s existing unlimited representation |
| `canStart` | `Boolean` | Derived — false until a valid range/count combination exists |

On "start", this state is translated into the existing `RepetitionSettings` domain object
(unchanged shape, specs/004) and handed to the ViewModel exactly as `DrillPanel` does today — this
sheet replaces *where* the settings are collected, not the settings object itself.

## `NavigationTab`

Drives the bottom nav shell (`presentation/navigation/`).

| Value | Route | Destination |
|---|---|---|
| `LIBRARY` | `Routes.HOME` | Existing `HomeScreen` |
| `GOALS` | `Routes.GOALS` | New `ComingSoonScreen` (stubbed until Phase 7) |
| `NOTES` | `Routes.NOTES` | New `ComingSoonScreen` (stubbed until Phase 6) |
| `SETTINGS` | `Routes.SETTINGS` | New `ComingSoonScreen` (stubbed until Phase 8) |

An `enum class NavigationTab(val route: String, val icon: ..., val labelRes: ...)` is the natural
shape — no persistence, purely a UI navigation-state value re-derived from the current back-stack
entry on every recomposition.

## `DailyGoalUiState` (Home screen placeholder)

| Field | Type | Notes |
|---|---|---|
| `isPlaceholder` | `Boolean` | Always `true` until specs/007 lands (FR-010) |
| `progressFraction` | `Float` | `0f` while placeholder, to avoid a fabricated ring value |

No new repository or use case backs this — `HomeViewModel` supplies a constant placeholder state
until Phase 7 replaces it with a real one. This type exists purely so the placeholder-ness is
explicit and typed rather than an implicit "0 means nothing yet" convention.
