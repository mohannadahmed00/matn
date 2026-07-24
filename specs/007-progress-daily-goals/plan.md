# Implementation Plan: Progress & Daily Goals

**Branch**: `007-progress-daily-goals` | **Date**: 2026-07-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/007-progress-daily-goals/spec.md`

## Summary

Deliver the phase's recall-based progress metric on top of the Phase 1–6 + 10 foundation:
(1) a per-verse **"mark as memorized"** action + indicator on the reading carousel (with a
"mark entire chapter" convenience action), backed by a new UUID-keyed `memorization` table;
(2) a **per-matn progress percentage** derived by a single SQL aggregate and shown identically on
the matn details header, the library card, and the Goals dashboard; (3) a **daily goal**
(verses/day, default 10) stored in the existing `app_setting` table with a **completion ring** on
Home; and (4) a real **Goals tab** dashboard that replaces the `ComingSoonScreen` stub. The daily
count is an **append-only, once-per-verse-per-day** record in a new `daily_practice` table: a verse
is credited when the student marks it memorized *or* completes a full playthrough in
Memorization/A–B Loop mode (never in Normal continuous mode, never more than once/day). Practice
completion is captured by a pure-observer `PracticeSignalRecorder` (mirroring the existing
`SessionStateRecorder`) reading a natural-completion marker added to `PlaybackState`. Local calendar
day comes from an injected `today: () -> Long` epoch-day provider (new `kotlinx-datetime`
dependency). Everything lands in `commonMain`; no `expect`/`actual` code.

## Technical Context

**Language/Version**: Kotlin 2.x, Compose Multiplatform (Material 3), coroutines/Flow.

**Primary Dependencies**: SQLDelight (existing `ContentDatabase`/`Content.sq`), Koin
(`di/ContentModule.kt`), Jetbrains `navigation-compose` (`MatnNavHost.kt`). **One new dependency**:
`kotlinx-datetime` for correct local-calendar-day math (research.md D1; justified in Complexity
Tracking).

**Storage**: SQLDelight — two new tables (`memorization`, `daily_practice`) via an additive
migration `3.sqm` (schema v3 → v4). The daily goal reuses the existing `app_setting` key/value
table (no new table). No existing table is modified.

**Testing**: `kotlin.test` + `kotlinx-coroutines-test` in `commonTest` (repositories via the
in-memory driver per `TestDatabase.kt`; the `PracticeSignalRecorder` via `PlaybackController` +
`FakeAudioEngine` per `SessionStateRecorderTest.kt`; use cases and ViewModels device-free); the
migration guarded by a `MigrationV3Test` mirroring the existing `MigrationV2Test`. A `@Preview` per
Principle II for every new state-rendering composable.

**Target Platform**: Android + iOS via the KMP `shared` module; no new platform code.

**Project Type**: Mobile app (KMP) — single `shared` module holding domain/data/presentation.

**Performance Goals**: SC-001 — marking a verse updates the matn percentage within 1 s (SQLDelight
reactive aggregate round-trip). SC-005 — the ring reflects goal completion within the same reactive
round-trip. Progress aggregate is O(verses) per emission over a low-thousands corpus.

**Constraints**: Offline-first (zero network); recall-based progress fully decoupled from raw
playback frequency (FR-007/FR-011); "today" is the device's local calendar day, **re-evaluated on a
poll so the count resets at midnight even while the app stays open** (FR-013, research D9);
append-only daily record — un-marking never decrements the day's count (FR-014); progress % agrees
across all three surfaces (SC-002); all new surfaces native RTL with Phase 10 tokens.

**Scale/Scope**: 2 new tables + 1 migration; 2 new domain repositories (`ProgressRepository`,
`DailyGoalRepository`) with SQLDelight impls; ~7 use cases; 1 pure-observer
`PracticeSignalRecorder` + a small additive completion marker on `PlaybackState`/`PlaybackController`;
1 new screen (Goals dashboard) replacing the Goals `ComingSoonScreen` route; 2 shared components
(`DailyGoalRing`, `MatnProgressBar`) + 1 new glyph (`MemorizedGlyph`); Home ring wiring (the
`DailyGoalUiState` placeholder already exists), details-header progress, library-card progress, and
the carousel memorized action/indicator.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Clean Architecture & Layer Boundaries** — PASS. New domain surface (progress/goal models,
  `ProgressRepository`/`DailyGoalRepository` interfaces, use cases) is pure Kotlin in
  `commonMain/domain`; SQLDelight implementations stay in `data/repository`; screens reach domain
  only through use cases injected via Koin. The `PracticeSignalRecorder` lives in the existing
  `playback` package beside `SessionStateRecorder` and depends only on `PlaybackController.state`
  (read) + `ProgressRepository` (write) — no new inward-pointing violation.
- **II. MVVM Presentation** — PASS by construction. New `GoalsViewModel` and the Home/details
  additions expose immutable UI-state via `StateFlow` through `BaseViewModel`; the Goals screen
  ships a stateless content composable + thin stateful holder, with `@Preview`s for loaded / zero /
  populated states (enumerated in contracts/goals-ui-contract.md).
- **III. DRY via Base Abstractions** — PASS. New use cases implement the existing
  `UseCase`/`FlowUseCase` contracts with `Resource`; the ring and progress bar are extracted once
  and shared (Home + Goals, details + Goals) rather than copy-pasted (Principle VIII second-use
  rule); the practice recorder reuses the `SessionStateRecorder` observer shape.
- **IV. Shared-First Multiplatform** — PASS. All logic (progress derivation, daily-count
  bookkeeping, completion detection, goal bounds) lands in `commonMain`; no `expect`/`actual`
  additions — the local-day provider is a `commonMain` lambda over `kotlinx-datetime`.
- **V. Test-First & Testable Design** — PASS. Memorization toggle, chapter bulk, progress
  aggregate, daily-count dedup/append-only, goal bounds, the recall-mode completion gate, day
  rollover, and ViewModel state are all device-free `commonTest` targets. The epoch-day source is
  injected (`today: () -> Long`) and the millis clock stays injected (`clock: () -> Long`), never
  read from `System`/`Clock.System` inside domain/data logic — matching the constitution's named
  "clock/time source used by the … heuristic" rule.
- **VI. Offline-First & Future-Proof Data** — PASS. `memorization` and `daily_practice` rows each
  carry their own UUID `id` (Constitution VI names the "progress record" explicitly) independent of
  the verse they reference; `ON DELETE CASCADE` from `verse` (text identity), never from
  `audio_asset`, so removing downloaded audio can never erase progress (FR-004); fully local;
  schema is sync-safe (stable ids, day-scoped rows).
- **VII. Experience Fidelity: Audio, RTL & Accessibility** — PASS with gates: the memorized
  indicator/action must be a distinct hand-drawn glyph (not a color variant), all new surfaces
  render native RTL, and the practice recorder must **observe** playback state without adding a
  mutation site to the controller's decisions (the completion marker is set only at the two natural
  boundaries the controller already handles). Progress toward "memorized" is decoupled from raw
  playback count by construction (recall-mode gate + once/day dedup) — the phase's owning invariant
  per the constitution's Phased Delivery section.
- **VIII. Design Fidelity & Reusable Composables** — PASS with gates: implementers MUST fetch the
  registered Stitch designs before UI work — *Progress & Goals* `f059cccd2f634bc9ba2cf4d620e5df80`
  (Phase 7) and the daily-goal ring region of *Home / Library* `618643f891144557b5a4ddf4bbad0c03`
  (docs/DESIGN-SOURCE.md) — and build with Phase 10 tokens
  (`presentation/theme/{Color,Type,Shape,Spacing}.kt`); zero raw hex/`.dp`/`.sp` literals; repeated
  units (ring, progress bar, matn-progress row, memorized glyph) extracted as shared stateless
  components at second use.

**Post-design re-check (after Phase 1)**: PASS — no violations introduced. The single new dependency
(`kotlinx-datetime`) is recorded in Complexity Tracking with its justification. Complexity Tracking
otherwise empty.

## Project Structure

### Documentation (this feature)

```text
specs/007-progress-daily-goals/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── progress-contract.md        # memorization + progress + daily-goal repositories/use cases
│   ├── practice-signal-contract.md # PlaybackState completion marker + PracticeSignalRecorder
│   └── goals-ui-contract.md        # Goals screen, Home ring, details/library progress, previews
└── tasks.md             # Phase 2 output (/speckit-tasks — not created by /speckit-plan)
```

### Source Code (repository root)

```text
shared/src/commonMain/
├── sqldelight/com/giraffe/matn/db/
│   ├── Content.sq                          # MODIFY — add memorization/daily_practice tables + queries
│   └── migrations/3.sqm                    # NEW — v3→v4 additive migration (memorization, daily_practice)
└── kotlin/com/giraffe/matn/
    ├── domain/
    │   ├── model/
    │   │   ├── MatnProgress.kt             # NEW — matnId + memorizedCount + totalCount (+ derived fraction)
    │   │   └── DailyProgress.kt            # NEW — practicedToday + goal (+ derived fraction/isComplete)
    │   ├── repository/
    │   │   ├── ProgressRepository.kt       # NEW — memorization state + matn progress + daily practice
    │   │   └── DailyGoalRepository.kt      # NEW — get/observe/set the daily goal (app_setting)
    │   └── usecase/
    │       ├── ToggleVerseMemorizedUseCase.kt   # NEW — set memorized on/off; ON also credits today
    │       ├── MarkChapterMemorizedUseCase.kt   # NEW — bulk chapter mark/un-mark
    │       ├── ObserveVerseMemorizationUseCase.kt # NEW — Set<verseId> memorized, per matn (indicators)
    │       ├── ObserveMatnProgressUseCase.kt    # NEW — one matn's progress (details header)
    │       ├── ObserveLibraryProgressUseCase.kt # NEW — all متون progress (library cards + Goals)
    │       ├── ObserveDailyProgressUseCase.kt   # NEW — combine today's count + goal → DailyProgress
    │       └── SetDailyGoalUseCase.kt           # NEW — bounded (≥1) goal write
    ├── data/repository/
    │   ├── ProgressRepositoryImpl.kt       # NEW — SQLDelight-backed (memorization + daily_practice)
    │   └── DailyGoalRepositoryImpl.kt      # NEW — app_setting-backed, default 10
    ├── playback/
    │   ├── PlaybackController.kt           # MODIFY — publish a natural-completion marker on PlaybackState
    │   └── PracticeSignalRecorder.kt       # NEW — pure observer: recall-mode completion → recordPractice
    ├── di/ContentModule.kt                 # MODIFY — register repos, use cases, recorder, today/clock lambdas
    └── presentation/
        ├── common/
        │   ├── DailyGoalRing.kt            # NEW — shared stateless ring (Home + Goals)
        │   ├── MatnProgressBar.kt          # NEW — shared stateless progress bar/row (details + Goals)
        │   ├── AnnotationGlyphs.kt         # MODIFY — add MemorizedGlyph (distinct hand-drawn shape)
        │   └── MatnCard.kt                 # MODIFY — show per-matn progress on the card
        ├── navigation/
        │   ├── MatnNavHost.kt              # MODIFY — GOALS route → real GoalsScreen (was ComingSoon)
        │   └── NavigationTab.kt            # MODIFY — doc comment: GOALS now has a real screen
        ├── goals/
        │   ├── GoalsScreen.kt              # NEW — stateless content + stateful holder (Stitch f059cccd…)
        │   ├── GoalsUiState.kt             # NEW
        │   └── GoalsViewModel.kt           # NEW
        ├── home/
        │   ├── DailyGoalUiState.kt         # MODIFY — real fields (practiced/goal/isComplete); drop placeholder
        │   ├── HomeUiState.kt              # MODIFY — add per-matn progress for cards
        │   ├── HomeViewModel.kt            # MODIFY — collect daily progress + library progress
        │   └── HomeScreen.kt               # MODIFY — render real ring (Stitch 618643f8… ring region)
        ├── details/
        │   ├── MatnDetailsUiState.kt       # MODIFY — header progress + per-verse memorized flags
        │   ├── MatnDetailsViewModel.kt     # MODIFY — collect progress; toggle-memorized + chapter intents
        │   └── MatnDetailsScreen.kt        # MODIFY — header progress bar
        └── player/ReadingCarousel.kt       # MODIFY — memorized indicator + action on the active verse

shared/src/commonTest/kotlin/com/giraffe/matn/
├── data/
│   ├── ProgressRepositoryTest.kt          # NEW — toggle, chapter bulk, aggregate, zero-verse, audio-removal, daily dedup/append-only, mid-session rollover (D9), rapid toggle, restart persistence
│   ├── ProgressPerformanceTest.kt         # NEW — SC-001 guard (≥1,000 verses, mark → progress emission)
│   ├── DailyGoalRepositoryTest.kt         # NEW — default 10, set/get, ≥1 bound
│   └── MigrationV3Test.kt                  # NEW — v3 data survives v3→v4 migration
├── playback/
│   ├── PracticeSignalRecorderTest.kt      # NEW — recall-mode credit; Normal none; dedup; QueueEnded; no controller mutation
│   └── PlaybackControllerTest.kt          # MODIFY — completion-marker tick set on natural transition, suppressed on next()/error-skip
├── domain/
│   ├── ObserveDailyProgressUseCaseTest.kt # NEW — fraction/isComplete combine
│   └── MarkChapterMemorizedUseCaseTest.kt # NEW — bulk credits each newly-memorized verse once
└── presentation/
    ├── GoalsViewModelTest.kt              # NEW — loaded/zero states, goal edit
    ├── HomeViewModelTest.kt               # MODIFY — daily-ring + card-progress wiring
    └── MatnDetailsViewModelTest.kt        # MODIFY — header progress + toggle-memorized
```

**Structure Decision**: Single `shared` KMP module, mirroring the layering already in place
(`domain` / `data` / `presentation` + `playback` + `di`). A new `presentation/goals` package sits
beside the existing `home`/`details`/`notes`/`search` packages; the practice recorder joins the
existing `playback` package next to `SessionStateRecorder`. No `androidApp`/`iosApp` changes — the
Phase 10 shell already hosts `MatnNavHost` and the Goals tab, and the platform DI already registers
`DatabaseDriverFactory`.

## Complexity Tracking

> One new third-party dependency requires justification (Constitution § Technology & Architecture
> Constraints); no principle violations.

| Addition | Why Needed | Simpler Alternative Rejected Because |
|----------|------------|--------------------------------------|
| `kotlinx-datetime` dependency | Correct **local calendar day** (epoch-day) for the daily count and its midnight reset (FR-013), across time zones and DST | Deriving local-day from epoch-millis by hand is wrong at TZ/DST boundaries; an `expect`/`actual` date lookup duplicates platform date code and still needs injection for tests. `kotlinx-datetime` is the standard KMP companion to `kotlinx-coroutines`, used only behind the injected `today: () -> Long` provider so domain/data stay pure and device-free. |
