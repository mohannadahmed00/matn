# Feature Specification: Phase 1 — Reading Experience (static)

**Feature Branch**: `002-reading-experience`

**Created**: 2026-07-19

**Status**: Draft

**Input**: User description: "read @docs/ROADMAP.md and create specification for Phase 1 — Reading Experience (static)"

## Overview

Phase 1 puts the first screens in front of a student. Building directly on the Phase 0
content foundation (متن / chapter / verse domain model and its offline local store), it
delivers the **static reading experience**: a Home library of available متون, a Matn
Details screen with its header and full verse list, table-of-contents navigation for
structured متون, a fully right-to-left layout, and elegant, legible Arabic typography with
faithful diacritics (تَشْكِيل).

"Static" is the defining boundary of this phase: **there is no audio and no playback**. The
goal is to validate the core reading UI/UX — can a student find a matn, open it, and read
it comfortably, in the correct order, in beautiful Arabic, right-to-left — *before* the
playback, repetition, and progress machinery is layered on top in later phases. Value is
delivered when the متون that exist in the local store from Phase 0 can be browsed and read
end to end, entirely offline.

## Clarifications

### Session 2026-07-19

- Q: What language should the interface chrome (labels, empty-state message, buttons, table-of-contents header) use? → A: Follow the device locale — provide Arabic and English strings, falling back to Arabic when the locale is neither.
- Q: What is the supported range of the Arabic reading font-size control? → A: A small set of 4–5 discrete named steps (Small / Medium / Large / X-Large), defaulting to Medium.
- Q: Is the chosen reading font size a single global preference or remembered per matn? → A: A single global preference applied to every matn.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read a matn from start to finish (Priority: P1)

A student opens a matn and reads it: a header describing the text (cover, title, author,
description, totals), followed by the complete list of verses in their correct order, each
verse showing its number and its Arabic text with full diacritics. The whole screen is
right-to-left and the Arabic is rendered in an elegant, legible classical typeface. This is
the heart of the app in static form — the reading surface everything else is built around.

**Why this priority**: Reading the text faithfully is the entire point of the phase (and the
product). A single matn that can be opened and read cleanly — correct order, intact
diacritics, native RTL, comfortable typography — is the smallest slice that proves the
reading experience works. Library browsing and chapter navigation are ways *into* this
screen; without the reading screen itself there is nothing to reach.

**Independent Test**: Open a matn present in the local store and verify the header shows its
title, author, description, total verse count, and total estimated duration, and that every
verse is displayed in correct matn-global order with its Arabic text and every diacritic
intact, laid out right-to-left, with no network connection.

**Acceptance Scenarios**:

1. **Given** a matn in the local store, **When** its details screen is opened, **Then** the
   header shows the cover image (or a placeholder when none exists), title, author,
   description, total verse count, and total estimated duration.
2. **Given** an opened matn, **When** its verses are displayed, **Then** every verse appears
   in the correct matn-global reading order, each showing its display number and its Arabic
   text.
3. **Given** verses containing diacritics, **When** they are rendered, **Then** the Arabic
   text — including every diacritic — matches the stored content exactly, with no truncation,
   clipping, or missing marks.
4. **Given** the details screen, **When** it is displayed, **Then** the entire layout —
   text alignment, reading direction, and element placement — is right-to-left.
5. **Given** the device has no network connectivity, **When** a matn is opened and read,
   **Then** all content renders successfully from the local store.

---

### User Story 2 - Browse the library and choose a matn (Priority: P2)

From the Home screen a student sees the collection of available متون presented as a grid of
cards, each showing the matn's cover image, title, author, verse count, and estimated
listening duration. Tapping a card opens that matn's reading screen. This is the front door
of the app and how a student discovers and selects what to study.

**Why this priority**: The library is the natural entry point and makes the app feel whole,
but the reading screen (P1) is the core value it leads to. A student could reach a matn
directly for testing, so the library is the next slice rather than the first.

**Independent Test**: With one or more متون in the local store, open the Home screen and
verify each matn appears as a card with cover (or placeholder), title, author, verse count,
and estimated duration; tap a card and confirm it opens that matn's reading screen.

**Acceptance Scenarios**:

1. **Given** one or more متون in the local store, **When** the Home screen is shown, **Then**
   each matn appears as a card displaying its cover image (or placeholder), title, author,
   verse count, and estimated duration.
2. **Given** the Home screen, **When** a matn card is tapped, **Then** that matn's reading
   screen opens.
3. **Given** an empty local store, **When** the Home screen is shown, **Then** a clear empty
   state is displayed rather than a blank screen or an error.
4. **Given** the Home screen, **When** it is displayed, **Then** its layout is right-to-left.

---

### User Story 3 - Navigate a structured matn by its table of contents (Priority: P3)

For a structured matn (verses grouped into chapters/فصول), the reading screen offers a table
of contents listing the chapters in order. Selecting a chapter jumps the reader straight to
that chapter's first verse, so a student is not forced to scroll a long flat list to reach a
section. Simple (flat) متون show no table of contents and read as a single ordered list.

**Why this priority**: Structured متون need chapter navigation to be usable, and it is named
explicitly for this phase — but a matn can already be read top to bottom without it (P1), so
it enhances rather than blocks the core reading experience.

**Independent Test**: Open a structured matn, verify its chapters are listed in order in a
table of contents, select a chapter and confirm the view moves to that chapter's first
verse; then open a simple matn and confirm no table of contents is shown and its verses read
as one continuous ordered list.

**Acceptance Scenarios**:

1. **Given** a structured matn, **When** its reading screen is opened, **Then** a table of
   contents lists all chapters in their correct order.
2. **Given** the table of contents of a structured matn, **When** a chapter is selected,
   **Then** the view moves so that chapter's first verse is brought into view.
3. **Given** a simple matn, **When** its reading screen is opened, **Then** no table of
   contents is shown and all verses are presented as a single ordered list.

---

### User Story 4 - Adjust Arabic reading font size (Priority: P4)

A student can make the Arabic verse text larger or smaller across a comfortable, legible
range, and the change applies immediately to the text on screen. The chosen size is
remembered so the reading experience stays consistent the next time the app is opened.

**Why this priority**: Adjustable Arabic typography is part of the reading experience this
phase validates, and readability matters a great deal for متون — but a sensible default size
already makes reading fully possible, so size adjustment is a refinement layered on top of
P1.

**Independent Test**: On a reading screen, change the font size to its largest and smallest
settings and confirm the Arabic text resizes live and stays legible with no clipping or
overlapping layout; reopen the matn and confirm the chosen size is retained.

**Acceptance Scenarios**:

1. **Given** a reading screen, **When** the font size control is adjusted, **Then** the
   Arabic verse text resizes immediately to reflect the new setting.
2. **Given** the font size set to its minimum or maximum, **When** verses are displayed,
   **Then** the text remains fully legible and the layout does not clip, overlap, or break.
3. **Given** a chosen font size, **When** the app is closed and a matn is reopened, **Then**
   the previously chosen size is applied.

---

### Edge Cases

- **Empty library**: With no متون in the local store, the Home screen shows a clear empty
  state, not a blank screen or crash.
- **Missing cover image**: A matn (on its card and in its header) without a cover image shows
  a consistent placeholder rather than a broken/empty image slot.
- **Very long matn**: A matn of up to ~500 verses opens and scrolls smoothly from top to
  bottom without noticeable stutter or long blank waits.
- **Long / diacritic-heavy verse**: A very long or heavily diacriticized verse wraps and
  displays in full, right-to-left, without truncation, clipping, or lost marks.
- **Simple matn, no chapters**: Opening a simple matn shows no table of contents; chapter
  navigation is simply absent, not an empty/broken control.
- **Structured matn with a single chapter**: The table of contents still lists that one
  chapter and behaves correctly.
- **Mixed Arabic/Latin or numeric content in a verse**: Text renders correctly within the
  right-to-left layout without misordering.
- **Font-size extremes**: At the smallest and largest sizes, verse rows remain readable and
  the list layout stays intact.

## Requirements *(mandatory)*

### Functional Requirements

**Home screen — library of متون**

- **FR-001**: The system MUST present, on a Home screen, the collection of متون available in
  the local store as a browsable grid of cards.
- **FR-002**: Each matn card MUST display the matn's cover image (or a consistent placeholder
  when none is set), title, author, total verse count, and estimated total listening
  duration.
- **FR-003**: Selecting a matn card MUST navigate to that matn's Matn Details / reading
  screen.
- **FR-004**: When the local store contains no متون, the Home screen MUST show a clear empty
  state rather than a blank screen or an error.

**Matn Details — header**

- **FR-005**: The Matn Details screen MUST show a header containing the matn's cover image (or
  placeholder), title, author, description, total verse count, and total estimated duration.

**Verse list — reading**

- **FR-006**: The system MUST display a matn's verses in their correct matn-global display
  order (as defined by the Phase 0 data model), independent of internal storage order.
- **FR-007**: Each verse row MUST show the verse's display number, its Arabic text, and its
  individual duration.
- **FR-008**: Arabic verse text MUST be preserved and rendered exactly as stored, including
  all diacritics (تَشْكِيل), with no truncation, clipping, or normalization loss.
- **FR-009**: Arabic text MUST be rendered in an elegant, legible classical Arabic typeface
  appropriate for reading متون.
- **FR-010**: All Phase 1 screens (Home, Matn Details, verse list, table of contents) MUST
  use a fully native right-to-left layout — reading direction, text alignment, and element
  placement all right-to-left.
- **FR-011**: The verse list MUST scroll smoothly for large متون (up to at least 500 verses)
  without noticeable stutter or excessive load delay.
- **FR-012**: Long and heavily-diacriticized verse text MUST wrap and display in full within
  the right-to-left layout without truncation or clipping.

**Table of contents — structured متون**

- **FR-013**: For a structured matn, the system MUST display a table of contents listing its
  chapters in their correct order.
- **FR-014**: Selecting a chapter in the table of contents MUST move the reading view so that
  chapter's first verse is brought into view.
- **FR-015**: For a simple matn, no table of contents MUST be shown; its verses MUST be
  presented as a single continuous ordered list.

**Reading typography control**

- **FR-016**: The system MUST provide a control to adjust the Arabic reading font size across
  a small set of 4–5 discrete named steps (e.g., Small / Medium / Large / X-Large), defaulting
  to Medium, with each step applied live to the displayed verse text.
- **FR-017**: The chosen reading font size MUST persist locally as a single global display
  preference — applied to every matn, not remembered per matn — so it is retained across app
  sessions.

**Localization & interface language**

- **FR-021**: All interface chrome (screen labels, empty-state message, buttons, table-of-contents
  header, and similar app-provided text — as distinct from the stored Arabic verse content) MUST
  follow the device locale, providing both Arabic and English strings and falling back to Arabic
  when the device locale is neither.

**Data source & offline**

- **FR-018**: All Phase 1 screens MUST render entirely from the local store established in
  Phase 0, functioning fully with no network connection.
- **FR-019**: The reading experience MUST consume content through the Phase 0 domain /
  repository layer and MUST NOT duplicate, bypass, or redefine the established content model.

**Scope boundaries**

- **FR-020**: This phase MUST NOT include audio playback or playback controls, active-verse
  highlighting or auto-scroll, repetition logic, search, bookmarks, notes, progress tracking
  or "mark as memorized", daily goals, "Continue Learning" resume state, downloads or storage
  management, dark mode, or tablet-adaptive layouts — each belongs to a later phase and is out
  of scope here.

### Key Entities *(include if feature involves data)*

Phase 1 introduces **no new persisted content entities**; it reads the entities defined and
persisted in Phase 0. The reading experience is a presentation of:

- **Matn (متن)**: Read for the library card and details header — title, author, description,
  cover image reference, structure kind (simple or structured), and derived totals (verse
  count, estimated total duration).
- **Chapter / Section (فصل)**: Read to build the table of contents for structured متون —
  ordered list of chapter titles and their reading order.
- **Verse (بيت / آية)**: Read to render the verse list — matn-global display number, Arabic
  text with diacritics, and individual duration.

The only Phase 1-owned persisted state is a lightweight **reading display preference** (the
chosen Arabic font-size step — one of 4–5 discrete named steps, default Medium), stored once as
a single global UI setting applied to every matn, independent of any content or learning state.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A student can go from the Home screen to reading a matn's first verses in **no
  more than 2 taps**.
- **SC-002**: **100%** of a matn's verses are displayed in the correct reading order with
  their Arabic text and every diacritic identical to the stored content (zero misordered
  verses, zero altered or missing marks).
- **SC-003**: Opening a matn of up to **500 verses** shows its first verses within **1
  second**, and the list scrolls end to end smoothly without visible stutter on a typical
  current mid-range device.
- **SC-004**: For a structured matn, **100%** of its chapters are reachable from the table of
  contents, and selecting any chapter brings its first verse into view within **1 second**.
- **SC-005**: Every Phase 1 screen (Home, details, verse list, table of contents) presents a
  fully right-to-left layout, with **zero** left-to-right layout regressions.
- **SC-006**: All Phase 1 screens render successfully with the device fully offline (airplane
  mode), making **zero** network requests.
- **SC-007**: The Arabic reading font size can be adjusted across its 4–5 discrete named steps
  with the text remaining legible and the layout intact (no clipping or overlap) at both the
  smallest (Small) and largest (X-Large) steps, and the chosen size — a single global preference
  applied to every matn — is retained after restarting the app.
- **SC-008**: Empty-library and missing-cover-image cases each show a graceful state (empty
  message / placeholder) in **100%** of occurrences, with no blank screens or crashes.

## Assumptions

- **Content is already loaded**: Phase 1 assumes the local store already contains at least one
  simple and, ideally, one structured matn seeded in Phase 0. Phase 1 does not author,
  import, or validate content — it presents what Phase 0 provides.
- **No audio in this phase**: Consistent with the roadmap, no verse "play" control, playback
  bar, highlighting, or auto-scroll is included. Individual verse duration is shown as
  read-only metadata (it already exists from Phase 0), not as a playback affordance.
- **No progress bar in the header yet**: The product's Matn Details header ultimately includes
  a progress bar, but progress is a recall-based metric owned by Phase 6. Phase 1 omits the
  progress bar because no progress data source exists yet; the header layout leaves room for
  it later.
- **Default theme and phone layout**: Phase 1 targets the default (light) theme and phone
  layout. Dark mode and tablet-adaptive layouts are explicitly Phase 8 and are out of scope
  here; screens should nonetheless not crash on rotation or on differing screen sizes.
- **Font size is a display preference, not learning state**: Persisting the chosen Arabic font
  size is a lightweight UI setting and is distinct from the "Continue Learning" cached state
  introduced in Phase 4.
- **Estimated duration is derived**: A matn's total estimated duration and verse count are
  computed from the Phase 0 content (summed verse durations / counted verses), not stored as
  authoritative fields.
- **Shared-first presentation**: The reading UI and its view-model/state logic are shared
  across both target mobile platforms from a single source, with only genuinely
  platform-specific rendering at the edges, consistent with the project's multiplatform
  approach.

## Dependencies

- **Phase 0 — Foundation & Data Model** MUST be complete: Phase 1 reads متن / chapter / verse
  content and their derived totals through the Phase 0 domain and repository layer and the
  local store it established.
- Phase 1 is a hard prerequisite for **Phase 2 — Core Audio Playback**, which layers audio,
  active-verse highlighting, and auto-scroll onto this reading surface.
