# Contract: Teacher UI (Audio)

Extends Phase 11's `contracts/teacher-ui-contract.md`. Same rules apply throughout: stateless
content composable + thin holder (Principle II), tokens only (Principle VIII), every string from
`TeacherStrings`, every screen previewed in Arabic/RTL **and** English/LTR (FR-039).

**Design source obligation**: *Upload Matn (Timestamp Map)* and the audio column of *Upload Matn
(Per-Verse)* (`4f1bee3d7518487b986c7c63cb3c07ff`) MUST be fetched through the `stitch` MCP server
before any of this is written. Phase 11 deliberately left the audio column unimplemented and said so
in its `design-notes.md`; this phase implements it. The timestamp-map screen's *layout* is adopted;
its *architecture* stays rejected (see `split-contract.md` §6).

## 1. Verse row — audio slot

Added to the existing `VerseRow`, which currently renders drag handle + index + Arabic text field.

| State | Shows | Actions |
|-------|-------|---------|
| `Empty` | "add recording" affordance | attach |
| `Uploading(progress)` | determinate progress | cancel |
| `Loaded(durationMs)` | duration, play/stop control | play, replace, remove |
| `Failed(error)` | plain-language error + retry (FR-040) | retry, replace |

Rules:

- The slot never owns verse text state; the ViewModel remains the sole owner (Phase 11's 500-row
  rule).
- Per-row progress is ViewModel state keyed by verse id, never a `remember` inside the row — a
  scrolled-away row that resumes must show its true progress.
- `Loaded` play is single-verse audition, independent of the matn preview transport (§3).
- Attaching a file whose profile mismatches shows the rejection inline, naming both profiles
  (FR-005b), and leaves the previous state untouched.

## 2. Split screen — new

Reached from the editor ("split from one recording"). States:

| State | Content |
|-------|---------|
| `NoSource` | pick-a-recording prompt, accepted formats and limits stated up front (FR-004) |
| `Loading(progress)` | frame indexing + peak extraction progress; cancellable |
| `Ready` | waveform, scope picker, marker layer, per-verse range fields, problem list |
| `Splitting(progress)` | per-verse upload progress, cancellable; editing disabled |
| `Failed(error)` | retryable/not stated (FR-040); ranges preserved |

Regions:

- **Waveform** — `Canvas`, pure function of `(peaks, ranges, viewport, selection)`. **Left-to-right
  in both interface languages** (research D11); chrome around it mirrors normally.
- **Scope picker** — first/last verse of the run this recording covers, defaulting to the whole matn
  (FR-013a). Changing it discards out-of-scope ranges with a confirm.
- **Marker layer** — drag to move a boundary; keyboard nudge for fine adjustment. Dragging is the
  one interaction with a hard frame budget (FR-041): markers move against a cached peaks array, with
  no re-decode during the gesture.
- **Range fields** — numeric start/end per verse in scope, two-way bound with the markers (FR-014).
  Fields follow UI direction.
- **Problem list** — live output of `SplitPlanValidator`, blocking problems and warnings visually
  distinct (`split-contract.md` §2), each row jumping to its verse.
- **Actions** — "split and upload" (disabled while any blocking problem exists), "replace
  recording" (confirms, then discards ranges — FR-020), "cancel".

## 3. Preview transport — new

A bar available from the editor once at least one verse has audio.

| State | Content |
|-------|---------|
| `Idle` | play-from-first |
| `Buffering(verseNumber)` | downloading that verse into the cache |
| `Playing(verseNumber, positionMs)` | sounding verse indicated (FR-024), pause/stop |
| `Paused(verseNumber)` | resume/stop |
| `MissingAudio(verseNumber)` | that verse is named; skip or stop (FR-025) |

Rules:

- Starting from any verse is supported (FR-022) by selecting a row and pressing play.
- Preview stops cleanly on any content edit or new attachment (FR-026).
- Playback is gapless by construction (research D9) — the UI must not insert its own delay between
  verses, e.g. by awaiting a per-verse state round trip before feeding the next verse.

## 4. Library and publish surfaces — changed

- **Library rows** already render `AudioCompletenessBadge`; it now shows a count for `PARTIAL`
  ("12 of 109 recorded") per FR-031. Same component, one new parameter — not a second badge.
- **Validation panel** renders V4/V5 as errors rather than notes (`validation-contract.md` §4), and
  the publish button is disabled while any blocking problem stands.
- **Publish confirm dialog** states audio completeness alongside verse count, so the terminal action
  names what is being published.

## 5. Preview matrix (Principle II)

| Composable | Required previews |
|------------|-------------------|
| `VerseRow` | empty / uploading / loaded / failed — ×2 languages |
| `SplitScreenContent` | no-source / ready-valid / ready-with-problems / splitting — ×2 languages |
| `WaveformCanvas` | with ranges, with an overlap highlighted — ×2 languages (to prove the timeline does **not** mirror) |
| `PreviewBar` | idle / playing / missing-audio — ×2 languages |
| `AudioCompletenessBadge` | none / partial-with-count / complete — ×2 languages |

Every preview is driven by hand-built sample state: no ViewModel, no DI, no decoder, no network.

## 6. Accessibility

- The waveform is not the only way to set a boundary — the numeric fields are a complete alternative
  path (FR-014), which is what keeps the screen usable without fine pointer control.
- Progress and error states carry `A11yLabels`-pattern descriptions, as Phase 11's states do.
- Duration and progress are announced as text, never conveyed by color or waveform shape alone.
