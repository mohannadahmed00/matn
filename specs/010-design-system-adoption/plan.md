# Implementation Plan: Phase 10 — Design System Adoption

**Branch**: `010-design-system-adoption` (git: `feature/010-design-system-adoption`) | **Date**: 2026-07-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/010-design-system-adoption/spec.md`

## Summary

Retrofit the already-shipped Phases 1–5 screens (Home, Matn Details, Reading & Playback,
Repetition setup) to the canonical Stitch design set registered in `docs/DESIGN-SOURCE.md`, and
add the persistent bottom-navigation shell the design treats as universal chrome but that was
never specified. This is presentation-layer only: centralized design tokens replace the current
ad-hoc palette and raw literals; the reading screen becomes a focused 3-verse carousel; repetition
configuration becomes one bottom sheet; a 4-tab bottom nav wraps every top-level screen with
Goals/Notes/Settings stubbed until their own phases land. No domain, data, or repository changes.

## Technical Context

**Language/Version**: Kotlin 2.x, Compose Multiplatform (Material 3)

**Primary Dependencies**: Compose Multiplatform UI + `compose.material3`, `navigation-compose`
(Jetbrains, already used by `MatnNavHost.kt`), Koin 4.0 for DI (already wired via
`di/ContentModule.kt`), Compose `composeResources` for bundled fonts (Amiri already bundled this
way)

**Storage**: N/A — no new persisted entities; this phase reuses existing repositories unchanged

**Testing**: `kotlin.test` + `kotlinx-coroutines-test` in `commonTest` for ViewModel/state logic;
Compose `@Preview` per Principle II for every state-rendering composable (no snapshot-testing
library currently in the project — previews remain the UI-regression guard, consistent with
Phases 2–5)

**Target Platform**: Android + iOS (KMP `shared` module, thin `androidApp`/`iosApp` shells)

**Project Type**: Mobile app (KMP), single `shared` module holding domain/data/presentation

**Performance Goals**: No new perf targets beyond existing ones (gapless verse playback,
Principle VII); carousel transitions and bottom-sheet open/close should feel immediate
(sub-frame-drop) on mid-tier devices, matching the existing player bar's responsiveness

**Constraints**: Offline-first (no network calls introduced), RTL-native (all new screens/sheets
must render correctly right-to-left), light-theme only this phase (dark mode is Phase 9)

**Scale/Scope**: 4 screens re-skinned/reworked (Home, Matn Details, Reading & Playback,
Repetition Setup), 2 new shared token files, 1 new navigation shell with 1 new placeholder screen,
0 new domain entities

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Clean Architecture & Layer Boundaries** — PASS. This phase touches only the presentation
  layer (screens, theme, navigation). No new domain or data code; existing ViewModels keep their
  existing use-case dependencies.
- **II. MVVM Presentation** — PASS, with attention required at design time: the new carousel and
  repetition-setup sheet MUST still be pure functions of ViewModel `StateFlow` state with a
  stateless content composable + thin stateful holder, and MUST ship `@Preview`s for their key
  states (loaded / first-verse / last-verse for the carousel; A-B-off / A-B-on for the sheet).
- **III. DRY via Base Abstractions** — PASS. No new ViewModel base types needed; existing
  `BaseViewModel`/result wrapper patterns are reused as-is.
- **IV. Shared-First Multiplatform** — PASS. All new composables and tokens live in
  `commonMain`; no platform-specific UI logic is introduced.
- **V. Test-First & Testable Design** — PASS, scoped down: this phase changes no
  playback/repetition/progress *behavior* (FR-005), so no new domain tests are required, but
  existing behavioral tests (`PlaybackControllerTest`, `RepetitionControllerTest`,
  `RepetitionPlannerTest`, `PlayerBarViewModelTest`, `MatnDetailsViewModelTest`) MUST still pass
  — updated only for new composable/selector structure, never for changed assertions (FR-012).
- **VI. Offline-First & Future-Proof Data** — N/A. No persisted entities touched.
- **VII. Experience Fidelity: Audio, RTL & Accessibility** — PASS, with a gate: the carousel
  rework MUST NOT regress gapless playback or interruption handling from specs/003; RTL MUST be
  verified for the carousel, the bottom sheet, and the bottom nav specifically since they're new
  layouts, not just re-skins.
- **VIII. Design Fidelity & Reusable Composables** — this is the principle the whole phase exists
  to satisfy. Gate: every touched screen fetched from Stitch before implementation (already done,
  see `docs/DESIGN-SOURCE.md`); zero hard-coded color/dp/sp literals remain in touched screens;
  repeated elements (verse card, nav item, stepper, toggle pill) extracted into shared stateless
  components at their second use, each with a `@Preview`.

No violations requiring Complexity Tracking — this phase reduces complexity (removes scattered
raw literals and scattered repetition controls) rather than adding it.

## Project Structure

### Documentation (this feature)

```text
specs/010-design-system-adoption/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output (presentation-layer state shapes; no domain entities)
├── quickstart.md        # Phase 1 output
├── contracts/            # Phase 1 output
│   ├── design-tokens-contract.md
│   ├── navigation-contract.md
│   └── repetition-setup-contract.md
└── tasks.md              # Phase 2 output (/speckit-tasks — not created by /speckit-plan)
```

### Source Code (repository root)

```text
shared/src/commonMain/kotlin/com/giraffe/matn/presentation/
├── theme/
│   ├── Color.kt              # MODIFY — replace manuscript palette with M3 role set
│   ├── Type.kt                # MODIFY — add Source Serif 4 + Plus Jakarta Sans font families
│   ├── Shape.kt                # NEW — corner-radius scale (lg/xl/full)
│   ├── Spacing.kt              # NEW — spacing scale (unit/gutter/margin-mobile/margin-desktop)
│   ├── FontScale.kt           # unchanged
│   └── MatnTheme.kt           # MODIFY — wire in Shape/Spacing alongside existing Color/Type
├── home/
│   └── HomeScreen.kt          # MODIFY — top bar, daily-goal placeholder ring, re-skin
├── details/
│   ├── MatnDetailsScreen.kt   # MODIFY — re-skin only, structure unchanged
│   └── TableOfContents.kt     # MODIFY — re-skin only
├── player/
│   ├── PlayerBar.kt           # REWORK — floating control bar per carousel design
│   ├── DrillPanel.kt          # REPLACE — superseded by RepetitionSetupSheet (below)
│   ├── ReadingCarousel.kt     # NEW — previous/active/next 3-verse stack
│   └── RepetitionSetupSheet.kt # NEW — consolidated A-B/steppers/start bottom sheet
├── navigation/
│   ├── MatnNavHost.kt         # MODIFY — wrap in Scaffold + bottom NavigationBar, add routes
│   └── ComingSoonScreen.kt    # NEW — shared placeholder for Goals/Notes/Settings tabs
└── common/
    ├── ContinueLearningCard.kt # MODIFY — re-skin only
    ├── CoverImage.kt          # MODIFY — re-skin only
    ├── MatnCard.kt             # extract from HomeScreen.kt if not already separate (2nd-use rule)
    └── PlaybackGlyphs.kt      # MODIFY — re-skin only

shared/src/commonTest/kotlin/com/giraffe/matn/
├── playback/PlaybackControllerTest.kt, RepetitionControllerTest.kt, RepetitionPlannerTest.kt
└── presentation/MatnDetailsViewModelTest.kt, PlayerBarViewModelTest.kt
    # all MODIFY only where composable structure/selectors changed — assertions unchanged (FR-012)
```

**Structure Decision**: Single KMP `shared` module (existing structure from Phases 1–5); this
phase adds no new module and no new top-level package — it works entirely within the existing
`presentation/{theme,home,details,player,navigation,common}` packages, consistent with Principle
IV (shared-first) and Principle I (presentation-only change).

## Complexity Tracking

*No entries — no Constitution Check violations.*
