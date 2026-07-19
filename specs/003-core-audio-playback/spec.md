# Feature Specification: Phase 2 — Core Audio Playback

**Feature Branch**: `003-core-audio-playback`

**Created**: 2026-07-19

**Status**: Draft

**Input**: User description: "read @docs/ROADMAP.md and create a specification for the Phase 2 — Core Audio Playback"

## Overview

Phase 2 brings the متن to life with sound. Building directly on the Phase 1 static
reading surface (Home library, Matn Details, verse list, RTL Arabic typography) and the
Phase 0 per-verse audio asset model, it delivers the **core audio playback experience**: a
student can play a matn's teacher-recorded recitation and follow along as the app reads it
aloud, verse by verse, with the active verse highlighted and the list auto-scrolling to keep
it in view.

Because the audio is produced as **one micro-file per verse**, the defining engineering
promise of this phase is **gapless playback** — the recitation must flow naturally from one
verse's file into the next with no audible click or silence. On top of that, Phase 2 adds the
familiar transport controls a listener expects (play / pause / resume / stop, next / previous
verse, a scrub bar within the active verse, and playback-speed adjustment) and the
system-level behaviors that make hands-free study practical: the audio keeps playing when the
app is in the background or the screen is off, the screen stays awake while playing, and
interruptions (phone calls, other apps taking audio focus, Bluetooth device changes) pause
playback gracefully instead of letting it silently die.

Two boundaries define this phase. First, **playback is plain continuous playback** — each
verse plays once and advances to the next until the matn ends, then stops. The repetition
matrix (per-verse and per-matn repeat counters, Memorization mode, A–B loop, unlimited
repetition) is **Phase 3** and is explicitly out of scope here. Second, **playback state is
in-session only** — Phase 2 does not persist the last verse or millisecond position across app
restarts; that cached "Continue Learning" state is **Phase 4**. Value is delivered when a
student can open a matn seeded from Phase 0 and listen to it, start to finish, gaplessly,
while reading along — entirely offline.

## Clarifications

### Session 2026-07-19

- Q: When a verse's audio file is missing or cannot be played during continuous playback, what should the app do? → A: Skip to the next verse automatically, showing a brief non-blocking notice.
- Q: What form should the playback-speed control take, and over what range? → A: Discrete steps 0.5× / 0.75× / 1.0× / 1.25× / 1.5×, defaulting to 1.0×.
- Q: After an audio interruption (call, focus loss, Bluetooth disconnect) ends, how should playback resume? → A: Auto-resume after a transient focus loss; stay paused for manual resume after a non-transient interruption (call, other media app, device disconnect).
- Q: How should a student start playback of a matn? → A: Both a per-verse play icon (start from that verse) and a global play control (start from the first/selected verse), feeding one session.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Listen to a matn read aloud while following the text (Priority: P1)

A student opens a matn, taps play on a verse (or a global play control), and the app begins
reciting from that verse using the teacher's recording. As each verse plays, it is clearly
highlighted and the list auto-scrolls to keep it comfortably in view. When a verse's audio
finishes, playback flows seamlessly into the next verse with no audible gap or click, and
continues verse by verse until the end of the matn, then stops. At any point the student can
pause, resume, or stop. This is the heart of the phase — turning the static reading surface
into a guided, read-along recitation.

**Why this priority**: Hearing the teacher's recitation while reading is the core of the
product's memorization promise, and gapless verse-to-verse flow is the single most important
quality signal for this phase. A matn that can be played from a verse, highlights and follows
along, advances gaplessly, and can be paused/resumed/stopped is the smallest slice that proves
audio playback works. Everything else in this phase refines or protects this loop.

**Independent Test**: Open a matn seeded from Phase 0, start playback from a verse, and verify
the recitation plays, the active verse is highlighted, the list auto-scrolls to follow it,
playback advances to each following verse with no audible gap or click, and pause / resume /
stop each behave correctly — all with no network connection.

**Acceptance Scenarios**:

1. **Given** an opened matn with per-verse audio, **When** the student starts playback from a
   verse, **Then** that verse's audio begins playing and the verse is visually highlighted as
   active.
2. **Given** a verse is playing, **When** its audio reaches the end, **Then** playback advances
   automatically to the next verse with no audible gap or click, and the highlight and
   auto-scroll move to the newly active verse.
3. **Given** playback is progressing, **When** each verse becomes active, **Then** the list
   auto-scrolls so the active verse is kept comfortably in view (not jarringly snapped to an
   edge).
4. **Given** audio is playing, **When** the student pauses, **Then** playback stops at the
   current position and the active verse remains highlighted; **When** the student resumes,
   **Then** playback continues from the same position.
5. **Given** audio is playing or paused, **When** the student stops, **Then** playback ends and
   the active-verse highlight is cleared.
6. **Given** playback reaches the last verse of the matn, **When** that verse finishes, **Then**
   playback stops (it does not loop or restart), because repetition is out of scope for this
   phase.
7. **Given** the device has no network connectivity, **When** a matn is played, **Then** all
   audio plays successfully from the locally stored per-verse files.

---

### User Story 2 - Control and fine-tune playback (Priority: P2)

While listening, the student uses a persistent control bar to jump to the next or previous
verse, to scrub within the currently playing verse to replay or skip a phrase, and to change
the playback speed so a fast recitation can be slowed down for careful memorization (or sped up
for review). Changes apply immediately and the chosen speed carries across verse transitions.

**Why this priority**: Continuous play (P1) delivers the core value, but real memorization
practice needs manual control — jumping back a verse, scrubbing to re-hear a phrase, and
slowing the recitation down. These controls make the playback usable for study rather than just
passive listening, so they are the next slice after the core loop.

**Independent Test**: During playback, use next / previous to move between verses and confirm
the correct verse becomes active and plays; scrub within the active verse and confirm audio
seeks to the chosen position; change the playback speed and confirm the recitation speed changes
immediately and remains applied as playback advances to the next verse.

**Acceptance Scenarios**:

1. **Given** a persistent control bar is shown during playback, **When** the student taps next,
   **Then** playback moves to the following verse, which becomes active and begins playing.
2. **Given** playback is on a verse, **When** the student taps previous, **Then** playback moves
   to the preceding verse, which becomes active and begins playing.
3. **Given** the first verse is active, **When** the student taps previous, **Then** the behavior
   is well defined and safe (it restarts the current verse rather than moving before the start
   of the matn); **Given** the last verse is active, **When** the student taps next, **Then**
   playback stops at the end of the matn rather than erroring.
4. **Given** a verse is playing, **When** the student drags the scrub bar, **Then** audio seeks
   to the chosen position within that verse and the displayed position reflects the new point.
5. **Given** playback is active, **When** the student selects a different playback speed within
   the supported range, **Then** the recitation speed changes immediately without stopping
   playback.
6. **Given** a non-default playback speed is selected, **When** playback advances to the next
   verse, **Then** the chosen speed remains applied to the new verse.

---

### User Story 3 - Keep listening hands-free and through interruptions (Priority: P3)

A student props the phone up (or pockets it) and recites along hands-free. While audio is
playing, the screen stays awake so they can keep reading; when they switch to another app or
turn the screen off, the recitation keeps playing and can be controlled from a system media
notification / lock-screen controls. If a phone call comes in, another app takes over audio, or
Bluetooth headphones disconnect, playback pauses gracefully instead of continuing into the void
or dying silently — and can be resumed once the interruption is over.

**Why this priority**: Background playback, wake lock, and interruption handling are what make
the app practical for real, hands-free memorization sessions, and the constitution treats
interruption handling as contractual. They protect and extend the core loop rather than
providing it, so they follow P1 and P2 — but a playback feature that stops the moment the screen
locks or a notification arrives would feel broken, so they are firmly in this phase.

**Independent Test**: Start playback, lock the screen or switch apps, and confirm audio
continues and can be controlled from the media notification / lock screen; confirm the screen
does not auto-sleep while playing; then simulate an interruption (incoming call or audio-focus
loss, or disconnect Bluetooth) and confirm playback pauses gracefully and can be resumed.

**Acceptance Scenarios**:

1. **Given** audio is playing, **When** the student switches to another app or turns the screen
   off, **Then** playback continues uninterrupted in the background.
2. **Given** audio is playing in the background, **When** the student views the system media
   notification / lock screen, **Then** controls for at least play / pause and next / previous
   verse are available and operate the playback.
3. **Given** audio is actively playing, **When** the app is in the foreground, **Then** the
   screen is kept awake and does not auto-dim/sleep from inactivity; **When** playback is paused
   or stopped, **Then** the normal screen-timeout behavior resumes.
4. **Given** audio is playing, **When** an interruption occurs (incoming call, another app
   requests audio focus, or a Bluetooth output device disconnects), **Then** playback pauses
   rather than continuing silently or crashing.
5. **Given** playback was paused by a transient interruption, **When** the interruption ends and
   audio focus returns, **Then** playback resumes automatically; **Given** playback was paused by
   a non-transient interruption, **When** it ends, **Then** playback remains paused and the
   student can resume manually.

---

### Edge Cases

- **Play from the middle**: Starting playback from a verse in the middle of the matn plays from
  that verse forward, not from the beginning.
- **Rapid next/previous**: Quickly tapping next or previous several times settles on the correct
  final verse and plays it, without stacking audio or skipping erratically.
- **Scrub to the very end of a verse**: Scrubbing to the end of the active verse's audio triggers
  the same gapless advance to the next verse as natural completion.
- **Next on the last verse / previous on the first verse**: Boundaries are handled gracefully —
  next at the end stops cleanly; previous at the start restarts the current verse.
- **Missing or unreadable audio file for a verse**: If a verse's audio asset is missing or cannot
  be read, playback skips automatically to the next verse with a brief non-blocking notice rather
  than freezing or crashing; if no playable verse remains, it stops cleanly with a message.
- **Very short verse audio**: A very short verse file still highlights, advances gaplessly, and
  does not cause the highlight/auto-scroll to flicker or skip.
- **Interruption while paused**: An interruption arriving while already paused does not cause an
  unexpected auto-resume when focus returns.
- **Headphone unplug**: Removing wired/Bluetooth output pauses playback (rather than blasting
  audio from the speaker), consistent with interruption handling.
- **Speed change while paused**: Selecting a new speed while paused applies it when playback
  resumes.
- **App backgrounded then returned**: Returning to the app after background playback shows the
  correct active verse, highlight, position, and play/pause state matching what actually played.

## Requirements *(mandatory)*

### Functional Requirements

**Starting & core transport**

- **FR-001**: The system MUST let a student start playback via BOTH a per-verse play control on a
  verse row (playback begins from that verse) AND a global play control on the reading screen
  (playback begins from the first verse, or the last-selected verse); both feed one shared playback
  session and begin playing the chosen verse's stored audio.
- **FR-002**: The system MUST provide play, pause, resume, and stop controls for the active
  playback session.
- **FR-003**: Pausing MUST hold playback at the current position and keep the active verse
  highlighted; resuming MUST continue from that same position.
- **FR-004**: Stopping MUST end the playback session and clear the active-verse highlight.

**Continuous, gapless progression**

- **FR-005**: When a verse's audio finishes, the system MUST automatically advance to the next
  verse in matn-global reading order and begin playing it.
- **FR-006**: Verse-to-verse transitions MUST be gapless — the next verse's audio MUST be
  pre-buffered so there is no audible gap, click, or silence between separate per-verse files.
- **FR-007**: When playback reaches and finishes the last verse of the matn, the system MUST stop
  (Phase 2 performs no repetition, looping, or restart).

**Active-verse highlighting & auto-scroll**

- **FR-008**: The system MUST clearly highlight the currently playing verse as active, and MUST
  move the highlight to each new verse as playback advances.
- **FR-009**: The verse list MUST auto-scroll to keep the active verse comfortably in view
  (centered or near-top), smoothly rather than jarringly, as playback progresses.

**Navigation, scrubbing & speed**

- **FR-010**: The system MUST present a persistent playback control bar (the "player bar") while a
  playback session is active, exposing the transport controls and current playback status.
- **FR-011**: The system MUST provide next-verse and previous-verse controls that move playback to
  the adjacent verse, which becomes the active, playing verse.
- **FR-012**: Next / previous MUST handle matn boundaries safely: next on the last verse stops at
  the end of the matn; previous on the first verse restarts the current verse — neither errors nor
  moves outside the matn.
- **FR-013**: The system MUST provide a scrub bar for the currently active verse, letting the
  student seek to any position within that verse's audio, with the displayed position reflecting
  the seek.
- **FR-014**: The system MUST provide a playback-speed control offering the discrete steps 0.5×,
  0.75×, 1.0×, 1.25×, and 1.5× (defaulting to 1.0×), applied immediately to the active audio with
  the recitation kept intelligible (pitch preserved), and the selected step MUST persist across
  verse transitions for the duration of the session.

**Background playback, wake lock & system controls**

- **FR-015**: Playback MUST continue when the app is backgrounded or the screen is off, until
  paused or stopped by the student, an interruption, or the end of the matn.
- **FR-016**: While audio is playing in the background, the system MUST expose media controls via
  the platform's media notification / lock-screen surface for at least play / pause and next /
  previous verse, and those controls MUST operate the same playback session.
- **FR-017**: While audio is actively playing with the app in the foreground, the system MUST keep
  the screen awake, and MUST release the wake lock (restore normal screen timeout) when playback
  is paused or stopped.

**Audio interruption handling**

- **FR-018**: On an audio interruption — incoming call, another app requesting audio focus, or a
  Bluetooth / wired output device disconnecting — the system MUST pause playback gracefully rather
  than continuing silently or crashing.
- **FR-019**: After a transient interruption ends and audio focus is regained, the system MUST
  resume playback automatically; after a non-transient interruption, playback MUST remain paused
  for the student to resume manually.

**Resilience**

- **FR-020**: If a verse's audio asset is missing or cannot be played, the system MUST automatically
  skip to the next verse and continue playback, surfacing a brief, non-blocking notice — rather than
  stopping, freezing, or failing silently. If no playable verse remains, playback stops cleanly with
  a message.

**Data source & offline**

- **FR-021**: All playback MUST use the locally stored per-verse audio assets and content
  established in Phase 0, consumed through the domain / repository layer, and MUST function fully
  with no network connection.
- **FR-022**: The playback engine MUST assume exactly one audio file per verse (the locked
  per-verse micro-file model) and MUST NOT assume a shared or continuous audio file to seek across
  verse boundaries.

**Scope boundaries**

- **FR-023**: This phase MUST NOT include the repetition matrix — per-verse repeat counter,
  per-matn repeat counter, Memorization mode, A–B loop mode, or unlimited-repetition handling
  (all Phase 3) — nor cross-session persistence of the last verse / playback position / settings
  or "Continue Learning" resume (Phase 4), nor progress tracking, "mark as memorized", search,
  bookmarks, notes, downloads, dark mode, or tablet-adaptive layouts (later phases). Phase 2
  performs plain continuous, single-pass playback whose state lives only for the current session.

### Key Entities *(include if feature involves data)*

Phase 2 introduces **no new persisted entities**. It reads the Phase 0 content and audio
references and manages a transient, in-memory playback state that is not saved across app
restarts (that persistence is Phase 4).

- **Verse (بيت / آية)**: Read for playback — its matn-global order, its Arabic text (for the
  active-verse highlight against the Phase 1 reading surface), and its **audio reference** (the
  single per-verse audio file to play).
- **Matn (متن)** / **Chapter (فصل)**: Read to determine the ordered sequence of verses to play
  and the boundaries at which continuous playback stops.
- **Playback Session (transient)**: The in-memory state of the current listening session —
  which matn and verse are active, the position within the active verse, whether it is
  playing/paused, and the selected playback speed. This state exists only for the duration of the
  session and is intentionally **not** persisted across restarts in this phase.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A student can start listening to a matn in **no more than 2 taps** from its reading
  screen (e.g., open control / tap a verse's play).
- **SC-002**: Verse-to-verse transitions during continuous playback are **gapless** — in **100%**
  of automatic advances there is no perceptible gap, click, or silence between per-verse files on
  a typical current mid-range device.
- **SC-003**: The active-verse highlight and auto-scroll track the audio so the currently playing
  verse is highlighted and visible within **1 second** of that verse beginning, for **100%** of
  verse transitions.
- **SC-004**: Play, pause, resume, stop, next, and previous each take visible effect within **300
  milliseconds** of the student's action.
- **SC-005**: Changing playback speed takes audible effect **immediately** (without stopping or
  restarting playback) and the chosen speed remains applied across every subsequent verse in the
  session.
- **SC-006**: Scrubbing within the active verse seeks audio to the chosen position, and reaching
  the end of a verse (by scrub or natural completion) triggers the same gapless advance in
  **100%** of cases.
- **SC-007**: Playback continues without interruption when the app is backgrounded or the screen
  is off, and can be controlled from the media notification / lock screen, in **100%** of
  sessions.
- **SC-008**: The screen remains awake for the entire duration of active foreground playback and
  returns to normal timeout behavior once playback is paused or stopped.
- **SC-009**: In **100%** of interruption events (call, audio-focus loss, output-device
  disconnect), playback pauses gracefully with no crash; transient interruptions auto-resume and
  non-transient ones allow manual resume.
- **SC-010**: All playback occurs with the device fully offline (airplane mode), making **zero**
  network requests.

## Assumptions

- **Content and audio are already present**: Phase 2 assumes Phase 0 seeded at least one matn
  whose verses each have a correctly trimmed per-verse audio file (consistent lead-in/lead-out
  silence and loudness). Phase 2 plays what exists; it does not author, import, validate, or
  process audio content.
- **Plain continuous playback only**: Consistent with the roadmap, Phase 2 plays each verse once
  and advances until the matn ends, then stops. This is the "Normal Continuous" behavior
  (effectively $V_r = 1$, $M_r = 1$); the configurable repetition matrix and A–B loop are Phase 3.
- **In-session state only**: Playback position, active verse, and selected speed live only for the
  current session and are intentionally not persisted across app restarts. The "Continue Learning"
  cache (last matn/verse, millisecond position, settings, in-progress loop) is Phase 4.
- **Playback speed range** *(confirmed via clarification)*: The speed control offers the discrete
  steps **0.5×, 0.75×, 1.0×, 1.25×, 1.5×**, defaulting to **1.0×**, matching the product spec's
  0.5×–1.5× range; pitch is preserved at non-1.0× so the recitation stays intelligible.
- **Starting playback** *(confirmed via clarification)*: Playback can be started from a verse-level
  play icon on the reading screen (per the Phase 1 verse row) and from a global play control; both
  feed the same single playback session.
- **Scrub granularity**: The scrub bar operates **within the currently active verse only**
  (per-verse files), not across the whole matn; moving between verses uses next / previous.
- **Interruption resume policy** *(confirmed via clarification)*: Transient audio-focus loss (e.g.,
  a short notification sound) auto-resumes when focus returns; a non-transient loss (a phone call,
  another media app taking over, or output-device disconnect) leaves playback paused for manual
  resume. This satisfies the constitution's "pause and allow resume, never silently die" rule.
- **Media notification controls**: Background control exposes at least play / pause and next /
  previous; a scrub bar and speed control in the notification are not required for this phase.
- **Highlighting reuses the Phase 1 surface**: Active-verse highlighting and auto-scroll are
  layered onto the existing Phase 1 reading screen rather than introducing a new reading surface.
- **Default theme and phone layout**: Phase 2 targets the default (light) theme and phone layout,
  consistent with Phase 1; dark mode and tablet-adaptive layouts remain Phase 8.
- **Shared-first playback logic**: The playback session/state logic is shared from a single source
  across both target platforms, with only the genuinely platform-specific audio engine at the edge
  (ExoPlayer on Android, AVQueuePlayer on iOS), per the project's multiplatform approach.

## Dependencies

- **Phase 0 — Foundation & Data Model** MUST be complete: Phase 2 plays the per-verse audio assets
  and reads verse ordering through the Phase 0 domain / repository layer and local store, and
  relies on the locked one-file-per-verse audio model.
- **Phase 1 — Reading Experience (static)** MUST be complete: Phase 2 layers playback,
  active-verse highlighting, and auto-scroll onto the Phase 1 reading screen, control bar host,
  and RTL verse list.
- Phase 2 is a hard prerequisite for **Phase 3 — Repetition Engine**, which builds the repeat
  counters, playback modes, and A–B loop directly on this playback engine, and for **Phase 4 —
  Continue Learning**, which persists and resumes this playback state.
