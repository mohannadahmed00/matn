# Feature Specification: Phase 0 — Foundation & Data Model

**Feature Branch**: `001-foundation-data-model`

**Created**: 2026-07-18

**Status**: Draft

**Input**: User description: "read @docs/ROADMAP.md and create specification for the Phase 0 — Foundation & Data Model"

## Overview

Phase 0 establishes the content foundation that every later phase builds on: the domain
model for a matn (متن) and its verses, a local store that persists and reads that content
fully offline, and the locked-in per-verse audio asset model. This phase delivers **no
end-user screens**. Its "consumers" are the later feature phases (reading UI, playback,
repetition, search, progress) and the content-prep workflow that loads a teacher's متون
into the app. Value is delivered when a matn — simple or structured — can be loaded once
and read back completely, in the correct order, with each verse resolving to exactly one
dedicated audio file, and with identifiers stable enough to support the future
online/sync roadmap.

## Clarifications

### Session 2026-07-18

- Q: For structured متون, is verse position a single matn-global number or a composite (chapter order + within-chapter position)? → A: Matn-global sequential number on every verse; chapter grouping is an overlay derived from each verse's chapter link.
- Q: When a content-integrity problem (duplicate display order, missing audio reference) is detected at load, is the load atomic-reject, partial+flag, or store-then-validate? → A: Atomic reject — the entire matn load aborts, persists nothing for that matn, and reports the specific problem(s).
- Q: Where do entity UUIDs originate — content-authored, device-generated, or hybrid? → A: Content-authored — the seed content carries pre-assigned UUIDs that the store trusts and persists; reload dedup is keyed on them, keeping identities globally stable across devices.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Persist and read back a simple matn (Priority: P1)

Content for a **simple** matn — a flat, ordered list of verses — can be loaded into the
local store once and then read back in full: the matn's descriptive information plus every
verse in its correct sequential order, each verse retaining its Arabic text with full
diacritics (تَشْكِيل). This is the minimum viable foundation: without it, no reading,
playback, or progress phase has any data to operate on.

**Why this priority**: It is the irreducible core of the entire app. A single simple matn
that can be stored and faithfully retrieved is the smallest slice that proves the domain
model, the persistence layer, and stable ordering all work together. Every subsequent phase
depends on it.

**Independent Test**: Load a known simple matn (e.g., a matn of N verses) into the store,
then query it back. Verify the matn's metadata and that all N verses are returned in the
exact intended display order with their Arabic text (including diacritics) unchanged, using
no network connection.

**Acceptance Scenarios**:

1. **Given** an empty store, **When** a simple matn with its ordered verses is loaded,
   **Then** the matn can be retrieved by its identifier and reports the correct title,
   author, description, and total verse count.
2. **Given** a loaded simple matn, **When** its verses are queried, **Then** they are
   returned in a stable, correct display order that does not depend on internal row order or
   identifier values.
3. **Given** a verse containing diacritics, **When** it is stored and read back, **Then**
   the Arabic text — including every diacritic — is identical to what was loaded.
4. **Given** a loaded matn, **When** the device has no network connectivity, **Then** all
   read operations still succeed.

---

### User Story 2 - Persist and read back a structured matn with chapters (Priority: P2)

Content for a **structured** matn — verses organized hierarchically into chapters/sections
(فصول) — can be loaded and read back with its hierarchy intact: chapters in their correct
order, and each chapter's verses grouped under it in their correct order. This is what makes
table-of-contents navigation possible in the later reading phase.

**Why this priority**: Many متون are structured, and the spec requires table-of-contents
navigation for them. The hierarchy must exist in the data model from day one, but a simple
matn (P1) alone is already a demonstrable foundation, so structure is the next slice rather
than the first.

**Independent Test**: Load a known structured matn (multiple chapters, each with several
verses) into the store, then query it back. Verify chapters return in order, each chapter's
verses return grouped and ordered correctly, and the whole matn's verse sequence is
coherent end to end.

**Acceptance Scenarios**:

1. **Given** an empty store, **When** a structured matn with chapters and their verses is
   loaded, **Then** its chapters can be retrieved in their intended order.
2. **Given** a loaded structured matn, **When** the verses of a specific chapter are
   queried, **Then** only that chapter's verses are returned, in their correct order.
3. **Given** a structured matn, **When** its full verse sequence is read, **Then** the
   overall verse order across chapters is coherent and matches the source content.
4. **Given** the same store, **When** both a simple matn and a structured matn are loaded,
   **Then** each is retrievable independently and correctly without interfering with the
   other.

---

### User Story 3 - Resolve each verse to its dedicated audio asset (Priority: P3)

Every verse resolves to exactly one dedicated audio asset reference (the per-verse
micro-file model, e.g. `matn_01_verse_005.mp3`). The reference is stored and retrievable
alongside the verse, so the later playback phase can locate the correct file for any verse.
The model is shaped so that an alternate reciter's audio can later map to the same verse
identifiers without schema changes.

**Why this priority**: The per-verse audio asset model is explicitly locked in this phase,
but Phase 0 only needs to *model and resolve* the reference — actual playback is Phase 2.
It is therefore valuable but can follow the two content-structure slices.

**Independent Test**: For a loaded matn, request the audio asset reference of any given
verse and confirm exactly one reference is returned, and that it is the one associated with
that verse.

**Acceptance Scenarios**:

1. **Given** a loaded matn, **When** the audio asset for a specific verse is requested,
   **Then** exactly one audio asset reference is returned for that verse.
2. **Given** a verse, **When** its audio asset is inspected, **Then** the reference is
   distinct from every other verse's reference (one file per verse, no shared/continuous
   file).
3. **Given** the audio asset model, **When** a future alternate reciter is considered,
   **Then** the model can associate a second audio reference with the same verse identifier
   without redefining the verse.

---

### Edge Cases

- **Empty store**: Querying for a matn that has not been loaded returns a clear "not found"
  result rather than an error or a partially-populated object.
- **Simple matn queried for chapters**: A simple (flat) matn has no chapters; a
  chapter query for it returns an empty result, not an error.
- **Verse ordering collisions**: If two verses were assigned the same matn-global display
  number during content prep, the entire matn load MUST be rejected (atomically, persisting
  nothing for that matn) and the collision reported, rather than silently producing an
  ambiguous sequence.
- **Missing audio reference**: A verse loaded without its audio asset reference MUST cause the
  entire matn load to be rejected atomically (persisting nothing for that matn) and the gap
  reported, rather than being silently stored as playable.
- **Diacritic-heavy / long text**: Very long verses and heavily diacriticized text round-trip
  without truncation or normalization loss.
- **Reload / re-seed**: Loading the same matn again does not create duplicate matn, chapter,
  or verse records; identifiers remain stable across reloads.

## Requirements *(mandatory)*

### Functional Requirements

**Domain model & identity**

- **FR-001**: The system MUST define a domain model with three core content entities — Matn,
  Chapter/Section, and Verse — usable independently of any user interface.
- **FR-002**: Every persisted entity (Matn, Chapter/Section, Verse, and each audio asset
  mapping) MUST carry a stable universally-unique identity that is independent of display
  order, insertion order, or any local row identifier. These UUIDs MUST originate in the
  content source (assigned during content prep); the store persists the authored UUIDs as-is
  rather than generating its own, so identities are globally consistent across devices and
  future sync.
- **FR-003**: Each Verse MUST additionally carry a **matn-global** sequential display number
  (1..N across the entire matn) that is stable and defines its reading order within its matn,
  separate from its unique identity. For structured متون, chapter grouping is an overlay
  derived from each verse's owning-chapter link; the global number alone determines the full
  reading sequence, and a verse's position within its chapter follows from that global order.

**Content persistence & retrieval (offline)**

- **FR-004**: The system MUST persist matn content to local device storage and retrieve it
  without requiring any network connection.
- **FR-005**: The system MUST allow content to be loaded (seeded) into the local store and
  read back so that a loaded matn is fully retrievable by its identity.
- **FR-006**: The system MUST return a matn's verses in their correct, stable display order,
  independent of internal storage order.
- **FR-007**: The system MUST preserve Arabic verse text exactly, including all diacritics
  (تَشْكِيل), with no loss or normalization on a store-and-read-back round trip.
- **FR-008**: The system MUST support observing/reading content so that later phases can
  react to the loaded content set (e.g., list available متون, read one matn's verses).
- **FR-009**: Re-loading the same matn MUST NOT create duplicate matn, chapter, or verse
  records, and MUST preserve existing stable identities. Sameness is determined by the
  content-authored UUID (FR-002): a reload with a matching UUID updates/upserts in place
  rather than inserting a second record.

**Structure (simple & structured متون)**

- **FR-010**: The system MUST support both simple متون (a flat ordered list of verses) and
  structured متون (verses grouped into ordered chapters/sections).
- **FR-011**: For a structured matn, the system MUST return its chapters in their correct
  order and MUST return the verses belonging to a given chapter, grouped and ordered by the
  matn-global verse display number (FR-003). A chapter's verse order is a contiguous slice of
  that global sequence.
- **FR-012**: For a simple matn, a chapter query MUST return an empty result rather than an
  error, and its verses MUST still be retrievable as a single ordered sequence.

**Audio asset model (locked)**

- **FR-013**: The system MUST associate each verse with exactly one dedicated audio asset
  reference under the per-verse micro-file model (file boundary = verse boundary); a
  shared/continuous audio file model MUST NOT be assumed anywhere in the data model.
- **FR-014**: The system MUST allow the audio asset reference for any given verse to be
  resolved from that verse's identity.
- **FR-015**: The audio asset model MUST be structured so that alternate reciter audio can be
  mapped to the same verse identities in the future without redefining verses or breaking
  existing references.

**Future-proofing & scope boundaries**

- **FR-016**: The data model and identifiers MUST NOT contain assumptions that would block
  the later roadmap: remote accounts, cloud backup, cross-device sync, online catalog with
  selective download, multi-reciter audio, and shared community content.
- **FR-017**: The local store MUST be the single source of truth for content in this phase;
  the app MUST function fully with no network available.
- **FR-018**: This phase MUST NOT include any user-facing screens, audio playback, repetition
  logic, search, bookmarks, notes, progress tracking, or resume/"Continue Learning" state —
  these belong to later phases and are out of scope here.
- **FR-019**: The system MUST surface content-integrity problems detectable at load time —
  duplicate verse display numbers and verses missing an audio asset reference — by rejecting
  the entire matn load atomically (persisting nothing for that matn) and reporting the
  specific problem(s), rather than storing ambiguous or incomplete content silently.

### Key Entities *(include if feature involves data)*

- **Matn (متن)**: A single memorizable text. Attributes: stable unique identity, title,
  author, description, cover image reference, structure kind (simple or structured), and
  derivable totals (verse count, estimated total duration). It is the top-level container for
  chapters and verses. (Download status and file size are future concerns and MUST NOT be
  precluded, but are out of scope for this phase.)
- **Chapter / Section (فصل)**: An ordered grouping of verses within a structured matn.
  Attributes: stable unique identity, owning matn, title, and display order. Absent for
  simple متون.
- **Verse (بيت / آية)**: A single unit of text. Attributes: stable unique identity, owning
  matn, optional owning chapter (absent for simple متون), a matn-global sequential display
  number (unique within its matn), Arabic text with diacritics, an individual audio duration,
  and a link to its audio asset. It is the atomic unit of ordering and, later, of playback and
  progress. The matn-global number is the single source of truth for reading order; chapter
  membership groups verses but does not carry a separate within-chapter numbering.
- **Audio Asset**: The mapping between a verse and its dedicated per-verse audio file.
  Attributes: the verse identity it belongs to, a reciter identity (a single default reciter
  in this phase, extensible to alternates later), a file reference, and duration. Exactly one
  asset per verse per reciter.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A loaded simple matn of any size can be read back with **100%** of its verses
  present and in the correct display order.
- **SC-002**: A loaded structured matn returns **100%** of its chapters in order and each
  chapter's verses correctly grouped and ordered, with no verse mis-assigned to the wrong
  chapter.
- **SC-003**: Verse Arabic text, including every diacritic, is **identical** before and after
  a store-and-read-back round trip (zero character loss or alteration).
- **SC-004**: Every verse in a loaded matn resolves to **exactly one** audio asset reference,
  and no two distinct verses share the same reference.
- **SC-005**: All content read operations succeed with the device fully offline (airplane
  mode), with **zero** network requests made.
- **SC-006**: Reading the full verse list of a matn of up to **500 verses** returns in under
  **1 second** on a typical current mid-range device.
- **SC-007**: Loading the same matn twice results in **no duplicate** matn, chapter, or verse
  records and identical stable identities across both loads.
- **SC-008**: Loading content that contains a duplicate verse display number, or a verse
  missing its audio asset reference, is **detected, reported, and rejected atomically** at
  load time in 100% of such cases — leaving **zero** records persisted for that matn — rather
  than stored silently or partially.

## Assumptions

- **Content availability**: At least one real or representative sample matn (one simple and,
  ideally, one structured) is loaded/seeded during this phase so the store and repositories
  are demonstrable and testable end to end. Bulk authoring of the full content library is a
  content-prep activity outside this phase.
- **Audio in this phase is reference-only**: Phase 0 models and resolves the per-verse audio
  *reference* (and its duration metadata); it does not decode, buffer, or play audio — that
  is Phase 2. The presence of the actual audio binary on device is validated as a reference,
  not by playback.
- **Content-authored identities**: Seed content arrives with pre-assigned UUIDs for every
  matn, chapter, verse, and audio-asset mapping. Content prep (outside this phase) is
  responsible for assigning and keeping those UUIDs stable across re-exports; the store treats
  them as authoritative and does not mint its own.
- **Single reciter for v1**: Exactly one reciter (the teacher) is represented now; the audio
  asset model reserves room for alternate reciters later without requiring them yet.
- **No UI**: This phase produces the schema, domain entities, and content read/write
  capability only. It is validated through automated tests, not through screens.
- **Later-phase entities are out of scope but not precluded**: Bookmarks, notes, progress
  records, and resume/"Continue Learning" state are introduced in their own phases; the
  schema designed here must leave room for them (per the future-proofing requirement) but
  MUST NOT implement them now.
- **Platform reach**: The foundation is shared logic intended to serve both target mobile
  platforms from a single source, consistent with the project's multiplatform approach.

## Dependencies

- This is Phase 0 — the first phase in `docs/ROADMAP.md`. It has **no prior-phase
  dependencies** and is a hard prerequisite for Phase 1 (Reading Experience) and everything
  after it.
