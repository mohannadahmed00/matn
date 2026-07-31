# Design Notes: Phase 12 — Audio Capture & Slicing (fetched via Stitch MCP, 2026-07-31)

## T039 — Upload Matn (Per-Verse), audio column (`4f1bee3d7518487b986c7c63cb3c07ff`)

Fetched via the `stitch` MCP server's `get_screen` against project `5201142409061412050`
(`docs/DESIGN-SOURCE.md`), the same screen Phase 11 fetched for its text/reorder regions
(`specs/011-teacher-authoring-upload/design-notes.md`) — Phase 11 deliberately left the audio
column unimplemented; this phase implements it.

### Audio column — layout and states

Per verse row, the audio slot sits **after the Arabic text field, before the delete button** (row
order: drag handle → numbered index → Arabic text → **audio slot** → delete):

- **Empty**: an "Upload Audio" prompt (capture icon: `upload_file`).
- **Loaded**: a play control (capture icon: `play_circle`), the duration ("0:12"), and a close/
  remove control (capture icon: `close`) that doubles as "replace" (attaching a new file over an
  existing one).
- **Uploading** and **Failed** are not distinct states in the capture (a static mock has no
  in-flight upload to show) — `contracts/teacher-ui-contract.md` §1's four states
  (Empty/Uploading/Loaded/Failed) are this phase's addition on top of the two the capture shows,
  following the same determinate-progress and inline-error pattern Phase 11 already established for
  cover upload.

### Deviation carried from Phase 11 (Deviation 3 restated)

No Material Icons dependency in `:teacherApp` (Ground Rule 3 — fixed dependency set). Every icon
above (`upload_file`, `play_circle`, `close`) renders as its equivalent plain-text label or a
Unicode glyph, exactly as Phase 11 recorded for the drag handle and delete action.

## T063 — Upload Matn (Timestamp Map) (`f55899af78974175b345f3c3cd048387`)

Fetched from the same project. Per `docs/DESIGN-SOURCE.md` Open issue #2 and `plan.md`/
`research.md` D11: the screen's **architecture** (verses as ranges within one persisted shared
file) stays rejected — the constitution's per-verse audio lock forbids it. Only the **layout** is
adopted, as an authoring-time splitter whose output converges on the same per-verse-file artifact
as the per-verse attach path.

### Layout regions

- **Portal chrome**: the same header/sidebar/identity shell as every other producer screen
  (`PortalShell`, unchanged from Phase 11).
- **Audio player / waveform region**: playback transport (play, ±10 s seek), a current-time /
  total-duration readout, and a waveform graph with an explicit instruction to click the graph to
  add a new boundary marker.
- **Verse boundary markers on the waveform**: labeled segments directly on the waveform (e.g.
  "Verse 1", "Verse 2", "Verse 3"), each with its own delete control.
- **Per-verse range fields**: a repeated block per verse — verse number, then a "start (ms)" and
  "end (ms)" numeric input pair. This is the field-based alternative path
  `contracts/teacher-ui-contract.md` §2 requires alongside the waveform (FR-014) — the capture
  already shows fields and markers as two views of the same data, which is exactly the two-way
  binding the contract calls for.
- **Problem list**: a distinct "Review Alerts" panel naming concrete problems (an overlap between
  two verses given as a negative millisecond delta; an uncovered gap between two verses given in
  seconds) — this maps directly onto `SplitPlanValidator`'s blocking/warning rows (`split-contract.md`
  §2): the overlap is R5 (blocking), the uncovered gap is R7 (warning).
- **Actions**: a primary "save and verify boundaries" action and a "cancel/discard" action — mapped
  in this phase to "split and upload" (disabled while any blocking problem exists) and "cancel"
  (`contracts/teacher-ui-contract.md` §2).

### Two deviations recorded (Principle VIII)

1. **LTR waveform timeline in both interface languages** (research D11). The capture is Arabic/RTL
   throughout, including the waveform region, but this phase deliberately keeps the *waveform's own*
   timeline left-to-right even when the surrounding chrome mirrors for Arabic — audio timelines read
   left-to-right in every editor a teacher has used, and mirroring the waveform would also mirror the
   meaning of "forward." The numeric start/end fields and all other chrome mirror normally.
2. **No persisted timestamp map, no "generating" file object.** The capture's model implies one
   continuous file plus a saved map; this phase saves *slices*, not a map, and the source recording,
   the markers, and the ranges are never persisted anywhere (FR-018, `split-contract.md` §6). The
   capture's visual vocabulary (waveform, markers, range fields, problem list) is adopted; its
   persistence model is not.
3. **No Material Icons dependency** (same as Deviation 3 above) — the waveform's own play/seek
   glyphs render as text/Unicode, not vector icons.

## What this phase does NOT build from these captures

- The capture's "generating" implicit state for audio (mentioned in Phase 11's notes as a state the
  capture does not show) is not built here either — Empty/Uploading/Loaded/Failed cover every real
  state; there is no separate "generating" state in this design.
- Any UI for live microphone capture — both screens show *uploading a file*, matching the spec's
  ingest-only scope (Clarifications, spec.md).

## T095 — Post-implementation notes (2026-07-31)

Recorded after all five user stories landed, following the Phase 11 precedent of closing the
design-gap log with what was actually built vs. what was planned.

### The Stitch fetches (T039, T063) — confirmed, no surprises

Both captures matched the summaries `plan.md`/`research.md` anticipated. The per-verse audio
column's three states (empty/upload-prompt, loaded/play+duration, and an implicit "replace" via
the same close control) mapped directly onto this phase's four-state contract
(Empty/Uploading/Loaded/Failed) with Uploading/Failed added as this phase's own states, since a
static capture has nothing in flight to show. The Timestamp Map capture's waveform, markers, range
fields, and "Review Alerts" panel mapped cleanly onto `WaveformCanvas`/`MarkerLayer`/range fields/
`SplitPlanValidator`'s problem list — no unplanned layout deviation was needed beyond the two
already recorded (LTR timeline, no persisted map).

### The bit-reservoir artifact (`research.md` D2) — accepted as designed, not separately verified

`FrameAccurateSlicer` cuts at frame boundaries exactly as specified; the up-to-511-byte bit
reservoir carryover at a slice's first frame or two is an inherent property of frame-accurate
cutting without re-encoding (spec Q1's deliberate choice), not something this implementation could
avoid or that testing could meaningfully measure without a real perceptual-audio pipeline. No
teacher-reported audible click exists yet to revisit it against.

### Real deviations found only during implementation

1. **The synthesized-fixture limitation is real, not hypothetical.** `tiny.mp3`
   (`teacherApp/src/test/resources/tiny.mp3`) is `Mp3Fixtures`-style silent frames — valid 4-byte
   headers, zeroed bodies — because no MP3 encoder is available in this environment (no `ffmpeg`,
   no `lame`, no network access to fetch one). JLayer decodes it without error, which is what
   `JLayerAudioProbeTest` actually proves (decode-path correctness), but it is not a claim that a
   *musically meaningful* file was decoded. Revisit if a real encoder becomes available.
2. **`JvmFileByteSource` does not wrap each `read()` in its own `withContext(Dispatchers.IO)`.**
   The original implementation did; `Mp3FrameIndex.build` performs one read per frame (hundreds for
   a long recording), so that was a dispatcher hop per 4-byte read — wasteful, and it made
   `SplitViewModelTest` (which must run on `StandardTestDispatcher` per T070) hang indefinitely,
   because `Dispatchers.setMain(StandardTestDispatcher())` created in isolation carries a scheduler
   disconnected from the `runTest` block's own, so `advanceUntilIdle()` could never drain the
   resulting flood of cross-dispatcher resumptions. Fixed two ways, both real improvements: (a)
   `JvmFileByteSource.read()` is now a plain blocking call with no internal dispatcher switch; (b)
   `SplitViewModel` wraps the *whole* load (frame indexing + peak decode) and the *whole* split
   (slicing) in one `withContext(Dispatchers.Default)` each, matching `research.md` D7's "decode
   runs off the UI thread" intent at the granularity it was meant to apply — once per operation, not
   once per read. `SplitViewModelTest` also had to construct `Dispatchers.setMain` from the
   `runTest` block's own `testScheduler`, not a separately-instantiated one.
3. **`Mp3FrameIndex`'s exclusive end-of-range must not reuse `frameAtMs`'s clamp.** `frameAtMs`
   (used for a *start* boundary) clamps its result to `offsets.size - 1`, the last valid frame
   index. Reusing it for an *exclusive* end bound silently truncated the last frame from a range
   whose end fell exactly at the source's total duration. `FrameAccurateSlicer` now computes the end
   frame independently, clamped to `offsets.size` (one past the last index), not `offsets.size - 1`.
4. **T080's proof obligation held with one expected, harmless exception.** `ContentSeedLoader` tests
   and `ProgressRepositoryTest` passed with zero edits, confirming D12 — the flip is genuinely
   invisible to student ingestion. `ContentIntegrityValidatorTest` itself did need edits (three
   cases asserted the old deferred classification), but that is exactly T081's job in the same
   phase, not a violation of the "caller-supplied policy parameter" escape hatch — the escape hatch
   is for when the *student-facing* tests break, which they did not.

### What was not executed live

Same category as Phase 11's unexecuted-live items: no local Supabase stack (`supabase start`)
exists in this environment, so `RlsPolicyTest`'s new A1–A7 audio cases (T092) are written and
compile-verified but not run — `.github/workflows/rls-policy-tests.yml` is the actual enforcement
mechanism for FR-038, same as it was for Phase 11's R/W-series. No live Supabase project pass was
performed for the storage-limit migration (T003) or the audio upload/download/delete round trip;
`quickstart.md` §3's walkthrough is recorded as not independently re-verified against a real
project in this session (see T096 note in `quickstart.md` history / PR description).

### Live-pass findings (2026-07-31, first real run of the split screen)

Three bugs surfaced the moment a teacher opened *Split from one recording* against the real
project — none of which the automated suite could have caught, because all three live in the
Swing/Compose edge between the file chooser and the ViewModel:

1. **The split source was rejected at the per-verse 10 MB ceiling instead of 300 MB.**
   `JvmFileChooser.pickAudio()` (T044) was written for the per-verse path and hard-coded
   `MAX_PER_VERSE_AUDIO_BYTES`; T066 then reused it verbatim for the split source, whose ceiling is
   300 MB (FR-004). Any continuous recording big enough to be worth splitting — i.e. the normal
   case — was refused before reaching `LoadSplitSourceUseCase`, where the correct 300 MB check
   lives and was never reached. `pickAudio` now takes an explicit `maxBytes`, with both ceilings as
   named constants, so a call site cannot silently inherit the wrong one.
2. **That rejection was then swallowed silently.** `SplitScreen`'s `onPickSource` mapped
   `AudioPickResult.Rejected` and `Cancelled` to the same `Unit`, so a refused pick was
   indistinguishable from a dismissed dialog: the button appeared dead. `AudioPickResult` gained a
   distinct `TooLarge`, and `SplitViewModel.onSourceRejected` now drives `SplitUiState.Failed` with
   a named reason.
3. **The empty state rendered the *rejection* messages as its up-front hint.** `NoSourceContent`
   showed `sourceTooLarge` and `sourceTooLong` — both phrased as failures ("this recording is
   larger than…") — to state the limits per FR-004, so the screen opened looking like something had
   already gone wrong. Replaced with a purpose-written `splitSourceHint`; the two rejection strings
   now serve only their real errors, which are themselves now typed (`AudioAttachError.SourceTooLarge`
   /`SourceTooLong`) instead of raw English inside `AppError.Storage` — that raw-string path was also
   a latent Ground Rule 6 violation, since it bypassed `TeacherStrings` entirely.

One UX gap found in the same pass and fixed rather than deferred: `SplitUiState.Loading`/`Splitting`
carry a `progress: Float` that nothing ever updates, and the screen rendered it with a *determinate*
`CircularProgressIndicator`. For a long recording that is a frozen empty ring for many seconds —
visually identical to a hung screen. Both states now use an indeterminate indicator plus a label
naming what is happening. Determinate rendering should return only when frame indexing and peak
decoding actually emit incremental progress.

**Regression coverage added**: `LoadSplitSourceUseCaseTest` (new) locks the ceiling asymmetry in
place — a 120 MB source is accepted, 301 MB is `SourceTooLarge`, over 4 hours is `SourceTooLong` —
plus profile-mismatch and unreadable-content rejection. The chooser half stays untestable (it opens
a Swing dialog), which is exactly why the ceiling now has to be passed in explicitly.

### Second live-pass findings (2026-07-31, split screen with real ranges)

1. **The action bar was squeezed to nothing.** `ReadyContent` put its per-verse `LazyColumn` in a
   `Column` with no `weight(1f)`, so the list claimed all remaining height and crushed the
   replace/split buttons under it; the cancel row below was pushed off-screen entirely. The screen
   now has an explicit three-band layout — pinned heading, a `weight(1f)` content region that the
   verse list scrolls inside, and a pinned action bar — and the two previously separate button rows
   (cancel in `SplitContent`, replace/split in `ReadyContent`) are consolidated into that one bar.
   The problem list also moved out of the scrolling list to sit directly above the actions: as an
   `item` at the end of a 100-verse `LazyColumn` it was unreachable without scrolling past every
   verse, which defeats the point of live validation.

2. **No way to hear a range before committing to it** — the gap that made the whole marker workflow
   guesswork. Added per-row audition (`SplitViewModel.onAuditionRange`), which cuts the range with
   the **same `AudioSlicer` the split itself uses** and plays those exact bytes through a new
   `PreviewPlayer.playClip(bytes, displayNumber)`. Deliberately not "seek the source file and play
   from startMs to endMs": that would preview something subtly different from what gets stored.
   Slicing for real means the teacher hears the frame-snapped result including the bit-reservoir
   artifact at the head (`split-contract.md` §4) — i.e. the thing they would otherwise only discover
   after uploading. `playClip` reuses `JvmPreviewPlayer`'s existing decode-and-write path; the only
   thing it skips is `PreviewCache`, since nothing is stored yet.

Covered by three new `SplitViewModelTest` cases: audition slices *only* the range under audition
(not the whole plan) and plays the sliced bytes; pressing it again stops rather than restarting; a
verse with no range yet does nothing. The shared `awaitUntil` helper generalises the earlier
`awaitReady` workaround — every ViewModel path that hops to `Dispatchers.Default` needs it, because
`advanceUntilIdle()` does not own that thread.

### Correction to the previous entry — the pinned problem list must be bounded

Moving `ProblemList` out of the scrolling verse list (previous entry, item 1) fixed reachability but
introduced a worse failure: as a *pinned, unbounded* `Column` in a `Column`, it measured to whatever
its content needed, so `weight(1f)` gave the content region what was left — nothing. A freshly
loaded matn produces one `MissingRange` per verse (109 for al-Jazariyyah), so the screen was
entirely red problem rows with no waveform, no verse list, and no action bar.

A pinned band is only safe if its height cannot grow with content. `ProblemList` now shows a count
header, at most `MAX_VISIBLE_PROBLEMS` (4) rows — blocking first, since those are what stop the
split — and a "+N more" line. `SplitReadyManyProblemsPreview` renders exactly this 109-verse
just-loaded state so the bound stays visible in review.

General lesson, worth stating because it bit twice in the same screen: in a `Column` with a
`weight(1f)` child, **every other child must have content-independent height**. Both bugs here were
the same mistake from opposite directions — first an unbounded `LazyColumn` with no weight, then an
unbounded pinned list next to a weighted sibling.

### Third live-pass findings — the split row, and what counts as an "error"

1. **Verse text was missing from the split rows.** Each row showed only a display number, so the
   teacher marking boundaries by ear had no way to know what verse 7 actually *says* — which is the
   one thing you need when the whole task is matching audio to words. Rows now render
   `verse.arabicText` (RTL regardless of interface language, per FR-021), ellipsized to two lines.

2. **`MissingRange` was styled as an error, so the screen opened shouting.** A freshly loaded matn
   has no ranges at all, so every verse was a red "has no range yet" line before the teacher had
   done anything wrong. It reports *work remaining*, not a mistake. The band now leads with a
   neutral progress count ("12 of 109 verses ranged") and styles only genuine mistakes — overlaps,
   inverted, too short, out of bounds — as errors. **The validator is unchanged**: `MissingRange` is
   still blocking and still disables *Split and upload* (`split-contract.md` §2 R1); only its
   presentation changed. Progress-vs-error is a UI distinction, not a rule change.

3. **Stop was not discoverable, and did not actually stop.** The row control toggled glyph-only
   (▶ → ⏹), which reads as decoration; it now renders an explicit "◼ Stop". Behind it was a real
   bug: `JvmPreviewPlayer`'s `finally` always called `SourceDataLine.drain()`, which **blocks until
   every buffered frame has played** — correct when a clip ends naturally, exactly wrong on an
   explicit stop, where the teacher keeps hearing audio after pressing it. Stop now `flush()`es
   instead. A second latent race was fixed alongside: a torn-down job's `finally` could stamp `Idle`
   over the clip that had just replaced it, so playback jobs now carry a generation token and only
   publish their terminal state if still current.

4. **The unlabelled "◎" button was a puzzle.** Its only job was highlighting the verse's range on
   the waveform. Removed; selection is now implicit on clicking the row, with the selected row
   tinted. An icon whose meaning cannot be guessed is worse than no icon.

### Fourth live-pass findings — playback ownership, and drag-to-place boundaries

1. **Play/stop desynced from the audio, and repeated presses stacked overlapping playbacks.** One
   root cause with two faces. `playClip`/`play` launched into a private scope and returned
   immediately, so no caller could know when playback ended; the ViewModels compensated by clearing
   their "now playing" state on any `PreviewState.Idle`. But starting a playback *stops the previous
   one first*, which emits `Idle` — so the state was wiped the instant playback began. The control
   reverted to "play" while audio ran, and since the state claimed nothing was playing, the next
   press started a **second** clip instead of stopping the first. `JvmPreviewPlayer` made that worse
   with a single shared `@Volatile stopped` flag: it was set true then immediately back to false for
   the new attempt, and the old job — blocked inside `SourceDataLine.write()` — routinely missed the
   brief `true` window and never exited.

   Fixed by changing who owns what, not by patching the symptom:
   - `play`/`playClip` now **suspend for the duration of playback**, documented on the interface as
     part of the contract. A caller that cannot await the end cannot keep its state honest.
   - Each attempt owns a `Playback` token carrying its own `cancelled` flag and audio line. A token
     the old job owns can never be un-cancelled by a newer one, which removes the race entirely.
   - `stop()` flushes the line from the caller's thread, silencing it immediately and unblocking an
     in-flight `write()` so the loop reaches its next cancellation check promptly.
   - The terminal `Idle` is published only by the attempt still registered as current.
   - Both ViewModels now clear playing state in the `finally` of the coroutine that awaited
     playback, guarded so a job cancelled by its successor cannot wipe the newer one's state. The
     `Idle` collector in `EditorViewModel` survives only to mirror `previewState` for the transport
     bar.

   `SplitViewModelTest`'s fake was itself part of the problem: it returned immediately from
   `playClip`, so every "is it still playing?" assertion passed vacuously. It now gates on a
   `CompletableDeferred` and exposes `liveCount`, which is what actually proves two clips never
   overlap. Two regression tests cover the reported behaviour directly.

2. **Boundaries are now placed by dragging the waveform** (FR-014's pointer path, which previously
   only nudged *existing* boundaries and so was unusable for setting one from scratch). Flow: focus
   a verse's Start or End field to arm it, drag anywhere on the waveform, release. While dragging,
   a tertiary-coloured playhead tracks the pointer, a floating `m:ss.mmm` label follows it, and a
   hint line names which verse and which boundary the drag is writing. A drag with nothing armed is
   deliberately inert — a stray drag must not silently rewrite whichever boundary was last touched.
   A first drag on a verse with no range seeds a 2 s range around the position, clearing R3's 300 ms
   minimum without further editing. Typing into the fields still works and writes the same range;
   the two paths are the accessible alternative to each other (`teacher-ui-contract.md` §6).
   Editing a range that is currently being auditioned stops playback, so a boundary is never judged
   against a stale clip. `MarkerLayer` was rewritten for this and no longer re-decodes during the
   gesture (FR-041 unchanged) — it only maps x → milliseconds against already-loaded peaks.

### Fifth live-pass findings — the drag layer never received events, and the missing playhead

1. **Dragging did nothing because `MarkerLayer` measured zero width.** It applied only
   `.height(...)`, so `BoxWithConstraints` wrapped its content — and its sole child used
   `matchParentSize()`, which by definition contributes nothing to the parent's size. `maxWidth`
   therefore measured 0, the `widthPx > 0` guard rejected every gesture, and the whole layer was
   inert while looking correct in code and in review. Fixed with `fillMaxWidth()`, and the guard
   kept: it is right to refuse gestures on a zero-width timeline, it was the sizing that was wrong.

   The gesture handling was rebuilt at the same time. It previously stacked `detectTapGestures`
   (with `onPress`/`tryAwaitRelease`) *and* `detectDragGestures` on the same box; both detectors see
   the same events, so a single drag reported its end twice. One `awaitPointerEventScope` loop now
   handles press → move → release, which also makes a plain press place the boundary without
   waiting for touch slop.

2. **No playhead during playback.** Added `SplitUiState.Ready.playheadMs`, fed by a state collector
   that maps the player's **clip-relative** position onto the **source** timeline by adding the
   range's `startMs`. Without that offset the marker would snap to the beginning of the recording
   whenever an audition started, regardless of which verse was sounding — a test covers exactly that
   mapping. The collector is scoped to the playhead alone and deliberately does not touch
   `auditioningVerseId`; re-introducing that coupling is the desync bug from the previous entry.

   Rendering distinguishes the two markers because they answer different questions: the playhead
   (tertiary, thin) is where the audio *is*, the scrub marker (primary, thick, drawn last so it is
   never hidden) is where the teacher is *pointing*. The floating `m:ss.mmm` label and the hint line
   both prefer the scrub position while dragging — mid-drag the number that matters is the one about
   to be committed, not the playback position.

### Marker persistence — the readout must outlive the gesture

The on-waveform marker and its timestamp were driven by `scrubMs` alone, which is non-null only
*during* a drag. Releasing the pointer therefore erased the one on-waveform indication of where the
boundary being edited actually sits, leaving the teacher to read it back out of the numeric field.

Added `SplitUiState.Ready.armedBoundaryMs`, derived (not stored — it is exactly "look up the armed
boundary in `ranges`", and a stored copy would be another thing to keep in step with every edit).
The marker now renders at `scrubMs ?: armedBoundaryMs`: the pointer's position while dragging, the
committed value once released, and nothing at all when no field holds focus. The label falls back to
the playhead when nothing is armed, so playback position stays readable either way.

Precedence, applied identically to the floating label and the hint line: **scrub → armed boundary →
playhead**. The value being placed always outranks where the audio happens to have reached.

### Two readouts, not one — the playhead needs its own timestamp

The floating label used a single slot (`markerMs ?: playheadMs`), so with a field focused *and* a
clip playing it showed only the armed boundary — hiding the playback position exactly when it is
most useful, i.e. while listening to decide whether the boundary is in the right place.

There are now two independent labels, positioned so they cannot collide as the playhead sweeps past
the marker:

| Readout | Colour | Edge |
|---------|--------|------|
| Playhead — where the audio *is*, sweeping start → end | tertiary | top |
| Armed boundary — where the focused field is pinned | primary | bottom |

Both flip to the left of their line near the right edge of the timeline, so a label never runs off
the end. The flip threshold uses an estimated label width (`MatnSpacing.unit * 8`) rather than
measuring the text: an exact placement pass is not worth it for a hint that moves every audio frame,
and the estimate only has to be good enough to keep the label on screen.

`LABEL_WIDTH` is a spacing-token multiple, not a bare `.dp` literal — Ground Rule 5 applies to
sizing constants in `:teacherApp`, not only to inline modifier values.

### A real transport — playhead separated from the boundary markers

The playhead existed only as a by-product of auditioning a verse: it appeared when a clip played,
vanished when it stopped, and there was no way to play the *recording itself* or to move to an
arbitrary position. That makes the natural workflow impossible — you cannot listen around to find
where a verse begins before assigning its boundary.

**Source playback.** `startSourcePlayback` plays the recording from the playhead in bounded
`SOURCE_CHUNK_MS` (60 s) slices, re-cut on demand. Slicing "playhead → end" in one go would be
precisely the whole-file-in-memory read `research.md` D8 forbids — the remainder of a 300 MB source.
The cost is a brief gap at each chunk boundary, since the player opens a line per clip. That is
acceptable for a seek-and-listen aid, and is explicitly *not* how the matn preview works: FR-023
requires gaplessness there, which is why it keeps one line open across verses (research D9).

**One playhead, always meaningful.** `playheadMs` is now a non-null transport position that advances
during playback (audition *or* source), stays where it stopped, and is what a free drag moves. The
clip-relative positions the player reports are mapped back onto the source timeline through a single
`playbackOriginMs` — the verse's start for an audition, the chunk's start for source playback — so
one mechanism serves both.

**Which marker a drag moves is decided once, at press.** Landing within `GRAB_TOLERANCE` of the
armed boundary drags that boundary; anywhere else drags the playhead. Deciding per-move would let a
drag hand off mid-gesture as it swept past the marker, so a seek could silently rewrite a verse
boundary — the one thing the teacher explicitly must be protected from. Seeking therefore never
touches the plan, and a test asserts every range is byte-identical across a seek performed *with a
boundary field focused*.

**`isSeeking` suppresses position updates from the player** while a drag is in progress. Without it,
audio still in the buffer would keep publishing positions and yank the marker back from under the
pointer.

### Transport state race, and unambiguous drag ownership

1. **`isPlayingSource` was cleared by the playback it had just replaced.** `startSourcePlayback`
   calls `stopSourcePlayback`, which cancels the running job — but that job's `finally` runs
   *asynchronously*, so it landed after the replacement had already set the flag true and stomped
   it. Symptom: dragging the playhead mid-playback left the button showing "play" while audio kept
   sounding, and every later press repeated the stomp, with no way back to a consistent state.
   This is the same generation race already fixed inside `JvmPreviewPlayer`, reappearing one layer
   up — cancellation being asynchronous means *any* "stop then start" pair needs a token, not just
   the one that has already bitten. `sourcePlaybackToken` now gates the terminal `setState`, so only
   the playback still registered as current may report that it ended.

2. **Drag ownership is now decided by one explicit precedence, fixed at press:**

   | Press lands | Moves |
   |-------------|-------|
   | On the playhead (within `GRAB_TOLERANCE`) | the playhead — and disarms any focused field |
   | Anywhere else, with a Start/End field focused | that boundary |
   | Anywhere else, nothing focused | the playhead |

   Resolved once, never reconsidered mid-gesture: re-deciding per move would let a drag hand off as
   it swept past another marker, so a seek could silently rewrite a verse boundary. `onSeekStart`
   fires before any position is reported and does three things together — stops playback, clears
   `activeBoundary`, and (via the caller) drops the text field's focus, so the focus ring cannot
   keep claiming a boundary the ViewModel has already disarmed.

   Dragging the playhead now **stops** playback rather than seeking underneath it. Audio continuing
   from the old position while the pointer moves elsewhere is disorienting, and keeping a live
   stream in step with a drag is exactly how the state drifts out of sync with what is sounding.
   Play resumes from wherever the playhead was dropped.

### The playhead drag killed itself, and needed a real target

**`pointerInput` was keyed on a value the gesture itself changes.** `playheadX` (and
`markerMs != null`) were passed as `pointerInput` keys, so the first reported position moved the
playhead, which changed the key, which tore the detector down and relaunched it — the drag died on
its own first pixel. Both are now read through `rememberUpdatedState` inside the running gesture,
with only `durationMs`/`widthPx` as keys. General rule worth remembering: **never key `pointerInput`
on state the gesture mutates.**

**A 3 px line is not a drag target.** Even once the gesture survived, grabbing the playhead meant
hitting within `GRAB_TOLERANCE` of a hairline — and with a boundary armed, the rest of the surface
belonged to that boundary, so the transport was effectively undraggable exactly when the teacher was
mid-edit. Added a tinted **seek lane** along the top of the waveform that always seeks regardless of
focus; the playhead's timestamp already sat there, so the label doubles as the visible handle.
Proximity grabbing on the line itself still works anywhere, as a shortcut.

Resulting precedence at press: **seek lane or playhead → seek (and disarm); else armed boundary;
else seek.** The hint line names the top strip whenever a boundary is armed, so the escape route is
discoverable at the moment it is needed.

The playhead tracks playback in both modes already — `playbackOriginMs` maps the player's
clip-relative position onto the source timeline, set to the verse's start for an audition and to the
chunk's start for source playback — so it follows a sliced verse and the whole recording alike.
