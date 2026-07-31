<!--
Sync Impact Report
==================
Version change: 1.5.0 → 2.0.0
Rationale: MAJOR, driven by roadmap Phases 11–13, which replace binary-bundled content with a
remote-backend model — teacher uploads, students browse a remote catalog of overviews, students
download per matn. Principle VI's requirement that "the app MUST ship with at least one complete,
immediately playable matn" is **removed**: with teacher-uploaded content as the sole channel, a
bundled matn would be content no teacher published and none can revise. Removing a MUST and
redefining what the offline guarantee covers is precisely what the Governance versioning policy
reserves MAJOR for, and shipped behaviour changes — a network-less first launch is now an empty
library rather than a working app. Three smaller corrections ride along: (1) the Stack constraint
still described Matn as "Android + iOS" with modules shared/androidApp/iosApp, though a desktop
(JVM) client landed in 4941cc7 and :teacherApp adds a second; (2) the per-verse audio lock could be
mis-read as forbidding an authoring tool that *accepts* a continuous recording and slices it — the
lock is unchanged, but the input/output distinction is now explicit; (3) phase prerequisites extended
through 13.

History:
  - 2.0.0 (2026-07-26): Principle VI — bundled-starter-matn requirement REMOVED; acquiring content
    now requires connectivity unconditionally, with offline usability guaranteed only for content
    already downloaded. Stack — desktop (JVM) targets recognized; student clients distinguished
    from the producer client. Audio asset model — per-verse lock unchanged, with an explicit
    carve-in for authoring-time tooling whose output is per-verse files.
  - 1.5.0 (2026-07-25): Principle VI — offline guarantee scoped to content already on the device;
    acquiring new content may require connectivity, conditional on a bundled starter matn and full
    offline usability thereafter. Resolves the Phase 8 deviation.
  - 1.4.2 (2026-07-24): "Phased, Incremental Delivery" references the new Phase 10 (Design System
    Adoption) and its dependency note. No semantic change.
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
  - VI. Offline-First & Future-Proof Data — BREAKING: the bundled-starter-matn condition is removed;
    acquisition unconditionally requires connectivity. Rationale rewritten, since it previously
    argued for the starter by name. (2.0.0)
  - Technology & Architecture Constraints / Stack — desktop (JVM) targets added; student clients
    distinguished from the producer client (:teacherApp). (2.0.0)
  - Technology & Architecture Constraints / Audio asset model — per-verse lock restated unchanged,
    with authoring-time input explicitly carved in. (2.0.0)
  - VI. Offline-First & Future-Proof Data — offline guarantee scoped to on-device content;
    connectivity permitted for acquisition under two conditions. (1.5.0)
  - II. MVVM Presentation (NON-NEGOTIABLE) — added: stateless "content" + thin stateful holder
    split; mandatory @Preview coverage for state-rendering composables. (1.3.0)
Added sections:
  - VIII. Design Fidelity & Reusable Composables — new principle (1.4.0)
Removed sections: None

Templates & artifacts reviewed:
  (✅ = checked, no action outstanding. ⚠ = checked, unresolved finding. Absence of a file is not
   a finding and is not listed.)

  Checked for 2.0.0:
  ✅ .specify/templates/* — no template hard-codes the module list, the target platforms, or the
       audio asset model, so this amendment required no propagation.
  ✅ docs/PRODUCT-SPEC.md — updated in the same PR, and the most consequential doc change of the
       three: "online catalog + selective download" moves out of § Future-Proof Engineering (V2)
       into v1; § Storage & Downloads no longer bundles anything; Phase 6 library-wide search
       narrows to catalog titles plus full text within downloaded matns.
  ✅ docs/ROADMAP.md — updated in the same PR: Phases 1–10 marked complete, and the single
       "Phase 11 — Teacher Dashboard & Firebase Upload" entry rewritten as Phases 11–13 under a
       new "Content Delivery & Authoring" section. Phase 13 supersedes Phase 8's delivery model.
  ✅ docs/DESIGN-SOURCE.md — updated in the same PR: open issues #2 and #3 resolved, both Upload
       screens added to the phase-mapped registry, stale "current-code gap" note closed.
       Holds the volatile Stitch IDs so this constitution does not.

  Carried from 1.4.0 (Principle VIII), retained as history — "the new UI rules" below means
  Principle VIII, not anything in this amendment:
  ✅ .specify/templates/plan-template.md — "Constitution Check" gate references this file
       dynamically; no hard-coded principle text to update.
  ✅ .specify/templates/spec-template.md — no constitution-coupled sections; no change needed.
  ✅ .specify/templates/tasks-template.md — task categories compatible with principles;
       the new UI rules surface as presentation-task expectations, no template edit required.
  ✅ .specify/templates/checklist-template.md — generic; no change needed.

Deferred TODOs:
  - ~~"Upload Matn (Timestamp Map)" screen should be retired in Stitch.~~ Closed 2026-07-26: the
    screen is retained as appearance-only reference for roadmap Phase 12's authoring-time
    splitter. The rejected shared-audio-file *architecture* remains forbidden.
    See docs/DESIGN-SOURCE.md "Open issues" #2.
  - ~~Dark-mode tokens (docs/DESIGN-SOURCE.md "Open issues" #5) remain undefined — owned by
    Phase 9.~~ Closed 2026-07-25 by Phase 9; MatnDarkColors is gated by ColorContrastTest.
  - Two dependency justifications are outstanding under "Adding a new third-party dependency
    requires justification against a simpler alternative" (Technology & Architecture Constraints):
    (a) Phase 11 — Ktor, the project's first HTTP client. Supabase ships no official client SDK
    for desktop JVM, so all backend access is PostgREST/Storage/Auth REST from one commonMain
    implementation rather than three platform SDK paths.
    See docs/ROADMAP.md § "Backend access — REST, not platform SDKs".
  - ~~(b) Phase 12 — an MP3 decoder (JLayer/mp3spi vs. bundled ffmpeg), needed for verse slicing and
    preview. Unavoidable: DesktopAudioEngine records that javax.sound.sampled ships no MP3 codec.~~
    Closed 2026-07-31: discharged by `specs/012-audio-capture-slicing/plan.md`'s Complexity
    Tracking. Landed narrower than this TODO anticipated — slicing and duration needed no decoder
    at all (pure frame-header arithmetic in `commonMain`); `javazoom:jlayer:1.0.1` is used only for
    waveform peaks and preview playback, decode-only, declared by `:teacherApp` alone. See
    `research.md` D1 for the JLayer vs. bundled-ffmpeg vs. mp3spi/tritonus comparison.
  - Phase 13 must delete, not merely bypass, the superseded delivery stack: the packs/* modules,
    play-asset-delivery-ktx, the three platform ContentDeliveryEngine implementations, the iOS ODR
    tags, bundledSampleMatns(), and the whole isStarter path. Leaving it in place would contradict
    Principle III and the amended Principle VI simultaneously.
-->

# Matn Constitution

Matn is a Kotlin Multiplatform memorization companion for Islamic texts (المتون), offline-first
for content the student has downloaded (Principle VI). This constitution defines the
non-negotiable engineering principles that keep the codebase clean, testable, scalable, and free
of duplication. Product requirements (the WHAT) live in `docs/PRODUCT-SPEC.md`; the phased
delivery sequence (the HOW/WHEN) lives in `docs/ROADMAP.md`. The canonical UI
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
swap its content source without touching domain or UI code — as Phase 13 does, replacing
store-bundled asset packs with Supabase behind the unchanged `ContentDeliveryEngine` seam.

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
- Local persistence is the source of truth; the app MUST function fully with no network **for all
  content already on the device** — reading, playback, repetition, progress, bookmarks, notes, and
  resume never require connectivity, and no personal data may depend on a network round-trip.
- **Acquiring new content REQUIRES connectivity.** From Phase 13 the remote catalog is the only
  content channel: nothing ships in the binary, so a first launch with no network is an empty
  library showing a connect-to-browse state. The one binding condition is that content already
  acquired MUST remain fully usable offline thereafter — reading, playback, repetition, progress,
  bookmarks, notes, and resume, with no network round-trip.
- State required by "Continue Learning" (last matn/verse, millisecond position, repetition
  settings, active A–B loop range) MUST be persisted on every verse transition or config change.
- Schemas MUST avoid assumptions that block later remote accounts, cross-device sync,
  multi-reciter audio mapping, or shared community content.

**Rationale**: Retrofitting stable IDs and sync-safe schemas later is expensive and error-prone;
structuring for it now is nearly free. The offline guarantee is about the student's *own* library —
what they have must always work, on a plane, in a masjid basement, with no signal. Bundling the
catalog into the binary was never what that guarantee meant, and forcing it would make the app grow
without bound as the library does.

Through 1.5.0 this principle additionally required a bundled starter matn, so that a network-less
first launch was still a working app. **That requirement was removed in 2.0.0**: with teacher-uploaded
content as the sole channel, a matn compiled into the binary would be content no teacher published
and no teacher can revise — a permanent fixture outside the system that produces everything else.
The cost is real and accepted: a student's *first* launch now needs connectivity. What the guarantee
protects is every launch after that.

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
  raised rather than silently implemented. Designs are authoritative about *appearance*, not about
  *architecture*. (Concretely: a design presupposing a shared/continuous audio file with timestamp
  maps does not license building that model — the per-verse lock in Technology & Architecture
  Constraints governs. Its *layout* may still be adopted where the implementation honours the
  lock, as roadmap Phase 12's authoring-time splitter does; see docs/DESIGN-SOURCE.md open issue
  #2 for the worked example of separating the two.)
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

- **Stack**: Kotlin Multiplatform with Compose Multiplatform UI; `shared` (domain + data +
  presentation logic) plus one module per client. Base package `com.giraffe.matn`.
  - **Student clients** — `androidApp`, `iosApp` (primary targets), and `desktopApp` (JVM). All
    three render the same shared `App()`; platform differences live behind the domain-defined
    engine interfaces below, never in duplicated UI.
  - **Producer client** — `teacherApp` (JVM), the content authoring tool built in roadmap Phases 11
    and 12. From Phase 13 it is the origin of *all* student-visible content: nothing ships in a
    client binary. It depends on `shared` for the domain model and design tokens but has its own
    UI surface, and MUST NOT be reachable from any student client.
  - Adding a new client module requires updating this list in the same PR.
- **Language/UI**: Kotlin with coroutines/Flow for async and state; Compose Material 3 for UI.
- **Persistence & audio** are accessed only through domain-defined interfaces; concrete engines
  (SQLDelight local database, ExoPlayer/AVQueuePlayer) live in the data/platform layers.
- **Audio asset model** is locked: exactly one micro-audio file per verse (matching how the
  teacher's recordings are produced). Data models and playback MUST assume this per-verse file
  boundary; a shared/continuous-file model is out of scope.
  - This governs the **persisted and shipped** asset model. Authoring-time tooling MAY accept a
    continuous recording as *input* provided it emits per-verse files, and provided no timestamp
    offsets against a shared asset are persisted, exported, or shipped to a student client. A
    matn whose verses resolve to ranges within one file is the rejected model, whatever produced
    it, and is a blocking review failure.
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
  reordered relative to each other provided their own prerequisites hold. Phase 10 (Design System
  Adoption) is a cross-cutting retrofit of Phases 1–5's UI and SHOULD land before Phases 6–9 begin
  their own UI work, since it establishes the shared token system and navigation shell those
  phases would otherwise have to invent independently. Phases 11 → 12 → 13 (Content Delivery &
  Authoring) are strictly ordered; Phase 13 **supersedes** Phase 8's store-bundled delivery model
  rather than building on it, keeping only its `ContentDeliveryEngine` seam and
  `ContentPackRepository` abstraction.
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

**Version**: 2.0.0 | **Ratified**: 2026-07-18 | **Last Amended**: 2026-07-26
