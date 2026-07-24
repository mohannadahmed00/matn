# Feature Specification: Phase 4 — Continue Learning & State Persistence

**Feature Branch**: `005-continue-learning`

**Created**: 2026-07-24

**Status**: Draft

**Input**: User description: "read @docs/ROADMAP.md and create a specification for the Phase 4 — Continue Learning & State Persistence"

## Overview

Phase 4 makes Matn **remember**. Through Phases 1–3 the app became a capable memorization tool —
a student can open a matn, read it, hear the teacher's recitation gaplessly, and drill it with
repeat counters and A–B loops. But every one of those sessions evaporates the moment the app
closes. Reopening Matn drops the student back at the library with nothing carried over: the matn
they were working through, the verse they reached, how far into that verse they were, and the
whole drill they had configured are all gone, to be set up again by hand.

This phase closes that gap. It **persists the student's learning session locally** — which matn
was open, which verse was last listened to, the position within that verse, the per-matn
repetition settings, and an in-progress A–B loop range — writing it as the session moves rather
than hoping for a clean exit. It then surfaces that saved state as a **Continue Learning** entry
on the Home screen: a single, prominent, one-tap way back into exactly what the student was doing.

The defining promise of the phase is that **the drill resumes, not just the verse**. Restoring a
verse number would be a bookmark. Matn's value is in the repetition matrix, so a student who was
looping verses 12–18 seven times each must come back to that loop still configured — not to a
bare verse with default counters. Phase 3 deliberately shaped its state to make this possible:
settings are already scoped per matn and the loop range is already anchored to stable verse
identity, so Phase 4 persists that shape rather than redesigning it.

Two boundaries define the phase. First, it persists **session state, not progress** — no "mark as
memorized", completion percentage, or daily goal appears here; the recall-based progress metric is
**Phase 6**, and the constitution requires it to stay decoupled from playback activity. Second, it
is **local-only** — state lives on the device, with no account, cloud backup, or cross-device sync;
those are v2, and the constitution only requires that the data shape not block them later. Value is
delivered when a student can close the app mid-drill, reopen it, tap once, and be back exactly where
they were.

## Clarifications

### Session 2026-07-24

- Q: When the student taps Continue Learning, should playback start automatically or should the session be restored in a paused state? → A: Auto-play immediately — a single tap resumes listening, without a second explicit play.
- Q: On resume, should playback restart from the exact saved position within the verse, or from the beginning of the saved verse? → A: From the exact saved millisecond position within the verse.
- Q: What event establishes or updates the pointer that drives the Continue Learning entry — opening a matn, or actually playing audio in it? → A: Only when audio actually plays. Browsing into a matn without listening never changes the entry, so a real in-progress session is not displaced by a glance. The concept is "last listened matn", not "last opened".
- Q: When the saved verse no longer exists but its matn does, where should resuming land? → A: The nearest surviving verse in reading order — nearest preceding first, else nearest following — preserving the student's place as closely as the content allows.
- Q: What should "clear the saved session" clear? → A: Only the Continue Learning entry (the last-listened pointer). Each matn retains its own saved verse, position, and repetition settings, so the action is non-destructive and reversible by listening again.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Pick up exactly where I left off, in one tap (Priority: P1)

A student was working through a matn on the bus and closed the app. Later they open Matn again.
The Home screen shows a **Continue Learning** entry at the top, naming the matn they were in and
the verse they had reached, with enough context to recognize it at a glance. They tap it once and
land directly in that matn's reading screen, scrolled to that verse, with the session restored —
no navigating the library, no hunting for their place, no re-configuring anything.

**Why this priority**: This is the phase's headline value and the reason the roadmap includes it.
A student who has to re-find their place every session pays a friction tax on every study session,
which is exactly the friction the product exists to remove. A Continue Learning entry that appears,
names the right place, and returns the student to it is the smallest slice that delivers the phase's
promise — everything else deepens the fidelity of that restoration.

**Independent Test**: Listen partway into a matn, fully close the app, reopen it, and verify the
Home screen shows a Continue Learning entry naming that matn and verse; tap it and verify the app
opens that matn at that verse — all offline.

**Acceptance Scenarios**:

1. **Given** a student has listened partway into a matn and closed the app, **When** they reopen the
   app, **Then** the Home screen shows a Continue Learning entry identifying that matn and the verse
   they reached.
2. **Given** the Continue Learning entry is shown, **When** the student taps it, **Then** the app
   opens that matn's reading screen positioned at the saved verse, with that verse visible and
   marked as the active verse, and playback resumes automatically from the exact saved position
   within that verse — no second tap required.
3. **Given** the app has never been used to listen to anything, **When** the student opens the Home
   screen, **Then** no Continue Learning entry is shown and the library is presented normally, with
   no empty or broken placeholder.
4. **Given** a saved session exists, **When** the student opens the app with no network connection,
   **Then** the Continue Learning entry and the resumed session work fully offline.
5. **Given** the student resumes and then listens further, **When** they close and reopen the app
   again, **Then** the Continue Learning entry reflects the newer position, not the older one.
6. **Given** another app holds audio focus (for example an active call), **When** the student taps
   Continue Learning, **Then** the session is restored correctly but held paused rather than playing
   over the other app.

---

### User Story 2 - Resume the drill, not just the verse (Priority: P2)

A student had set up a serious drill — repeat each verse 7 times, loop verses 12 through 18, and
keep going indefinitely — then closed the app mid-session. On returning and tapping Continue
Learning, the app restores not only the verse but the **entire configuration**: the A–B loop is
still set to 12–18, the verse repeat counter is still 7, and the derived mode indicator again reads
A–B Loop. The student presses on with the same drill instead of rebuilding it from defaults.
Separately, a matn they had configured differently keeps *its own* settings — opening it shows the
counters they last used there, not the ones from another matn.

**Why this priority**: Restoring only a verse number would reduce Matn to a bookmark and lose the
repetition matrix that makes it a memorization companion. Phase 3 explicitly shaped its state so
this phase could persist it, so this is the point of the whole exercise — but it builds on top of
the entry and navigation delivered in P1, so it follows it.

**Independent Test**: Configure a distinctive drill on a matn (non-default counters plus an A–B
range), close the app, reopen, resume, and verify the counters, loop range, and derived mode all
match what was set; then configure a second matn differently and verify each matn independently
restores its own settings.

**Acceptance Scenarios**:

1. **Given** a student had set a verse repeat counter and a matn repeat counter on a matn, **When**
   they resume that matn after an app restart, **Then** both counters are restored to the values
   they last used for that matn.
2. **Given** a student had an active A–B loop range when the app closed, **When** they resume,
   **Then** the same inclusive range is active, its verses are visually marked as the range, and
   playback is restricted to it.
3. **Given** repetition settings are restored, **When** the mode indicator is displayed, **Then** it
   shows the mode derived from the restored counters and range — the mode is recomputed, never
   restored as an independently stored value that could contradict them.
4. **Given** two different matn were each configured with different repetition settings, **When**
   the student opens each after a restart, **Then** each shows its own saved settings and neither
   affects the other.
5. **Given** a matn that has never been configured, **When** the student opens it after a restart,
   **Then** it starts at the Phase 3 defaults rather than inheriting another matn's settings.
6. **Given** a student had set an unlimited (∞) counter, **When** they resume, **Then** the counter
   is restored as unlimited rather than being converted to a finite number or reset to the default.

---

### User Story 3 - My place survives however the app closed, and fails safely (Priority: P3)

The student's saved place holds up regardless of *how* the app ended — backgrounded and later
evicted by the system, force-closed from the app switcher, ended by a crash, or lost to a device
restart. The app does not rely on a graceful shutdown to have written state. And when the saved
state can no longer be honored — the matn it points at was removed, or the verse no longer exists
because content changed — the app degrades quietly: it does not show a broken entry, and it never
fails to open.

**Why this priority**: Persistence that only survives a polite exit is not persistence; the most
common real-world exits are abrupt. Equally, a Continue Learning entry that crashes or dead-ends on
stale content would be worse than none at all. These protect the guarantees made in P1 and P2 rather
than adding new capability, so they come last — but a resume feature that loses state on a force-quit
would read as simply broken.

**Independent Test**: Listen partway in, then terminate the app abruptly (force-close from the app
switcher rather than a clean exit), reopen, and verify the saved place is intact and accurate; then
make the saved target invalid (remove or alter the referenced content) and verify the app opens
cleanly with the entry either absent or safely handled.

**Acceptance Scenarios**:

1. **Given** a student is listening, **When** the app is force-closed from the app switcher without
   a graceful exit, **Then** on reopening, the saved matn, verse, and settings reflect the session
   up to shortly before the termination.
2. **Given** a student is listening, **When** the app is backgrounded and later evicted by the
   operating system, **Then** the saved state is intact on the next launch.
3. **Given** saved state points at a matn that is no longer available, **When** the student opens
   the Home screen, **Then** no broken Continue Learning entry is shown and the Home screen loads
   normally.
4. **Given** saved state points at a verse that no longer exists within a matn that does, **When**
   the student resumes, **Then** the app opens that matn at the nearest surviving verse in reading
   order, starting from the beginning of that verse, with the matn's repetition settings retained.
5. **Given** the student is listening and the session advances from verse to verse, **When** each
   verse transition occurs, **Then** the saved state is updated so that an abrupt termination
   immediately afterward loses at most the current verse's partial progress.

---

### Edge Cases

- **Resume after finishing a matn**: If the saved session had reached and completed the last verse,
  resuming lands somewhere sensible and defined (rather than past the end of the matn).
- **Resume into an A–B loop whose range is partly gone**: If content changed so the saved range's
  start or end verse no longer exists, the range is corrected or cleared rather than left pointing
  at nothing.
- **Saved verse falls outside its own saved loop range**: State that is internally inconsistent
  (verse outside range) is reconciled on restore rather than producing playback that immediately
  jumps or stalls.
- **Resuming a matn already open**: Tapping Continue Learning while already in that matn does not
  create a second session or restart it unexpectedly.
- **Switching matn mid-session**: Opening a different matn and listening updates which matn the
  Continue Learning entry points at, while the previous matn keeps its own saved settings.
- **Browsing without listening**: Opening one or several matn without starting playback leaves the
  Continue Learning entry pointing at the last matn actually listened to — a glance never displaces
  real progress.
- **Configured but never played**: Setting up a drill on a matn and closing the app before pressing
  play preserves those settings for that matn, without making it the Continue Learning entry.
- **Dismiss then reopen the matn**: After dismissing the Continue Learning entry, opening that same
  matn directly still restores its saved verse, position, and repetition settings — dismissal hides
  the shortcut, it does not erase progress.
- **Dismiss then listen again**: Listening to any matn after a dismissal re-establishes the Continue
  Learning entry pointing at that matn.
- **Very first verse, zero position**: A session saved at the very start of a matn produces a valid
  entry rather than being mistaken for "no saved state".
- **Rapid open-and-close**: Opening a matn and immediately closing the app records a coherent state
  rather than a partially written one.
- **Missing audio for the saved verse**: If the saved verse's audio is unplayable, resuming applies
  the Phase 2 skip-with-notice behavior rather than failing to resume at all.
- **Resuming while another app holds audio**: Because resume auto-plays, tapping Continue Learning
  during a call or while another app owns audio focus restores the session paused rather than
  seizing audio or silently failing.
- **Saved position at the very end of a verse**: A position saved milliseconds before a verse ends
  resumes without stalling — it either completes the verse or advances gaplessly, never hangs.
- **Storage unavailable or write fails**: If saved state cannot be written, the listening session
  continues working normally — persistence failure never breaks playback.
- **Corrupt or unreadable saved state**: Unreadable state is treated as "no saved state" and the app
  starts clean instead of failing to launch.

## Requirements *(mandatory)*

### Functional Requirements

**What is captured**

- **FR-001**: The system MUST persist locally, per matn, the student's session state: the last
  listened verse, the playback position within that verse, the verse repeat counter, the matn repeat
  counter, and any active A–B loop range.
- **FR-002**: The system MUST additionally persist a single pointer to the **most recently listened
  matn**, so the Home screen can identify which saved session to offer as Continue Learning.
- **FR-002a**: The pointer in FR-002 MUST be established or updated **only when audio actually begins
  playing** in a matn. Merely opening a matn's reading screen, scrolling it, or adjusting its settings
  without starting playback MUST NOT change which matn Continue Learning offers.
- **FR-002b**: A per-matn saved session record (FR-001) MUST be created on first **playback** in that
  matn **or** on the first **repetition-settings change** made for it — a drill configured but not yet
  played MUST survive a restart. Mere navigation into a matn, with neither playback nor a settings
  change, MUST NOT create a record. Creating a record this way MUST NOT by itself move the FR-002
  pointer, which remains playback-gated.
- **FR-003**: Persisted verse and matn references MUST use stable identities rather than display
  positions or list indices, so saved state survives reordering, scrolling, and content updates.
- **FR-004**: The playback position MUST be persisted at millisecond granularity.
- **FR-005**: The system MUST NOT persist the playback **mode**. Mode remains derived from the
  restored counters and range, so a restored session can never display a mode that contradicts its
  own configuration.
- **FR-006**: The system MUST NOT persist in-flight repetition progress — which repetition of a verse
  or which pass through a range was underway. Those are session-scoped in Phase 3; a resumed session
  starts a fresh count against the restored targets.
- **FR-007**: Unlimited (∞) counter values MUST be persisted and restored as unlimited, remaining
  distinguishable from any finite value and from an unset value.

**When it is captured**

- **FR-008**: The system MUST update saved state on **every verse transition** and on **every
  repetition-settings change** (counter change, A–B range set, changed, or cleared).
- **FR-009**: The system MUST update saved state when a listening session is paused, stopped, or
  backgrounded, and periodically during continuous playback, such that an abrupt termination loses at
  most the current verse's partial position.
- **FR-010**: Saving state MUST NOT depend on a graceful app shutdown — state MUST survive
  force-close, system eviction, crash, and device restart.
- **FR-011**: Persisting state MUST NOT interrupt, stall, or audibly affect playback; a failure to
  write MUST leave the listening session fully functional.

**The Continue Learning entry**

- **FR-012**: When saved state exists, the Home screen MUST present a **Continue Learning** entry,
  presented per the Home / Library design registered in `docs/DESIGN-SOURCE.md`.
- **FR-013**: The entry MUST identify the matn and the verse the student will return to, with enough
  context to be recognized at a glance.
- **FR-014**: The entry MUST be actionable in a **single tap**, taking the student directly into the
  restored session with no intermediate screen or confirmation.
- **FR-015**: When no saved state exists, or the saved state cannot be honored, the Home screen MUST
  omit the entry entirely and present the library normally — never a broken, empty, or placeholder
  entry.
- **FR-016**: The entry MUST reflect the most recent session; after resuming and listening further,
  it MUST describe the newer position.
- **FR-017**: The student MUST be able to dismiss the Continue Learning entry. Dismissing MUST clear
  only the last-listened pointer (FR-002), so the entry is no longer offered.
- **FR-017a**: Dismissing MUST be **non-destructive**: every matn's saved verse, playback position,
  and repetition settings MUST be retained, so opening any matn afterwards still restores its drill
  (FR-023). Listening to any matn again MUST re-establish the entry.

**Restoring a session**

- **FR-018**: Resuming MUST open the saved matn's reading screen positioned at the saved verse, with
  that verse presented as the active verse and visible without manual scrolling.
- **FR-019**: Resuming MUST restore that matn's repetition settings — both counters and any A–B loop
  range — so the drill continues as configured rather than at defaults.
- **FR-020**: A restored A–B loop range MUST be active on resume: playback is restricted to it and
  its verses are visually marked as the range, matching Phase 3 behavior.
- **FR-021**: Resuming via Continue Learning MUST begin playback automatically, without requiring a
  second explicit play action — the single tap both restores the session and resumes listening.
- **FR-022**: Resuming MUST begin playback at the **exact saved millisecond position** within the
  saved verse, not at the start of that verse.
- **FR-022a**: Because resuming starts audio immediately, it MUST respect the Phase 2 audio rules on
  entry — if audio focus cannot be acquired or an interruption is already active, the session MUST
  restore correctly in a paused state rather than failing to resume or playing over another app.
- **FR-023**: Opening a matn directly (not via Continue Learning) MUST also restore that matn's own
  saved repetition settings, since settings are scoped per matn.
- **FR-024**: A matn with no saved settings MUST open at the Phase 3 defaults.

**Resilience**

- **FR-025**: If saved state references a matn that no longer exists, the system MUST treat it as no
  saved state and MUST NOT surface a broken entry.
- **FR-026**: If saved state references a verse that no longer exists within an existing matn, the
  system MUST resume at the **nearest surviving verse in reading order** — the nearest preceding verse
  if one exists, otherwise the nearest following verse — rather than failing or discarding the
  session. Playback MUST begin at the **start** of that substituted verse, since the saved millisecond
  offset belongs to a verse that no longer exists. That matn's repetition settings MUST be retained.
- **FR-027**: If a saved A–B range references verses that no longer exist, the system MUST correct or
  clear the range rather than activating an invalid range.
- **FR-028**: Internally inconsistent saved state — such as a saved verse outside its own saved loop
  range — MUST be reconciled to a valid session on restore.
- **FR-029**: Unreadable or corrupt saved state MUST be treated as no saved state; the app MUST start
  normally rather than failing to launch.

**Data & offline**

- **FR-030**: All saved state MUST live locally on the device and MUST be readable and writable with
  no network connection.
- **FR-031**: The saved-state shape MUST NOT block the later addition of remote accounts, cloud
  backup, or cross-device sync.

**Scope boundaries**

- **FR-032**: This phase MUST NOT introduce progress tracking, "mark as memorized", completion
  percentages, or daily goals (Phase 6); search, bookmarks, or notes (Phase 5); downloads or storage
  management (Phase 7); dark mode or tablet-adaptive layouts (Phase 8); nor any account, cloud
  backup, or cross-device sync (v2). It persists and restores session state only.

### Key Entities *(include if feature involves data)*

Phase 4 is the first phase to **persist** playback-related state. It turns the transient session
state shaped by Phases 2 and 3 into durable local records.

- **Saved Matn Session** (new, persisted): One record per matn the student has either listened to or
  configured — the last listened verse, the position within it, the verse and matn repeat counters,
  and any A–B loop range. Created on first playback or first settings change for that matn (FR-002b),
  never on mere navigation. Keyed by the matn's stable identity, matching the per-matn scoping
  established in Phase 3. Notably **excludes** the derived playback mode and in-flight repetition
  progress, both of which are recomputed or reset rather than stored.
- **Last Listened Matn Pointer** (new, persisted): A single reference identifying which matn the
  student most recently *played audio in* — the input for the Continue Learning entry. Updated only
  when playback begins (FR-002a), so browsing cannot displace a real in-progress session. Separate
  from the per-matn records, because every matn keeps its own settings while only one is "most
  recently listened".
- **Continue Learning Entry** (derived, not stored): The Home screen offer, computed by resolving the
  last-listened pointer against its saved session and current content. It exists only when that
  resolution succeeds, which is what keeps stale state from surfacing as a broken entry.
- **Verse / Matn**: Read to resolve saved identities into real content, to validate that saved state
  can still be honored, and to position the restored reading screen.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A student returning to the app reaches their previous verse in **exactly one tap** from
  the Home screen.
- **SC-002**: A resumed session restores the correct matn, verse, both repeat counters, and any A–B
  loop range in **100%** of resumes from valid saved state.
- **SC-003**: Saved state survives **100%** of abrupt terminations (force-close, system eviction,
  crash, device restart), losing at most the partial position within the verse that was playing.
- **SC-004**: After any verse transition or settings change, the saved state reflects that change
  within **1 second**.
- **SC-005**: The Continue Learning entry is displayed within the Home screen's normal load, adding
  no more than **200 milliseconds** to the time the student waits before the Home screen is usable.
- **SC-006**: Resuming reaches a ready, correctly positioned reading screen within **2 seconds** of
  the tap, with audio playing from the saved position by that point.
- **SC-006a**: Resumed audio begins within **1 second** of the saved position in **100%** of resumes
  from valid state — the student hears the recitation continue, not restart.
- **SC-007**: In **100%** of cases where saved state cannot be honored (missing matn, missing verse,
  invalid range, corrupt data), the app opens normally with no error, no crash, and no broken entry.
- **SC-008**: Persistence never disrupts listening — **zero** audible interruptions, stalls, or
  playback failures are attributable to saving state, including during long unlimited-repetition
  sessions.
- **SC-009**: Each matn independently restores its own repetition settings, with **zero** cross-matn
  leakage across a test set of at least three differently configured matn.
- **SC-009a**: Dismissing the Continue Learning entry loses **zero** saved per-matn state — every
  matn still restores its verse, position, and settings afterwards.
- **SC-009b**: Opening matn without playing them changes the Continue Learning entry in **zero**
  cases; the entry continues to name the last matn actually listened to.
- **SC-010**: All saving and restoring works with the device fully offline (airplane mode), making
  **zero** network requests.

## Assumptions

- **Phase 3 shaped the state; Phase 4 persists it unchanged.** Repetition settings are already scoped
  per matn and the A–B range is already anchored to stable verse identity (Phase 3 FR-007, FR-031).
  This phase stores that shape rather than redesigning it.
- **Mode is derived, never stored.** Phase 3 established the playback mode as a computed label over
  the counters and range. Persisting it would let a restored session contradict its own
  configuration, so it is recomputed on restore.
- **In-flight repetition progress is not restored.** Phase 3 defines the current repetition and pass
  indices as session-scoped, reset on stop. A resumed session therefore begins a fresh count against
  the restored targets — the student's *configuration* is what carries across restarts, not their
  position within a repetition cycle.
- **One Continue Learning entry, many saved sessions.** Every matn keeps its own saved settings, but
  Home offers a single entry for the most recently *listened* matn, matching the product spec's
  singular `last_opened_matn_id` and the single card in the Home design. A multi-entry "recent matn"
  list is not part of this phase.
- **"Listened", not "opened"** *(confirmed via clarification)*: The product spec's field name
  `last_opened_matn_id` is read as *last listened*. The pointer moves only when audio plays
  (FR-002a), so browsing the library never discards a genuine in-progress drill. Per-matn records are
  slightly broader — a configured-but-unplayed drill is still saved (FR-002b) — because losing a
  deliberate configuration on restart would contradict Phase 3's per-matn settings promise.
- **Resume auto-plays** *(confirmed via clarification)*: Tapping Continue Learning both restores the
  session and starts audio, so the promise is genuinely one tap. Whether the session was playing or
  paused when saved is therefore **not** persisted — resume always plays. The Phase 2 audio-focus and
  interruption rules still gate that start (FR-022a).
- **Resume is position-exact** *(confirmed via clarification)*: Playback continues from the saved
  millisecond offset inside the verse rather than restarting the verse, which is what makes the
  millisecond-accuracy requirement in FR-004 meaningful rather than decorative.
- **Local-only, single-device.** No account, cloud backup, or cross-device sync; the constitution
  requires only that the data shape not preclude them later.
- **Session state, not progress.** Nothing here counts toward memorization progress. The
  recall-based metric is Phase 6 and the constitution requires it to stay decoupled from playback
  activity.
- **No dedicated screen exists for this phase.** Continue Learning is a region of the Home / Library
  design rather than its own Stitch screen, per `docs/DESIGN-SOURCE.md`. UI work derives from that
  card region.
- **Default theme and phone layout**, consistent with Phases 1–3; dark mode and tablet-adaptive
  layouts remain Phase 8.
- **Shared-first persistence logic**: the state-capture and restore logic lives once in shared code,
  with only the platform storage edge differing, per the project's multiplatform approach.

## Dependencies

- **Phase 0 — Foundation & Data Model** MUST be complete: this phase persists references to Phase 0
  entities using their stable identities and writes through the established local persistence and
  repository layer.
- **Phase 1 — Reading Experience (static)** MUST be complete: the Continue Learning entry is hosted
  on the Phase 1 Home screen, and resuming lands on the Phase 1 reading surface.
- **Phase 2 — Core Audio Playback** MUST be complete: this phase persists and restores the playback
  position, active verse, and session that Phase 2 introduced.
- **Phase 3 — Repetition Engine** MUST be complete *(satisfied — merged in `a0c7c03`)*: this phase
  persists the per-matn repetition settings and A–B loop range that Phase 3 defined and deliberately
  shaped for persistence.
- Phase 4 has no downstream phase that hard-depends on it; Phases 5–8 may proceed independently.
