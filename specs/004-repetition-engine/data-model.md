# Phase 1 Data Model: Repetition Engine

**Feature**: Phase 3 — Repetition Engine | **Date**: 2026-07-20 | **Plan**: [plan.md](./plan.md)

**No database schema change.** This phase adds **no persisted entity, table, column, or query**. It
extends the in-memory playback session established in Phase 2 with repetition state, shaped so
Phase 4 persists it per matn without reshaping it (FR-031, Principle VI).

---

## 1. Domain models (`commonMain/domain/model`)

### 1.1 `RepeatCount` — a finite count or explicit unlimited

```kotlin
sealed interface RepeatCount {
    data class Finite(val value: Int) : RepeatCount   // invariant: 1..MAX
    data object Unlimited : RepeatCount

    companion object {
        const val MIN = 1
        const val MAX = 99
        val ONE = Finite(1)
    }
}
```

Satisfies FR-003 directly: unlimited is a **distinct type**, never a sentinel `0`/`null`/absent
field, so "unlimited" can never be confused with "unset" anywhere in the state, the UI, or (in
Phase 4) storage. `Finite` construction clamps to `MIN..MAX` per FR-006.

### 1.2 `LoopRange` — the A–B selection

```kotlin
data class LoopRange(val startVerseId: String, val endVerseId: String)
```

Identified by **stable verse UUIDs**, not list positions (Principle VI) — the range survives
scrolling, list rebuilds, and later persistence. Resolved against the queue to indices at use time;
an inverted selection is normalized (swap) rather than rejected, per FR-012. `startVerseId ==
endVerseId` is legal and yields a single-verse loop.

### 1.3 `RepetitionSettings` — one matn's configured drill

```kotlin
data class RepetitionSettings(
    val verseRepeat: RepeatCount = RepeatCount.ONE,
    val matnRepeat: RepeatCount = RepeatCount.ONE,
    val loopRange: LoopRange? = null,
) {
    val mode: PlaybackMode get() = when {
        loopRange != null -> PlaybackMode.A_B_LOOP
        verseRepeat != RepeatCount.ONE || matnRepeat != RepeatCount.ONE -> PlaybackMode.MEMORIZATION
        else -> PlaybackMode.NORMAL
    }
}
```

Defaults are $V_r$ = $M_r$ = 1 with no range — i.e. **exactly Phase 2 behavior** (FR-004, SC-010).
`mode` is a **computed property with no backing field** (FR-008 / research D5), so the displayed mode
cannot contradict the configuration.

### 1.4 `PlaybackMode` — a derived label

```kotlin
enum class PlaybackMode { NORMAL, MEMORIZATION, A_B_LOOP }
```

Displayed only. Never stored, never set by the student, never an input to any decision — the planner
reads counters and range, not mode.

### 1.5 `PlaybackCursor` — position within the drill

```kotlin
data class PlaybackCursor(
    val verseIndex: Int,   // index into the queue's tracks
    val repetition: Int,   // 1-based, within the current verse
    val pass: Int,         // 1-based, over the active range
)
```

### 1.6 `RepetitionProgress` — what the player bar renders

```kotlin
data class RepetitionProgress(
    val repetition: Int,
    val verseRepeatTarget: RepeatCount,
    val pass: Int,
    val matnRepeatTarget: RepeatCount,
)
```

A projection of the cursor plus targets (FR-020). `Unlimited` targets render as **∞**, not a number.

---

## 2. Extension to `PlaybackState` (§2.6 of the Phase 2 data model)

`PlaybackState` gains three fields; every existing field keeps its meaning and default, so Phase 2
behavior is untouched when the defaults apply.

| Field | Type | Meaning |
|-------|------|---------|
| `settings` | `RepetitionSettings` | The active matn's drill configuration (FR-007). |
| `cursor` | `PlaybackCursor?` | Where the drill currently is; `null` when there is no session. |
| `loopRangeVerseIds` | `Set<String>` | Verse IDs inside the active range, for list highlighting (FR-014). Empty when no range is set. |

Derived, no backing field:

```kotlin
val mode: PlaybackMode get() = settings.mode
val repetitionProgress: RepetitionProgress? get() = cursor?.let { … }
```

These three fields are precisely what Phase 4 must persist per matn alongside the existing
`matnId` / `activeVerseId` / `positionMs` / `speed` (FR-031) — including the in-progress A–B range
the constitution calls out by name.

---

## 3. Session-scoped state (inside `PlaybackController`, not in `PlaybackState`)

| Field | Type | Purpose |
|-------|------|---------|
| `window` | `List<WindowEntry>` | The materialized entries currently loaded in the engine playlist. |
| `failedVerseIds` | `MutableSet<String>` | Verses whose audio failed this session; skipped on all later passes (research D8 / FR-028). |

```kotlin
private data class WindowEntry(val track: AudioTrack, val cursor: PlaybackCursor)
```

`WindowEntry` is the mapping that makes the sliding window work: the engine playlist index is an
index into `window`, and each entry carries the cursor that produced it. This is deliberately
**private to the controller** — no other layer needs to know repetitions are materialized as
duplicate playlist items.

---

## 4. The planner (`commonMain/playback/RepetitionPlanner`)

Pure, injected-nothing, coroutine-free (research D3):

```kotlin
sealed interface PlanStep {
    data class Advance(val cursor: PlaybackCursor) : PlanStep
    data object End : PlanStep
}

object RepetitionPlanner {
    fun next(
        cursor: PlaybackCursor,
        settings: RepetitionSettings,
        range: IntRange,
        isPlayable: (Int) -> Boolean = { true },
    ): PlanStep
}
```

`isPlayable` is how failed verses (D8) are skipped without the planner knowing what a failure *is* —
it stays a pure function of its arguments. Rules, applied in order:

| # | Condition | Result | Requirement |
|---|-----------|--------|-------------|
| 1 | `verseRepeat` is `Unlimited` | same verse, `repetition + 1` | FR-016 (∞ $V_r$ never advances) |
| 2 | `repetition + 1 <= verseRepeat` | same verse, `repetition + 1` | FR-001 |
| 3 | `verseIndex + 1 <= range.last` | next playable verse, `repetition = 1` | FR-010 |
| 4 | `matnRepeat` is `Unlimited` | `range.first`, `repetition = 1`, `pass + 1` | FR-016 |
| 5 | `pass + 1 <= matnRepeat` | `range.first`, `repetition = 1`, `pass + 1` | FR-002 / FR-010 |
| 6 | otherwise | `End` | FR-010 (stop after all passes) |

Rule 1 preceding rule 2 is what makes unlimited $V_r$ + finite $M_r$ terminate-free rather than
contradictory (the "both counters unlimited" edge case): the range end is simply never reached, and
$M_r$ never comes into play.

**Lowered counters (FR-024)** need no rule of their own: because only the window *tail* is ever
recomputed, a $V_r$ lowered below the completed count fails rule 2 on the next evaluation and falls
through to rule 3 — the current repetition finishes and playback advances. No negative or "owed"
count is representable.

---

## 5. Settings store (Phase 4 seam)

```kotlin
// domain/repository
interface RepetitionSettingsStore {
    fun get(matnId: String): RepetitionSettings
    fun put(matnId: String, settings: RepetitionSettings)
}

// data/repository — this phase
class InMemoryRepetitionSettingsStore : RepetitionSettingsStore   // Map<String, RepetitionSettings>
```

`get` returns the defaults (§1.3) for an unknown matn, so "never configured" and "configured to the
defaults" are indistinguishable — which is correct, and keeps FR-004 trivially true. Phase 4 swaps
in a SQLDelight-backed implementation with **no change to any caller** (research D4).

---

## 6. UI-state additions

### 6.1 `PlayerBarUiState` (+4 fields)

| Field | Type | Renders |
|-------|------|---------|
| `mode` | `PlaybackMode` | The derived mode chip |
| `repetition` / `verseRepeatTarget` | `Int` / `RepeatCount` | "3 / 7", or "3 / ∞" |
| `pass` / `matnRepeatTarget` | `Int` / `RepeatCount` | Pass indicator |
| `settings` | `RepetitionSettings` | Seeds the drill panel's current values |

### 6.2 `MatnDetailsUiState` (+2 fields)

| Field | Type | Renders |
|-------|------|---------|
| `loopRangeVerseIds` | `Set<String>` | In-range verse rows are visually distinguished (FR-014) |
| `loopRange` | `LoopRange?` | Start/end markers on the boundary rows |

Both remain pure projections of `PlaybackController.state` — no new state ownership in any
ViewModel (Principle II).

---

## 7. State transitions (delta from Phase 2 §6)

Only these transitions are new or changed; all other Phase 2 transitions are untouched.

| Trigger | Phase 2 behavior | Phase 3 behavior |
|---------|------------------|------------------|
| `TrackTransition(i)` | active verse ← `tracks[i]` | cursor ← `window[i].cursor`; active verse ← that cursor's verse; drop consumed, refill tail |
| `QueueEnded` | → `ENDED` | Reached only when the planner returned `End` — the window is drained deliberately, so → `ENDED` is correct and unchanged |
| `TrackError(i)` | skip one index forward | record `failedVerseIds += verse`; replan from that cursor skipping the verse; if the range has no playable verse → `ENDED` + `NoPlayableAudio` |
| counter / range change | — (n/a) | recompute window **tail only**; current item untouched (SC-005). Range change that orphans the playhead → jump to range start (research D7) |
| `next()` / `previous()` | index ± 1 | cursor → next/previous **verse** at `repetition = 1`, wrapping at range bounds (FR-021) |
| `stop()` | reset session state | additionally reset `cursor` and `failedVerseIds`; **retain** `settings` in the store (FR-023) |

**Invariant** enforced across all of the above: the currently playing engine item is never replaced
in place. Every reconfiguration path rewrites only entries strictly after it.
