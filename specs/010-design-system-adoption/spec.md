# Feature Specification: Phase 10 — Design System Adoption

**Feature Branch**: `010-design-system-adoption`

**Created**: 2026-07-24

**Status**: Draft

**Input**: User description: "Retrofit Phases 1-5's UI to the canonical Stitch design set (docs/DESIGN-SOURCE.md): centralized design tokens, re-skinned Home/Matn Details/Reading & Playback/Repetition Setup screens, the reading experience reworked to a focused 3-verse carousel, a consolidated repetition-setup bottom sheet, and a persistent bottom-navigation shell with stubbed Goals/Notes/Settings tabs."

## Overview

Phases 1–5 shipped with an ad-hoc "manuscript" color palette, a single bundled font, no shared
spacing/shape tokens, and scattered repetition controls, because they were built before the
project's canonical Stitch design set was accessible. Phase 10 does not add a new feature area —
it retrofits the screens those phases already shipped so they match the canonical designs, and it
closes a standing gap between `docs/PRODUCT-SPEC.md`/`docs/DESIGN-SOURCE.md` and the app: a
persistent bottom-navigation shell that the design treats as universal chrome but that has never
been specified or built.

This phase is cross-cutting rather than additive. It changes how existing, already-shipped
screens look and are organized; it does not change what the underlying repetition engine,
playback engine, or continue-learning state persistence do. A student's memorization data,
in-progress sessions, and playback behavior must be unaffected — only the screens around them
change.

## Clarifications

### Session 2026-07-24

- Q: Should the Reading & Playback screen keep its current scrollable verse list with a docked
  player bar, or adopt the design's focused single/triple-verse carousel? → A: Adopt the
  carousel — it is what the canonical design specifies, not just a restyle of the existing list.
- Q: Should this work be organized as patches to each of specs/002–004, or as one new
  cross-cutting phase? → A: One new phase (this spec), so the visual-refresh work is traceable as
  its own unit instead of silently rewriting already-merged phases.
- Q: The design's bottom nav includes Goals and Notes tabs whose screens don't exist yet (Phases
  6–7 unbuilt) — build the shell now or wait? → A: Build the shell now; route the not-yet-built
  tabs to a shared "coming soon" placeholder, since the shell itself is chrome shared by every
  screen and shouldn't be re-derived later.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read and listen with the focused verse view (Priority: P1)

A student opens a matn to read and listen. Instead of scrolling a full list of verses with a
player bar docked at the bottom, they see a focused view: the verse just finished (muted,
de-emphasized), the current verse (large, highlighted, clearly the center of attention), and the
verse coming up next (muted). A floating control bar — scrub bar, playback speed, repeat toggle,
notes shortcut, and previous/play-pause/next transport — sits below the verse stack. This is the
core moment-to-moment experience of the app and the highest-value part of the redesign.

**Why this priority**: This is the screen a student spends the most time in, and it is the
biggest structural change (not just a re-skin) in this phase — it needs to be right before
anything else in this phase matters.

**Independent Test**: Open a matn with at least 3 verses and start playback; verify exactly the
previous, active, and next verse are visible at once, the active verse is visually distinguished,
and the floating control bar exposes scrub, speed, repeat, notes, and prev/play-pause/next
without leaving the screen.

**Acceptance Scenarios**:

1. **Given** a matn is open with playback on verse N (where N is not the first or last verse),
   **When** the reading screen renders, **Then** verse N−1 appears muted above, verse N appears
   highlighted and centered, and verse N+1 appears muted below.
2. **Given** the active verse finishes playing and gapless playback advances to the next verse,
   **When** the transition completes, **Then** the three-verse stack shifts so the new current
   verse becomes the highlighted one, matching existing gapless-playback behavior from
   specs/003-core-audio-playback.
3. **Given** the reading screen is visible, **When** the student taps play/pause, skip next, or
   skip previous on the floating control bar, **Then** playback responds exactly as it does today
   (no regression in specs/003-core-audio-playback behavior).
4. **Given** the active verse is the first verse of the matn, **When** the reading screen renders,
   **Then** there is no previous-verse slot shown (or it renders empty/absent rather than
   erroring); the same applies to the next-verse slot on the last verse.

---

### User Story 2 - Configure repetition from one consolidated screen (Priority: P2)

A student wants to drill a range of verses. Today this requires finding a stepper panel for
verse/matn repeat counts and separately long-pressing individual verses to set an A–B loop range,
with no single place that shows the whole configuration or an explicit way to start. In the
redesign, a single bottom sheet holds all of it: an A–B toggle, start/end verse selectors, a
single-verse repeat counter, a whole-segment repeat counter, an infinite-repeat option, and one
button that starts the configured session.

**Why this priority**: Repetition is the app's core memorization mechanic (specs/004); this
phase doesn't change what it can do, only how discoverable and coherent configuring it is — a
real usability fix, but one step behind the reading screen itself.

**Independent Test**: From the reading screen, open repetition setup, toggle A–B mode on, pick a
start and end verse, set a single-verse repeat count and a segment repeat count, then tap the
start action, and verify playback begins honoring exactly that configuration — the same
underlying behavior specs/004-repetition-engine already tests, just reached through the new UI.

**Acceptance Scenarios**:

1. **Given** the repetition setup sheet is open, **When** the student toggles A–B mode and picks
   a start and end verse, **Then** the selected range is reflected before playback starts.
2. **Given** the student sets a single-verse repeat count and a segment repeat count (including
   the infinite option), **When** they tap the start action, **Then** playback begins and follows
   the existing $V_r$/$M_r$ semantics from specs/004-repetition-engine unchanged.
3. **Given** the repetition setup sheet is open with no changes made, **When** the student
   dismisses it, **Then** no repetition session starts and any prior configuration is unaffected.

---

### User Story 3 - Navigate the app through a persistent bottom nav (Priority: P3)

A student sees a persistent bottom navigation bar with four tabs — Library, Goals, Notes,
Settings — on every top-level screen. Tapping Library shows the existing Home screen. Since
Goals and Notes are memorization-progress and bookmark features not yet built, tapping those
tabs shows a simple "coming soon" placeholder rather than being hidden or disabled, so the app's
overall shape is visible even before every tab is functional.

**Why this priority**: This is new chrome, not a restyle of something that already exists, and
every other screen in this phase sits underneath it — but the app is usable without it (deep
links / existing navigation still work), so it ranks behind the two screens above.

**Independent Test**: From the Home screen, verify the bottom nav shows all four tabs; tap each
non-Library tab and verify it lands on a placeholder screen communicating the feature isn't
available yet, without crashing or losing any in-progress reading/playback state on Library.

**Acceptance Scenarios**:

1. **Given** any top-level screen, **When** it renders, **Then** the bottom nav shows Library,
   Goals, Notes, and Settings, with the current tab visually indicated.
2. **Given** the bottom nav, **When** the student taps Goals or Notes, **Then** a placeholder
   screen appears explaining the feature is coming soon, with a way back to Library.
3. **Given** a student has an in-progress reading/playback session on Library, **When** they tap
   away to another tab and back to Library, **Then** the session state is preserved exactly as
   specs/005-continue-learning already guarantees.

---

### User Story 4 - See the redesigned Home and Matn Details screens (Priority: P4)

A student opens the app and sees the Home screen re-skinned to the canonical design: a top bar,
a daily-goal progress indicator, the existing Continue Learning card, and the matn grid, all
using the new color palette, typography, and spacing. Opening a matn shows its Details screen
re-skinned the same way, with its existing header, table of contents, and verse list unchanged in
structure.

**Why this priority**: Purely visual (no new interaction surface), and the least risky part of
this phase — it ranks last because nothing else depends on it, unlike the screens above.

**Independent Test**: Open the Home screen and a matn's Details screen and verify all colors,
fonts, and spacing are drawn from the shared token files (not literals), and that every element
present before this phase (Continue Learning card, matn grid, header, table of contents, verse
list) is still present and functions identically.

**Acceptance Scenarios**:

1. **Given** the Home screen, **When** it renders, **Then** a top bar and a daily-goal progress
   indicator are present in addition to the pre-existing Continue Learning card and matn grid.
2. **Given** the Matn Details screen, **When** it renders, **Then** its existing header, table of
   contents, and verse list (including long-press loop-boundary controls) are all still present
   and functionally unchanged, using the new visual tokens.
3. **Given** the daily-goal progress indicator, **When** no real daily-goal data exists yet
   (Phase 7 not built), **Then** it renders a clearly placeholder/static state rather than
   fabricating a misleading number.

---

### Edge Cases

- What does the reading screen show for the first verse (no previous) or last verse (no next) of
  a matn, or for a matn with only one or two verses total?
- What happens if a student opens repetition setup, sets an A–B range, then backs out without
  starting — does any partial configuration leak into the next session?
- What happens when a student taps a stubbed Goals/Notes/Settings tab mid-playback — does audio
  keep playing in the background per specs/003's background-playback guarantee?
- What happens when Arabic verse text is long enough that it would overflow the active-verse card
  in the carousel — does it scale down, scroll, or wrap without clipping any diacritics?
- What happens to the bottom nav and floating control bar on small-height devices where both
  would compete for vertical space?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a single shared set of design tokens (color roles,
  typography, spacing, corner radii) that every screen touched by this phase reads from, with no
  screen defining its own color, spacing, or type-size values.
- **FR-002**: The reading/playback screen MUST show at most three verses at once — the previous,
  active, and next verse relative to current playback position — with the active verse visually
  distinguished from the other two.
- **FR-003**: The reading/playback screen MUST expose scrub position, playback speed, a repeat
  entry point, a notes entry point, and previous/play-pause/next transport controls, without
  changing the underlying playback behavior guaranteed by specs/003-core-audio-playback.
- **FR-004**: The system MUST provide one consolidated repetition-setup surface exposing: an A–B
  loop on/off toggle, start and end verse selection, a single-verse repeat count, a whole-segment
  repeat count, an infinite-repeat option, and one explicit action that starts the configured
  session.
- **FR-005**: Repetition setup MUST configure the same underlying repetition model
  (specs/004-repetition-engine's $V_r$/$M_r$/A–B loop semantics) as today — this phase changes
  only how that configuration is presented and reached, not its behavior.
- **FR-006**: The system MUST provide a persistent bottom navigation surface with four
  destinations — Library, Goals, Notes, Settings — visible on every top-level screen.
- **FR-007**: Tapping a not-yet-implemented navigation destination (Goals, Notes; Settings unless
  a minimal settings screen already exists) MUST show a placeholder communicating the feature is
  not yet available, rather than erroring, crashing, or silently doing nothing.
- **FR-008**: Navigating between bottom-nav destinations MUST NOT interrupt in-progress audio
  playback or discard in-progress reading/repetition/continue-learning state.
- **FR-009**: The Home screen MUST retain its existing Continue Learning card and matn grid, and
  MUST additionally present a top bar and a daily-goal progress indicator.
- **FR-010**: Where real data for a redesigned element does not exist yet (e.g. the daily-goal
  progress indicator ahead of specs/007), the system MUST show a clearly-placeholder state rather
  than a fabricated or misleading value.
- **FR-011**: The Matn Details screen MUST retain its existing header, table-of-contents, verse
  list, and loop-boundary long-press controls, re-rendered using the shared design tokens.
- **FR-012**: All existing automated tests for Phases 1–5 MUST continue to pass unmodified in
  their assertions of underlying behavior after this phase's UI changes (tests may be updated for
  new composable structure/selectors, not for changed business logic).
- **FR-013**: Dark-mode theming is explicitly out of scope for this phase; only light-theme
  tokens are defined (dark mode remains owned by Phase 9 per `docs/ROADMAP.md`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A student can identify, at a glance, which single verse is currently active while
  reading/listening, on every matn with 3 or more verses, without any ambiguity between the
  active verse and its neighbors.
- **SC-002**: A student can fully configure and start a repetition session (range, per-verse
  count, per-segment count) without leaving a single screen or backing out to set the range
  separately from the counts.
- **SC-003**: From any top-level screen, a student can reach any of the four main app areas
  (Library, Goals, Notes, Settings) in exactly one tap, and always understands whether the area
  they landed on is available now or coming later.
- **SC-004**: Every screen touched by this phase visually matches its canonical design reference
  in `docs/DESIGN-SOURCE.md` (color palette, typography, spacing) as confirmed by design review
  against the fetched Stitch screenshots.
- **SC-005**: 100% of Phase 1–5 automated tests that assert playback, repetition, or
  continue-learning *behavior* (as opposed to now-superseded UI structure) pass unchanged after
  this phase.

## Assumptions

- Dark mode is out of scope; the Stitch design set is light-theme only, so no dark token values
  exist yet to adopt (tracked separately under Phase 9).
- The daily-goal progress indicator on Home displays a placeholder/static state until
  specs/007 (Progress & Daily Goals) implements real tracking.
- The Goals, Notes, and (if no minimal screen exists) Settings bottom-nav tabs route to one
  shared "coming soon" placeholder screen until their owning phases (6, 6, 8 respectively) land.
- The canonical screen picks from `docs/DESIGN-SOURCE.md`'s resolved duplicates are: "Matn
  Details (Refined)" and "Reading & Playback (Updated)".
- This phase reuses the existing domain/data layers unchanged (specs/001–005) — no new entities,
  no schema changes, no repository interface changes. It is presentation-layer only.
- "Settings" as a destination is out of scope beyond routing to the shared placeholder — no new
  settings functionality is introduced by this phase.
