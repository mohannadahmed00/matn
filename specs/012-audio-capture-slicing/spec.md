# Feature Specification: Phase 12 — Audio Capture & Slicing

**Feature Branch**: `feature/012-audio-capture-slicing`

**Created**: 2026-07-31

**Status**: Draft

**Input**: User description: "read @docs/ROADMAP.md and creat a specification to Phase 12"

## Overview

Phase 11 gave the teacher the whole textual lifecycle — create, correct, publish, withdraw — but
every matn it produces is **text-complete and audio-empty**. A matn with no sound is not a
memorization companion; it is a book. Phase 12 adds the sound.

The teacher gets two ways to attach audio, and they produce the **identical artifact**: an ordered
set of one audio file per verse, held in remote storage alongside the matn.

- **Per-verse upload** — the teacher supplies one already-cut recording per verse, row by row.
- **Split from a continuous recording** — the teacher supplies one long recording of the whole
  matn, marks where each verse begins and ends on a waveform, adjusts the boundaries numerically,
  and the tool cuts that recording into per-verse files on save.

The second path is an *authoring convenience only*. The markers, the boundary numbers, and the
continuous recording itself are working material: they are never stored as part of the matn, never
uploaded as the matn's audio, and never reach a student. What is stored is the same per-verse set
the first path produces. This is what keeps the per-verse audio lock intact — the constitution
forbids a continuous file as the *runtime asset model*, not as an authoring input.

Two further things follow from audio existing at all:

- **Preview playback.** The teacher hears the assembled matn the way a student will — verse after
  verse, gapless — before publishing it, so a mis-cut verse or a missing recording is caught by the
  person who made it rather than by a student.
- **Audio integrity becomes blocking.** Phase 11 evaluated "this verse has no audio" and "two
  verses share the same audio" and reported them as outstanding work, because no audio authoring
  existed. It exists now, so those problems stop publication.

Two boundaries define the phase precisely:

- **No student-app change.** Students still see exactly what they see today. Consuming this
  catalog — remote sync, per-matn download, the empty/offline first launch — is Phase 13.
- **No new content model.** The matn record, its identifiers, its publication lifecycle, and its
  access rules are Phase 11's and are not redesigned. Phase 12 fills in the audio fields the
  Phase 11 shape already reserved and advances the audio-completeness state those fields derive.

## Clarifications

### Session 2026-07-31

- Q: May a partially recorded matn be published, or must every verse have audio first? → A: Every
  verse must have audio. Phase 11 deferred the audio-integrity rules with the explicit note that
  they become blocking once audio authoring exists; this is that phase. The partial state remains
  the teacher's own progress view and a defence for Phase 13, but it is not publishable
  (FR-028, FR-030).
- Q: Does the tool record from a microphone, or only ingest recordings the teacher made elsewhere?
  → A: Ingest only. The teacher uploads finished recordings — one per verse, or one continuous
  recording of the whole matn. Live capture inside the tool is out of scope.
- Q: For the continuous path, is the single recording stored as-is alongside a start/end map for
  each verse, or cut into per-verse files? → A: **Cut into per-verse files on save.** The teacher's
  screen is the same either way — upload one recording, mark each verse's start and end on the
  timeline — but what is stored is one file per verse, and the markers are discarded. Storing the
  recording plus a map is the model the constitution's per-verse audio lock rejects outright
  ("a matn whose verses resolve to ranges within one file is the rejected model, whatever produced
  it"), and reversing it would require a constitution amendment, a Phase 1 data-model change,
  rework of all three playback engines onto seek-within-file, rework of Phase 13's per-matn
  download, and a catalog carrying two runtime audio models at once. Slicing keeps every one of
  those untouched at the cost of a decoder dependency in the authoring tool. This was raised as a
  conflict and decided deliberately, not assumed.

- Q: How is the single stored audio profile achieved — by re-encoding what the teacher supplies, or
  by constraining what is accepted? → A: **Copy, never re-encode.** A split cuts the source at its
  own frame boundaries, so the stored bytes are the teacher's bytes and nothing is decoded and
  re-compressed. A per-verse upload is stored as supplied, but must match the matn's established
  sample rate and channel layout or it is rejected with a message naming the mismatch. This keeps
  the phase's new dependency to a *decoder* only — needed for the waveform, durations, and preview
  — and avoids both generation loss and minutes of processing per 500-verse matn. Frame-boundary
  cutting places each boundary within one frame of the requested position, comfortably inside
  SC-004's tolerance.

- Q: When audio is replaced or removed, or its verse is deleted, is the stored file deleted
  immediately? → A: **Deleted only after the matn write commits.** The new state is written first;
  the now-unreferenced file is deleted once that write has succeeded. Deleting first would let a
  failed save leave a published matn pointing at a file that no longer exists, violating FR-035. An
  interrupted deletion leaves an unreferenced file behind — it costs storage but breaks nothing,
  and that is the safe direction to fail in.

- Q: Does resuming an interrupted batch upload survive closing the tool, and what records the
  progress? → A: **It survives a restart, and nothing extra records it — already-landed work is
  derived, not tracked.** For per-verse attaching, each verse commits on its own, so the matn's own
  audio references say what is done. For a split, the matn is written once at the end (FR-019
  forbids persisting a half-applied split), so the matn cannot be the source of truth mid-run;
  instead a re-run treats an expected per-verse file **already present in storage at its own path**
  as done and uploads only the rest. Either way there is no separate progress record to keep
  consistent with reality.

- Q: Does a split always cover the whole matn, or can it cover part of it? → A: **A split targets a
  chosen verse range**, defaulting to the whole matn. Every verse *within the chosen range* still
  needs a valid range on the waveform or the split is refused; verses outside it keep their
  existing audio untouched. This is what lets a teacher who re-recorded verses 40–55 fix exactly
  those without re-marking the other 484, and it makes User Story 5's partial re-split the same
  flow as the first pass rather than a second mechanism.

- Q: What are the size and duration ceilings for supplied audio? → A: **A single per-verse
  recording may be up to 10 MB; a continuous recording up to 300 MB and 4 hours.** A 15-second
  verse at ordinary spoken-word bitrate is well under 1 MB and a 500-verse matn recorded in one
  pass runs roughly 90 minutes and 100 MB, so these ceilings catch wrong-file mistakes without
  rejecting legitimate recordings. The limits are enforced by the tool before uploading, so the
  teacher learns of a problem immediately rather than after a long transfer. The storage bucket's
  own limits — currently 5 MB and image types only, set when covers were the only stored files —
  MUST be widened to match, and remain the server-side backstop.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Attach a recording to a single verse (Priority: P1)

The teacher opens a matn whose text is already entered. Each verse row now has an audio slot. They
pick a recording for a verse; the tool checks it, shows its duration, and uploads it. The row shows
the recording is in place. They can play it back on the spot, replace it with a better take, or
remove it. When they leave and reopen the matn, every recording they attached is still there.

**Why this priority**: This is the smallest complete slice of the phase — one verse with sound
proves the entire path (accept a file, validate it, store it per verse, read it back, play it). The
splitting path in User Story 2 produces the same artifact, so it cannot be trusted until this works.

**Independent Test**: Open a matn with five verses; attach a recording to three of them; verify each
row shows its duration, plays back correctly, and that after quitting and reopening the tool all
three are still attached and the matn reports partial audio.

**Acceptance Scenarios**:

1. **Given** a verse with no audio, **When** the teacher selects a recording in an accepted format,
   **Then** it uploads, the row shows the recording's duration, and the verse's stored audio
   reference points at that verse's own file.
2. **Given** a verse with audio, **When** the teacher plays it from the row, **Then** it plays from
   the stored file and can be stopped.
3. **Given** a verse with audio, **When** the teacher attaches a different recording, **Then** the
   previous one is replaced — not kept alongside — and the stored duration updates.
4. **Given** a verse with audio, **When** the teacher removes it, **Then** the verse returns to
   having no audio, the stored file is released, and the matn's audio-completeness state falls back
   accordingly.
5. **Given** a file in an unaccepted format or above 10 MB, **When** it is chosen, **Then** it is
   rejected before any upload begins, with a message naming the accepted formats and the exceeded
   limit, and the verse's existing audio is untouched.
6. **Given** an upload in flight, **When** the network drops, **Then** the failure is reported as
   retryable, the verse keeps whatever audio it had before, and no half-written file is left
   referenced.
7. **Given** several verses uploading at once, **When** the teacher keeps typing or scrolling,
   **Then** editing is never blocked and each row reports its own progress.

---

### User Story 2 - Split one continuous recording into per-verse files (Priority: P1)

The teacher recorded the whole matn in one sitting. They upload that single recording, and the tool
draws its waveform. They step through the recording and place a marker at each verse boundary,
adjusting a marker by dragging it or by typing exact start and end values for a verse. The tool
tells them immediately when two verses overlap, when a stretch of the recording belongs to no verse,
or when a verse has no range at all. When the boundaries are right they save, and the tool cuts the
recording into one file per verse and uploads those. The continuous recording and the marker
positions are not part of what was saved.

**Why this priority**: This is the path that matches how the teacher actually records, and without
it a 500-verse matn means 500 separate files cut by hand outside the tool. It is equal in priority
to User Story 1 because it is the realistic bulk path, but it depends on that story's artifact
being right.

**Independent Test**: Upload one continuous recording covering ten verses; place ten ranges; save;
verify ten separate per-verse files exist with the expected durations, that each verse's audio plays
its own portion, and that neither the continuous recording nor any marker or timing offset was
stored anywhere in the matn.

**Acceptance Scenarios**:

1. **Given** a matn with verse text and a continuous recording, **When** the recording is loaded,
   **Then** its waveform and total duration are shown and each verse can be given a range on it.
2. **Given** a marker placed on the waveform, **When** the teacher drags it or edits the verse's
   start/end values directly, **Then** both views stay in agreement and the verse's range updates.
3. **Given** two verses whose ranges overlap, **When** the overlap is created, **Then** it is
   reported immediately, naming both verses, and saving is refused until it is resolved.
4. **Given** a stretch of the recording assigned to no verse, **When** the ranges are checked,
   **Then** the gap is reported with its position, so the teacher can decide whether it is silence
   or a missed verse.
5. **Given** a verse in the split's scope with no range, **When** the teacher saves, **Then** that
   verse is named and the split is refused rather than producing a zero-length file.
6. **Given** a recording covering only verses 40–55, **When** the teacher scopes the split to that
   run and saves, **Then** only those verses' audio is replaced and every other verse keeps the
   file it already had.
7. **Given** valid ranges for every verse in scope, **When** the teacher saves, **Then** exactly one
   file per verse is produced, each covering that verse's range, and each verse's stored audio
   reference points at its own file.
8. **Given** a completed split, **When** the stored matn is inspected, **Then** it contains no
   continuous recording, no marker list, and no timing offset against any shared file.
9. **Given** a completed split, **When** the teacher re-opens the matn later, **Then** the per-verse
   files behave exactly like ones attached one at a time — playable, replaceable, removable.
10. **Given** a split that fails partway through, **When** it stops, **Then** the matn is left
    either fully split or unchanged, never with some verses pointing at files that were never
    uploaded.
11. **Given** a split interrupted after some files uploaded, **When** the teacher reopens the tool
    and re-runs the same split, **Then** the files already in storage are not uploaded again and
    only the remainder transfers.

---

### User Story 3 - Hear the matn as a student will (Priority: P2)

Before publishing, the teacher plays the matn from the tool. It plays verse after verse with no gap
between them, showing which verse is sounding. They can start from any verse, pause, and stop. If a
verse has no recording the preview says so plainly rather than falling silent without explanation.

**Why this priority**: Preview is what turns "the files uploaded" into "the matn is right". It needs
audio to exist first, so it follows Stories 1 and 2 — but a matn published without ever being heard
is exactly the failure this phase exists to prevent.

**Independent Test**: Take a matn with audio on every verse; play it from the first verse and
confirm it advances through all verses without gaps and with the sounding verse indicated; then
remove one verse's audio and confirm the preview identifies that verse rather than stalling.

**Acceptance Scenarios**:

1. **Given** a matn with audio on every verse, **When** the teacher starts preview, **Then** verses
   play in order with no audible gap at the boundaries and the currently sounding verse is shown.
2. **Given** a preview in progress, **When** the teacher pauses and resumes, **Then** playback
   continues from where it stopped.
3. **Given** a preview in progress, **When** the teacher selects a different verse, **Then**
   playback jumps to that verse and continues from there.
4. **Given** a matn where some verses have no audio, **When** the preview reaches one, **Then** it
   is identified by number as missing a recording and the teacher can skip it or stop.
5. **Given** a preview in progress, **When** the teacher edits verse text or attaches new audio,
   **Then** preview stops cleanly rather than playing content that no longer matches.

---

### User Story 4 - Publish a matn with its audio complete (Priority: P2)

The teacher asks the tool to check the matn. Missing recordings and duplicated recordings are now
listed as problems that stop publication, alongside the text and structure problems Phase 11 already
caught. When everything is in place the teacher publishes, and the matn is recorded as fully
recorded. In the matn list, every matn shows how far along its recording is, so the teacher can see
at a glance what is still waiting.

**Why this priority**: This is the phase's terminal action and what makes the audio real, but it can
only be exercised once there is audio to publish.

**Independent Test**: Take a matn with audio on all but one verse; run the check and confirm the
missing verse is named and publishing is refused; attach the last recording, re-check, publish, and
confirm the stored matn records that every verse has audio.

**Acceptance Scenarios**:

1. **Given** a matn where at least one verse has no audio, **When** validation runs, **Then** every
   such verse is named, the problem is blocking, and publishing is refused.
2. **Given** two verses referencing the same audio file, **When** validation runs, **Then** both are
   named, the problem is blocking, and publishing is refused.
3. **Given** a matn with audio on every verse and no other problems, **When** the teacher publishes,
   **Then** it becomes published and its audio-completeness state records that every verse has
   audio.
4. **Given** matns in mixed recording states, **When** the teacher opens the matn list, **Then**
   each shows its recording progress — none, partial with a count, or complete.
5. **Given** a published matn, **When** its declared content size is read, **Then** it accounts for
   the audio as well as the text, so a later phase can state a download size before downloading.
6. **Given** a matn that validates in the tool, **When** its stored form is checked against the
   student app's existing content-ingestion rules, **Then** no problem of any kind is raised —
   text, structure, or audio.

---

### User Story 5 - Correct a recording after publishing (Priority: P3)

The teacher hears a mistake in a verse of a matn published last week. They open it, replace that one
verse's recording, and save. The correction is live without a release and without touching any other
verse. A student reading it while the save happens gets either the old recording or the new one,
never a broken reference.

**Why this priority**: The same argument Phase 11 made for text — content only a developer can fix
is the problem this whole pipeline exists to remove — applied to audio. It is P3 only because it
reuses the lifecycle Phase 11 already built.

**Independent Test**: Publish a fully recorded matn; replace one verse's audio; confirm the matn
stays published, the new recording is what an anonymous reader receives, and every other verse's
audio reference is unchanged.

**Acceptance Scenarios**:

1. **Given** a published matn, **When** the teacher replaces one verse's recording and saves,
   **Then** the matn stays published and only that verse's audio changes.
2. **Given** a published matn with complete audio, **When** the teacher removes a verse's recording
   and saves, **Then** the save is refused, because a published matn may not become incomplete.
3. **Given** a published matn, **When** the teacher re-splits a portion of it from a new continuous
   recording, **Then** only the verses in that portion change and the rest keep their existing
   files.
4. **Given** a published matn being corrected, **When** an anonymous reader fetches it mid-save,
   **Then** every audio reference it receives resolves to a file that exists.
5. **Given** a matn changed on another machine since it was loaded, **When** the teacher saves audio
   changes, **Then** the conflict is reported and the other change is not overwritten.

---

### Edge Cases

- Verses are reordered, inserted, or deleted after audio is attached: each recording must stay with
  the verse it belongs to, not with a position, and deleting a verse must release its recording.
- A verse's text is edited after its recording is attached: the recording stays attached, since the
  tool cannot know whether the edit was a typo fix or a re-wording — but the change is visible to
  the teacher.
- The continuous recording is replaced after markers have been placed: the existing markers must not
  be silently applied to a different recording of a different length.
- A marker range is a few milliseconds long, or its end precedes its start: refused with the verse
  named, rather than producing an unplayable file.
- The continuous recording is shorter than the last verse's end value: reported against the
  recording's actual length.
- Ranges are placed for only part of the split's chosen scope and the teacher saves: the unranged
  verses are named and the split is refused, rather than silently splitting a subset. Narrowing the
  scope to the verses actually covered is the teacher's way out.
- The split's scope is changed after ranges are placed: ranges for verses dropped from the scope
  are discarded, and verses newly added to it start with no range.
- Verses are reordered so a split's scope is no longer contiguous: the scope is re-derived from the
  verses it named, not from their positions.
- The tool is closed mid-split, or the machine loses power: the matn is left either fully split or
  unchanged; no verse points at a file that was never uploaded.
- Upload of 500 per-verse files: progress is reportable and interruptible, and an interruption is
  resumable without re-uploading what already landed.
- A split is abandoned rather than resumed, leaving files in storage that the matn never
  referenced: a later split with different ranges must overwrite them rather than treating them as
  finished work, and they otherwise linger unreferenced.
- Remaining storage is exhausted partway through a batch: reported as a storage problem naming the
  limit, with the matn left consistent.
- Two verses are given ranges producing byte-identical audio: allowed as audio, but two verses
  sharing one stored *file* is not — each verse gets its own file.
- A recording whose format is accepted but which is silent or corrupt beyond decoding: rejected with
  a message distinguishing "cannot read this file" from "wrong format".
- Recordings arriving from different sources with different sample rates or channel counts: the
  mismatched file is rejected against the matn's established profile rather than being converted,
  and the message names both profiles so the teacher can re-export it correctly.
- Every verse's audio is removed and the teacher then attaches audio in a different profile: the
  matn has no established profile once it holds no audio, so the new profile becomes the matn's.
- Preview is started while uploads are still in flight: preview plays what is stored, and says which
  verses are not stored yet.
- The waveform under a right-to-left interface: time still runs in the direction the teacher expects
  for a timeline, and the choice is consistent between the waveform and the numeric fields.
- A matn with all verses' audio removed at once: it returns to no-audio, and if published the save
  is refused.
- A deletion of an unreferenced file fails after its write committed: the file lingers, counting
  against storage, while the matn itself stays correct — reported in diagnostics, not to the
  teacher as a failure.
- A published matn is unpublished and republished after audio changes: identifiers of the matn, its
  verses, and their audio must survive, so nothing that referenced them is orphaned.

## Requirements *(mandatory)*

### Functional Requirements

#### Accepted audio & the stored artifact

- **FR-001**: The system MUST store exactly one audio file per verse. It MUST NOT store, upload, or
  reference a shared or continuous audio file as the matn's audio, and MUST NOT persist any timing
  offset against such a file.
- **FR-002**: Each verse's audio MUST carry a stable identifier, a reference to its own file, and
  the file's measured duration. The duration MUST be measured from the stored file rather than
  entered by the teacher.
- **FR-003**: The system MUST accept the format the teacher's recordings are produced in — MP3 —
  for both a per-verse recording and a continuous one, and MUST reject anything it cannot accept
  with a message naming what is accepted.
- **FR-004**: The system MUST reject a per-verse recording larger than 10 MB, and a continuous
  recording larger than 300 MB or longer than 4 hours, and MUST state the exceeded limit and the
  supplied file's own size or duration in the rejection.
- **FR-004a**: These limits MUST be checked before the file is uploaded, so an oversized file is
  refused immediately rather than after a long transfer.
- **FR-004b**: The storage backend's own size and content-type limits MUST be widened to admit
  audio and MUST remain at least as permissive as FR-004's ceilings, so that a file the tool
  accepts is never rejected by storage. They remain the server-side backstop against a client that
  skips the check.
- **FR-005**: Every stored per-verse file within a matn MUST share one encoding profile — the same
  sample rate and channel layout — so verse-to-verse playback cannot click or shift pitch. This
  MUST be achieved by constraining what is accepted, not by re-encoding: the stored bytes are the
  teacher's bytes.
- **FR-005a**: The system MUST NOT decode and re-compress supplied audio. A split MUST cut the
  source at its own frame boundaries, and a per-verse upload MUST be stored as supplied.
- **FR-005b**: The first audio accepted into a matn MUST establish that matn's encoding profile.
  Any later audio whose profile differs MUST be rejected with a message naming the expected and
  the supplied profile, leaving the verse's existing audio untouched.
- **FR-006**: A verse's audio file MUST be addressed under its own matn and verse identity, so that
  no two verses can ever resolve to the same stored file.
- **FR-007**: The matn's declared content size MUST include its audio once audio exists, so a later
  phase can state a download size before downloading.

#### Per-verse upload

- **FR-008**: Teachers MUST be able to attach a recording to any individual verse, see its duration
  once stored, and play it back from the verse's row.
- **FR-009**: Teachers MUST be able to replace a verse's recording, which discards the previous one
  rather than accumulating takes.
- **FR-010**: Teachers MUST be able to remove a verse's recording, returning that verse to having no
  audio.
- **FR-011**: Each in-flight upload MUST report its own progress, MUST NOT block editing of any
  other part of the matn, and MUST leave the verse's prior audio intact if it fails.
- **FR-012**: A failed or interrupted upload MUST NOT leave a verse referencing a file that does not
  exist or is incomplete.

#### Splitting a continuous recording

- **FR-013**: Teachers MUST be able to supply one continuous recording and see it rendered as a
  waveform with its total duration.
- **FR-013a**: Teachers MUST be able to choose which contiguous run of verses the recording covers,
  defaulting to the whole matn. Every verse in the chosen scope MUST receive a range; verses
  outside it MUST keep their existing audio unchanged and MUST NOT be validated against this
  recording.
- **FR-014**: Teachers MUST be able to assign each verse a start and end position within that
  recording, both by placing and dragging markers on the waveform and by editing the values
  numerically, with the two views always in agreement.
- **FR-015**: The system MUST continuously validate the assigned ranges and MUST report, naming the
  verses concerned: ranges that overlap, verses in scope with no range, ranges whose end is at or
  before their start, ranges shorter than a minimum usable length, ranges extending beyond the
  recording, and stretches of the recording assigned to no verse.
- **FR-016**: Overlaps, missing ranges, inverted or too-short ranges, and out-of-bounds ranges MUST
  block the split. An unassigned stretch of recording MUST be reported as a warning, not a block,
  since deliberate silence between verses is normal.
- **FR-017**: On save, the system MUST cut the continuous recording into one file per verse
  according to the assigned ranges and upload those files as that matn's verse audio.
- **FR-018**: The continuous recording, the marker positions, and the numeric ranges MUST be
  transient authoring state only. They MUST NOT be persisted in the matn record, uploaded as matn
  content, or made available to any student client.
- **FR-019**: A split MUST leave the matn either fully updated for every verse in its scope or
  entirely unchanged; a partially applied split MUST NOT be persisted. The per-verse files MUST all
  be uploaded before the matn is written, and the matn MUST be written once.
- **FR-019a**: An interrupted split MUST be resumable by re-running it, and the re-run MUST skip
  any expected per-verse file already present in storage rather than uploading it again. Resume
  MUST work after the tool has been closed and reopened.
- **FR-019b**: Resume state MUST be derived — from the matn's own audio references for per-verse
  attaching, and from the presence of the expected files in storage for a split. The system MUST
  NOT keep a separate record of upload progress.
- **FR-019c**: Skipping an already-present file MUST apply only when the split being run is the
  same one that was interrupted — the same source recording with the same ranges. If either has
  changed, every affected verse's file MUST be re-uploaded and overwritten, so a leftover file from
  an abandoned split can never be mistaken for the current one.
- **FR-020**: Replacing the continuous recording MUST NOT silently reuse marker positions placed
  against the previous one; the teacher MUST be told the ranges no longer apply.
- **FR-021**: Audio produced by splitting MUST be indistinguishable, once stored, from audio
  attached per verse — playable, replaceable, and removable in exactly the same way.

#### Preview playback

- **FR-022**: Teachers MUST be able to play a matn's stored audio in verse order from the authoring
  tool, starting at any verse, with pause, resume, and stop.
- **FR-023**: Preview MUST advance from verse to verse with no audible gap, matching how a student
  hears it.
- **FR-024**: Preview MUST indicate which verse is currently sounding.
- **FR-025**: When preview reaches a verse with no stored audio, it MUST identify that verse rather
  than stopping or falling silent without explanation.
- **FR-026**: Preview MUST stop cleanly when the matn's content changes underneath it.

#### Audio completeness, validation & publishing

- **FR-027**: The matn's audio-completeness state MUST continue to be derived from how many verses
  have audio — never set by hand — and MUST update as audio is attached and removed.
- **FR-028**: The audio-integrity rules deferred in Phase 11 — a verse with no audio, and two verses
  sharing an audio reference — MUST now be blocking problems that prevent publication.
- **FR-029**: Validation MUST continue to report every problem in one pass, each naming the specific
  verse or chapter it concerns, and MUST continue to distinguish blocking problems from warnings.
- **FR-030**: A published matn MUST NOT be savable in a state where any verse lacks audio, so a
  published matn can never become incomplete through an edit.
- **FR-031**: The matn list MUST show each matn's recording progress — none, partial with how many
  verses remain, or complete.

#### Lifecycle & correctness

- **FR-032**: A verse's audio MUST be bound to that verse's identity, not its position, so
  reordering verses never reassigns recordings.
- **FR-033**: Deleting a verse MUST release its stored audio; no orphaned file may remain
  referenced by the matn.
- **FR-033a**: A stored file that becomes unreferenced — by replacement, removal, or verse deletion
  — MUST be deleted only after the matn write that unreferenced it has succeeded. The system MUST
  NOT delete a file before the write, so a failed write always leaves the matn referencing a file
  that still exists.
- **FR-033b**: A deletion that fails or is interrupted after its write committed MUST NOT fail the
  operation or block the teacher; the unreferenced file is a storage cost, not a correctness
  problem, and MUST NOT be reported as an error the teacher must act on.
- **FR-034**: Teachers MUST be able to change audio on an already-published matn, which stays
  published, with the correction becoming what readers receive on the next read.
- **FR-035**: Any save that changes audio MUST leave the stored matn either fully updated or
  unchanged, and every audio reference a concurrent reader receives MUST resolve to a file that
  exists.
- **FR-036**: A matn unpublished and republished after audio changes MUST keep its matn, verse, and
  audio identifiers unchanged.
- **FR-037**: The system MUST continue to detect that a matn was changed elsewhere since it was
  loaded and report the conflict rather than overwriting it.
- **FR-038**: Readers without an account MUST be able to read a published matn's audio files and
  MUST NOT be able to read a draft's, and MUST NOT be able to write, replace, or delete any audio
  file by any request. These rules MUST be covered by automated tests exercising both the permitted
  and the refused cases.

#### Interface

- **FR-039**: Every surface added by this phase MUST use the shared design tokens, MUST be fully
  available in both Arabic and English with no untranslated text, and MUST mirror with the selected
  interface language, consistent with Phase 11.
- **FR-040**: Audio, upload, storage, and decoding failures MUST be reported as plain-language
  messages stating what failed and whether retrying is worthwhile — never a raw technical error.
- **FR-041**: For a matn of at least 500 verses, scrolling the verse list while uploads are in
  flight, and dragging a marker on a loaded waveform, MUST each complete without any single frame
  exceeding 32 milliseconds.

#### Boundaries

- **FR-042**: This phase MUST NOT change the behavior, content, or appearance of any student client.
- **FR-043**: This phase MUST NOT change the matn record's identity, publication lifecycle, or
  access model established in Phase 11, beyond filling in its audio fields.

### Key Entities

- **Verse audio**: One stored recording belonging to exactly one verse. Carries a stable
  identifier, a reference to its own file, and a measured duration. This is the only audio a matn
  persists and the only audio a student ever receives.
- **Continuous source recording**: One long recording covering a matn or a contiguous run of its
  verses, supplied by the teacher as authoring input. Exists only for the duration of a splitting
  session; never part of the matn.
- **Verse range**: A start and end position within the continuous recording, assigned to one verse.
  Transient working material, consumed by the split and then discarded.
- **Split plan**: One continuous recording, the run of verses it covers, that run's verse ranges,
  and the problems found in them. Valid only while its recording is loaded.
- **Upload batch**: The set of per-verse files produced by one split or one multi-verse attach,
  tracked in memory so it can report progress and be interrupted. It is not persisted — a resumed
  run rebuilds it by comparing what the matn and storage already hold against what the operation
  needs (FR-019b).
- **Audio completeness**: The derived state of a matn — no verse has audio, some do, all do —
  recomputed from the verses themselves.
- **Validation report**: Unchanged in shape from Phase 11, but the audio rules move from the
  outstanding-work list to the blocking list.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A teacher can take a text-complete 20-verse matn and a single continuous recording of
  it, and reach a published, fully recorded matn in under 25 minutes with no developer assistance.
- **SC-002**: Splitting a continuous recording of a 100-verse matn — placing and adjusting every
  boundary — takes under 30 minutes, versus cutting 100 files by hand outside the tool.
- **SC-003**: 100% of the stored matns produced by either path consist of exactly one audio file per
  verse, with no matn anywhere in the catalog referencing a shared or continuous file and no timing
  offset persisted — verified across the full published catalog.
- **SC-004**: Every per-verse file produced by splitting covers its assigned range within 50
  milliseconds at each boundary, verified by measuring the stored files' durations against the
  ranges that produced them.
- **SC-004a**: No stored verse file is a re-compression of what the teacher supplied — verified by
  confirming that a split file's audio data is byte-identical to the corresponding stretch of the
  source, and that a per-verse upload's stored bytes match the file chosen.
- **SC-005**: 100% of range problems — overlaps, missing ranges, inverted ranges, too-short ranges,
  out-of-bounds ranges — are reported before the split runs, verified by one deliberately defective
  plan per rule.
- **SC-006**: No matn can be published with a verse missing audio or with two verses sharing a
  recording — 100% of such attempts refused, verified by one draft per rule.
- **SC-007**: Every published matn's audio-completeness state and declared size match its actual
  stored contents 100% of the time.
- **SC-008**: Preview plays a fully recorded matn end to end with no audible gap at any verse
  boundary, and the indicated verse matches the sounding verse throughout.
- **SC-009**: No interruption — network loss, cancellation, quitting mid-split, exhausted storage —
  leaves a matn referencing a file that does not exist: 100% of interrupted operations leave the
  matn either fully updated or unchanged.
- **SC-010**: An interrupted batch upload of 500 verse files resumes without re-uploading any file
  that already landed — including after the tool has been closed and reopened, verified by killing
  the tool mid-batch, restarting it, and confirming the re-run uploads only the remainder.
- **SC-011**: Correcting a single verse's recording in a published matn takes under 3 minutes and
  changes no other verse's stored audio.
- **SC-012**: A draft's audio files are unreadable without an account and every unauthenticated
  write attempt against any audio file is refused — 100% of attempted access violations blocked in
  the access-rule test suite.
- **SC-013**: Scrolling a 500-verse list during uploads and dragging a waveform marker each produce
  no frame longer than 32 milliseconds.
- **SC-014**: Every screen added by this phase is complete and usable in both interface languages —
  zero untranslated labels, zero clipped text, zero controls left on the wrong side after mirroring.
- **SC-015**: Student clients behave identically before and after this phase — verified by the
  existing student test suite passing unchanged.

## Assumptions

- **Audio is supplied as MP3 files, not recorded in the tool** (confirmed 2026-07-31). The teacher
  records with their own equipment; the tool ingests, cuts, stores, and previews. Live microphone
  capture inside the authoring tool is not part of this phase — "capture" here means capturing
  recordings into the catalog.
- **Publishing requires complete audio** (confirmed 2026-07-31). Phase 11 stated the audio rules
  become blocking once audio authoring exists (its FR-027), so a partially recorded matn stays a
  draft. The partial completeness state remains meaningful for the teacher's own progress view and
  as a defence for Phase 13, but it is not a publishable state.
- **Audio is stored as supplied, never re-encoded** (confirmed 2026-07-31). Uniformity within a
  matn is enforced by rejecting a mismatched profile, not by converting it. The phase therefore
  needs a decoder and no encoder.
- **Splitting happens in the authoring tool, on the teacher's machine.** There is no server-side
  processing component, consistent with the project having no backend of its own beyond the hosted
  data and storage services.
- **The continuous recording is never uploaded as matn content.** Whether it is held only in memory
  or in a scratch location on the teacher's machine during the session is an implementation
  concern; what matters is that it is not part of the matn and not reachable by a student.
- **Audio follows verse identity.** Reordering verses moves recordings with them; deleting a verse
  deletes its recording. The teacher is never asked to re-associate audio after an edit.
- **Editing a verse's text does not invalidate its recording.** The tool cannot tell a typo fix from
  a re-wording, so it keeps the audio and leaves the judgement to the teacher.
- **Re-splitting replaces audio for the verses in the split's scope only** (confirmed 2026-07-31).
  Verses outside that scope keep their existing files, so a partial re-record is possible without
  redoing the matn. Scope is a contiguous run of verses, defaulting to all of them; splitting
  several disjoint stretches means running the split once per stretch.
- **Storage layout follows the path scheme already established** — audio lives under the matn's own
  prefix, per verse, alongside its cover image. No second bucket, no second provider. The bucket's
  current 5 MB / image-only restriction was set when covers were the only stored files and is
  widened by this phase (FR-004b), not worked around.
- **Unreferenced files are deleted opportunistically, not swept** (confirmed 2026-07-31). There is
  no background reconciliation pass in this phase; a file stranded by an interrupted deletion stays
  until someone removes it. Building a sweep is deferred rather than forgotten.
- **Declared size grows to cover audio**, closing the Phase 11 assumption that it counted text only.
- **The waveform is an authoring aid, not a design deliverable in its own right.** Its layout is
  adapted from the captured timestamp-map screen, whose *architecture* — verses as ranges within one
  shipped file — remains rejected. Its Arabic, mirrored form is original work, as in Phase 11.
- **A decoder for the teacher's recording formats is a new dependency** and requires justification
  against a simpler alternative under the constitution's dependency clause, since the desktop
  platform's built-in audio support does not cover the formats teachers record in. This is a known,
  pre-recorded obligation, not a discovery.
- **The teacher is online while authoring**, as in Phase 11. There is no offline queue; failures are
  reported and retried.

## Out of Scope

- All student-app changes: remote catalog sync, per-matn download of text and audio, the
  first-launch empty/offline state, and removal of the bundled/asset-pack delivery stack
  (**Phase 13**).
- Live microphone recording inside the authoring tool.
- Audio editing beyond cutting at verse boundaries: noise reduction, normalization to a loudness
  target, trimming silence, fades, re-takes assembled from multiple sources, or any effect.
- Automatic verse-boundary detection — silence detection, speech alignment, or transcript matching.
  Boundaries are placed by the teacher.
- Multiple reciters per matn or per verse, and any reciter selection interface. The single implicit
  reciter from Phase 11 is unchanged.
- Version history or rollback of a replaced recording.
- Streaming preview of unsaved audio, and preview of a matn's audio from any client other than the
  authoring tool.
- Bulk operations across multiple matns.
- Any interface language beyond Arabic and English.

## Dependencies

- **Phase 11** — the authoring client, the sign-in and session model, the matn record and its
  identifiers, the publication lifecycle, the validation pipeline with its deferred audio rules, and
  the shared backend client. Complete.
- **Phase 1** — the per-verse audio asset model and the stable-identifier domain model these files
  attach to. Complete.
- **Phase 10** — the shared design tokens every new surface consumes. Complete.
- **A decoder** for the recording formats the teacher supplies, needed for waveform rendering,
  duration measurement, and preview. Cutting needs only frame-boundary parsing, not decoding, and
  no encoder is needed at all (FR-005a). New dependency; requires justification in the same change.
- **Blocks Phase 13**, which downloads exactly the per-verse files this phase produces and relies on
  the audio-completeness state to decide what a student may see.
