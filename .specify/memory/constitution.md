<!--
Sync Impact Report
==================
Version change: 1.1.0 → 1.2.0
Rationale: Aligned the constitution with the rewritten roadmap in
docs/PLAN-matn-product-spec-v1.md — added a "Phased, Incremental Delivery" workflow
subsection (per-phase spec-kit cycles, independently buildable/testable slices, phase
dependency ordering), named SQLDelight as the persistence engine, and locked the
per-verse micro-audio-file asset model in Technology & Architecture Constraints
(MINOR: new guidance and specifiers, no principle removed or redefined).

History:
  - 1.2.0 (2026-07-18): Roadmap alignment — phased delivery, SQLDelight, per-verse audio model.
  - 1.1.0 (2026-07-18): Added "Branching & Pull Requests" subsection — protected main/develop,
    PR-only flow, gitignored assistant config.
  - 1.0.0 (2026-07-18): Initial ratification (MAJOR: first adoption) — 7 principles,
    Technology & Architecture Constraints, Development Workflow & Quality Gates, Governance.

Modified principles: None
Added sections:
  - Development Workflow & Quality Gates → "Phased, Incremental Delivery" (new subsection)
  - Technology & Architecture Constraints → SQLDelight persistence + locked per-verse audio model
Removed sections: None

Templates & artifacts reviewed:
  ✅ .specify/templates/plan-template.md — "Constitution Check" gate references this file
       dynamically; no hard-coded principle text to update.
  ✅ .specify/templates/spec-template.md — no constitution-coupled sections; no change needed.
  ✅ .specify/templates/tasks-template.md — task categories compatible with principles;
       no change needed.
  ✅ .specify/templates/checklist-template.md — generic; no change needed.
  ⚠ .specify/templates/commands/*.md — directory not present in repo; nothing to reconcile.
  ✅ docs/PLAN-matn-product-spec-v1.md — rewritten as phased roadmap; principles now align
       with the phase model and dependency ordering.

Deferred TODOs: None.
-->

# Matn Constitution

Matn is an offline-first Kotlin Multiplatform (Android + iOS) memorization companion for
Islamic texts (المتون). This constitution defines the non-negotiable engineering principles
that keep the codebase clean, testable, scalable, and free of duplication as it is delivered
through the phased roadmap in `docs/PLAN-matn-product-spec-v1.md` and grows toward the
online/sync capabilities scheduled there.

## Core Principles

### I. Clean Architecture & Layer Boundaries

The codebase MUST be organized into three strictly separated layers: **domain**, **data**, and
**presentation**.

- The domain layer (entities, use cases, repository interfaces) MUST NOT depend on any other
  layer, on any framework, or on any platform API. It is pure Kotlin in `commonMain`.
- The data layer implements domain repository interfaces and owns all persistence, audio-asset,
  and (future) network access. The domain layer depends on repository **interfaces**, never on
  their implementations.
- The presentation layer depends on the domain layer only, through use cases — never directly on
  data-layer classes.
- Dependencies MUST point inward (presentation → domain ← data). A violation of this direction is
  a blocking review failure.

**Rationale**: Enforced boundaries make each layer independently testable and let the data layer
swap from bundled files to streaming/sync (v2) without touching domain or UI code.

### II. MVVM Presentation (NON-NEGOTIABLE)

All UI state MUST flow through the MVVM pattern with unidirectional data flow.

- Every screen has a `ViewModel` that exposes a single immutable, observable UI-state object
  (a Kotlin `data class` / sealed state) via `StateFlow`. Composables render state and emit
  intents/events; they MUST NOT contain business logic or call use cases/repositories directly.
- ViewModels MUST NOT reference Compose, Android `Context`, `View`, or any platform UI type.
- State mutation happens only inside the ViewModel; Composables are pure functions of state.

**Rationale**: A single observable state source keeps the reading/auto-scroll/playback screens
predictable and testable without a device.

### III. DRY via Base Abstractions

Repeated cross-cutting structure MUST be lifted into shared base types rather than copy-pasted.

- Presentation MUST provide a `BaseViewModel` encapsulating the common state container,
  coroutine scope, error handling, and loading semantics.
- Use cases MUST share a common contract (e.g., `UseCase`/`FlowUseCase` base with an `invoke`
  operator); repositories share a base contract for common CRUD/observe patterns where applicable.
- A generic result/error wrapper (e.g., `Result`/`Resource` sealed type) MUST be used for
  fallible operations instead of ad-hoc nullable returns or thrown exceptions across layers.
- Before duplicating a block of logic a second time, the author MUST extract it into a base type,
  extension, or shared utility.

**Rationale**: The spec has many near-identical flows (per-verse, per-matn, per-bookmark);
base abstractions keep them consistent and shrink the surface area of bugs.

### IV. Shared-First Multiplatform

Business logic MUST live once in `commonMain`; platform code is a thin edge only.

- Domain and data logic (models, use cases, repositories, playback/repetition state machines,
  progress heuristics) MUST be implemented in `commonMain`.
- `expect`/`actual` declarations are reserved for genuinely platform-specific capabilities
  (audio engine — ExoPlayer on Android, AVQueuePlayer on iOS — local storage, wake lock,
  notifications). Platform `actual` implementations MUST contain no business logic.
- Duplicating logic across `androidMain` and `iosMain` is prohibited; if both need it, it belongs
  in `commonMain`.

**Rationale**: Single-source logic is the core value of the KMP choice and prevents the two
platforms from drifting apart.

### V. Test-First & Testable Design (NON-NEGOTIABLE)

Testability is a design constraint, not an afterthought.

- Domain use cases and the playback/repetition/progress logic MUST have unit tests in
  `commonTest`, written against the same behavior described in the spec. These tests MUST NOT
  require a device, emulator, or real audio files.
- All collaborators (repositories, audio player, clock/time source used by the spaced-repetition
  heuristic) MUST be injected via interfaces so they can be faked in tests.
- New or changed domain/data behavior MUST land with tests in the same change. A change to
  repetition, A–B loop, or "memorized" logic without accompanying tests is a blocking failure.

**Rationale**: The memorization matrix (Vr, Mr, ∞, A–B loops) and honest progress metric are the
product's core correctness surface and must be provable in isolation.

### VI. Offline-First & Future-Proof Data

Data models MUST be designed on day one for the v2 online/sync roadmap.

- Every persisted entity (matn, verse, bookmark, note, progress record) MUST carry a stable
  `UUID` identity independent of display order or local row IDs.
- Local persistence is the source of truth for v1; the app MUST function fully with no network.
- State required by "Continue Learning" (last matn/verse, millisecond position, repetition
  settings, active A–B loop range) MUST be persisted on every verse transition or config change.
- Schemas MUST avoid assumptions that block later remote accounts, cross-device sync,
  multi-reciter audio mapping, or shared community content.

**Rationale**: Retrofitting stable IDs and sync-safe schemas later is expensive and error-prone;
structuring for it now is nearly free.

### VII. Experience Fidelity: Audio, RTL & Accessibility

The features that make Matn feel like a memorization companion are contractual, not optional
polish.

- Verse-to-verse audio transitions MUST be gapless via deliberate pre-buffering of the next
  verse (ExoPlayer gapless/concatenation on Android, AVQueuePlayer on iOS).
- Audio interruptions (calls, audio-focus loss, Bluetooth changes) MUST be handled gracefully —
  pause and allow resume, never silently die.
- The UI MUST be fully native RTL, support dark mode, adjustable Arabic font size, tablet-adaptive
  layouts, and meet contrast-accessibility guidelines.
- Progress toward "memorized" MUST be decoupled from raw playback count, per the spec (explicit
  self-report and/or multi-session spaced heuristic).

**Rationale**: These are the differentiators called out in the product spec; regressions here
break the product's promise even if the app "works."

## Technology & Architecture Constraints

- **Stack**: Kotlin Multiplatform with Compose Multiplatform UI; modules `shared` (domain + data +
  presentation logic), `androidApp`, and `iosApp`. Base package `com.giraffe.matn`.
- **Language/UI**: Kotlin with coroutines/Flow for async and state; Compose Material 3 for UI.
- **Persistence & audio** are accessed only through domain-defined interfaces; concrete engines
  (SQLDelight local database, ExoPlayer/AVQueuePlayer) live in the data/platform layers.
- **Audio asset model** is locked: exactly one micro-audio file per verse (matching how the
  teacher's recordings are produced). Data models and playback MUST assume this per-verse file
  boundary; a shared/continuous-file model is out of scope.
- **Dependency injection** MUST be used to wire layers; no manual singletons or service locators
  reached across layer boundaries.
- Adding a new third-party dependency requires justification against a simpler alternative in the
  PR description.

## Development Workflow & Quality Gates

- **Feature flow**: spec → plan (with Constitution Check gate) → tasks → implementation, per the
  Spec Kit templates in `.specify/`.
- **Constitution Check**: every `/speckit-plan` MUST pass the Constitution Check gate before
  design proceeds; violations go in the plan's Complexity Tracking with explicit justification or
  the design is revised.
- **Code review** MUST verify: layer-dependency direction, MVVM state discipline, logic placed in
  `commonMain`, presence of tests for changed domain/data logic, and no duplicated logic that
  should be a base abstraction.
- **CI expectation**: `commonTest` (and platform host tests where relevant) MUST pass before merge.

### Phased, Incremental Delivery

Development follows the phased roadmap in `docs/PLAN-matn-product-spec-v1.md`.

- Each phase is scoped to a single Spec Kit cycle (`/specify` → `/plan` → `/tasks` → `/implement`)
  and MUST produce an independently buildable and testable slice of the app — no phase may leave
  the app in a non-building or untestable state.
- Phase prerequisites MUST be respected: Phases 0 → 1 → 2 are strictly ordered hard prerequisites;
  Phase 4 (Continue Learning / state persistence) requires Phases 1–3; Phases 3, 5, 6, 7, 8 may be
  reordered relative to each other provided their own prerequisites hold.
- Foundational invariants MUST be established in their owning phase and upheld thereafter:
  UUID-based domain entities and the per-verse audio asset model in Phase 0; recall-based progress
  (not raw listen count) in Phase 6.

### Branching & Pull Requests

- `main` and `develop` are protected: they MUST NOT receive direct pushes. All changes reach them
  only through pull requests.
  - `main` holds released/stable state; `develop` is the integration branch.
- All work happens on short-lived branches created from `develop`, named by intent
  (`feature/…`, `fix/…`, `chore/…`, `docs/…`). Feature PRs target `develop`; `develop → main`
  PRs cut a release.
- Every PR MUST pass CI (Principle V) and a review verifying constitution compliance before merge.
- Local-only tooling and editor/assistant config (e.g., `.claude/`, `.opencode/`) MUST stay
  gitignored and out of the repository.

## Governance

- This constitution supersedes ad-hoc conventions; where a practice conflicts with it, the
  constitution wins.
- **Amendments** require a documented rationale, an update to this file, a version bump per the
  policy below, and propagation to affected `.specify/` templates.
- **Versioning policy** (semantic):
  - **MAJOR**: removing or redefining a principle in a backward-incompatible way.
  - **MINOR**: adding a principle/section or materially expanding guidance.
  - **PATCH**: clarifications and wording fixes with no semantic change.
- **Compliance review**: PRs and plans MUST be checked against these principles; unjustified
  complexity is rejected. Justified exceptions are recorded in the relevant plan's Complexity
  Tracking table.

**Version**: 1.2.0 | **Ratified**: 2026-07-18 | **Last Amended**: 2026-07-18
