# Feature Specification: Progress & Daily Goals

**Feature Branch**: `007-progress-daily-goals`

**Created**: 2026-07-24

**Status**: Draft

**Input**: User description: "read @docs/ROADMAP.md and create a specification for the Phase 7 — Progress & Daily Goals"

## Clarifications

### Session 2026-07-24

- Q: Which actions credit a verse toward the daily goal ("practiced today")? → A: Marking the verse memorized, or completing at least one full playthrough of it in Memorization or A–B Loop mode. A Normal continuous single playthrough does not count.
- Q: When a verse that already counted toward today's goal is later un-marked as memorized, does today's ring count decrease? → A: No — the daily practice record is append-only for the day; un-marking corrects the matn percentage only and never reduces today's count.
- Q: What is the default daily goal shown before the student sets one? → A: 10 verses per day.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Mark verses as memorized and see per-matn progress (Priority: P1)

A student who has been working through a matn wants an honest record of what they have actually committed to memory — not how many times they pressed play. As they master each verse, they mark it as memorized directly from the reading screen with a single action. The verse then shows a clear "memorized" indicator, and the matn's progress reflects the share of its verses the student has memorized, visible both on the matn's details header and on its library card. Mistaken or forgotten verses can be un-marked at any time.

**Why this priority**: This is the foundational, recall-based progress signal the whole phase is built on — the roadmap explicitly calls for "a recall-based progress metric rather than raw playback frequency." Everything else in this phase (daily goals, the completion ring, the Goals dashboard) derives its numbers from this per-verse memorized state, so it must exist first. It also delivers standalone value: even with no goals set, a student gets an honest, persistent picture of their memorization progress.

**Independent Test**: Can be fully tested by opening a matn, marking several verses as memorized, confirming each shows a memorized indicator and the matn's progress percentage updates accordingly (on both the details header and the library card), un-marking a verse and seeing the percentage drop, then restarting the app to confirm the memorized state persists — with no daily-goal or Goals-tab features involved.

**Acceptance Scenarios**:

1. **Given** a verse is displayed in the reading screen and is not yet memorized, **When** the student activates its "mark as memorized" action, **Then** the verse is marked memorized and shows a visible memorized indicator.
2. **Given** a memorized verse, **When** the student activates the action again, **Then** the verse is un-marked and the indicator disappears.
3. **Given** a matn with a known verse count, **When** some of its verses are marked memorized, **Then** the matn's progress percentage equals the memorized verses divided by the total verses, shown on the matn details header.
4. **Given** the library grid, **When** a matn has memorized verses, **Then** its card reflects the same progress percentage as the details screen.
5. **Given** a structured matn with chapters (فصول), **When** the student uses the "mark entire chapter as memorized" convenience action, **Then** every verse in that chapter becomes memorized and the matn percentage updates accordingly; un-marking the chapter reverses it.
6. **Given** memorized verses exist, **When** the app is fully closed and reopened, **Then** all memorized states and the resulting percentages are unchanged.
7. **Given** a verse is replayed many times in one sitting without being marked, **When** the progress percentage is computed, **Then** it does not change — progress reflects explicit memorization, not playback frequency.

---

### User Story 2 - Set a daily goal and track it with a completion ring (Priority: P2)

A student wants a lightweight daily commitment — for example, "practice 10 verses a day" — and a simple visual that tells them, at a glance, whether they have met it today. They set (or adjust) their daily goal, and the Home screen shows a completion ring that fills as they practice verses during the day. When the goal is reached, the ring reads as complete. Each new day the ring resets so the student always sees today's effort.

**Why this priority**: The daily completion ring is the phase's signature motivational surface (it is called out on the Home screen in the product spec), but it depends on the memorization/practice signal established in User Story 1. It turns the honest progress record into a daily habit loop, which is the second-highest-value slice.

**Independent Test**: Can be fully tested by setting a daily goal, practicing verses until the ring fills, confirming the ring shows today's count against the goal, verifying that replaying the same verse repeatedly does not inflate the count beyond one per verse per day, changing the goal and seeing the ring re-scale, and confirming the ring resets to empty at the start of a new day — independently of the Goals dashboard.

**Acceptance Scenarios**:

1. **Given** no daily goal has been set, **When** the student first sees the goal surface, **Then** a sensible default goal is offered and can be changed to any positive whole number of verses.
2. **Given** a daily goal of N verses, **When** the student practices M distinct verses today (M < N), **Then** the Home completion ring shows M of N and is partially filled.
3. **Given** a daily goal of N verses, **When** the student reaches N distinct practiced verses today, **Then** the ring reads as complete (100%).
4. **Given** a verse already counted toward today's goal, **When** the student practices or replays that same verse again today, **Then** the daily count does not increase (a verse counts at most once per day).
5. **Given** progress toward today's goal, **When** the local calendar day rolls over, **Then** the ring resets to zero for the new day, while historical memorized state is unaffected.
6. **Given** a daily goal exists, **When** the student changes the goal value, **Then** the ring immediately re-scales its fill against the new target using today's existing count.

---

### User Story 3 - Review overall progress on the Goals tab (Priority: P3)

A student wants one place to see how they are doing across everything they are memorizing. They open the Goals tab and find today's completion ring, their daily-goal setting, and a per-matn breakdown showing how much of each matn they have memorized. This replaces the "coming soon" placeholder that the Goals tab has shown since the app shell was introduced.

**Why this priority**: The dedicated Goals dashboard is the most surface-area-heavy piece and is valuable only once the underlying memorized state (US1) and daily goal (US2) exist to populate it. It also fulfills the shell commitment to turn the stubbed Goals tab into a real feature area, but it is the least critical of the three slices for day-one value.

**Independent Test**: Can be fully tested by memorizing verses in two different متون and setting a daily goal, opening the Goals tab, and confirming it shows today's ring, the editable daily goal, and a per-matn progress list that matches each matn's details screen — with the "coming soon" placeholder gone.

**Acceptance Scenarios**:

1. **Given** the app shell's bottom navigation, **When** the student opens the Goals tab, **Then** it shows the real progress dashboard rather than a "coming soon" placeholder.
2. **Given** memorized verses across multiple متون, **When** the student views the Goals tab, **Then** each matn appears with its title and current memorized percentage, matching that matn's details screen.
3. **Given** the Goals tab, **When** the student views it, **Then** today's completion ring and the current daily-goal value are shown, and the goal can be edited from here.
4. **Given** the student edits the daily goal on the Goals tab, **When** they return to the Home screen, **Then** the Home ring reflects the updated goal.
5. **Given** no verses have been memorized yet and no goal beyond the default, **When** the student opens the Goals tab, **Then** a purposeful empty/zero state invites them to start rather than showing a broken or blank screen.

---

### Edge Cases

- **Empty library / matn with zero verses**: A matn with no verses must show 0% (or a defined "no verses" state) without a divide-by-zero error, and must not break the overall dashboard.
- **Fully memorized matn**: When every verse of a matn is memorized, its progress reads exactly 100%, and the details/library/Goals views agree.
- **Un-marking after it counted today**: Un-marking a verse as memorized adjusts the matn percentage but does not reduce today's practiced-verse count — the day's practice record is append-only (see FR-014), so the ring never shows a negative or phantom count.
- **Day rollover mid-session**: If the local day changes while the app is open and the student is mid-practice, the ring for the new day starts fresh; verses practiced before midnight remain attributed to the prior day.
- **Device clock / time-zone change**: "Today" is defined by the device's local calendar day; a manual clock or time-zone change is handled gracefully (the ring reflects the device's current local day) without corrupting historical records or crashing.
- **Goal set to an extreme value**: Setting a very large daily goal simply leaves the ring partially filled; setting a goal at or below the minimum is bounded to a valid positive value.
- **Rapid toggling**: Repeatedly toggling a verse's memorized state in quick succession must settle on a single consistent final state, never duplicate records.
- **Audio removed** (interaction with future Storage phase): Memorized state, goals, and daily counts attach to verse/matn identity, not audio files — removing a matn's downloaded audio must not erase memorization progress.
- **Bulk chapter mark with partial pre-existing state**: Marking a chapter memorized when some of its verses were already memorized results in all of them memorized, counted once each, with no double-counting in the percentage.

## Requirements *(mandatory)*

### Functional Requirements

#### Memorization state

- **FR-001**: Students MUST be able to mark any verse as memorized, and to un-mark it, directly from where the verse is displayed in the reading screen, with a single action.
- **FR-002**: A memorized verse MUST show a persistent, visible memorized indicator wherever the verse is rendered in the reading screen, distinct from playback highlighting and from any bookmark/note indicators.
- **FR-003**: For structured متون, the system MUST provide a convenience action to mark (and un-mark) an entire chapter/section as memorized, which sets the memorized state of every verse in that chapter without double-counting.
- **FR-004**: Memorized state MUST persist across app restarts, remain local to the device, and survive removal of a matn's downloaded audio.
- **FR-005**: Memorized state MUST be keyed to stable verse identity, so it is unaffected by audio download state, reordering of display, or re-rendering.

#### Progress percentage

- **FR-006**: Each matn MUST expose a progress percentage equal to its memorized verses divided by its total verses, and MUST display it on the matn details header and on the matn's library card.
- **FR-007**: Progress MUST be derived solely from the recall-based memorized signal and MUST NOT be influenced by raw playback or replay counts.
- **FR-008**: When a matn has zero verses, the system MUST present a defined state (e.g., 0% or "no verses") without error.

#### Daily goal & completion ring

- **FR-009**: Students MUST be able to set and change a daily goal expressed as a positive whole number of verses to practice per day. Before the student sets one, the system MUST default the daily goal to 10 verses.
- **FR-010**: The system MUST track, per local calendar day, the number of *distinct* verses the student has practiced that day, where a verse counts at most once per day regardless of how many times it is replayed or repeated.
- **FR-011**: The system MUST define "practiced today" as one of two recall-oriented actions on a verse: the student marking it memorized, or completing at least one full playthrough of that verse within a Memorization-mode or A–B Loop session. A Normal continuous single playthrough MUST NOT credit a verse, and no verse MAY be credited more than once per day regardless of how many times it is replayed.
- **FR-012**: The Home screen MUST show a daily completion ring that visualizes today's distinct practiced-verse count against the current daily goal, filling proportionally and reading as complete when the goal is met.
- **FR-013**: The daily count and completion ring MUST reset at the start of each local calendar day, while leaving accumulated memorized state and matn percentages unaffected.
- **FR-014**: The daily practiced-verse count MUST never become negative or exceed the number of distinct verses actually practiced that day. The day's practice record is append-only: once a verse is credited for a given day, later un-marking its memorized state MUST NOT remove that day's credit (it adjusts only the matn percentage).

#### Goals tab (shell integration)

- **FR-015**: The Goals tab in the app's bottom navigation MUST replace its "coming soon" placeholder with the progress dashboard delivered by this feature.
- **FR-016**: The Goals dashboard MUST present today's completion ring, the current daily-goal value (editable from here), and a per-matn breakdown listing each matn with its current memorized percentage consistent with that matn's details screen.
- **FR-017**: The Goals dashboard MUST present a purposeful zero/empty state when nothing has been memorized and no goal beyond the default has been set.

#### Presentation & consistency

- **FR-018**: All progress percentages for the same matn MUST agree across the library card, the matn details header, and the Goals dashboard at any given time.
- **FR-019**: Memorized indicators, the completion ring, and empty/zero states MUST render correctly in RTL layout and respect the app's established visual language (design tokens and components from the shared design system).

### Key Entities

- **Memorization Status**: Whether a specific verse is memorized. Keyed to stable verse identity; carries the time it was marked (for ordering and as the basis for the daily practice signal). At most one status per verse; toggleable.
- **Matn Progress** *(derived, not independently stored)*: A matn's memorized-verse count over its total verse count, expressed as a percentage. Computed from Memorization Status; surfaced on the library card, details header, and Goals dashboard.
- **Daily Goal**: The student's target number of distinct verses to practice per day. A single device-local setting with a default; editable.
- **Daily Practice Record**: For a given local calendar day, the set of distinct verses practiced that day, from which the day's count (and the completion ring's fill) is derived. Verses count at most once per day; the record is append-only within a day (un-marking a verse's memorized state does not remove it) and is scoped to a day, forming the basis for the reset behavior.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A student can mark a verse as memorized and see the matn's progress percentage update within 1 second, with no more than one tap on the verse's own controls (discoverable without instruction).
- **SC-002**: For 100% of matn views, the progress percentage shown on the library card, the details header, and the Goals dashboard match each other at all times.
- **SC-003**: Progress percentage reflects only memorized verses: replaying any single verse any number of times without marking it produces exactly 0 change in progress, in 100% of cases.
- **SC-004**: 100% of memorized state, daily-goal settings, and the current day's count are preserved and correct after a full app restart.
- **SC-005**: When the daily goal is met, the completion ring reads as complete (100%) and, when the local day changes, resets to 0% for the new day — verified across a day boundary with no manual data cleanup.
- **SC-006**: A verse practiced (or replayed) multiple times in one day contributes exactly 1 to the day's count toward the daily goal, never more.
- **SC-007**: The Goals tab shows real progress content (or a purposeful zero state) — the "coming soon" placeholder no longer appears anywhere for this tab.
- **SC-008**: A first-time user can locate and change their daily goal without instruction, verified by a task-completion walkthrough with no more than one wrong tap.

## Assumptions

- **Recall-based metric only (this phase)**: Progress is driven by the explicit "mark as memorized" self-report, per the roadmap's "recall-based progress metric rather than raw playback frequency." The product spec's *optional, additive* spaced-repetition heuristic (a verse counting only after being practiced across multiple distinct sessions with time gaps) is deferred beyond this phase; the daily-practice model is structured so it can be layered in later without reworking the memorized-state data.
- **Daily goal unit** *(informed default)*: The daily goal is expressed as a number of *verses to practice per day*, which aligns with the phase's recall-based framing. Time-based goals (e.g., "listen 20 minutes") — the alternative example in the product spec — are out of scope for this phase because minutes measure playback effort rather than recall.
- **Definition of "practiced today"** *(clarified 2026-07-24)*: A verse is counted toward the daily goal when the student either marks it memorized or completes at least one full playthrough of it within a Memorization-mode or A–B Loop session (building on the Phase 3 playback and Phase 4 repetition engines). A Normal continuous single playthrough does not count, and re-listening credits a verse at most once per day, keeping the daily metric decoupled from raw playback frequency.
- **Mark granularity**: Marking is primarily per-verse; a "mark entire chapter" convenience action is provided for structured متون. Per-matn and per-chapter percentages are always *derived* from per-verse state, never stored independently, so they cannot drift.
- **"Today" is device-local**: The daily count and reset are based on the device's local calendar day; the app does not depend on the network or a server clock.
- **Reminders out of scope**: Daily-goal reminder notifications (and the notification permission they require) are handled by the Phase 9 onboarding/permissions flow, not this phase.
- **Streaks and history graphs out of scope**: This phase delivers today's completion ring and current percentages. Multi-day streak tracking and historical progress charts are future scope.
- **Local only, no sync**: Memorization state and goals are device-local in this phase; account sync and cross-device progress remain future (v2) scope per the product spec.
- **Prerequisites**: Phase 2 (reading experience) provides the verse rendering this feature attaches its action and indicator to; Phases 3–4 (playback and repetition) provide the practice signal for the daily count; the Phase 10 app shell provides the Goals tab this feature fills and the Home surface where the ring lives.
