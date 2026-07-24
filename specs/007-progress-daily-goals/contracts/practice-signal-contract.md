# Contract: Playback Practice Signal

How completing a verse during playback credits the daily goal (FR-011), without coupling progress to
raw playback frequency. Two pieces: a natural-completion marker the controller publishes, and a pure
observer that records the credit. Research D2.

## 1. `PlaybackState` completion marker (additive)

`PlaybackController` gains a small additive field on its existing `PlaybackState` — the completed
verse plus a monotonically increasing tick so repeat consumers can distinguish successive
completions of the *same* verse:

```
// conceptual — final field name/shape settled in implementation
val lastCompletedVerseId: String? = null
val completionTick: Long = 0        // increments each time a verse finishes naturally
```

**Set only at natural boundaries** (the controller already handles these):

| Site | Completed verse |
|------|-----------------|
| `AudioEngineEvent.TrackTransition` handler | the **outgoing** window entry's `verseId` (the track that just finished) |
| `AudioEngineEvent.QueueEnded` handler | the final `activeVerseId` before the queue ended |

**Never set by**: `next()`, `previous()`, `stop()`, `seekTo()`, `pause()`, interruption pauses, or
`TrackError` skips. User-initiated or error transitions are **not** completions.

The marker is otherwise inert: it changes nothing about playback decisions and is ignored by every
existing consumer (Principle VII — no new mutation site in playback logic).

## 2. `PracticeSignalRecorder` (NEW, pure observer)

Built like `SessionStateRecorder`: constructed with `PlaybackController.state`, a
`ProgressRepository`, an injected `today: () -> Long`, and a `CoroutineScope`; started once from DI
(`initMatnKoin`). It never calls back into the controller.

**Behavior**: subscribe to `state`; on each **new** `completionTick`, evaluate the recall-mode
predicate against the current `settings`; if it holds, call
`progressRepository.recordPractice(lastCompletedVerseId)`.

**Recall-mode predicate** (FR-011):
```
settings.loopRange != null ||
settings.verseRepeat != RepeatCount.ONE ||
settings.matnRepeat != RepeatCount.ONE
```
- Memorization mode (`Vr > 1` and/or `Mr > 1`) → credits.
- A–B Loop mode (`loopRange != null`) → credits.
- **Normal continuous** (`Vr = 1`, `Mr = 1`, no loop) → **never credits** (clarification Q1).

**Idempotence**: crediting is safe to repeat — the DB `UNIQUE(day_epoch, verse_id)` dedups (FR-010),
so re-hearing a verse across passes or sessions in the same day counts once (SC-006).

**Failure isolation**: a failed `recordPractice` is swallowed and never disturbs playback (mirrors
`SessionStateRecorder`'s W7 rule).

## 3. Edge behavior

- **Day rollover mid-session**: `recordPractice` uses `today()` at credit time, so a verse completed
  after local midnight is attributed to the new day (spec edge "Day rollover mid-session").
- **Same verse, many passes**: a `Vr = 5` drill fires up to 5 completion ticks for the verse; the
  first credits, the rest are `OR IGNORE` no-ops.
- **User skips before the end**: `next()` produces no completion tick → no credit (matches
  "completed a full playthrough").
- **Marking during playback**: `ToggleVerseMemorizedUseCase` credits independently (D3); if the same
  verse is also completed in a recall session that day, the two credits collapse to one row.

## 4. Test obligations (`commonTest` — `PracticeSignalRecorderTest`, via `FakeAudioEngine`)

1. Memorization-mode session: completing verse V credits exactly one `daily_practice` row for
   `today()`.
2. Normal-mode session (`Vr=1, Mr=1`, no loop): completing verses credits **nothing**.
3. A–B loop session: completing a verse in the loop credits it.
4. Same verse completed twice in one day → one row (dedup).
5. User `next()` (no natural completion) → no credit.
6. Day rollover: a completion after `today()` advances lands under the new day.
7. Recorder never mutates `PlaybackController` (assert state/calls unchanged), mirroring
   `SessionStateRecorderTest`.
