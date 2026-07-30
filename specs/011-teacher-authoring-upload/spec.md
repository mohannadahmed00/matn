# Feature Specification: Phase 11 — Teacher Authoring Tool: Foundation & Upload

**Feature Branch**: `feature/011-teacher-authoring-upload`

**Created**: 2026-07-26

**Status**: Draft

**Input**: User description: "read @docs/ROADMAP.md and create a specification to Phase 11"

## Overview

Through Phase 10 every matn a student can open was compiled into the app binary: a hardcoded
catalog list, one bundled starter matn, and store-delivered asset packs. There was no way for the
teacher who actually produces the content to add, correct, or withdraw a matn without a developer
editing source and shipping a new release.

Phase 11 is the first of three phases that replace that model with a
**teacher publishes → students browse → students download** pipeline. It builds the producer end
of that pipeline: a desktop authoring tool the teacher signs into, uses to enter a matn's
metadata and verse **text**, and publishes to a shared remote catalog. It also builds the shared
backend client every later phase depends on, and defines the catalog's stored shape.

Two boundaries define this phase precisely:

- **No audio.** Recording, per-verse audio upload, waveform splitting, and preview playback are
  Phase 12. Phase 11 delivers a matn's words, structure, and identity — not its sound. A matn
  published here is therefore *text-complete but audio-empty*, and it says so: every matn record
  carries an audio-completeness state, which Phase 12 advances and Phase 13 filters on so a
  student is never offered a silent matn.
- **No student-app change.** Students continue to see exactly what they see today. Consuming this
  catalog is Phase 13. Nothing published in this phase becomes visible in any student client
  during this phase.

Within those bounds the teacher owns the full textual lifecycle: create, save, reopen, correct,
publish, correct again, and withdraw. That is what makes the phase valuable standing alone — the
entire textual corpus becomes the teacher's to maintain without a developer, and the catalog
schema is exercised by a real producer before any consumer is built against it.

## Clarifications

### Session 2026-07-26

- Q: Phase 11 has no audio, but the reused content-integrity rules treat a verse with no audio as
  invalid. Can a text-only matn be published? → A: Yes. Publishing is allowed with no audio, and
  audio-completeness is tracked as its own state on the matn record — advanced by Phase 12 as
  audio arrives, and used by Phase 13 to decide what a student may see. The audio-related
  integrity rules (missing audio, duplicate audio reference) are therefore *deferred* in this
  phase rather than dropped: they do not block publishing here, and they become blocking in
  Phase 12.
- Q: Does Phase 11 include managing matns that already exist — listing them, reopening a draft,
  editing a published matn, unpublishing? → A: Yes, the full lifecycle. The teacher can list every
  matn with its state, reopen a draft to continue work, edit a matn that is already published, and
  unpublish one to withdraw it. Without this a typo in a published matn would be unfixable, which
  defeats the point of moving content out of the binary.
- Q: How does a matn's default reciter get set, given the field is stored but this phase has no
  audio? → A: One implicit reciter. The tool assigns a single fixed institutional reciter identity
  to every matn automatically; it is neither shown nor editable, and there is no reciter list. The
  phase is scoped to one teacher who is also the reciter, and the stored shape must merely not
  block multi-reciter mapping later.
- Q: How is in-progress work protected between explicit saves — a crash partway through a
  500-verse matn currently loses everything? → A: Periodic autosave of the open **draft** to remote
  storage, plus save-on-close, alongside the explicit save button. No local buffer, so drafts stay
  remote-only. Autosave does not apply to a matn that is already published, so partial corrections
  never go live — those save only when the teacher explicitly saves.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Sign in to the teacher portal (Priority: P1)

The teacher opens the authoring tool on their computer and is asked to sign in. After entering
their credentials they land on the portal's main surface, identified by name, with their remaining
content storage shown. If they close the tool and reopen it later the same day, they are still
signed in. Signing out returns them to the sign-in screen and no further content can be written.

**Why this priority**: Every other action in this phase is a write to shared content storage, and
writes are the only operations that require identity. Nothing else can be built or trusted until
one authenticated round-trip works end to end.

**Independent Test**: Launch the tool with no stored session; verify the sign-in screen appears,
correct credentials reach the portal and wrong credentials produce a clear, non-technical error;
verify the session survives a restart and that signing out revokes write access.

**Acceptance Scenarios**:

1. **Given** the tool is launched with no stored session, **When** the teacher enters valid
   credentials, **Then** they reach the portal surface with their display name shown.
2. **Given** the sign-in screen, **When** the teacher enters an unrecognized email or wrong
   password, **Then** an understandable error is shown and no session is stored.
3. **Given** a signed-in teacher, **When** the tool is closed and reopened, **Then** they are
   still signed in without re-entering credentials.
4. **Given** a signed-in teacher, **When** they sign out, **Then** the sign-in screen returns and
   any subsequent attempt to save or publish is refused.
5. **Given** no network connection, **When** the teacher attempts to sign in, **Then** a clear
   "cannot reach the server" message appears rather than a generic failure.
6. **Given** the portal in either interface language, **When** the teacher switches to the other,
   **Then** every visible label changes language, the layout mirrors to that language's direction,
   the choice survives a restart, and no stored content changes.

---

### User Story 2 - Create a matn and save it as a draft (Priority: P1)

The teacher starts a new matn and fills in its identity: title, author/scholar, a short
description, and a cover image. If the matn is structured, they define its chapters/sections in
order. They save it as a draft. The draft is stored remotely, not on their machine, and is not
visible to students.

**Why this priority**: Together with User Story 1 this is the smallest slice that proves the whole
producer path — authenticate, write a document, write a file, read it back. Verse content is
meaningless without an owning matn.

**Independent Test**: Sign in, create a matn with title/author/description/cover and two
chapters, save as draft, quit the tool, reopen, and confirm the draft is retrievable with every
field and the cover image intact.

**Acceptance Scenarios**:

1. **Given** a signed-in teacher on a blank form, **When** they enter a title, author,
   description, and cover image and save as draft, **Then** the draft is stored and confirmed.
2. **Given** a form with a required field empty, **When** the teacher saves, **Then** the empty
   field is flagged inline and nothing is stored.
3. **Given** a matn declared as structured, **When** the teacher adds chapters, **Then** each
   chapter has a title and an explicit position, and positions cannot collide.
4. **Given** a saved draft, **When** the teacher reopens the tool, **Then** every stored field —
   including the cover image — is restored exactly as entered.
5. **Given** a cover image that exceeds the accepted size or format, **When** it is chosen,
   **Then** it is rejected with a message naming the accepted limits, and the rest of the form is
   preserved.
6. **Given** the network drops mid-save, **When** the save fails, **Then** the teacher's entered
   work remains on screen and the failure is reported as retryable — never silently discarded.
7. **Given** an open draft with unsaved changes, **When** the teacher keeps working without saving,
   **Then** the draft is autosaved remotely within 60 seconds, the last-saved time is visible, and
   editing is never interrupted by the save.

---

### User Story 3 - Enter verse text (Priority: P2)

The teacher builds the matn's body as an ordered list of verses. They add a verse, type its
Arabic text right-to-left, and repeat. They can drag a verse to a new position, delete a verse,
and assign each verse to a chapter when the matn is structured. Verse numbering follows the list
order and stays correct after any reorder or deletion.

**Why this priority**: This is the actual content of a matn and the bulk of the teacher's time,
but it needs an owning matn (User Story 2) to attach to.

**Independent Test**: Open a draft, add five verses of Arabic text, reorder two of them by
dragging, delete one, and confirm the resulting order, numbering, and text survive a save and
reload.

**Acceptance Scenarios**:

1. **Given** an open draft, **When** the teacher adds a verse and types Arabic text, **Then** the
   text renders right-to-left with the app's Arabic typography and is stored verbatim, including
   diacritics.
2. **Given** a list of verses, **When** the teacher drags one to a new position, **Then** the
   displayed order updates immediately and verse numbering is renumbered consistently.
3. **Given** a list of verses, **When** the teacher deletes one, **Then** the remaining verses
   stay in order with no gap in numbering.
4. **Given** a structured matn, **When** the teacher assigns verses to chapters, **Then** every
   verse belongs to exactly one existing chapter.
5. **Given** an empty verse row, **When** the teacher saves the draft, **Then** the empty row is
   flagged and the draft is not stored in an inconsistent state.

---

### User Story 4 - Validate and publish (Priority: P2)

Before publishing, the teacher asks the tool to check the matn. Problems are listed in plain
language, each pointing at the verse or chapter it concerns. When there are no problems the
teacher publishes. Publishing flips the matn from draft to published; only published matns are
readable by anyone other than the teacher.

Because this phase has no audio, the matn publishes as text-complete and audio-empty, and the
tool says so plainly rather than treating the absent audio as an error. The matn's
audio-completeness state records the fact, and the teacher can see at a glance which published
matns are still waiting on recordings.

**Why this priority**: Publishing is the phase's terminal action and the one that makes content
real, but it can only be exercised once there is a complete matn to publish.

**Independent Test**: Take a draft containing a deliberate defect (two verses sharing a number),
run validation, confirm the defect is reported and publishing is refused; fix it, re-run, publish,
and confirm the stored matn's state changed to published with an audio-completeness state of
"no audio".

**Acceptance Scenarios**:

1. **Given** a draft with duplicate verse numbers, duplicate identifiers, a verse pointing at a
   non-existent chapter, or a chapter-structure mismatch, **When** validation runs, **Then** every
   problem is listed with the specific verse or chapter named, and publishing is refused.
2. **Given** a draft whose verses have no audio and no other problems, **When** validation runs,
   **Then** the missing audio is reported as an informational "waiting on recordings" note, not an
   error, and publishing is permitted.
3. **Given** a draft with no problems, **When** the teacher publishes, **Then** the matn's state
   becomes published, its audio-completeness state records that no verse has audio, and the change
   is confirmed.
4. **Given** a matn in draft state, **When** an unauthenticated reader requests it, **Then** it is
   not returned.
5. **Given** a matn in published state, **When** an unauthenticated reader requests it, **Then**
   its catalog fields — including its audio-completeness state — are returned.
6. **Given** any unauthenticated party, **When** they attempt to create, change, publish, or
   delete any matn, **Then** the attempt is refused regardless of the request's shape.
7. **Given** a matn that validates in the tool, **When** its stored form is loaded by the app's
   existing content-ingestion rules, **Then** every problem those rules can raise about its *text*
   and *structure* is absent.

---

### User Story 5 - Manage the catalog: reopen, correct, withdraw (Priority: P2)

The teacher opens the portal and sees every matn they have created, each showing its title,
publication state, verse count, and whether it is still waiting on recordings. They pick up a
half-finished draft where they left off. They notice a typo in a matn published last week, open it,
fix the verse, and save — the correction is live without a release. They decide one matn was
published too early and withdraw it, returning it to draft so no student sees it.

**Why this priority**: A 500-verse matn is not one sitting, so reopening a draft is load-bearing
for User Story 3 even existing in practice. And a published matn that cannot be corrected
reintroduces the exact problem this phase exists to remove — content only a developer can change.

**Independent Test**: Create one draft and one published matn; confirm both appear in the list
with correct state; reopen the draft and add a verse; edit a verse in the published matn and
confirm the change is readable by an anonymous reader immediately after saving; unpublish it and
confirm the anonymous reader can no longer read it.

**Acceptance Scenarios**:

1. **Given** several matns in mixed states, **When** the teacher opens the portal, **Then** each is
   listed with its title, publication state, verse count, and audio-completeness state.
2. **Given** a saved draft, **When** the teacher reopens it, **Then** all metadata, chapters, and
   verses load exactly as last saved and are fully editable.
3. **Given** a published matn, **When** the teacher edits a verse and saves, **Then** the matn
   stays published and the corrected text is what an anonymous reader receives.
4. **Given** a published matn, **When** the teacher unpublishes it, **Then** its state returns to
   draft and an anonymous reader can no longer read it.
5. **Given** an unpublished matn, **When** the teacher publishes it again, **Then** it becomes
   readable again with its identifier unchanged, so nothing that referenced it is orphaned.
6. **Given** a published matn being edited, **When** the save is in flight, **Then** an anonymous
   reader receives either the old version or the new one in full — never a partially updated matn.
7. **Given** a matn opened for editing, **When** it was changed elsewhere since it was loaded and
   the teacher saves, **Then** the conflict is reported and the other change is not overwritten.

---

### User Story 6 - Import verse text in bulk (Priority: P3)

Rather than typing every verse, the teacher imports a prepared file of verse text. The tool shows
what it will import before committing, reports any rows it cannot read, and appends the accepted
rows to the verse list where they can still be edited and reordered.

**Why this priority**: A pure time-saver over User Story 3, which already delivers the capability.
Real matns run to hundreds of verses, so it matters — but it is not on the critical path.

**Independent Test**: Import a file of 50 verse rows including two malformed rows; confirm the
preview reports exactly the two bad rows, that the teacher can cancel or proceed, and that
proceeding appends 48 editable verses in file order.

**Acceptance Scenarios**:

1. **Given** a well-formed import file, **When** the teacher imports it, **Then** a preview shows
   the row count and the first rows before anything is committed.
2. **Given** a file with unreadable or empty rows, **When** it is previewed, **Then** each
   problem row is identified by its position and the teacher chooses to cancel or import the rest.
3. **Given** an accepted import, **When** it completes, **Then** the imported verses appear in
   file order at the end of the existing list and behave exactly like hand-entered verses.
4. **Given** a file that cannot be decoded as UTF-8 text, **When** it is chosen, **Then** it is
   rejected with a message naming the expected format and encoding, and the existing list is
   untouched.
5. **Given** verse lines containing commas and quotation marks, **When** they are imported,
   **Then** the punctuation is preserved verbatim and no line is split on it.

---

### Edge Cases

- The teacher's session expires mid-edit: unsaved work must survive re-authentication rather than
  being lost to a failed save, and autosave must not silently discard it while unauthenticated.
- Autosave fails repeatedly because the network is down: the teacher must find out they are working
  unprotected rather than believing their draft is safe.
- The operating system's credential store is unavailable, locked, or refuses access: sign-in must
  still work for the session at hand, with the loss of persistence made visible rather than
  presented as a failure to sign in.
- The tool is terminated while an autosave is in flight: the stored draft must be either the
  previous state or the new one in full.
- The same teacher account is signed in on two machines and both edit the same matn: the second
  write must not silently destroy the first — the conflict is detected and reported.
- The network drops between saving verse text and uploading the cover image: the stored matn must
  not be left half-written and unreadable.
- A published matn is edited while an anonymous reader is fetching it: the reader gets a complete
  version, old or new, never a mixture.
- A matn is unpublished while an anonymous reader holds a stale reference to it: the read fails
  cleanly as "not available" rather than returning partial or misleading content.
- A matn is unpublished and republished: its identifier and its verses' identifiers must be
  unchanged, so anything that referenced them still resolves.
- A matn with several hundred verses: the verse list must stay responsive to typing, scrolling,
  and dragging.
- Arabic text pasted from another program carrying invisible directional or formatting characters:
  stored text must match what the teacher sees.
- An import file whose lines are all blank, or which is empty: reported as nothing to import rather
  than appending empty verses.
- The same file is imported twice: the verses are appended twice, which the preview's row count
  makes visible before it happens.
- Two matns are given the same title: allowed, since identity is not the title — but the teacher
  is warned.
- All verses are deleted from a matn: publishing it must be refused, and if it is already
  published the save must be refused rather than leaving a published empty matn.
- Remaining storage is exhausted mid-upload: the failure is reported as a storage problem, not a
  generic error.

## Requirements *(mandatory)*

### Functional Requirements

#### Teacher client & identity

- **FR-001**: The system MUST provide a desktop authoring client, separate from every student
  client and not reachable from any of them.
- **FR-002**: The authoring client MUST require the teacher to sign in before any content can be
  created, changed, or published.
- **FR-003**: The system MUST persist an authenticated session across restarts of the client and
  MUST refresh it without re-prompting until it is explicitly ended or is rejected by the server.
- **FR-003a**: The persisted session credential MUST be held in the operating system's credential
  store. Where a platform provides none, the fallback MUST be a file readable only by the teacher's
  own account, and the tool MUST make the reduced protection visible rather than silent.
- **FR-003b**: No credential, token, or password MAY be written to any log, error message,
  diagnostic output, or crash report.
- **FR-004**: The system MUST let the teacher sign out, after which no content operation succeeds
  until they sign in again, and the persisted credential MUST be erased from the credential store.
- **FR-005**: The authoring client MUST present authentication, network, and storage failures as
  plain-language messages that state what failed and whether retrying is worthwhile.
- **FR-006**: The authoring client MUST use the same shared visual design tokens as the student
  clients; it MUST NOT define its own colors, type scale, spacing, or shapes.
- **FR-006a**: The authoring client's interface MUST be available in both Arabic and English, with
  no untranslated text in either. The teacher MUST be able to switch between them, and the choice
  MUST persist across restarts.
- **FR-006b**: Layout direction MUST follow the selected interface language — right-to-left
  throughout in Arabic, left-to-right in English — including navigation, forms, lists, drag
  handles, and drag direction.
- **FR-006c**: Switching the interface language MUST NOT alter any stored content or its direction.
  (Verse-text direction itself is specified once, in FR-021.)

#### Catalog shape

- **FR-007**: The system MUST store each matn as a single remote record carrying its identity
  (stable unique identifier), title, author, description, cover-image reference, structure kind,
  default reciter, its ordered chapters, and its ordered verses.
- **FR-007a**: The default reciter MUST be assigned automatically from a single fixed institutional
  reciter identity, identical for every matn. It MUST NOT be entered, chosen, or edited by the
  teacher, and MUST NOT be left empty. The stored shape MUST still permit a per-matn or per-verse
  reciter later without changing the identifiers already written.
- **FR-008**: Every matn, chapter, and verse MUST carry a stable unique identifier that is
  independent of display order, generated once at creation and never reused or reassigned —
  including across an unpublish/republish cycle.
- **FR-009**: The stored record's field set MUST correspond one-to-one with the field set the
  student app's existing content-ingestion path already accepts, so that a later phase's sync is a
  direct projection and not a translation.
- **FR-010**: Each matn record MUST carry a publication state of either draft or published, and
  MUST record when it was created and last changed.
- **FR-011**: Each matn record MUST carry an audio-completeness state distinguishing at minimum
  "no verse has audio", "some verses have audio", and "every verse has audio", derived from the
  matn's actual contents rather than set by hand.
- **FR-012**: Each matn record MUST expose the catalog-overview facts a later phase needs to list
  it without downloading it: title, author, description, cover image, verse count, declared
  content size, and audio-completeness state.
- **FR-013**: The stored shape MUST NOT prevent a later addition of multiple teachers or
  content ownership, even though this phase assumes exactly one teacher.

#### Metadata authoring

- **FR-014**: Teachers MUST be able to enter and change a matn's title, author, description, and
  structure kind.
- **FR-015**: Teachers MUST be able to attach a cover image, see it previewed, and replace or
  remove it.
- **FR-016**: The system MUST reject cover images outside the accepted formats or size limit and
  MUST state the limits in the rejection message.
- **FR-017**: Teachers MUST be able to define an ordered set of chapters for a structured matn,
  each with a title and a distinct position.
- **FR-018**: The system MUST require title, author, and structure kind before a matn can be
  saved as a draft.

#### Verse-text authoring

- **FR-019**: Teachers MUST be able to add, edit, and delete verses in an ordered list.
- **FR-020**: Teachers MUST be able to reorder verses by dragging, and the system MUST keep verse
  display numbers consistent with the resulting order after any add, delete, or reorder.
- **FR-021**: Verse text entry MUST render and edit Arabic right-to-left **in both interface
  languages** — matn content is Arabic regardless of the chrome around it — and MUST store the text
  exactly as entered, including diacritics.
- **FR-022**: Teachers MUST be able to assign each verse to a chapter when the matn is structured,
  and the system MUST prevent assigning a verse to a chapter that does not exist.
- **FR-023**: Teachers MUST be able to import verse text in bulk from a file, review a preview of
  what will be imported, see any unreadable lines identified by line number, and cancel or proceed.
- **FR-023a**: The import format MUST be plain UTF-8 text with one verse per line. Blank and
  whitespace-only lines MUST be skipped without being reported as problems. No delimiter, column,
  or quoting convention is applied — a line's entire content is that verse's text, so commas and
  quotation marks inside Arabic verse text carry through unchanged.
- **FR-023b**: The system MUST accept either line-ending convention, MUST ignore a leading
  byte-order mark, and MUST reject a file it cannot decode as UTF-8 with a message naming the
  expected encoding rather than importing damaged text.
- **FR-024**: Imported verses MUST be indistinguishable from hand-entered verses once imported —
  editable, reorderable, and deletable in the same way.
- **FR-025**: For a matn of at least 500 verses, typing into a verse, scrolling the list, and
  dragging a verse to a new position MUST each complete without any single frame exceeding 32
  milliseconds — that is, no visible stutter at 30 frames per second or better.

#### Validation

- **FR-026**: The system MUST validate a matn against exactly the content-integrity rules the
  student app's ingestion path already enforces — blank identifiers, duplicate identifiers,
  duplicate verse display numbers, duplicate chapter positions, verses referencing a non-existent
  chapter, and a structure declaration that disagrees with the actual chapter/verse arrangement —
  rather than a parallel set of rules defined for the tool.
- **FR-027**: The audio-related rules among those — a verse with no audio, and two verses sharing
  an audio reference — MUST be evaluated and reported but MUST NOT block publishing in this
  phase. They are reported as outstanding work, and they become blocking when audio authoring
  exists.
- **FR-028**: Validation MUST report every problem found in one pass, each naming the specific
  matn, chapter, or verse it concerns, in language the teacher can act on, and MUST distinguish
  blocking problems from outstanding-work notes.
- **FR-029**: The system MUST refuse to publish a matn that has any outstanding *blocking*
  validation problem.
- **FR-030**: A matn with no verses MUST NOT be publishable, and MUST NOT be savable while
  published.

#### Persistence, publishing & lifecycle

- **FR-031**: Teachers MUST be able to save a matn as a draft at any point, and drafts MUST be
  stored remotely rather than only on the teacher's machine.
- **FR-031a**: While a **draft** is open with unsaved changes, the system MUST autosave it to
  remote storage at least once every 60 seconds and again when the matn is closed, in addition to
  honoring explicit saves.
- **FR-031b**: Autosave MUST NOT apply to a matn that is currently published — an open published
  matn is written only on an explicit save, so a partial correction is never readable by anyone.
- Q: Where does the persisted session credential live on the teacher's machine? → A: The operating
  system's credential store / keychain, with a plain-file fallback only on a platform that offers
  none. The stored credential is the only thing standing between file access and write authority
  over all student content, so it is not kept in plaintext where a file-reading attacker can take
  it.
- Q: What language and direction is the authoring tool's own interface, given matn content is
  Arabic and right-to-left regardless? → A: Both Arabic and English, switchable by the teacher,
  with layout direction following the selected language — the whole tool mirrors to right-to-left in
  Arabic. This is broader than the captured design, which shows English chrome only, so the Arabic
  chrome and its mirrored layout are original work for this phase.
- Q: What format is the bulk verse-text import — the roadmap says CSV, which brings quoting rules
  that collide with commas and quotation marks inside Arabic verse text? → A: Plain UTF-8 text, one
  verse per line, blank lines skipped. No delimiter, so no quoting rules and no escaping bugs, and
  the teacher can produce it from any text editor. Chapter assignment already happens in the verse
  list after import, so there are no extra columns to carry. This is a deliberate departure from
  the roadmap's wording.
- **FR-031c**: Autosave MUST NOT interrupt or block editing, MUST show when the draft was last
  saved, and on failure MUST report the failure without discarding work and retry at the next
  interval.
- **FR-032**: A save or publish that fails MUST leave the teacher's on-screen work intact and MUST
  report the failure as retryable or not.
- **FR-033**: A save, publish, or unpublish MUST leave the stored matn either fully updated or
  unchanged — never partially written, and never partially visible to a concurrent reader.
- **FR-034**: Teachers MUST be able to publish a validated matn, changing its state from draft to
  published.
- **FR-035**: Teachers MUST be able to list every matn they have created with its publication
  state, verse count, and audio-completeness state.
- **FR-036**: Teachers MUST be able to reopen any matn — draft or published — and edit its
  metadata, chapters, and verses.
- **FR-037**: Editing a published matn MUST keep it published; the correction becomes the version
  readers receive on the next read, with no separate re-publish step.
- **FR-038**: Teachers MUST be able to unpublish a published matn, returning it to draft and
  making it unreadable to anyone without an account, and MUST be able to publish it again
  afterwards with all identifiers preserved.
- **FR-039**: The publication state MUST be the only gate on whether a matn is readable by anyone
  other than the teacher — there is no separate approval or release step.
- **FR-040**: Readers without an account MUST be able to read published matns and MUST NOT be able
  to read drafts.
- **FR-041**: Readers without an account MUST NOT be able to create, change, publish, unpublish, or
  delete any matn or any stored file, by any request.
- **FR-042**: The access rules enforcing FR-040 and FR-041 MUST be covered by automated tests that
  exercise both the permitted and the refused cases; writing the rules without testing them is not
  sufficient.
- **FR-043**: The system MUST detect when a matn has been changed elsewhere since it was loaded
  and MUST report the conflict rather than overwriting the other change.

#### Boundaries

- **FR-044**: This phase MUST NOT change the behavior, content, or appearance of any student
  client.
- **FR-045**: This phase MUST NOT store, upload, or accept any audio, and MUST NOT persist any
  timing offsets against a shared or continuous audio file.

### Key Entities

- **Matn**: One memorization text. Carries a stable identifier, title, author, description, cover
  image, structure kind, default reciter, publication state, audio-completeness state,
  creation/modification times, and the overview facts (verse count, declared size) a catalog
  listing needs. Owns chapters and verses.
- **Chapter**: A named section within a structured matn, with a position that is unique inside its
  matn. Zero chapters for a simple matn.
- **Verse**: One line of the matn — the unit of memorization, repetition, and (from Phase 12)
  audio. Carries a stable identifier, its Arabic text, a display number reflecting list order, and
  an optional owning chapter. Its audio and duration are Phase 12's concern.
- **Teacher account**: The single authenticated identity permitted to write content. Has
  credentials, a display name, and a session.
- **Catalog**: The complete set of stored matns. Fully visible to the teacher with each matn's
  state; readable without an account for published entries only.
- **Validation report**: The outcome of checking a matn — a set of problems, each naming its
  subject and marked as blocking publication or as outstanding work.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A teacher can go from launching the tool to a published matn of 20 verses, entered
  by hand, in under 20 minutes with no developer assistance and no reference to written
  instructions beyond what the screen shows.
- **SC-002**: Adding, correcting, or withdrawing a matn requires zero changes to any source file
  and zero app releases — measured by performing all three against an unmodified, already-released
  build.
- **SC-003**: A typo in a published matn can be found, corrected, and made live in under 3
  minutes.
- **SC-004**: Importing 200 verses from a prepared file and confirming their order takes under 2
  minutes.
- **SC-005**: 100% of the *blocking* content-integrity problems the student app's ingestion path
  can reject are caught and reported by the tool before publishing — verified by feeding the tool
  one draft per rule and confirming each is refused.
- **SC-006**: Every matn the tool publishes carries no text- or structure-level integrity problem
  — zero such failures across the full published catalog when checked against the student app's
  ingestion rules.
- **SC-007**: Every published matn's audio-completeness state matches its actual contents 100% of
  the time, so that a later phase can rely on it to decide what a student may see.
- **SC-008**: A matn in draft state is unreadable by an unauthenticated reader, and every
  unauthenticated write attempt is refused — 100% of attempted access violations blocked in the
  access-rule test suite.
- **SC-009**: Editing a 500-verse matn — typing, scrolling, and dragging a verse from position 400
  to position 5 — produces no frame longer than 32 milliseconds.
- **SC-010**: No save, publish, or unpublish failure results in lost work: across all simulated
  network, authentication, and storage failures, the teacher's on-screen content is preserved 100%
  of the time.
- **SC-011**: No reader ever observes a partially written matn — 100% of concurrent read/write
  trials return either the complete prior version or the complete new one.
- **SC-012**: Every screen is complete and usable in both interface languages — zero untranslated
  labels, zero clipped or overlapping text, and zero controls left on the wrong side after
  mirroring, across all screens in both directions.
- **SC-013**: No credential, token, or password appears in plaintext anywhere on disk or in any
  log, diagnostic, or crash output — verified by inspecting every file the tool writes after a
  full sign-in, author, publish, and sign-out cycle.
- **SC-014**: An unexpected termination while authoring a draft — crash, closed window, power
  loss — costs at most 60 seconds of typing, measured from the last autosave.
- **SC-015**: Student clients behave identically before and after this phase — verified by the
  existing student test suite passing unchanged.

## Assumptions

- **Single teacher.** Exactly one teacher account exists per institution. There is no
  multi-teacher permission model and no content-ownership model, though the stored shape must not
  block adding one (FR-013).
- **Account provisioning is manual.** The single teacher's account is created out-of-band by an
  administrator; the tool has no self-registration, and password reset is handled outside the
  tool.
- **Students have no accounts.** Catalog reads are anonymous and gated only by publication state.
  Remote student accounts remain a later concern.
- **One reciter, implicit.** The single teacher is also the single reciter, so the reciter identity
  is a constant the tool applies rather than a field on the form. Multi-reciter audio mapping stays
  a later concern that the schema must not block (FR-007a, FR-013).
- **Desktop only, teacher-side.** The authoring tool runs on the teacher's computer. There is no
  mobile or web authoring surface in this phase.
- **The teacher is online while authoring.** Offline authoring and conflict-free merge are out of
  scope; the tool reports connectivity failures rather than queueing work. Crash protection is
  periodic remote autosave (FR-031a), not a local cache — there is no on-disk draft copy to
  recover from or reconcile.
- **Editing a published matn edits it in place.** There is no draft-alongside-published model and
  no version history — the correction replaces the published content atomically. Withdrawing is a
  separate, explicit unpublish.
- **Unpublish is how content is withdrawn; there is no delete.** Removing a matn permanently is
  out of scope, which also avoids orphaning anything that referenced its identifier.
- **Audio-completeness is derived, not declared.** It is computed from how many verses have audio,
  so it cannot drift from reality; in this phase it is always "no verse has audio".
- **Declared content size in this phase covers text only.** It grows to include audio in Phase 12,
  when there is audio to measure.
- **Bulk import is plain UTF-8 text, one verse per line** (FR-023a) — not CSV, despite the
  roadmap's wording, because a delimited format's quoting rules collide with punctuation that is
  ordinary inside Arabic verse text. Chapter assignment for imported verses is done after import in
  the verse list, not encoded in the file.
- **Import appends; it never replaces.** A second import adds to the existing list rather than
  overwriting it, so an accidental double import is corrected by deleting rows, not by recovering
  lost work.
- **Verse display numbers derive from list order.** The teacher does not enter them by hand, which
  is what makes reorder-safe renumbering possible.
- **The category selector shown in the design is not implemented.** The stored shape mirrors the
  student app's existing field set, which has no category concept; adding one would make the
  catalog schema diverge from what the app can ingest.
- **Storage-usage display is informational.** The quota figure shown in the portal reflects
  actual consumption; this phase adds no quota enforcement of its own beyond reporting the
  backend's own limits.
- **The teacher-portal navigation shell is taken from the producer chrome already present in the
  design set**, rather than invented for this phase. Its Arabic, right-to-left form is not in the
  captured design and is original work, derived by mirroring the captured layout rather than by
  redesigning it.
- **Arabic is the default interface language on first launch**, English being the alternative the
  teacher may switch to, on the grounds that the content and its author are Arabic.

## Out of Scope

- All audio: recording, per-verse audio upload, continuous-recording splitting, waveform marker
  placement, duration capture, and preview playback (**Phase 12**).
- All student-app changes: remote catalog sync, per-matn download, the first-launch empty/offline
  state, and removal of the bundled/asset-pack delivery stack (**Phase 13**).
- Permanent deletion of a matn, and version history or rollback of an edited matn.
- Multi-teacher accounts, roles, permissions, and content ownership.
- Student accounts, cross-device sync of student data, and community content.
- Analytics on student consumption, and any teacher-facing view of student progress.
- Any interface language beyond Arabic and English, and any locale-specific formatting of numbers,
  dates, or names beyond what those two require.
- Bulk operations across multiple matns (batch publish, batch unpublish, export of the whole
  catalog).

## Dependencies

- **Phase 1** — the stable-identifier domain model and the content field set this phase's stored
  shape mirrors. Complete.
- **Phase 10** — the shared design tokens the authoring client consumes rather than redefining.
  Complete.
- **A hosted backend** providing an authenticated document store, file storage, and identity, with
  server-side access rules. Its project setup is part of this phase.
- **A shared network client** in the common module, used by every client on every platform,
  because no platform-specific vendor library covers the desktop targets. Introducing it is the
  project's first network dependency and requires an explicit justification against a simpler
  alternative under the constitution's dependency clause.
- **Blocks Phase 12**, which adds audio to the matns this phase can already create and publish,
  and which is where the deferred audio-integrity rules (FR-027) become blocking.
- **Blocks Phase 13**, the student consumer of what Phases 11–12 upload, which relies on the
  audio-completeness state (FR-011) to decide what a student may see.
