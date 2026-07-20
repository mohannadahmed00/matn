# Contract: Repetition Engine

**Feature**: Phase 3 — Repetition Engine | **Date**: 2026-07-20

The UI contract exposed to the reading screen and player bar, plus the behavioral contract the
`commonTest` suite holds `PlaybackController` and `RepetitionPlanner` to. Types are defined in
[data-model.md](../data-model.md).

---

## 1. Intents (`PlaybackController`, additive to the Phase 2 surface)

| Intent | Signature | Contract |
|--------|-----------|----------|
| Set verse repeat | `setVerseRepeat(count: RepeatCount)` | Applies to the active matn. Takes effect on the **next** repetition boundary; the currently playing item is never interrupted. Finite values clamp to `1..99`. |
| Set matn repeat | `setMatnRepeat(count: RepeatCount)` | As above, at range scope. |
| Mark loop start | `setLoopStart(verseId: String)` | Sets A. If B is unset, B ← last verse. If the result is inverted, A/B swap. Defaults $M_r$ to `Unlimited` unless the student already chose a finite $M_r$ (research D6). |
| Mark loop end | `setLoopEnd(verseId: String)` | Sets B, same normalization. |
| Clear loop | `clearLoop()` | Range reverts to the whole matn. The session continues from the current verse — **no restart** (FR-013). |

All five are callable whether playback is `PLAYING`, `PAUSED`, or `IDLE`. When `IDLE` they update
the stored settings only; the next session starts with them applied.

**Guarantee (SC-005)**: none of these intents calls `setQueue`, `stop`, `prepare`, or `seekTo` on the
currently playing item. Each rewrites only the window tail via `replaceUpcoming`. The single
exception is a range change that leaves the playhead outside the new range, which deliberately
relocates playback (research D7 / FR-025).

---

## 2. State projection

`PlaybackController.state: StateFlow<PlaybackState>` gains `settings`, `cursor`, and
`loopRangeVerseIds`, plus derived `mode` and `repetitionProgress`. Consumers stay pure projections:

- `PlayerBarViewModel` → drill panel values, mode chip, "3 / 7" and pass indicators.
- `MatnDetailsViewModel` → in-range row styling and A/B boundary markers.

Neither ViewModel owns repetition state or decides an advance (Principle II).

---

## 3. Behavioral contract — `RepetitionPlanner`

Pure function; these are exhaustive table tests in `commonTest` with **no fakes**.

| # | Given (Vr, Mr, range, cursor) | `next(...)` | Requirement |
|---|-------------------------------|-------------|-------------|
| P1 | Vr=3, cursor rep=1 | rep=2, same verse | FR-001 |
| P2 | Vr=3, cursor rep=3, verse mid-range | next verse, rep=1 | FR-001 |
| P3 | Vr=1, verse = range.last, Mr=1, pass=1 | `End` | FR-009 / SC-010 |
| P4 | Vr=1, verse = range.last, Mr=3, pass=1 | range.first, rep=1, pass=2 | FR-002 |
| P5 | Vr=1, verse = range.last, Mr=3, pass=3 | `End` | FR-002 |
| P6 | Vr=`Unlimited`, rep=97 | rep=98, same verse | FR-016 |
| P7 | Mr=`Unlimited`, verse = range.last | range.first, pass+1 | FR-016 |
| P8 | Vr=`Unlimited`, Mr=`Unlimited` | same verse forever; range end unreachable | edge case |
| P9 | Vr=2, cursor rep=5 (counter lowered) | next verse, rep=1 — never negative | FR-024 |
| P10 | range = single verse, Mr=`Unlimited` | same verse, pass+1 | edge case (A==B) |
| P11 | `isPlayable` false for range.first+1 | skips it to the next playable verse | FR-028 |
| P12 | `isPlayable` false for every verse in range | `End` | FR-029 |

---

## 4. Behavioral contract — `PlaybackController` (against `FakeAudioEngine`)

| # | Scenario | Expected | Requirement |
|---|----------|----------|-------------|
| C1 | Vr=3, start session | window is `[v1, v1, v1]`-shaped; three transitions before the active verse changes | FR-001 / FR-017 |
| C2 | Transition consumes an entry | `dropConsumed()` + `replaceUpcoming(...)` called; playlist stays ≤ `1 + WINDOW_AHEAD` | FR-030 |
| C3 | `setVerseRepeat` while `PLAYING` | `replaceUpcoming` called; **no** `setQueue` / `stop` / `seekTo`; status stays `PLAYING` | SC-005 |
| C4 | `setVerseRepeat(2)` when rep already 5 | current repetition finishes, then advances | FR-024 |
| C5 | Loop set to v3..v5, run to v5 end | next active verse is v3; no entry outside v3..v5 ever enqueued | FR-011 / SC-006 |
| C6 | `next()` at rep 2 of 5 | remaining repetitions abandoned; next verse starts at rep=1 | FR-021 |
| C7 | `next()` on range.last with a loop set | wraps to range.first (does not leave the range) | FR-021 |
| C8 | `previous()` on range.first with a loop set | wraps to range.last | edge case |
| C9 | pause → resume mid-repetition | cursor unchanged; repetition not restarted or double-counted | FR-022 / SC-009 |
| C10 | `seekTo` backward, then play to end | exactly one repetition consumed | FR-019 / SC-009 |
| C11 | `InterruptionBegan` at rep 4 of 9 → `InterruptionEnded` | resumes at rep 4 | FR-026 / SC-009 |
| C12 | `TrackError` on a verse inside a loop | verse added to `failedVerseIds`; skipped on later passes; one notice | FR-028 / D8 |
| C13 | Every verse in range fails | `ENDED` + `NoPlayableAudio`; no busy loop | FR-029 |
| C14 | `stop()` | cursor + `failedVerseIds` reset; `settings` retained in the store | FR-023 |
| C15 | Defaults (Vr=1, Mr=1, no range) | byte-for-byte Phase 2 behavior: one pass, then `ENDED` | SC-010 |
| C16 | Settings set for matn A, then open matn B | matn B starts at defaults; returning to A restores A's settings | FR-007 |
| C17 | Range moved so the active verse is outside it | playback relocates to range start | FR-025 |
| C18 | `setSpeed` during a drill | speed applies to every subsequent repetition | FR-026 |

---

## 5. Engine contract additions

See [audio-engine-repetition.md](./audio-engine-repetition.md).

---

## 6. Non-contract (explicitly out of scope)

Not provided by this phase, per FR-033: persistence of settings/mode/range across restarts,
"Continue Learning" resume, any progress or "memorized" signal, and any inter-repetition silence
(FR-027).
