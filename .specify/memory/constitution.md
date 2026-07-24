<!--
Sync Impact Report
==================
Version change: 1.4.0 → 1.4.1
Rationale: docs/ROADMAP.md renumbered its phases from 0-indexed to 1-indexed so each phase number
matches its `specs/NNN-*` folder (Phase 1 ↔ 001-*, ..., Phase 5 ↔ 005-*), removing a recurring
source of off-by-one confusion. This document's Phase references in the "Phased, Incremental
Delivery" subsection are updated to match (Phase 4 Continue Learning → Phase 5, Phase 0 → Phase 1,
Phase 6 → Phase 7, etc.). No principle, rule, or gate changed — wording only, hence PATCH.

History:
  - 1.4.1 (2026-07-24): Phase-number references in "Phased, Incremental Delivery" updated to match
    ROADMAP.md's 1-indexed renumbering (was 0-indexed). No semantic change.
  - 1.4.0 (2026-07-24): Added Principle VIII — Stitch as canonical design source, mandatory
    design-token centralization, and reusable-component extraction as a blocking review item.
    Carve-out added for secret-free, root-level MCP server declarations being tracked.
  - 1.3.0 (2026-07-19): Principle II expanded — stateless/stateful screen split + @Preview
    coverage as blocking review items.
  - 1.2.1 (2026-07-18): Split docs into PRODUCT-SPEC.md + ROADMAP.md; repointed references accordingly.
  - 1.2.0 (2026-07-18): Roadmap alignment — phased delivery, SQLDelight, per-verse audio model.
  - 1.1.0 (2026-07-18): Added "Branching & Pull Requests" subsection — protected main/develop,
    PR-only flow, gitignored assistant config.
  - 1.0.0 (2026-07-18): Initial ratification (MAJOR: first adoption) — 7 principles,
    Technology & Architecture Constraints, Development Workflow & Quality Gates, Governance.

Modified principles:
  - II. MVVM Presentation (NON-NEGOTIABLE) — added: stateless "content" + thin stateful holder
    split; mandatory @Preview coverage for state-rendering composables. (1.3.0)
Added sections:
  - VIII. Design Fidelity & Reusable Composables — new principle (1.4.0)
Removed sections: None

Templates & artifacts reviewed:
  ✅ .specify/templates/plan-template.md — "Constitution Check" gate references this file
       dynamically; no hard-coded principle text to update.
  ✅ .specify/templates/spec-template.md — no constitution-coupled sections; no change needed.
  ✅ .specify/templates/tasks-template.md — task categories compatible with principles;
       the new UI rules surface as presentation-task expectations, no template edit required.
  ✅ .specify/templates/checklist-template.md — generic; no change needed.
  ⚠ .specify/templates/commands/*.md — directory not present in repo; nothing to reconcile.
  ✅ docs/PRODUCT-SPEC.md — Product Vision & Requirements (WHAT); unaffected by this amendment.
  ✅ docs/ROADMAP.md — phased delivery plan (HOW/WHEN); unaffected by this amendment.
  ✅ docs/DESIGN-SOURCE.md — NEW: Stitch project + screen→phase registry referenced by
       Principle VIII. Holds the volatile IDs so this constitution does not.

Deferred TODOs:
  - Duplicate Stitch screens (Matn Details ×2, Reading & Playback ×3) need a confirmed
    canonical pick; tracked in docs/DESIGN-SOURCE.md "Open issues".
  - "Upload Matn (Timestamp Map)" screen designs the rejected shared-audio-file model and
    should be retired in Stitch; tracked in docs/DESIGN-SOURCE.md "Open issues".
-->

# Matn Constitution

Matn is an offline-first Kotlin Multiplatform (Android + iOS) memorization companion for
Islamic texts (المتون). This constitution defines the non-negotiable engineering principles
that keep the codebase clean, testable, scalable, and free of duplication. Product requirements
(the WHAT) live in `docs/PRODUCT-SPEC.md`; the phased delivery sequence (the HOW/WHEN) lives in
`docs/ROADMAP.md`, which also schedules the later online/sync capabilities. The canonical UI
designs (the LOOK) live in the Stitch project registered in `docs/DESIGN-SOURCE.md`.

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
- **Stateless/stateful split**: Every screen MUST be split into (a) a **stateless "content"
  composable** that is a pure function of the screen's immutable UI-state `data class` plus
  intent lambdas, holding all rendering logic, and (b) a **thin stateful holder** composable
  that only collects the ViewModel's `StateFlow` and forwards intents. Rendering logic MUST NOT
  live in the holder. This keeps the render layer inspectable and testable in isolation from DI
  and the ViewModel.
- **Preview coverage**: Every composable that renders UI state MUST have at least one `@Preview`
  driven by hand-built sample state — no ViewModel, no DI, no database. Screen-level content
  composables MUST preview their key states (e.g. loaded / empty / error). A new or changed
  state-rendering composable without a preview is a **blocking review failure**.

**Rationale**: A single observable state source keeps the reading/auto-scroll/playback screens
predictable and testable without a device. Forcing a stateless content composable makes the UI a
pure function of state that can be previewed and screenshot-tested for every state (including RTL,
Arabic typography, and error/empty paths) without booting DI or a database — the fastest and most
reliable UI-regression guard.

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

### VIII. Design Fidelity & Reusable Composables (NON-NEGOTIABLE)

UI is implemented **from the approved design**, as **reusable components**, driven by **shared
tokens** — never invented per screen and never copy-pasted.

- **Canonical design source**: The Stitch project registered in `docs/DESIGN-SOURCE.md` is the
  single visual source of truth. Before implementing or restyling any screen, the implementer
  (human or model) MUST fetch that screen's design from Stitch via the configured `stitch` MCP
  server. Inventing a layout for a screen that has a design is a blocking review failure.
- **The constitution outranks the design**: Where a Stitch screen conflicts with a locked
  architectural decision or a principle here, **this document wins** and the conflict MUST be
  raised rather than silently implemented. (Concretely: designs presupposing a shared/continuous
  audio file with timestamp maps contradict the locked per-verse audio model and MUST NOT be
  built.) Designs are authoritative about *appearance*, not about *architecture*.
- **Tokens, not literals**: Color, typography, spacing, corner radii, and elevation MUST be
  defined once in a shared design-token layer derived from the Stitch Design System screen.
  Hard-coded literals (raw hex colors, magic `.dp`/`.sp` values) in screen or feature composables
  are a blocking review failure.
- **Reusable by default**: Every composable MUST be written as a self-contained, reusable
  component unless it is genuinely single-use. Concretely:
  - A UI element that appears on **two or more screens** — or is a recognizable repeated unit
    within one screen (verse row, matn card, player control, counter stepper, section header) —
    MUST live in the shared UI component layer, not inline in a screen.
  - The trigger to extract is the **second use, not the third**: before copy-pasting a composable
    block, extract it. This is Principle III (DRY) applied to the render layer.
  - Shared components MUST be **stateless and parameterized** — driven entirely by their
    parameters and intent lambdas, with no ViewModel, DI, navigation, or repository access — so
    they compose freely and preview in isolation.
  - Every shared component carries at least one `@Preview`, per Principle II.
- **Reuse before adding**: Before writing a new component, the author MUST check the existing
  shared UI layer for one that fits or can be parameterized. Near-duplicate components (two cards
  differing only by a label or icon) MUST be unified.

**Rationale**: With designs now reachable programmatically by any model, the failure mode shifts
from "no design" to "each screen re-implemented from scratch, slightly differently." Pinning a
canonical source, centralizing tokens, and forcing extraction at the second use keeps a
multi-screen, bilingual, RTL, light/dark app visually coherent — and keeps the render layer small
enough to actually preview and screenshot-test. Stating that the constitution outranks the design
prevents an out-of-date mockup from quietly reversing a locked architectural decision.

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
- **Design source**: UI designs are retrieved from Stitch through the `stitch` MCP server declared
  in `.mcp.json` (Claude Code) and `opencode.json` (OpenCode). These declarations are tracked and
  MUST stay secret-free — credentials live only in each developer's local environment
  (`STITCH_API_KEY`) or local OAuth credentials. Project and screen IDs live in
  `docs/DESIGN-SOURCE.md`, never inline in code.
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
- **UI review** MUST additionally verify (Principle VIII): the screen matches its Stitch design,
  no hard-coded colors/spacing/type literals, repeated elements extracted into shared stateless
  components rather than copy-pasted, and a `@Preview` present for each new state-rendering
  composable.
- **CI expectation**: `commonTest` (and platform host tests where relevant) MUST pass before merge.

### Phased, Incremental Delivery

Development follows the phased roadmap in `docs/ROADMAP.md` (feature detail for each phase is
specified in `docs/PRODUCT-SPEC.md`).

- Each phase is scoped to a single Spec Kit cycle (`/specify` → `/plan` → `/tasks` → `/implement`)
  and MUST produce an independently buildable and testable slice of the app — no phase may leave
  the app in a non-building or untestable state.
- Phase prerequisites MUST be respected: Phases 1 → 2 → 3 are strictly ordered hard prerequisites;
  Phase 5 (Continue Learning / state persistence) requires Phases 2–4; Phases 4, 6, 7, 8, 9 may be
  reordered relative to each other provided their own prerequisites hold.
- Foundational invariants MUST be established in their owning phase and upheld thereafter:
  UUID-based domain entities and the per-verse audio asset model in Phase 1; recall-based progress
  (not raw listen count) in Phase 7.

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
  - **Carve-out**: root-level *capability* declarations that every contributor needs — currently
    `.mcp.json` and `opencode.json`, which declare the `stitch` design server — ARE tracked, on the
    condition that they contain **no credentials**. The distinction is personal preference (local,
    ignored) versus shared project capability (tracked). Any file that would embed a token or API
    key stays untracked.

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

**Version**: 1.4.1 | **Ratified**: 2026-07-18 | **Last Amended**: 2026-07-24
