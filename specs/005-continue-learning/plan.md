# Implementation Plan: Phase 4 — Continue Learning & State Persistence

**Branch**: `005-continue-learning` | **Date**: 2026-07-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-continue-learning/spec.md`

## Summary

Phase 4 makes Matn remember. It persists the student's session — which matn, which verse, the
millisecond position inside it, the per-matn repetition settings, and any in-progress A–B loop —
and surfaces it as a one-tap **Continue Learning** entry on Home that resumes playback mid-verse
with the drill still configured.

The technical story is unusually short, because **Phases 2 and 3 built the seams on purpose**.
`PlaybackState`'s own KDoc says it "carries exactly the fields Phase 4 must persist … so Phase 4
persists this snapshot without reshaping it", and `RepetitionSettingsStore` is documented as "the
Phase 4 persistence seam — a SQLDelight-backed implementation substitutes in with no change to any
caller". This plan takes both at their word: it **swaps one Koin binding**, adds a
`SessionStateRecorder` that *observes* `PlaybackController.state` rather than modifying the
controller, and adds one defaulted parameter (`startPositionMs`) so every existing caller and test
stays source-compatible.

Two decisions carry the real weight. First, all the "what if the content changed?" rules
(FR-025–FR-028) collapse into a **pure `ResumeTargetResolver`** — table-testable with no fakes, no
database, no coroutines — the same treatment Phase 3 gave `RepetitionPlanner` and what the
constitution asks for on correctness-critical logic. Second, the FR-026 clarification (resume at the
*nearest surviving verse*) forces a data-model consequence that is easy to miss: the saved verse's
**display number must be persisted alongside its ID**, because once the verse row is deleted its ID
carries no ordering information and "nearest" becomes uncomputable.

**One pre-existing gap surfaced.** The project has **no SQLDelight migration files**; both drivers
pass `ContentDatabase.Schema`, so a new table reaches fresh installs via `create()` but never
reaches an existing install, whose first query against it would fail at runtime. Phase 1 added
`app_setting` this way without consequence only because the app is unreleased. This plan introduces
`1.sqm` and a migration test — in the phase whose whole purpose is not losing state, before first
release, while the fix costs one file.

## Technical Context

**Language/Version**: Kotlin 2.4.10 (Kotlin Multiplatform), JVM target 11 for Android.

**Primary Dependencies**: **No new dependency.** Existing only — Compose Multiplatform 1.11.1
(Material 3), AndroidX Lifecycle, CMP Navigation, Koin 4.0.0, kotlinx-coroutines 1.9.0,
SQLDelight 2.0.2, Media3 1.4.1 (`androidMain`).

**Storage**: SQLDelight. One new table `matn_session` (one row per matn), one new `app_setting` row
`last_listened_matn_id` reusing the existing key/value table and its `ReadingPreferences` precedent.
**Database version 1 → 2 with the project's first migration file.**

**Testing**: `kotlin.test` + `kotlinx-coroutines-test` in `commonTest`.
`ResumeTargetResolverTest` is a pure table test (no fakes). `SessionStateRecorderTest` drives a
`MutableStateFlow<PlaybackState>` against a fake repository, using the virtual-time scheduler to
exercise throttling.
`SessionStateRepositoryTest` and `MigrationTest` run on the in-memory SQLDelight driver. Process
death, OS eviction, and upgrade-in-place are device-validated ([quickstart.md](./quickstart.md) §B).

**Target Platform**: Android (minSdk 24, compileSdk 36) + iOS (iosArm64, iosSimulatorArm64); default
light theme + phone layout (dark mode / tablet remain Phase 8).

**Project Type**: Mobile — KMP shared library (`:shared`) consumed by `:androidApp` and `iosApp`.
Extends existing `data`/`domain`/`playback`/`presentation` slices; adds no module and no platform
shell change.

**Performance Goals**: Structural changes persisted within 1 s (SC-004); Continue Learning adds
≤200 ms to Home load (SC-005); resume ready and audible within 2 s (SC-006); resumed audio within
1 s of the saved position (SC-006a).

**Constraints**: Fully offline (FR-030/SC-010). A failed write must never disturb playback
(FR-011/SC-008), so position writes are throttled, never per-tick. Mode is **never** persisted
(FR-005) — structurally guaranteed, since `RepetitionSettings.mode` is a computed property with no
backing field. In-flight repetition indices are **not** persisted (FR-006). Existing Phase 2/3 test
assertions must not require edits.

**Scale/Scope**: 1 new table + 1 setting key + 1 migration; 3 domain types; 1 pure resolver;
1 recorder; 1 repository (interface + impl); 1 persistent `RepetitionSettingsStore` replacing the
in-memory one; 3 use cases; 1 reusable `ContinueLearningCard` composable; 1 nullable `HomeUiState`
field; 1 defaulted controller parameter. Session rows are bounded by library size (kilobytes).

## Constitution Check

*GATE: Passed before Phase 0 research. Re-checked after Phase 1 design — still PASS.*

Evaluated against constitution **v1.4.0** (this branch was synced to `develop` to pick up
Principle VIII, which governs the Home-screen UI work in this phase).

| Principle | Applies? | How this plan complies |
|-----------|----------|------------------------|
| **I. Clean Architecture & Layer Boundaries** | Yes | `SessionStateRepository` is a domain interface with a data-layer implementation; the resolver and use cases depend on the interface only. `SessionStateRecorder` observes a `StateFlow` and writes through the repository interface — no SQLDelight type escapes the data layer. `HomeViewModel` reaches persistence solely through use cases. Direction presentation → domain ← data is unchanged. |
| **II. MVVM Presentation (NON-NEGOTIABLE)** | Yes | `HomeUiState` gains one nullable field and stays a pure projection; the ViewModel owns no persistence decision. `ContinueLearningCard` ships as a **stateless content composable + thin holder**, with `@Preview`s for the states that matter: present, absent (card omitted), and long-title truncation. |
| **III. DRY via Base Abstractions** | Yes | Reuses `BaseViewModel`, `UseCase`, `Resource`/`AppError`, and the existing `storageCall` helper. `SavedMatnSession` embeds Phase 3's `RepetitionSettings` rather than re-declaring counters and range. The `app_setting` pointer reuses the `ReadingPreferences` key/value pattern rather than inventing a second singleton mechanism. Resolution rules exist in exactly **one** place. |
| **IV. Shared-First Multiplatform** | Yes | Every decision — write cadence, resolution rules, encoding, defaults — lives in `commonMain`. **No new `expect`/`actual` is introduced**; the existing `DatabaseDriverFactory` already covers both platforms, and the new table needs no platform code at all. |
| **V. Test-First & Testable Design (NON-NEGOTIABLE)** | Yes (**core**) | `ResumeTargetResolver` is pure and table-tested with no fakes. `SessionStateRecorder` is testable from a `MutableStateFlow` with no controller, engine, or database — its throttle is driven by the virtual-time scheduler, so nothing persisted depends on a real clock and there is no time source needing a fake. `MigrationTest` covers the upgrade path that fresh-install testing structurally cannot reach. |
| **VI. Offline-First & Future-Proof Data** | Yes (**this phase delivers it**) | This is the phase the principle's third bullet names outright — last matn/verse, millisecond position, repetition settings, and active A–B range persisted on every verse transition or config change. All identities are stable UUIDs (FR-003). Zero network. The schema adds no assumption blocking remote accounts or sync: `matn_session` is keyed by the same UUIDs a future sync would use, and the migration path introduced here (research D3) is exactly what lets sync-era columns be added later without data loss. A speculative `updated_at_ms` was deliberately **not** added — the principle requires the shape not to *block* sync, not to pre-build for it. |
| **VII. Experience Fidelity: Audio, RTL & Accessibility** | Yes | Resume reuses Phase 2's audio-focus and `PauseReason` machinery rather than adding a parallel path, so the contractual interruption behavior is preserved — resuming during a call restores paused, never plays over another app (FR-022a). Gaplessness is untouched: this phase adds no playback-path logic. Throttled writes protect against I/O-induced stutter. The card is laid out on the existing native-RTL Home surface. **No progress signal is produced** — upholding the decoupling of memorization from playback activity (Phase 6). |
| **VIII. Design Fidelity & Reusable Composables (NON-NEGOTIABLE)** | Yes | The card is implemented from the **Home / Library** Stitch screen (`618643f8…`, registered in `docs/DESIGN-SOURCE.md`), whose Continue Learning region is the design of record — `docs/DESIGN-SOURCE.md` notes Phase 4 has no dedicated screen, so this card region is the authority. `ContinueLearningCard` is built as a **reusable, stateless, parameterized** component in the shared UI layer rather than inline in `HomeScreen`, with previews. Styling uses existing theme tokens (`presentation/theme/`) — **no hard-coded colors or magic dp/sp**. |

**Additional constraints**

- **Stack** unchanged: KMP + Compose Multiplatform, MVVM, Koin DI, base package `com.giraffe.matn`.
- **Per-verse audio model honored**: resume seeks *within a single verse's own file*; nothing seeks
  across a shared continuous file.
- **DI**: `contentModule()` swaps `InMemoryRepetitionSettingsStore` → the persistent implementation
  and adds `SessionStateRepository`, `SessionStateRecorder`, and three use cases. No new module, no
  manual singleton.
- **New dependency**: none — the justification requirement is trivially satisfied.

**Result**: ✅ PASS — no violations. The Complexity Tracking table below is intentionally empty.

Three additions were weighed against "unjustified complexity" and kept, with reasons recorded:

- *`SessionStateRecorder` as a separate observer* rather than write calls inside
  `PlaybackController`. It costs one class but avoids threading persistence through 10+ mutation
  sites in a 602-line controller — each one a place to forget a write — and keeps the throttling
  policy in a single testable file (research D1).
- *`last_verse_display_number` as a second stored anchor* looks redundant beside the verse ID until
  the verse is deleted, which is precisely the FR-026 case. Without it the clarified behavior is
  unimplementable (research D4).
- *Introducing migration files* is new machinery in a phase that could have skipped it. It is
  justified by the gap being real, silent, and strictly cheaper to fix before release than after
  (research D3).

## Project Structure

### Documentation (this feature)

```text
specs/005-continue-learning/
├── plan.md              # This file
├── research.md          # Phase 0 — D1–D8 decisions
├── data-model.md        # Phase 1 — schema, domain types, resolution rules R1–R9
├── quickstart.md        # Phase 1 — validation guide (§A automated, §B device)
├── contracts/
│   └── session-persistence.md
├── checklists/
│   └── requirements.md  # 16/16 passing
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
shared/src/commonMain/
├── sqldelight/com/giraffe/matn/db/
│   ├── Content.sq                          # + matn_session table, + session queries
│   └── migrations/1.sqm                    # NEW — v1 → v2 (research D3)
└── kotlin/com/giraffe/matn/
    ├── domain/
    │   ├── model/
    │   │   ├── SavedMatnSession.kt         # NEW
    │   │   ├── ResumeTarget.kt             # NEW
    │   │   └── ContinueLearningEntry.kt    # NEW
    │   ├── repository/
    │   │   └── SessionStateRepository.kt   # NEW (interface)
    │   ├── session/
    │   │   └── ResumeTargetResolver.kt     # NEW (pure — R1–R9)
    │   └── usecase/
    │       ├── ObserveContinueLearningUseCase.kt   # NEW
    │       ├── ResolveResumeTargetUseCase.kt       # NEW
    │       └── DismissContinueLearningUseCase.kt   # NEW
    ├── data/repository/
    │   ├── SessionStateRepositoryImpl.kt           # NEW
    │   ├── PersistentRepetitionSettingsStore.kt    # NEW — replaces in-memory
    │   └── InMemoryRepetitionSettingsStore.kt      # retained for tests
    ├── playback/
    │   ├── SessionStateRecorder.kt         # NEW (observes controller state)
    │   └── PlaybackController.kt           # + startPositionMs (defaulted)
    ├── presentation/
    │   ├── common/ContinueLearningCard.kt  # NEW — reusable, stateless, previewed
    │   └── home/{HomeUiState,HomeViewModel,HomeScreen}.kt   # + card wiring
    └── di/ContentModule.kt                 # + bindings, swap settings store

shared/src/commonTest/kotlin/com/giraffe/matn/
├── session/ResumeTargetResolverTest.kt     # pure table tests
├── playback/SessionStateRecorderTest.kt    # write policy W1–W7
├── data/SessionStateRepositoryTest.kt      # round trip, P1–P5, S1–S5
└── db/MigrationTest.kt                     # v1 → v2
```

**Structure Decision**: No new module. The feature extends four existing slices of `:shared`
(`domain`, `data`, `playback`, `presentation`) following the layout Phases 0–3 established. The one
new package is `domain/session/`, holding the pure resolver — mirroring how Phase 3 gave
`RepetitionPlanner` a home distinct from the controller that uses it.

## Complexity Tracking

> No Constitution Check violations. Table intentionally empty — the three judgement calls above are
> recorded in the Constitution Check section rather than as violations, because each is justified
> under an existing principle rather than an exception to one.
