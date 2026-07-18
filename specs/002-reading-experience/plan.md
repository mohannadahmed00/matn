# Implementation Plan: Phase 1 — Reading Experience (static)

**Branch**: `002-reading-experience` | **Date**: 2026-07-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-reading-experience/spec.md`

## Summary

Phase 1 builds the first UI on top of the Phase 0 content foundation: a **Home** library grid of
متون, a **Matn Details / reading** screen (header + full verse list), a **table of contents** for
structured متون, a fully **right-to-left** layout, elegant **classical Arabic typography** with
intact diacritics, and an **adjustable reading font size** persisted globally. There is **no
audio and no playback** (FR-020) — this phase validates the reading UI/UX in isolation.

Technical approach: introduce the **presentation layer** wired strictly as
**Compose screen → ViewModel (`BaseViewModel`) → use case → repository interface** (Principle I),
with each screen exposing one immutable `StateFlow` UI state (Principle II). New shared base
types (`UseCase`/`FlowUseCase`, `BaseViewModel`) satisfy Principle III. All UI + state logic
lives in `commonMain` (Principle IV). The only new persisted state is a single global
`ReadingFontSize`, stored in a small `app_setting` key/value table in the existing
`ContentDatabase` behind a new `ReadingPreferencesRepository` (no new persistence dependency).
Interface chrome is localized via Compose string resources (Arabic base + English override,
Arabic fallback); verse text is rendered in a bundled Amiri font; navigation uses first-party
CMP Navigation. ViewModel/use-case/preference logic is proven device-free in `commonTest`
(Principle V); RTL/typography/font-size visuals are validated on-device.

## Technical Context

**Language/Version**: Kotlin 2.4.10 (Kotlin Multiplatform), JVM target 11 for Android.

**Primary Dependencies**: Compose Multiplatform 1.11.x (UI, Material 3, resources — already
present), AndroidX Lifecycle ViewModel/runtime for Compose (already present), Koin 4.x (DI),
kotlinx-coroutines/Flow (state), SQLDelight 2.x (reused for the font-size setting).
**New**: `org.jetbrains.androidx.navigation:navigation-compose` **2.9.2** (the version bundled
with Compose Multiplatform 1.11.0, verified against its release notes; pairs with the existing
lifecycle `2.11.0-beta01`) for multi-screen navigation, and a bundled **Amiri** OFL font asset —
each justified in [research.md](./research.md).

**Storage**: Reuses the Phase 0 SQLDelight `ContentDatabase` for all content reads. Adds one
additive `app_setting(key, value)` key/value table for the global font-size preference and
read-only aggregate queries (`COUNT`/`SUM`) for derived totals — **no change to the content
schema** (`matn`/`chapter`/`verse`/`audio_asset`).

**Testing**: `kotlin.test` + `kotlinx-coroutines-test` + `koin-test` in `commonTest` (ViewModel,
use-case, preference, and derived-totals logic) against faked repositories / the in-memory
SQLDelight driver. Compose UI visuals (RTL, Amiri, font-size steps) validated by an on-device
manual walkthrough — see [quickstart.md](./quickstart.md).

**Target Platform**: Android (minSdk 24, compileSdk 36) + iOS (iosArm64, iosSimulatorArm64),
single shared source in `commonMain`; default (light) theme + phone layout only.

**Project Type**: Mobile — Kotlin Multiplatform shared library (`:shared`) consumed by
`:androidApp` and `iosApp`. This phase adds a presentation layer inside `:shared/commonMain` and
replaces the sample `App.kt` root with the real navigation host.

**Performance Goals**: A matn of up to 500 verses shows its first verses within 1 second and
scrolls end to end smoothly (SC-003); chapter selection brings a chapter's first verse into view
within 1 second (SC-004); zero network requests on any screen (SC-006).

**Constraints**: Fully offline (FR-018/SC-006); fully native RTL on every screen (FR-010/SC-005);
Arabic verse text rendered verbatim with all diacritics, no truncation/clipping/normalization
(FR-008/SC-002); font-size legible with intact layout at Small and X-Large (SC-007); a single
**global** persisted font-size preference (FR-017); interface chrome follows device locale with
Arabic fallback (FR-021); strictly no audio/playback/progress/search/etc. (FR-020).

**Scale/Scope**: Presentation layer only — 2 screens (Home, Matn Details) + a TOC panel, 2
ViewModels + a `BaseViewModel`, 5 use cases + `UseCase`/`FlowUseCase` base, 1 new preferences
repository (+ SQLDelight settings table), additive derived-total queries, a navigation host,
localized strings, and one bundled font. Content scale: matn up to ~500 verses; a handful of
seeded متون.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design — still PASS.*

| Principle | Applies to Phase 1? | How this plan complies |
|-----------|---------------------|------------------------|
| **I. Clean Architecture & Layer Boundaries** | Yes (**adds presentation**) | Presentation reaches the domain **only through use cases** — `Compose → ViewModel → UseCase → repository interface`. ViewModels never import data-layer classes. New `ReadingPreferencesRepository` is a domain interface implemented in the data layer. Dependency direction presentation → domain ← data upheld. |
| **II. MVVM Presentation (NON-NEGOTIABLE)** | Yes (**core of this phase**) | Every screen has a `ViewModel` exposing one immutable UI-state via `StateFlow`; Composables are pure functions of state emitting intents, with no business logic or direct use-case/repo calls beyond intent dispatch. ViewModels reference no Compose/`Context`/platform UI type. One-shot effects (navigation, scroll-to) are a separate effect stream. |
| **III. DRY via Base Abstractions** | Yes | Introduces the previously-deferred base types: `BaseViewModel` (shared state container, scope, loading/error) and `UseCase`/`FlowUseCase` (shared `invoke` contract). Reuses the Phase 0 `Resource`/`AppError`. Duration formatting and the cover placeholder are single-sourced shared utilities/composables. |
| **IV. Shared-First Multiplatform** | Yes | All ViewModels, use cases, UI state, Compose screens, navigation, strings, and the font live in `commonMain`. No new `expect`/`actual` is required (preference persistence reuses the existing SQLDelight driver `actual`s; locale + font resolve through Compose resources). Zero logic duplicated across `androidMain`/`iosMain`. |
| **V. Test-First & Testable Design (NON-NEGOTIABLE)** | Yes | ViewModel, use-case, preference, and derived-total behavior (the spec's acceptance scenarios) is covered by `commonTest` unit tests against faked repositories / the in-memory driver — no device, emulator, or audio. Collaborators injected via interfaces (Koin). Tests land with the code. Compose visuals are validated on-device (quickstart) since they aren't unit-assertable. |
| **VI. Offline-First & Future-Proof Data** | Yes | Every screen renders from the local store; zero network (FR-018). The font-size preference is persisted immediately on change and survives restart (FR-017). It reuses stable-identity Phase 0 entities; the `app_setting` table is a forward-compatible key/value store that won't block later settings or sync. |
| **VII. Experience Fidelity: Audio, RTL & Accessibility** | Partially (**the RTL + typography slice**) | Delivers the in-scope fidelity guarantees: fully native RTL on all screens (FR-010), classical Arabic typeface with faithful diacritics (FR-008/FR-009), and adjustable Arabic font size (FR-016). Gapless audio and interruption handling are Phase 2+; **dark mode and tablet-adaptive layouts are explicitly deferred to Phase 8** by FR-020 (screens must not crash on rotation/size, but adaptivity is out of scope) — a scoped deferral, not a violation. |

**Additional constraints**
- **Stack** matches the constitution: KMP + Compose Multiplatform (Material 3), MVVM, Koin DI,
  base package `com.giraffe.matn`.
- **DI**: Koin's `contentModule()` is extended to provide the preferences repository, the five
  use cases, and the two ViewModels — no manual singletons across layer boundaries.
- **New dependencies** (CMP Navigation; bundled Amiri font) are each justified in
  [research.md](./research.md) against a simpler rejected alternative, per the constitution.

**Result**: ✅ PASS — no violations. Complexity Tracking table below is empty.

## Project Structure

### Documentation (this feature)

```text
specs/002-reading-experience/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output — presentation-layer tech decisions & rationale
├── data-model.md        # Phase 1 output — new setting, derived projections, UI-state models
├── quickstart.md        # Phase 1 output — automated + manual validation guide
├── contracts/           # Phase 1 output
│   ├── use-cases.md      # Use-case + repository-extension contracts (behavioural)
│   └── ui-contract.md    # ViewModel/state, navigation, RTL/typography/localization guarantees
├── checklists/          # (pre-existing — requirements.md)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

Existing KMP layout (`:shared` consumed by `:androidApp` + `iosApp`). This phase adds a
`presentation/` tree and a couple of data-layer additions under the existing `com.giraffe.matn`
base package, and replaces the sample `App.kt` root with the navigation host. Phase 0
domain/data code is reused unchanged except for the two additive extensions noted.

```text
shared/
├── build.gradle.kts                          # + navigation-compose dependency
├── src/
│   ├── commonMain/
│   │   ├── composeResources/
│   │   │   ├── font/                          # Amiri-*.ttf (OFL, bundled reading typeface)
│   │   │   ├── values/strings.xml             # Arabic base chrome strings (fallback)
│   │   │   └── values-en/strings.xml          # English chrome strings
│   │   ├── sqldelight/com/giraffe/matn/db/
│   │   │   └── Content.sq                     # + app_setting table, get/set setting,
│   │   │                                      #   countVersesByMatn / sumVerseDurationByMatn
│   │   └── kotlin/com/giraffe/matn/
│   │       ├── App.kt                         # REPLACED — hosts the NavHost root (RTL provider)
│   │       ├── core/
│   │       │   └── usecase/                    # UseCase, FlowUseCase base contracts (DRY)
│   │       ├── domain/
│   │       │   ├── model/                      # + ReadingFontSize, MatnSummary, MatnDetails
│   │       │   ├── repository/                 # + ReadingPreferencesRepository (interface),
│   │       │   │                               #   MatnRepository.observeLibrarySummaries
│   │       │   └── usecase/                    # ObserveLibrary, GetMatnDetails, ObserveVerses,
│   │       │                                   #   Get/SetFontSize
│   │       ├── data/
│   │       │   ├── repository/                 # ReadingPreferencesRepositoryImpl (app_setting);
│   │       │   │                               #   MatnRepositoryImpl += summaries
│   │       │   └── mapper/                     # + summary/setting mappers
│   │       ├── presentation/
│   │       │   ├── base/BaseViewModel.kt        # shared state/scope/loading/error (DRY)
│   │       │   ├── theme/                       # MatnTheme, Amiri FontFamily, font-size scale
│   │       │   ├── navigation/                  # NavHost graph, routes (home, matn/{matnId})
│   │       │   ├── home/                        # HomeViewModel, HomeUiState, HomeScreen, card
│   │       │   ├── details/                     # MatnDetailsViewModel, MatnDetailsUiState,
│   │       │   │                                #   MatnDetailsScreen, header, verse list, TOC
│   │       │   └── common/                      # cover placeholder, duration formatter, RTL util
│   │       └── di/
│   │           └── ContentModule.kt            # + prefs repo, use cases, ViewModel factories
│   └── commonTest/kotlin/com/giraffe/matn/
│       └── presentation/                        # HomeViewModel / MatnDetailsViewModel tests,
│                                                #   font-size persistence, derived-totals tests
```

`:androidApp` (`MainActivity` → `App()`) and `iosApp` (`ContentView` → `MainViewController`)
keep their thin entry points; they render the new `App()` navigation host. Sample files
(`Greeting*.kt`, the old `App.kt` body) are superseded by real screens.

**Structure Decision**: Kotlin Multiplatform shared library. All new presentation code
(ViewModels, screens, navigation, theme, base types) lives in
`shared/src/commonMain/kotlin/com/giraffe/matn/{presentation,core/usecase,domain/usecase}`; the
new preference persistence and derived-total queries extend the existing SQLDelight schema and
data layer additively; strings and the Amiri font live under `commonMain/composeResources`. No
new Gradle module is introduced; no `expect`/`actual` is added.

## Complexity Tracking

> No Constitution Check violations — this table is intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
