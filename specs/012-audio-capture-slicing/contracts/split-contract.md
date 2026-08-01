# Contract: Splitting a Continuous Recording

Governs the second authoring path. Its output is indistinguishable from the first path's
(`audio-artifact-contract.md`); everything here describes the authoring-time machinery that produces
it and then disappears.

## 1. Scope (FR-013a)

A split covers a **contiguous run of verses in list order**, defaulting to the whole matn.

- Verses outside the scope are not validated against the recording and keep their existing audio.
- Changing the scope discards ranges for verses dropped from it; verses newly added start unranged.
- The scope is remembered as a list of verse **ids**, so reordering verses mid-session does not
  silently re-target the split at different content.
- Splitting several disjoint stretches means running the split once per stretch.

## 2. Range rules

`SplitPlanValidator.validate(plan): SplitReport` runs on every edit and returns problems, each
naming its verse.

| Rule | Condition | Severity | Message subject |
|------|-----------|----------|-----------------|
| R1 `MissingRange` | a verse in scope has no range | Blocking | that verse |
| R2 `InvertedRange` | `endMs ≤ startMs` | Blocking | that verse |
| R3 `TooShort` | `endMs - startMs < 300 ms` | Blocking | that verse |
| R4 `OutOfBounds` | `endMs > source.durationMs` or `startMs < 0` | Blocking | that verse + the source length |
| R5 `Overlap` | two ranges intersect | Blocking | both verses |
| R6 `RangeOutOfScope` | a range names a verse not in scope | Blocking | that verse |
| R7 `UncoveredStretch` | ≥ 1 s of source covered by no range | **Warning** | the stretch's position |

R7 is a warning because silence between verses is normal (FR-016). Everything else refuses the
split (FR-015, FR-016).

The validator is a pure function of `SplitPlan` — no I/O, no decoder — so every rule is covered in
`commonTest` by constructing plans directly.

## 3. Frame snapping

A range's millisecond boundaries are advisory; the cut happens at frame boundaries.

```text
frameDurationMs = samplesPerFrame / sampleRate * 1000
  MPEG-1 Layer III: 1152 samples  →  26.12 ms @ 44.1 kHz, 24.00 ms @ 48 kHz
  MPEG-2/2.5 Layer III: 576 samples → half those

startFrame = the frame whose start is nearest to startMs
endFrame   = the frame whose end   is nearest to endMs   (exclusive)
```

Maximum boundary error is half a frame (~13 ms) and never exceeds one frame (~26 ms) — inside
SC-004's 50 ms without re-encoding.

The UI shows the *requested* millisecond values, not the snapped ones; the stored `durationMs` is
the snapped, measured truth (`data-model.md` A2).

## 4. What a slice contains

| Included | Excluded |
|----------|----------|
| Whole MPEG frames from `startFrame` to `endFrame - 1` | Any partial frame |
| — | The source's ID3v2 block (skipped by the frame index) |
| — | The source's ID3v1 trailer |
| — | The source's Xing/Info/VBRI header frame |

Excluding the Xing/Info/VBRI frame is not cosmetic: it declares the *source's* frame count and
duration, so a slice carrying it reports the wrong length in every player
(`research.md` D2).

**Known artifact, accepted**: Layer III's bit reservoir lets a frame reference up to 511 bytes of
main data from preceding frames, so a slice's first frame or two may decode imperfectly (≈50 ms).
This is inherent to frame-accurate cutting without re-encoding, which spec Q1 chose deliberately. It
falls at a verse boundary, where there is a natural pause. Revisit only if a teacher reports an
audible click.

## 5. Execution

```text
load     → build Mp3FrameIndex (one pass, offsets + cumulative durations, no decode)
         → stream-decode for waveform peaks (bounded memory, research D7)
mark     → validate on every edit (§2)
save     → for each verse in scope, in order:
             read its byte range from the source        (never the whole file, research D8)
             strip tags per §4
             tag = fnv1a64(bytes); objectPath = matns/{matnId}/verses/{verseId}-{tag}.mp3
             skip if already present at the expected size, else upload
         → write the matn row once                       ← COMMIT
         → delete superseded objects
```

Ordering, atomicity, resume, and cleanup are the shared rules in
`audio-artifact-contract.md` §4–§5 — the split does not get its own versions of them.

## 6. Transience (FR-018)

At no point is the source recording, the frame index, the peaks array, the scope, or any range:

- written to the matn row,
- uploaded to the bucket,
- persisted to the teacher's disk by the tool, or
- exposed to any student client.

Replacing the source discards every range and says so (FR-020) — ranges measured against one
recording are meaningless against another of a different length.

After a successful split, the plan is discarded. A later correction re-marks from scratch; the
resume predicate (`audio-artifact-contract.md` §5) is what makes an interrupted attempt cheap to
repeat, not a stored plan.
