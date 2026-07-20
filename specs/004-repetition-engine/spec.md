# Feature Specification: Phase 3 — Repetition Engine

**Feature Branch**: `004-repetition-engine`

**Created**: 2026-07-20

**Status**: Draft

**Input**: User description: "read @docs/ROADMAP.md and create a specification for the Phase 3 — Repetition Engine"

## Overview

Phase 2 taught the app to *read a matn aloud*. Phase 3 teaches it to **drill** — turning a
straight-through recitation into the repetition patterns students actually memorize with.

Memorization is repetition with structure: hear a verse seven times, move to the next, do the
same, then run the whole passage back from the top a few times. Today the student has to
achieve that by hand — replaying, scrubbing back, tapping previous. This phase makes the
repetition itself a first-class, configurable part of playback through two counters and three
playback modes:

- **Verse Repeat Counter ($V_r$)** — how many times each verse repeats before playback advances.
- **Matn Repeat Counter ($M_r$)** — how many times the whole selected range replays once its end
  is reached.
- **Normal Continuous Mode** — the Phase 2 behavior, preserved exactly ($V_r = 1$, $M_r = 1$).
- **Memorization Mode** — verse-by-verse drilling driven by $V_r$, with whole-range passes
  driven by $M_r$.
- **A–B Loop Mode** — the student marks a start verse (A) and an end verse (B), and playback
  loops inside that range, ignoring chapter and matn boundaries, until stopped.

Both counters accept a finite number **or unlimited (∞)**, and unlimited is an explicit,
first-class choice a student can select — not the accidental result of leaving a field empty.
Counters and mode are adjustable **before or during** playback, and changes take effect without
tearing down the listening session.

Everything in this phase rides on the Phase 2 engine and inherits its promises: repetitions —
including a verse repeating into *itself* — must be **gapless**, the active-verse highlight and
auto-scroll must keep tracking the audio, speed and transport controls keep working, and
interruptions still pause gracefully.

Two boundaries define the phase. First, **repetition state is in-session only** — Phase 3 does
not persist counters, the active mode, or an in-progress A–B range across app restarts; that
cached "Continue Learning" resume is **Phase 4**. Phase 3's obligation is to *shape* that state
so Phase 4 can persist it without redesigning it. Second, **repetition is not progress** — no
"mark as memorized", percentage, or daily goal is introduced here; the constitution requires
progress to be decoupled from raw playback count, and that metric arrives in **Phase 6**.

Value is delivered when a student can open a matn, set "repeat each verse 7 times, repeat the
whole thing 3 times" (or loop verses 12–18 indefinitely), press play, and put the phone down.

## Clarifications

### Session 2026-07-20

- Q: How do the A–B loop and the two counters compose? → A: Fully composable — $V_r$ repeats each verse inside the range and $M_r$ bounds the loop passes (defaulting to unlimited when a loop is set).
- Q: What is the scope and lifetime of the repetition settings ($V_r$, $M_r$, mode, loop range)? → A: Per-matn — each matn carries its own repetition settings, remembered while the app is running and shaped for Phase 4 to persist per matn.
- Q: Is the playback mode a stored setting the student selects, or derived from the configuration? → A: Derived — mode is a computed label over the counters and loop range; the student never selects a mode directly, so the indicator cannot contradict the configuration.
- Q: Should a configurable silent pause be insertable between repetitions? → A: No — every repetition is immediately gapless in Phase 3; a recite-along pause is deferred to a later phase.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Drill a verse a fixed number of times before moving on (Priority: P1)

A student opens a matn, sets the verse repeat counter to a number (say 7), and starts playback.
The app plays the first verse, then plays it again, and again — seven times total, each
repetition flowing seamlessly into the next with no audible gap — while the verse stays
highlighted and a small indicator shows which repetition is currently playing (e.g. "3 / 7").
After the seventh pass it advances to the next verse and the count starts over. The student can
raise or lower the counter mid-session without stopping playback, and can tap next at any time
to abandon the remaining repetitions and move on.

**Why this priority**: The per-verse repeat counter is the single feature that converts an audio
player into a memorization tool, and it is the one students reach for first. It is also the
smallest slice that proves the repetition engine works: a counted, gapless, self-advancing loop
layered on top of Phase 2 playback. Every other story in this phase is a variation on this
mechanism.

**Independent Test**: Open a matn seeded from Phase 0, set the verse repeat counter to a small
number (e.g. 3), start playback, and verify each verse plays exactly that many times — gaplessly,
with the highlight held and the repetition indicator advancing — before playback moves to the next
verse, all with no network connection.

**Acceptance Scenarios**:

1. **Given** the verse repeat counter is set to 3, **When** the student starts playback from a
   verse, **Then** that verse's audio plays three times in succession before playback advances to
   the next verse.
2. **Given** a verse is repeating, **When** one repetition ends and the next begins, **Then** the
   transition is gapless (no audible click or silence) and the same verse remains highlighted and
   in view.
3. **Given** a verse is on repetition 2 of 5, **When** the student views the player, **Then** the
   current repetition and the target count are visible.
4. **Given** a verse is on repetition 2 of 5, **When** the student taps next, **Then** the
   remaining repetitions are abandoned and the following verse begins its own full repeat count
   from 1.
5. **Given** playback is in progress with a verse repeat counter of 5, **When** the student changes
   the counter to 2, **Then** the change takes effect without stopping or restarting playback.
6. **Given** the verse repeat counter is 4 and a verse is on repetition 2, **When** the student
   pauses and later resumes, **Then** playback continues within the same repetition rather than
   restarting the count.

---

### User Story 2 - Loop a chosen passage between two verses (Priority: P2)

A student who is working on one difficult passage marks a start verse (A) and an end verse (B) —
say verses 12 through 18 — and starts playback. The app plays only those verses, and when it
reaches the end of the range it jumps straight back to the start of the range and plays it again,
continuing indefinitely until the student stops it. The looped range is clearly marked in the
verse list so the student can see the boundaries at a glance, and the loop can be cleared at any
time to return to the full matn.

**Why this priority**: The A–B loop is the second-most-requested memorization behavior and the one
that handles the "I keep stumbling over these six lines" case that a per-verse counter cannot.
It is independently valuable and independently testable — a student who has only this feature can
still drill a passage — and it introduces the range concept that Phase 4 must later resume.

**Independent Test**: Open a matn, mark a start and end verse a few verses apart, start playback,
and verify playback stays strictly inside that inclusive range, wraps from the end verse back to
the start verse without leaving the range, marks the range visually in the list, and continues
looping until manually stopped.

**Acceptance Scenarios**:

1. **Given** the student marks verse 12 as A and verse 18 as B, **When** playback starts, **Then**
   playback begins at verse 12 and plays only verses 12 through 18 inclusive.
2. **Given** an active A–B loop, **When** the last verse of the range finishes, **Then** playback
   returns to the first verse of the range and continues, with the same gapless transition quality
   as any other verse advance.
3. **Given** an active A–B loop spanning a chapter boundary, **When** playback reaches that
   boundary, **Then** playback continues through it — the loop range, not the chapter or matn
   boundary, defines where playback stops.
4. **Given** an active A–B loop, **When** the student clears the loop, **Then** playback range
   reverts to the whole matn and continues from the current verse without a session restart.
5. **Given** the student selects an end verse that comes before the chosen start verse, **When**
   the selection is made, **Then** the system prevents or corrects the inverted range rather than
   entering an unplayable state.
6. **Given** an active A–B loop, **When** the student views the verse list, **Then** the verses
   inside the loop range are visually distinguished from those outside it.
7. **Given** an active A–B loop with a verse repeat counter of 3, **When** playback runs, **Then**
   each verse inside the range plays three times before advancing — the counters apply inside the
   loop exactly as they do outside it.
8. **Given** the student marks an A–B range without changing the matn repeat counter, **When**
   playback reaches the end of the range, **Then** the range loops indefinitely (the matn repeat
   counter defaults to unlimited when a loop is set) until the student stops it or sets a finite
   pass count.

---

### User Story 3 - Replay the whole passage a set number of times (Priority: P3)

Having drilled verse by verse, the student wants the full run-through: they set the matn repeat
counter to 3, so once playback reaches the end of the matn (or of the active range) it starts
again from the beginning, three times in total, then stops. The player shows which pass is
currently running.

**Why this priority**: Whole-range repetition is the consolidation step that follows per-verse
drilling — valuable, but only after $V_r$ exists, and less frequently adjusted than the per-verse
counter. It reuses the same counting mechanism at a different scope, so it is a smaller increment
than Stories 1 and 2.

**Independent Test**: Set the matn repeat counter to 2 with the verse counter at 1, play a short
matn (or short range) to its end, and verify playback restarts from the beginning exactly once
more and then stops cleanly, with the current pass number visible.

**Acceptance Scenarios**:

1. **Given** the matn repeat counter is 3, **When** playback reaches the end of the range, **Then**
   playback restarts from the first verse of the range and continues, for three complete passes in
   total.
2. **Given** the matn repeat counter is 3 and the third pass reaches the end of the range, **When**
   that pass completes, **Then** playback stops cleanly rather than looping again.
3. **Given** the matn repeat counter is 1 and the verse repeat counter is 1, **When** playback runs
   to the end of the matn, **Then** behavior is identical to Phase 2 continuous playback — one pass,
   then stop.
4. **Given** a multi-pass session is running, **When** the student views the player, **Then** the
   current pass and the target number of passes are visible.

---

### User Story 4 - Repeat indefinitely and retune the drill while listening (Priority: P4)

A student practicing hands-free sets the verse counter to unlimited (∞) so a single verse keeps
repeating while they recite along, with no fixed stopping point. Later they switch the verse
counter back to a finite number and set the matn counter to unlimited so the whole passage cycles
continuously. All of this happens while audio keeps playing — selecting ∞ is an explicit choice in
the counter control, plainly shown in the player, and playback only ever ends when the student
stops it.

**Why this priority**: Unlimited repetition is essential for hands-free drilling but is a variation
on the counting behavior established in Stories 1–3 rather than new machinery, and live
reconfiguration is a refinement of an already-working session. Both are the last things needed to
make the engine feel complete.

**Independent Test**: Set the verse repeat counter to ∞, start playback, and verify a single verse
repeats gaplessly well past any finite count and never auto-advances; then change the counter to a
finite value mid-playback and verify playback advances normally from that point without a session
restart.

**Acceptance Scenarios**:

1. **Given** the verse repeat counter is set to unlimited, **When** playback runs, **Then** the
   active verse repeats indefinitely and never auto-advances to the next verse.
2. **Given** the verse repeat counter is unlimited, **When** the student taps next, **Then**
   playback moves to the next verse and begins repeating that verse indefinitely.
3. **Given** the matn repeat counter is set to unlimited, **When** playback reaches the end of the
   range, **Then** it restarts from the beginning of the range and continues indefinitely until the
   student stops it.
4. **Given** a counter is set to unlimited, **When** the student views the counter control and the
   player, **Then** unlimited is shown as an explicit, distinct value (not as a blank, zero, or
   missing setting).
5. **Given** an unlimited verse repeat is running, **When** the student changes the counter to a
   finite number, **Then** playback continues uninterrupted and the verse advances once that finite
   count is satisfied.

---

### Edge Cases

- **A equals B**: A single-verse A–B range must loop that one verse rather than being rejected or
  terminating immediately.
- **Both counters unlimited**: Unlimited $V_r$ makes the range end unreachable — the verse repeats
  forever and $M_r$ never comes into play. The system must behave predictably (no stall, no
  contradictory indicator) rather than deadlocking on an unreachable end-of-range.
- **Unplayable verse inside a loop**: If a verse's audio is missing or fails, its repetitions are
  skipped and the loop continues with the remaining verses — the session must not abort the whole
  drill for one bad file.
- **Range with no playable verses**: If every verse in the active range is unplayable, playback must
  stop cleanly with a message rather than spinning through an empty loop.
- **Previous at the start of a loop range**: Tapping previous on the first verse of an A–B range
  must behave predictably (wrap to the range's last verse) rather than escaping the range.
- **Shrinking the range under the playhead**: If the student moves A or B such that the currently
  playing verse falls outside the new range, playback must recover into the new range rather than
  continuing outside it or stopping unexpectedly.
- **Lowering a counter below the completed repetitions**: If a verse has already played 5 times and
  the counter is lowered to 2, the verse must finish its current repetition and advance rather than
  retroactively "owing" a negative count.
- **Interruption mid-repetition**: A phone call during repetition 4 of 9 must pause gracefully and,
  on resume, continue at repetition 4 — the repeat count must survive the interruption.
- **Scrubbing within a repeating verse**: Seeking backward inside a verse must not silently consume
  or reset a repetition; a repetition is counted when the verse's audio reaches its end.
- **Very large finite counts**: A large but finite counter must behave as a finite count (it
  eventually advances), remaining visually distinguishable from unlimited.
- **Mode switch mid-playback**: A configuration change that crosses a mode boundary while audio is
  playing (e.g. raising $V_r$ from 1 to 5, or marking an A–B range) must retune the session in place
  and update the displayed mode, not restart the matn from the beginning.

## Requirements *(mandatory)*

### Functional Requirements

**Repetition counters**

- **FR-001**: The system MUST provide a **Verse Repeat Counter ($V_r$)** controlling how many times
  each verse plays before playback advances to the following verse.
- **FR-002**: The system MUST provide a **Matn Repeat Counter ($M_r$)** controlling how many times
  the active playback range replays after its end is reached.
- **FR-003**: Both counters MUST accept either a **finite positive integer** or **unlimited (∞)**,
  and unlimited MUST be an explicit, selectable value represented distinctly in the app's state —
  never implied by an empty, absent, or zero-valued setting.
- **FR-004**: Both counters MUST default to **1**, so that an unconfigured session behaves exactly
  as Phase 2 continuous playback.
- **FR-005**: Both counters MUST be adjustable **before playback starts and while playback is in
  progress**, and a change made during playback MUST take effect without stopping, restarting, or
  audibly interrupting the session.
- **FR-006**: The system MUST reject or clamp invalid counter values (below the minimum of 1, or
  above the supported finite maximum) rather than entering an undefined repetition state.
- **FR-007**: Repetition settings — $V_r$, $M_r$, the active mode, and any A–B range — MUST be scoped
  **per matn**: each matn carries its own settings, and changing them for one matn MUST NOT affect
  another. Re-opening a matn within the same app run MUST restore the settings last used for that
  matn; a matn with no settings yet MUST start at the defaults (FR-004). Carrying these settings
  across app restarts is Phase 4 (FR-033).

**Playback modes**

- **FR-008**: The three playback modes — **Normal Continuous**, **Memorization**, and **A–B Loop** —
  MUST be **derived** from the effective configuration (the two counters plus the presence of an A–B
  range) rather than stored as a separate selectable setting. The student adjusts counters and the
  loop range; the system computes which mode those add up to and MUST display it. It MUST therefore
  be impossible for the displayed mode to contradict the active counters or range.
- **FR-009**: **Normal Continuous Mode** MUST play every verse in the matn exactly once in order and
  then stop, equivalent to $V_r = 1$ and $M_r = 1$ — preserving Phase 2 behavior unchanged.
- **FR-010**: **Memorization Mode** MUST play the active verse $V_r$ times, advance to the next
  verse and repeat it $V_r$ times, and continue in that pattern to the end of the active range; upon
  reaching the end of the range it MUST begin a new pass from the start of the range if further
  passes remain per $M_r$, and MUST stop cleanly once all passes are complete.
- **FR-011**: **A–B Loop Mode** MUST let the student mark a start verse (A) and an end verse (B),
  restrict playback to that **inclusive** range, and loop within it — crossing chapter and matn
  section boundaries freely — until the student stops playback or clears the loop.
- **FR-012**: The system MUST prevent or automatically correct an inverted range (B before A) and
  MUST support a single-verse range where A and B are the same verse.
- **FR-013**: The system MUST let the student **clear** an active A–B loop, returning the playback
  range to the full matn without ending the listening session.
- **FR-014**: The verses inside an active A–B range MUST be **visually distinguished** in the verse
  list, including clear indication of the range's start and end.
- **FR-015**: Because mode follows configuration (FR-008), any counter or range change made while
  playback is in progress — including one that moves the session from one mode to another — MUST
  retune the running session in place rather than restarting it from the beginning of the matn.
- **FR-016**: The A–B range MUST compose fully with both counters: within an active A–B loop, $V_r$
  MUST repeat each verse of the range as it does elsewhere, and $M_r$ MUST bound the number of loop
  passes. Setting an A–B range MUST default $M_r$ to unlimited (preserving the product spec's "loops
  until manually stopped" behavior out of the box), while leaving the student free to set a finite
  pass count. The A–B range MUST therefore act as the **active range** parameter of one shared
  repetition engine rather than a separate playback path — Normal, Memorization, and A–B Loop differ
  only in their range and counter values.

**Repetition execution**

- **FR-017**: A repetition of a verse MUST begin **gaplessly** from the end of the previous
  repetition — repeating a verse into itself MUST meet the same no-click, no-silence standard as
  advancing between two different verses.
- **FR-018**: Wrapping from the end of a range back to its start MUST also be gapless.
- **FR-019**: A repetition MUST be counted when the verse's audio **reaches its end**; scrubbing
  backward or forward within the verse MUST NOT consume, skip, or reset a repetition.
- **FR-020**: The system MUST surface the **current repetition progress** — which repetition of the
  active verse is playing against the target ($V_r$), and which range pass is running against the
  target ($M_r$) — with unlimited shown as ∞ rather than a number.
- **FR-021**: **Next / previous** MUST abandon the remaining repetitions of the current verse and
  start the target verse with a fresh repetition count; within an A–B loop they MUST wrap at the
  range boundaries instead of leaving the range.
- **FR-022**: **Pause and resume** MUST preserve the repetition and pass counters — resuming
  continues within the same repetition rather than restarting the verse's count.
- **FR-023**: **Stop** MUST end the session and reset the in-flight repetition and pass counters,
  while retaining the student's configured counter values and mode for the next session.
- **FR-024**: Lowering $V_r$ or $M_r$ below the number of repetitions already completed MUST cause
  the current repetition or pass to finish and playback to advance — never a negative, stalled, or
  retroactively "owed" count.
- **FR-025**: Moving the A or B boundary such that the currently playing verse falls outside the new
  range MUST relocate playback into the new range rather than continuing outside it or ending the
  session.
- **FR-026**: The Phase 2 transport behaviors MUST continue to work unchanged during repetition —
  playback speed applies uniformly across every repetition, the active verse stays highlighted with
  auto-scroll for the duration of its repetitions, background playback and screen wake lock persist,
  and interruptions pause gracefully and resume at the correct repetition.
- **FR-027**: The system MUST NOT insert any silence between repetitions in this phase — every
  repetition follows immediately and gaplessly (FR-017). A configurable recite-along pause between
  repetitions is explicitly out of scope and deferred to a later phase.

**Resilience**

- **FR-028**: If a verse's audio asset is missing or unplayable, the system MUST skip that verse and
  its remaining repetitions and continue the drill with the next verse in the range, surfacing the
  same brief non-blocking notice established in Phase 2.
- **FR-029**: If the active range contains no playable verses, playback MUST stop cleanly with a
  message rather than looping over an empty range or repeatedly retrying.
- **FR-030**: An unlimited-repetition session MUST remain stable and responsive over long unattended
  runs — controls stay responsive and the session does not degrade, stall, or accumulate unbounded
  state.

**Data, state shape & offline**

- **FR-031**: The active repetition state — mode, $V_r$, $M_r$, the current repetition and pass
  index, and any in-progress A–B range — MUST be held in the shared playback session state, keyed by
  matn per FR-007, and shaped so that **Phase 4** can persist and restore it per matn (including
  resuming into a mid-loop range) without reshaping it.
- **FR-032**: All repetition behavior MUST work fully offline against the locally stored per-verse
  audio assets, assuming exactly one audio file per verse.

**Scope boundaries**

- **FR-033**: This phase MUST NOT persist repetition settings, mode, or an in-progress A–B range
  across app restarts, and MUST NOT implement "Continue Learning" resume (all Phase 4); nor MUST it
  introduce progress tracking, "mark as memorized", daily goals (Phase 6), search, bookmarks, notes
  (Phase 5), downloads (Phase 7), dark mode, or tablet-adaptive layouts (Phase 8). A configurable
  silent pause between repetitions is likewise out of scope (FR-027).

### Key Entities *(include if feature involves data)*

Phase 3 introduces **no new persisted entities**. It extends the transient playback session
established in Phase 2 with repetition state, shaped for Phase 4 to persist later.

- **Repetition Settings**: The student's configured drill for **one matn** — the verse repeat
  counter, the matn repeat counter (each a finite count or unlimited), the active playback mode, and
  any A–B range. Keyed by the matn's stable identity, so each matn's drill is independent and Phase 4
  can persist one record per matn.
- **Playback Mode**: A **derived** label — Normal Continuous, Memorization, or A–B Loop — computed
  from the counters and the presence of a loop range. It is displayed, never stored independently, so
  it cannot drift out of agreement with the configuration it describes.
- **Loop Range**: The inclusive start (A) and end (B) verses bounding playback in A–B Loop mode,
  identified by stable verse identity rather than screen position, so the range survives scrolling
  and can later be persisted and restored.
- **Repetition Progress (transient)**: How far the current drill has advanced — which repetition of
  the active verse is playing and which pass through the range is running. Exists only for the
  duration of the session.
- **Verse / Matn / Chapter**: Read as in Phase 2 to determine ordering and the boundaries that the
  active range either respects (Normal / Memorization) or overrides (A–B Loop).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A student can configure a drill (set both counters) and start playback in **no more
  than 4 taps** from the reading screen, and the displayed mode always matches the active
  configuration.
- **SC-002**: With a verse repeat counter of N, each verse plays **exactly N times** before playback
  advances, in **100%** of verses across a full matn.
- **SC-003**: With a matn repeat counter of M, the range plays **exactly M complete passes** and then
  stops, in **100%** of sessions.
- **SC-004**: Every repetition boundary — verse repeating into itself and end-of-range wrapping back
  to the start — is **gapless**, with no perceptible click or silence, in **100%** of transitions on
  a typical current mid-range device.
- **SC-005**: A counter or mode change made during playback takes effect **without any audible
  interruption** and within **1 second**, in **100%** of changes.
- **SC-006**: During an active A–B loop, **zero** verses outside the marked range are ever played.
- **SC-007**: The current repetition and current pass shown in the player match the audio actually
  playing, updating within **1 second** of each repetition boundary.
- **SC-008**: An unlimited-repetition session runs for at least **60 minutes** unattended with
  playback uninterrupted and controls still responsive within **300 milliseconds**.
- **SC-009**: Pausing, being interrupted by a call, or scrubbing within a verse **never** loses or
  duplicates a repetition — the count on resume matches the count before the interruption in
  **100%** of cases.
- **SC-010**: With both counters at their defaults, playback is **behaviorally identical** to Phase 2
  continuous playback — a zero-regression check on the existing experience.
- **SC-011**: All repetition behavior works with the device fully offline (airplane mode), making
  **zero** network requests.

## Assumptions

- **Phase 2 engine is the substrate**: Phase 3 layers counting and range logic on top of the
  existing playback session rather than introducing a second playback path. Gapless transitions,
  highlighting, auto-scroll, speed, background playback, wake lock, and interruption handling are
  inherited, not rebuilt.
- **Repetition boundary is the audio file boundary**: Because each verse is exactly one audio file,
  a repetition is one complete play of that file — no timestamp math or mid-file loop points are
  involved.
- **Counting is deterministic and testable without audio**: The repetition state machine (counters,
  advance decisions, range wrapping, unlimited handling) is shared logic that can be unit-tested
  against a fake audio engine with no device and no real audio files, per the project's test-first
  constraint.
- **Finite counter range**: Counters accept a minimum of 1 and a reasonable finite maximum (assumed
  99) beyond which the student is expected to choose unlimited instead; the exact maximum is a
  presentation detail, not a behavioral one.
- **"Range" is a single concept**: "The active range" means the full matn under Normal and
  Memorization modes, and the A–B range under A–B Loop mode. $M_r$ applies to whichever is active.
- **A–B selection interaction**: The student marks A and B from the verse list (for example via a
  verse's overflow / long-press action offering "set loop start" and "set loop end"). The exact
  gesture is a design detail; the requirement is that both boundaries are settable from the reading
  surface and the resulting range is visible there.
- **Mode is derived, not contradictory** *(confirmed via clarification)*: Normal Continuous is simply
  both counters at 1 with no active loop range; Memorization is any configuration with $V_r > 1$ or
  $M_r \ne 1$; A–B Loop is any configuration with an active range. The mode indicator reports the
  effective configuration rather than acting as a separate setting that can disagree with the
  counters.
- **Repetition ≠ progress**: Repeating a verse fifty times produces no memorization progress in this
  phase. Progress remains explicitly decoupled from playback count and is introduced in Phase 6.
- **In-session state only** *(confirmed via clarification)*: Counters, mode, and any active A–B range
  are held **per matn** and survive switching between متون while the app is running, but are lost on
  app restart. They are intentionally shaped for per-matn persistence, which arrives in Phase 4.
- **Default theme and phone layout**: Consistent with Phases 1–2, this phase targets the default
  (light) theme and phone layout; dark mode and tablet-adaptive layouts remain Phase 8.
- **Content already exists**: Phase 3 drills the متون and per-verse audio seeded in Phase 0; it does
  not author, import, or validate content.

## Dependencies

- **Phase 0 — Foundation & Data Model** MUST be complete: repetition operates over the UUID-identified
  verse ordering and the locked one-file-per-verse audio model.
- **Phase 1 — Reading Experience (static)** MUST be complete: the counter controls, mode indicator,
  repetition progress, and A–B range highlighting are surfaced on the existing reading screen and
  player bar.
- **Phase 2 — Core Audio Playback** MUST be complete and is the direct substrate: repetition extends
  the existing playback session, queue, and gapless advance rather than replacing them.
- Phase 3 is a hard prerequisite for **Phase 4 — Continue Learning & State Persistence**, which
  persists and resumes the repetition settings and in-progress A–B loop range defined here.
