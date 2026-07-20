# Implementation Plan: Phase 3 — Repetition Engine

**Branch**: `004-repetition-engine` | **Date**: 2026-07-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-repetition-engine/spec.md`

## Summary

Phase 3 turns Phase 2's straight-through recitation into a configurable **drill**: a verse repeat
counter ($V_r$), a matn repeat counter ($M_r$) — each a finite count or explicit **∞** — and an
**A–B loop** range, composing into the three modes (Normal / Memorization / A–B Loop) that the
student never selects directly because **mode is derived** from the configuration.

The technical core is one decision. Phase 2's gapless guarantee comes from the native player
pre-buffering the **next playlist item**, so the way to repeat a verse gaplessly is to make the
repetition *be* the next playlist item. The controller therefore materializes the drill into a
**small sliding window** of playlist entries (current + 2 ahead), refilling the tail on every track
transition and trimming what has been consumed. Repeating verse *v* three times is simply the
playlist `[v, v, v]` — ordinary consecutive items that ExoPlayer and AVQueuePlayer already
pre-buffer, so FR-017 holds by construction rather than by delicate re-seek timing. Unlimited needs
no special case (the window just never runs dry), and a counter change rewrites only the **tail**,
so live reconfiguration is inaudible (SC-005).

All decisions live in `commonMain`. A pure, coroutine-free **`RepetitionPlanner`** — `next(cursor,
settings, range) → Advance | End` — holds the entire advance rule set and is table-testable with no
fakes at all, which is what the constitution demands of the memorization matrix (Principle V,
"the product's core correctness surface"). `PlaybackController` gains window management and per-matn
settings; the `AudioEngine` interface gains exactly **two mechanical primitives**
(`replaceUpcoming`, `dropConsumed`) that decide nothing. Settings sit behind a
`RepetitionSettingsStore` interface with an in-memory implementation, so Phase 4 substitutes
persistence without touching a single caller.

**No new dependency. No schema change. No new persisted entity.** The one honest carry-forward:
`AvQueueAudioEngine` is still the documented Phase 2 stub validated on macOS, and the two new
primitives inherit that constraint — this phase does not close that gap.

## Technical Context

**Language/Version**: Kotlin 2.4.10 (Kotlin Multiplatform), JVM target 11 for Android.

**Primary Dependencies**: **No new dependency.** Existing only — Compose Multiplatform 1.11.1
(Material 3 1.11.0-alpha07), AndroidX Lifecycle 2.11.0-beta01, CMP Navigation 2.9.2, Koin 4.0.0,
kotlinx-coroutines 1.9.0, SQLDelight 2.0.2, Media3 1.4.1 (`androidMain`). The new engine primitives
use Media3's `replaceMediaItems`/`removeMediaItems`, present since 1.1; iOS stays on first-party
AVFoundation.

**Storage**: **None added.** Repetition state is in-memory (FR-033); the Phase 0 SQLDelight database
is untouched — no schema change, no new table, column, or query. `RepetitionSettingsStore` is an
in-memory map behind a domain interface, positioned as the Phase 4 persistence seam.

**Testing**: `kotlin.test` + `kotlinx-coroutines-test` in `commonTest`. `RepetitionPlannerTest`
covers the advance rules as **pure table tests with no fakes**; `RepetitionControllerTest` drives
window management, loop wrapping, live reconfiguration, error skipping, and interruption/scrub
count-preservation against the existing `FakeAudioEngine` (extended to simulate a playlist). The
existing `PlaybackControllerTest` is the SC-010 zero-regression guard: its **observable-state**
assertions (`status`, `activeVerseId`, `activeIndex`, `notice`, wake lock) MUST NOT change, while its
**engine-mechanics** assertions (`lastQueue`, `startIndex`, `seekedToTrack`) may change only where the
window model demands it — exactly two assertions, sanctioned and enumerated in tasks.md T024.
Gapless repetition, 60-minute ∞ stability, and memory flatness are device-validated
([quickstart.md](./quickstart.md) §B).

**Target Platform**: Android (minSdk 24, compileSdk 36) + iOS (iosArm64, iosSimulatorArm64); default
light theme + phone layout only (dark mode / tablet remain Phase 8, FR-033).

**Project Type**: Mobile — KMP shared library (`:shared`) consumed by `:androidApp` and `iosApp`.
This phase extends the existing `playback` slice; it adds no module and no platform shell changes.

**Performance Goals**: Every repetition boundary and range wrap gapless (SC-004); counter/range
changes audible-interruption-free within 1 s (SC-005); repetition/pass indicators track audio within
1 s (SC-007); transport responsive within 300 ms during a 60-minute unlimited run (SC-008).

**Constraints**: Fully offline (FR-032/SC-011); the currently playing engine item is **never**
replaced in place; playlist bounded at `1 + WINDOW_AHEAD` items regardless of session length
(FR-030); repetitions counted at track transition, never by position, so scrubbing cannot consume one
(FR-019); defaults must be byte-for-byte Phase 2 behavior (SC-010); no inter-repetition silence
(FR-027); no persistence, progress, or "memorized" signal (FR-033).

**Scale/Scope**: 1 pure planner, 6 new domain types, 2 additive `AudioEngine` primitives (+ 2
platform mappings + fake), 1 settings store interface + in-memory impl, `PlaybackController`
extension (window management + per-matn settings), 3 UI-state extensions, 1 new drill-panel
composable + verse-row A/B affordances + range highlighting. Content scale unchanged (matn up to
~500 verses); the playlist stays ~3 items regardless of counters.

## Constitution Check

*GATE: Passed before Phase 0 research. Re-checked after Phase 1 design — still PASS.*

| Principle | Applies? | How this plan complies |
|-----------|----------|------------------------|
| **I. Clean Architecture & Layer Boundaries** | Yes | The planner and controller depend on the `AudioEngine` and `RepetitionSettingsStore` **interfaces**, never on Media3/AVFoundation types. New domain models are pure Kotlin with no framework import. Dependency direction presentation → domain ← data/platform is unchanged; ViewModels reach repetition only through the controller. |
| **II. MVVM Presentation (NON-NEGOTIABLE)** | Yes | Repetition state is added to the single `StateFlow<PlaybackState>`; `PlayerBarUiState`/`MatnDetailsUiState` stay **pure projections** and neither ViewModel gains state ownership or a decision. The new drill panel ships as a **stateless content composable + thin holder**, with `@Preview`s for the states that matter: finite counters, ∞, active A–B range, and Normal defaults (blocking review item). |
| **III. DRY via Base Abstractions** | Yes | Reuses `BaseViewModel`, `UseCase`, `Resource`/`AppError`, and the existing single `PlaybackController` — the reading screen and player bar consume one source, so no repetition logic is duplicated per screen. The advance rules exist in exactly **one** place (`RepetitionPlanner`); the controller never re-implements a branch of them. |
| **IV. Shared-First Multiplatform** | Yes | The planner, window management, per-matn settings, mode derivation, failed-verse handling, and every advance decision live once in `commonMain`. The two new `actual`-side primitives are mechanical playlist operations that decide nothing — this is precisely why engine-level repeat modes were **rejected** in research D1: they would have pushed "when do I stop repeating?" into two diverging native APIs. |
| **V. Test-First & Testable Design (NON-NEGOTIABLE)** | Yes (**core**) | The constitution names Vr/Mr/∞/A–B as the product's core correctness surface requiring proof in isolation. Extracting a **pure** planner is exactly that: 12 table-test rows with no device, no audio, no fakes, no coroutines. 18 further controller rows run against `FakeAudioEngine`. Tests land with the code; the constitution's "change to repetition or A–B logic without tests is a blocking failure" is honored. |
| **VI. Offline-First & Future-Proof Data** | Yes (**forward-designed**) | Zero network. `LoopRange` is keyed by **stable verse UUIDs**, not list positions, so a range survives scrolling and later persistence. `RepetitionSettingsStore` is the ready-made Phase 4 seam, and `PlaybackState` now carries exactly what the constitution names for "Continue Learning" — including the in-progress A–B range. Deferring persistence to Phase 4 is the roadmap's own sequencing, not a violation. |
| **VII. Experience Fidelity: Audio, RTL & Accessibility** | Yes | Extends the contractual gapless guarantee to a case Phase 2 never faced — a verse repeating into *itself* — and satisfies it structurally rather than by timing luck. Interruption handling is preserved and strengthened (the repetition count survives a call, FR-026/SC-009). Range highlighting is layered onto the existing native-RTL reading surface. **Repetition deliberately produces no progress signal**, upholding the principle that memorization is decoupled from playback count (Phase 6). Dark mode / tablet remain Phase 8 — scoped deferral. |

**Additional constraints**
- **Stack** unchanged: KMP + Compose Multiplatform, MVVM, Koin DI, base package `com.giraffe.matn`.
- **Per-verse audio model honored**: repetition is expressed as repeated *file* entries; nothing ever
  seeks within a shared continuous file.
- **DI**: `contentModule()` gains `RepetitionSettingsStore` (`single`); `PlaybackController` gains one
  constructor parameter. No new module, no manual singleton.
- **New dependency**: none — the constitution's justification requirement is trivially satisfied.

**Result**: ✅ PASS — no violations. The Complexity Tracking table below is intentionally empty.

Two additions were weighed against "unjustified complexity" and kept, with reasons recorded rather
than assumed:
- *The sliding window* is more machinery than a naive re-seek, but the naive approach **fails
  FR-017** outright (research D1). The complexity buys the phase's headline promise.
- *`RepetitionSettingsStore`* is an interface over a map. It is justified by Principle VI (the Phase 4
  seam) and costs one file; the rejected alternative left Phase 4 extracting state from the
  controller's internals.

## Project Structure

### Documentation (this feature)

```text
specs/004-repetition-engine/
├── plan.md              # This file
├── research.md          # Phase 0 — D1–D10 decisions
├── data-model.md        # Phase 1 — domain models, state deltas, planner rules
├── quickstart.md        # Phase 1 — validation guide (§A shared, §B Android, §C iOS)
├── contracts/
│   ├── repetition-contract.md        # Intents, state projection, P1–P12 + C1–C18
│   └── audio-engine-repetition.md    # The two additive engine primitives
├── checklists/requirements.md
├── spec.md
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
shared/src/
├── commonMain/kotlin/com/giraffe/matn/
│   ├── domain/model/
│   │   ├── RepeatCount.kt                   # NEW — Finite | Unlimited (FR-003)
│   │   ├── LoopRange.kt                     # NEW — A/B by stable verse UUID
│   │   ├── RepetitionSettings.kt            # NEW — + derived `mode`
│   │   ├── PlaybackMode.kt                  # NEW — derived label only
│   │   ├── PlaybackCursor.kt                # NEW — verse / repetition / pass
│   │   ├── RepetitionProgress.kt            # NEW — indicator projection
│   │   └── PlaybackState.kt                 # EXTEND — settings, cursor, loopRangeVerseIds
│   ├── domain/repository/
│   │   └── RepetitionSettingsStore.kt       # NEW — Phase 4 seam
│   ├── domain/audio/AudioEngine.kt          # EXTEND — replaceUpcoming, dropConsumed
│   ├── data/repository/
│   │   └── InMemoryRepetitionSettingsStore.kt  # NEW
│   ├── playback/
│   │   ├── RepetitionPlanner.kt             # NEW — pure advance rules (the core)
│   │   └── PlaybackController.kt            # EXTEND — window, cursor, settings, intents
│   ├── presentation/player/
│   │   ├── PlayerBarUiState.kt              # EXTEND — mode, repetition, pass, settings
│   │   ├── PlayerBarViewModel.kt            # EXTEND — forward repetition intents
│   │   ├── PlayerBar.kt                     # EXTEND — mode chip + "3 / 7" indicator
│   │   └── DrillPanel.kt                    # NEW — stateless counters/∞ panel + holder + previews
│   ├── presentation/details/
│   │   ├── MatnDetailsUiState.kt            # EXTEND — loopRangeVerseIds, loopRange
│   │   ├── MatnDetailsViewModel.kt          # EXTEND — set/clear A–B intents
│   │   └── MatnDetailsScreen.kt             # EXTEND — range highlighting + A/B affordance
│   └── di/ContentModule.kt                  # EXTEND — store + controller wiring
├── androidMain/kotlin/com/giraffe/matn/audio/
│   └── Media3AudioEngine.kt                 # EXTEND — replaceMediaItems / removeMediaItems
├── iosMain/kotlin/com/giraffe/matn/audio/
│   └── AvQueueAudioEngine.kt                # EXTEND — authored; validated on macOS (carried gap)
└── commonTest/kotlin/com/giraffe/matn/playback/
    ├── RepetitionPlannerTest.kt             # NEW — P1–P12, no fakes
    ├── RepetitionControllerTest.kt          # NEW — C1–C18
    ├── FakeAudioEngine.kt                   # EXTEND — simulated playlist + call records
    └── PlaybackControllerTest.kt            # SC-010 guard — only T024's 2 sanctioned assertions change
```

**Structure Decision**: No new module or source set. The phase extends the existing `playback`
domain/presentation slice inside `:shared`, keeping every decision in `commonMain` and touching the
two platform engines only for the two mechanical primitives. The one genuinely new architectural
element is `RepetitionPlanner`, deliberately extracted as a **pure function** so the memorization
matrix is provable in isolation as the constitution requires.

## Complexity Tracking

> No Constitution Check violations. Table intentionally empty — the two judgment calls that could
> have appeared here are justified in the Constitution Check section above.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *(none)* | — | — |
