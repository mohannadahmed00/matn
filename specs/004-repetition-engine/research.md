# Phase 0 Research: Repetition Engine

**Feature**: Phase 3 — Repetition Engine | **Date**: 2026-07-20 | **Plan**: [plan.md](./plan.md)

Every decision below resolves an unknown in the plan's Technical Context. The dominant question is
**D1**: how to repeat a per-verse audio file *gaplessly* when the Phase 2 engine's gapless guarantee
comes from the native player pre-buffering the **next playlist item**.

---

## D1 — How repetition is made gapless

**Decision**: Materialize repetitions as **duplicate playlist entries inside a small sliding
window**. The engine's playlist never holds the whole drill; it holds the currently playing entry
plus the next `WINDOW_AHEAD` (2) entries. On every track transition the controller drops consumed
entries and appends newly planned ones, keeping the playlist at a bounded ~3 items.

**Rationale**: This is the only option that gets gapless repetition **for free from the already
proven Phase 2 path**. Repeating verse *v* three times becomes the playlist `[v, v, v]` — from the
native player's point of view these are ordinary consecutive items, so ExoPlayer's gapless
concatenation and AVQueuePlayer's pre-enqueue pre-buffer the "next" file exactly as they already do
for `[v1, v2]`. No new gapless engineering, no new platform timing code, and FR-017 (a verse
repeating into itself must be as gapless as advancing between two verses) is satisfied by
construction rather than by careful re-seek timing.

It also handles the two hard requirements cleanly:

- **Unlimited (∞)** — an infinite drill is not materialized; the window simply never runs dry.
  Nothing in the design needs a finite total.
- **Live reconfiguration (FR-005/FR-015, SC-005)** — a counter change rewrites only the window
  **tail**, never the currently playing item, so the audio is not interrupted at all.

**Alternatives considered**:

| Alternative | Rejected because |
|-------------|------------------|
| **Engine-level repeat mode** — Media3 `REPEAT_MODE_ONE`, iOS `AVPlayerLooper` | Pushes the *decision* of when to stop repeating into platform code, violating Principle IV (platform `actual`s must hold no business logic). Counting finite repetitions means toggling repeat mode off at exactly the right transition on two different native APIs, and `AVPlayerLooper` has no finite-count mode at all. Two platforms would drift. |
| **Fully materialize the expanded queue up front** — `[v1,v1,v1,v2,v2,v2,…]` | Cannot express ∞ at all. A 500-verse matn at $V_r$=20 is a 10 000-item playlist. Any mid-flight counter change forces a full rebuild, which restarts/reprepares the player and produces exactly the audible interruption SC-005 forbids. |
| **Re-seek the same track on completion** — `seekToTrack(sameIndex)` when a repetition ends | Not gapless. The player has already torn down the item's buffer; re-seeking re-buffers and yields the audible click/silence FR-017 forbids. This is the naive approach and it fails the phase's headline promise. |

**Consequences**: the engine playlist index no longer maps 1:1 to a verse, so the controller must
keep a parallel list of window entries (`AudioTrack` + the cursor that produced it) and map
`engineIndex → PlaybackCursor`. This mapping is the one genuinely new piece of bookkeeping in the
phase and is covered by dedicated `commonTest` cases.

---

## D2 — Two additive `AudioEngine` primitives

**Decision**: Extend the Phase 2 `AudioEngine` interface with exactly two mechanical playlist
operations, both free of business logic:

```kotlin
/** Replace every item after the currently playing one. Must not disturb current playback. */
fun replaceUpcoming(tracks: List<AudioTrack>)

/** Drop every item before the currently playing one (keeps the playlist bounded). */
fun dropConsumed()
```

**Rationale**: These are the minimum primitives the sliding window needs, and both map directly onto
first-party APIs — Android: `player.replaceMediaItems(currentIndex + 1, mediaItemCount, items)` and
`player.removeMediaItems(0, currentIndex)`, both of which Media3 1.4.1 performs without interrupting
the current item; iOS: `AVQueuePlayer.remove(item)` for already-played items and `insert(item,
after:)` for the tail. They are pure playlist mechanics — *what* to enqueue is decided in
`commonMain` — so Principle IV holds.

**Alternatives considered**: a single `setWindow(tracks, currentIndex)` primitive was rejected
because it cannot express "leave the current item strictly untouched", which is the property that
makes live reconfiguration inaudible.

**Risk (carried forward honestly)**: the iOS `AvQueueAudioEngine` is **still a documented stub** from
Phase 2 — its transport body is authored and validated on macOS with Xcode (Phase 2 tasks T038/T040),
because the Windows Kotlin/Native sysroot does not expose the `AVPlayer` playback symbols. The two
new primitives inherit that constraint: they will be authored here and validated on macOS alongside
the outstanding Phase 2 iOS work. This phase does **not** close that gap, and the quickstart marks
every iOS row accordingly.

---

## D3 — The repetition planner is a pure function

**Decision**: All advance logic lives in a **pure, side-effect-free** `RepetitionPlanner` in
`commonMain` with the single responsibility of `next(cursor, settings, range) → Advance | End`. It
touches no coroutines, no engine, no clock.

**Rationale**: The constitution names the memorization matrix ($V_r$, $M_r$, ∞, A–B loops) as "the
product's core correctness surface [that] must be provable in isolation" (Principle V). A pure
function over a small cursor makes every rule in the spec — advance-within-verse, advance-to-next,
wrap-to-range-start, stop-after-$M_r$-passes, both-unlimited, lowered-counter — a one-line table test
with no fakes at all. The controller then holds only orchestration.

Advance rule (the whole engine, in order):

```text
next(cursor):
  Vr unlimited                      → (verse,        rep+1, pass)      # never advances
  rep + 1 <= Vr                     → (verse,        rep+1, pass)
  verse + 1 <= range.end            → (verse+1,      1,     pass)
  Mr unlimited                      → (range.start,  1,     pass+1)
  pass + 1 <= Mr                    → (range.start,  1,     pass+1)
  otherwise                         → End
```

**Alternatives considered**: folding the rules into `PlaybackController`'s event reducer (rejected —
it would only be testable through scripted engine events, turning table-testable arithmetic into
integration tests) and a coroutine-based generator/`Flow` of steps (rejected — adds suspension and
lifecycle to something that is a pure state function, and makes "what comes after this cursor?"
unanswerable without collecting).

---

## D4 — Per-matn settings behind a store interface

**Decision**: Introduce `RepetitionSettingsStore` (domain interface) with an in-memory
`InMemoryRepetitionSettingsStore` (data) keyed by `matnId`.

**Rationale**: FR-007 scopes settings per matn and FR-033 forbids persisting them this phase, while
Principle VI requires the shape be ready for Phase 4. An interface with a trivial map-backed
implementation costs almost nothing now and lets Phase 4 substitute a SQLDelight-backed
implementation **without touching `PlaybackController` or any ViewModel** — the seam is already
where it needs to be. It also keeps the controller from growing a private settings map that Phase 4
would have to surgically extract.

**Alternatives considered**: a private `Map<String, RepetitionSettings>` field on the controller
(rejected — Phase 4 would have to reach into the controller's internals, and the map would be
untestable in isolation) and persisting now via the Phase 0 `app_setting` table (rejected — FR-033
explicitly defers persistence to Phase 4; doing it here would leak scope).

---

## D5 — Mode is derived, never stored

**Decision**: `PlaybackMode` is a computed property over `RepetitionSettings`:

```text
loopRange != null                        → A_B_LOOP
verseRepeat != 1 || matnRepeat != 1      → MEMORIZATION
otherwise                                → NORMAL
```

**Rationale**: Directly implements the clarified FR-008. A derived label **cannot** disagree with the
configuration it describes, which deletes an entire class of contradictory states (indicator says
"Normal" while $V_r$ = 7) without a single validation rule. It also means "switching modes" needs no
code path of its own — FR-015 falls out of the ordinary counter/range update path.

**Alternatives considered**: a stored `mode` field with presets (rejected during `/speckit-clarify`
as Q3 option B — it requires mode↔counter reconciliation on every edit and can desynchronize).

---

## D6 — Setting an A–B range defaults $M_r$ to unlimited

**Decision**: Marking a loop range sets $M_r$ to `Unlimited` **unless the student has already chosen
a finite pass count for that matn**.

**Rationale**: Satisfies FR-016 — the product spec's "loops until manually stopped" is the expected
out-of-the-box behavior — while keeping the counters fully composable per the Q1 clarification.
Preserving an explicitly chosen finite $M_r$ avoids silently discarding the student's own setting.

---

## D7 — Range change under the playhead

**Decision**: When A or B moves such that the active verse falls outside the new range (FR-025),
playback **jumps immediately to the first verse of the new range** (rebuild window, `seekToTrack`),
accepting an audible transition.

**Rationale**: The alternative — finishing the current verse first — leaves audio playing outside the
range the student just drew, contradicting SC-006 ("zero verses outside the marked range are ever
played"). The jump is user-initiated and therefore expected, exactly like Phase 2's next/previous.
When the active verse is still *inside* the new range, nothing moves and only the window tail is
recomputed, so the common case (widening B) stays inaudible.

---

## D8 — Failed verses are skipped for the whole session

**Decision**: On `TrackError`, the verse is added to a session-scoped `failedVerseIds` set; the
planner skips it on **all subsequent passes**, not just the current one. If the set swallows every
verse in the range, playback stops with `NoPlayableAudio` (FR-029).

**Rationale**: FR-028 requires skipping the failed verse and its remaining repetitions. Without a
session-scoped set, an unlimited loop would re-attempt the same broken file on every pass and
re-emit the notice forever — the "spins/busy-loops" failure FR-029 and FR-030 exist to prevent. One
notice per verse per session is the honest behavior.

---

## D9 — Repetition counted at end-of-item, not by position

**Decision**: A repetition is consumed when the engine reports a **track transition** off that window
entry — never by watching `positionMs` approach `durationMs`.

**Rationale**: Directly satisfies FR-019 (scrubbing must not consume, skip, or reset a repetition):
seeking backward inside a verse changes `positionMs` but produces no transition, so the count is
untouched for free. Position-threshold counting would double-count on scrub-to-end and mis-count at
non-1.0× speeds.

---

## D10 — Window size of 2 entries ahead

**Decision**: `WINDOW_AHEAD = 2` (current + 2 upcoming).

**Rationale**: Gapless pre-buffering only requires the player to know the **immediately next** item;
a second entry provides margin so a transition landing between refills never leaves the playlist
empty. Larger windows increase the tail that must be rewritten on every counter change (raising the
cost and risk of live reconfiguration) with no audible benefit, and would keep more decoded buffers
resident for no reason.

---

## Dependencies

**No new third-party dependency.** The phase uses only what Phases 0–2 already introduced:
Kotlin 2.4.10 / KMP, Compose Multiplatform 1.11.1 + Material 3, Koin 4.0.0, coroutines 1.9.0,
SQLDelight 2.0.2 (read-only here), and Media3 1.4.1 on `androidMain` (already present — the two new
primitives use `replaceMediaItems`/`removeMediaItems`, available since Media3 1.1). iOS continues on
first-party AVFoundation. This satisfies the constitution's "new dependency requires justification"
rule trivially: there is none to justify.
