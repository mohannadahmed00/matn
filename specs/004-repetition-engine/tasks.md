---

description: "Task list for Phase 3 — Repetition Engine"
---

# Tasks: Phase 3 — Repetition Engine

**Input**: Design documents from `/specs/004-repetition-engine/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: REQUIRED (not optional). Constitution Principle V is NON-NEGOTIABLE: *"A change to
repetition, A–B loop, or 'memorized' logic without accompanying tests is a blocking failure."*

**Organization**: Grouped by user story. Phase 2 (Foundational) blocks all stories.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US4, mapping to the spec's prioritized user stories
- Every task names its exact file path

---

## 🛑 Read this before starting any task

These five rules apply to **every** task. Violating one breaks a requirement that the spec
measures directly.

1. **The SC-010 guard test is `shared/src/commonTest/kotlin/com/giraffe/matn/playback/PlaybackControllerTest.kt`.**
   It contains two kinds of assertion, and they have **different** rules:
   - **Observable-state assertions — NEVER change these.** `status`, `activeVerseId`, `activeIndex`,
     `activeDisplayNumber`, `positionMs`, `notice`, `pauseReason`, `hasSession`, and wake-lock state.
     These are what SC-010 actually protects. If one fails, your code is wrong — fix the code.
   - **Engine-mechanics assertions — may change ONLY where a task explicitly sanctions it.**
     `engine.lastQueue`, `engine.startIndex`, `engine.seekedToTrack`. The window model legitimately
     changes what the engine is handed. Exactly **one** task (T024) sanctions such a change, and it
     names the precise lines and their new values. Any other change to this file is a defect.

   **`activeIndex` is always queue-space** (`cursor.verseIndex`, an index into `queue.tracks`).
   **Engine indices are window-space** (indices into `window`). Never mix the two — this single
   invariant is what keeps the observable-state assertions passing.
2. **New fields on existing data classes MUST have defaults** that reproduce Phase 2 behavior
   (`RepeatCount.ONE`, `null`, `emptySet()`). Existing call sites must keep compiling unchanged.
3. **Never touch the currently playing engine item.** Reconfiguration only ever rewrites the
   playlist *tail* via `replaceUpcoming(...)`. Never call `setQueue`, `stop`, `pause`, or
   `prepare` to apply a settings change.
4. **Platform engine files hold no business logic** (Constitution Principle IV). `Media3AudioEngine`
   and `AvQueueAudioEngine` translate calls only — no `if` about counters, ranges, or modes.
5. **Never add a dependency and never change the database schema.** This phase adds neither.

**Verify at any point** with: `./gradlew :shared:allTests`

---

## Phase 1: Setup

**Purpose**: Confirm a green baseline before changing anything.

- [ ] T001 Run `./gradlew :shared:allTests` from the repo root and confirm all existing tests pass. If any fail, stop and report — do not begin Phase 2 on a red baseline.
- [ ] T002 Read `shared/src/commonMain/kotlin/com/giraffe/matn/playback/PlaybackController.kt` end to end and note the existing methods (`playFromVerse`, `next`, `previous`, `applyActiveIndex`, `startEventCollection`, `onTrackError`). Phase 3 extends this file; it does not replace it.

**Checkpoint**: Baseline green, existing controller understood.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain types, the pure planner, engine primitives, and DI. **No user story can start until this phase is complete.**

### Domain models

All six files go in `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/`. All are new files
with no dependencies on each other except where noted, so T003–T007 can run in parallel.

- [ ] T003 [P] Create `RepeatCount.kt` in `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/`:
  ```kotlin
  sealed interface RepeatCount {
      data class Finite(val value: Int) : RepeatCount {
          init { require(value in MIN..MAX) { "RepeatCount.Finite out of range: $value" } }
      }
      data object Unlimited : RepeatCount

      val isUnlimited: Boolean get() = this is Unlimited

      companion object {
          const val MIN = 1
          const val MAX = 99
          val ONE = Finite(1)
          /** The ONLY safe way to build a Finite from unvalidated input — clamps, never throws. */
          fun of(value: Int): Finite = Finite(value.coerceIn(MIN, MAX))
      }
  }
  ```
  The `init` block enforces the FR-006 invariant so an out-of-range counter is unrepresentable.
  **All UI and intent code MUST use `RepeatCount.of(...)`**, never `Finite(...)` directly, so user
  input is clamped rather than throwing. Do NOT use `0` or `null` to mean unlimited (FR-003).

- [ ] T004 [P] Create `PlaybackMode.kt` in `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/` with `enum class PlaybackMode { NORMAL, MEMORIZATION, A_B_LOOP }`. Add a KDoc line stating this is a derived label that is never stored or selected directly (FR-008).

- [ ] T005 [P] Create `LoopRange.kt` in `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/` with `data class LoopRange(val startVerseId: String, val endVerseId: String)`. Fields are stable verse UUIDs, never list indices.

- [ ] T006 [P] Create `PlaybackCursor.kt` in `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/` with `data class PlaybackCursor(val verseIndex: Int, val repetition: Int = 1, val pass: Int = 1)`. `repetition` and `pass` are 1-based.

- [ ] T007 [P] Create `RepetitionProgress.kt` in `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/` with `data class RepetitionProgress(val repetition: Int, val verseRepeatTarget: RepeatCount, val pass: Int, val matnRepeatTarget: RepeatCount)`.

- [ ] T008 Create `RepetitionSettings.kt` in `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/` (depends on T003, T004, T005) with:
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
  `mode` MUST be a computed property with no backing field.

### The pure planner — the correctness core

- [ ] T009 Create `RepetitionPlanner.kt` in `shared/src/commonMain/kotlin/com/giraffe/matn/playback/` (depends on T006, T008). Define:
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
  Implement the rules **in this exact order** (data-model.md §4):
  1. `settings.verseRepeat` is `Unlimited` → `Advance(cursor.copy(repetition = repetition + 1))`
  2. `repetition + 1 <= (verseRepeat as Finite).value` → `Advance(cursor.copy(repetition = repetition + 1))`
  3. next playable index `> cursor.verseIndex` within `range` exists → `Advance(PlaybackCursor(thatIndex, 1, pass))`
  4. `settings.matnRepeat` is `Unlimited` → `Advance(PlaybackCursor(firstPlayableInRange, 1, pass + 1))`
  5. `pass + 1 <= (matnRepeat as Finite).value` → `Advance(PlaybackCursor(firstPlayableInRange, 1, pass + 1))`
  6. otherwise → `End`
  In rules 3–5, skip any index where `isPlayable(index)` is false. If no index in `range` is playable, return `End`. This file MUST have no coroutines, no engine reference, no clock, and no `var`.

- [ ] T010 Create `RepetitionPlannerTest.kt` in `shared/src/commonTest/kotlin/com/giraffe/matn/playback/` (depends on T009) with one test function per row **P1–P12** in [contracts/repetition-contract.md](./contracts/repetition-contract.md) §3. Use plain `kotlin.test` assertions and no fakes, no coroutines, no `runTest`. Name each test after its row, e.g. `fun p9_lowering_Vr_below_completed_repetitions_advances()`. **Run `./gradlew :shared:allTests` and confirm all 12 pass before continuing.**

### Engine primitives

- [ ] T011 Extend `shared/src/commonMain/kotlin/com/giraffe/matn/domain/audio/AudioEngine.kt` by adding two methods to the `AudioEngine` interface: `fun replaceUpcoming(tracks: List<AudioTrack>)` and `fun dropConsumed()`. Copy the KDoc invariants from [contracts/audio-engine-repetition.md](./contracts/audio-engine-repetition.md) §1. Do not modify any existing member.

- [ ] T012 [P] Implement the two new methods in `shared/src/androidMain/kotlin/com/giraffe/matn/audio/Media3AudioEngine.kt` (depends on T011):
  ```kotlin
  override fun replaceUpcoming(tracks: List<AudioTrack>) {
      if (player.mediaItemCount == 0) return
      val from = player.currentMediaItemIndex + 1
      player.replaceMediaItems(from, player.mediaItemCount, tracks.map { MediaItem.fromUri(it.uri) })
  }

  override fun dropConsumed() {
      val current = player.currentMediaItemIndex
      if (current > 0) player.removeMediaItems(0, current)
  }
  ```
  Add no other logic. Do not call `prepare()`, `stop()`, or `seekTo()` in either method.

- [ ] T013 [P] Add the two methods to `shared/src/iosMain/kotlin/com/giraffe/matn/audio/AvQueueAudioEngine.kt` (depends on T011), following the existing stub convention in that file: build one fresh `AVPlayerItem` per track (a played `AVPlayerItem` cannot be re-enqueued), and add a `// macOS step:` comment for the `AVQueuePlayer.remove`/`insert(after:)` calls, exactly as the surrounding methods do. Keep the file compiling on Windows.

- [ ] T014 Extend `shared/src/commonTest/kotlin/com/giraffe/matn/playback/FakeAudioEngine.kt` (depends on T011) with a simulated playlist so window behavior is assertable: add `var playlist: List<AudioTrack>`, `var currentIndex: Int`, `val upcomingReplacements = mutableListOf<List<AudioTrack>>()`, and `var dropConsumedCount: Int`. Implement `replaceUpcoming` to record the call and replace `playlist` after `currentIndex`; implement `dropConsumed` to drop entries before `currentIndex`, re-base `currentIndex` to 0, and increment the counter. Have `setQueue` initialize `playlist`/`currentIndex`. **Do not remove or rename any existing member** — `PlaybackControllerTest` depends on them.

### Settings store (Phase 4 seam)

- [ ] T015 [P] Create `RepetitionSettingsStore.kt` in `shared/src/commonMain/kotlin/com/giraffe/matn/domain/repository/` (depends on T008) with `interface RepetitionSettingsStore { fun get(matnId: String): RepetitionSettings; fun put(matnId: String, settings: RepetitionSettings) }`. KDoc it as the Phase 4 persistence seam.

- [ ] T016 [P] Create `InMemoryRepetitionSettingsStore.kt` in `shared/src/commonMain/kotlin/com/giraffe/matn/data/repository/` (depends on T015) backed by a `private val map = mutableMapOf<String, RepetitionSettings>()`. `get` returns `map[matnId] ?: RepetitionSettings()` — never null, never throws.

### State + DI

- [ ] T017 Extend `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/PlaybackState.kt` (depends on T006, T008) by adding three fields **with defaults**: `val settings: RepetitionSettings = RepetitionSettings()`, `val cursor: PlaybackCursor? = null`, `val loopRangeVerseIds: Set<String> = emptySet()`. Add derived properties `val mode: PlaybackMode get() = settings.mode` and `val repetitionProgress: RepetitionProgress?` built from `cursor` + `settings`. Keep every existing field, default, and derived property unchanged.

- [ ] T018 Update `shared/src/commonMain/kotlin/com/giraffe/matn/di/ContentModule.kt` (depends on T016) by adding `single<RepetitionSettingsStore> { InMemoryRepetitionSettingsStore() }` and passing it to the existing `PlaybackController` registration: `single { PlaybackController(get(), get(), get(), get(), CoroutineScope(...)) }`. Add the matching parameter to `PlaybackController`'s constructor **as the last parameter before `scope`, WITH A DEFAULT VALUE**:
  ```kotlin
  private val settingsStore: RepetitionSettingsStore = InMemoryRepetitionSettingsStore(),
  ```
  **The default is mandatory, not optional.** `PlaybackControllerTest` constructs the controller with
  named arguments and does not pass this parameter; without a default the guard test **will not
  compile**, and editing it to add the argument is forbidden by rule 1.

  If importing `InMemoryRepetitionSettingsStore` (data layer) into `PlaybackController` bothers a
  reviewer on Principle I grounds, the accepted alternative is a companion default in the domain
  interface — e.g. `RepetitionSettingsStore.inMemory()`. Do **not** resolve it by dropping the default.

- [ ] T019 Run `./gradlew :shared:allTests`. Everything must compile and **all pre-existing tests must still pass**, including the untouched `PlaybackControllerTest`. Fix compilation only; do not weaken any existing test.

**Checkpoint**: Planner proven in isolation, engine primitives in place, state and DI ready. User stories can now begin.

---

## Phase 3: User Story 1 — Drill a verse a fixed number of times (Priority: P1) 🎯 MVP

**Goal**: With $V_r$ = N, each verse plays exactly N times — gaplessly — before advancing, with a visible "3 / 7" indicator.

**Independent Test**: Set the verse counter to 3, start playback, verify each verse plays three times with no audible gap, the indicator counts up, and playback then advances.

### Controller: the sliding window

- [ ] T020 [US1] In `shared/src/commonMain/kotlin/com/giraffe/matn/playback/PlaybackController.kt`, add the private window model and fields: `private data class WindowEntry(val track: AudioTrack, val cursor: PlaybackCursor)`, `private var window: List<WindowEntry> = emptyList()`, `private var failedVerseIds = mutableSetOf<String>()`, and `private companion object { const val WINDOW_AHEAD = 2 }`. Add nothing else yet.

- [ ] T021 [US1] In the same file, add `private fun activeRange(): IntRange` returning the queue index range for the active settings — `0..tracks.lastIndex` when `settings.loopRange == null`, otherwise the indices of the range's start/end verse IDs (normalize if inverted, i.e. `min..max`). Return `IntRange.EMPTY` when there is no queue.

- [ ] T022 [US1] In the same file, add `private fun buildWindow(from: PlaybackCursor): List<WindowEntry>` that starts with the entry for `from` and repeatedly calls `RepetitionPlanner.next(...)` — passing `activeRange()` and `isPlayable = { idx -> tracks[idx].verseId !in failedVerseIds }` — until it has `1 + WINDOW_AHEAD` entries or the planner returns `End`. Map each cursor to its `AudioTrack` via `queue.tracks[cursor.verseIndex]`.

- [ ] T023 [US1] In the same file, add `private fun refillWindow()`. It MUST perform these steps **in exactly this order**, because the engine re-bases its indices when consumed entries are dropped:
  1. Trim `window` to start at the current cursor's entry (drop everything before it) and call
     `engine.dropConsumed()` — after this the current entry is `window[0]` in **both** the controller
     and the engine, so they stay in lockstep.
  2. Rebuild the tail from the current cursor via `buildWindow(cursor)` and assign it to `window`.
  3. Call `engine.replaceUpcoming(window.drop(1).map { it.track })` — the entries **after** the
     current one only.

  It MUST NOT call `setQueue`, `play`, `pause`, `stop`, `prepare`, or `seekTo`. Getting step 1 and
  step 2 out of order, or trimming without calling `dropConsumed()`, desynchronizes the controller
  from the engine and mis-highlights verses.

- [ ] T024 [US1] In the same file, modify `startSession(...)` so that after the queue is built it: resolves `settings` (see the flush rule below) into state, sets `cursor` to `PlaybackCursor(q.startIndex, 1, 1)`, builds the window via `buildWindow(cursor)`, and calls `engine.setQueue(window.map { it.track }, 0)` instead of the raw track list. Keep the existing subscribe-before-prime ordering and the existing speed/`play()` calls exactly as they are.

  **Settings flush rule (pairs with T026's IDLE path)**: if the state's current `settings` are
  non-default *and* were configured while no session existed, keep them and write them to the store
  for this matn; otherwise load `settingsStore.get(matnId)`. In practice:
  ```kotlin
  val pending = _state.value.settings
  val settings = if (pending != RepetitionSettings() && !hadSession) pending
                 else settingsStore.get(matnId)
  settingsStore.put(matnId, settings)
  ```
  This is what makes "configure the drill, then press play" work, and it keeps FR-007's per-matn
  scoping intact because the flush is keyed by the matn actually being opened.

  **Replace the existing `applyActiveIndex(q.startIndex)` call** with a direct assignment of the
  active fields from `cursor` and `queue.tracks[cursor.verseIndex]`: `activeIndex = cursor.verseIndex`
  (queue-space), plus `activeVerseId`, `activeDisplayNumber`, `durationMs`, and `positionMs = 0`.
  `applyActiveIndex` took an engine index, which is now window-space — leaving the call would mix the
  two index spaces. Extract this into `private fun applyCursor(cursor: PlaybackCursor)` and reuse it
  in T025.

  **⚠️ This task sanctions the ONLY permitted edit to `PlaybackControllerTest.kt`** (rule 1). In the
  test `start from verse sets correct startIndex and reaches PLAYING after Ready`, exactly two
  engine-mechanics assertions change, because the engine is now handed the window rather than the
  whole queue:
  - `assertEquals(2, engine.startIndex)` → `assertEquals(0, engine.startIndex)`
  - `assertEquals(listOf("v1", "v2", "v3"), engine.lastQueue?.map { it.verseId })` →
    `assertEquals(listOf("v3"), engine.lastQueue?.map { it.verseId })`

  (Starting at v3 with default counters, the planner returns `End` immediately, so the window is
  exactly `[v3]`.) **Leave the other three assertions in that test untouched** — `PLAYING`,
  `activeVerseId == "v3"`, and `activeIndex == 2` must all still pass, and they are what SC-010
  protects. Add a one-line comment above each changed assertion citing T024. Change nothing else in
  the file.

- [ ] T025 [US1] In the same file, change the `AudioEngineEvent.TrackTransition` branch of `startEventCollection()` to resolve the new index through the window, **in this order**:
  1. `val entry = window.getOrNull(event.newIndex) ?: return@collectLatest` — resolve the cursor
     **before** any trimming, and resolve it **defensively**. Never index `window` with `[]` on an
     engine-supplied index; a stale or out-of-range index must be ignored, not crash.
  2. Set `cursor = entry.cursor` and apply the active fields via `applyCursor(entry.cursor)` from
     T024 — so `activeIndex` is `cursor.verseIndex` (**queue-space**), never `event.newIndex`.
  3. Call `refillWindow()` last.

  Do not change the `Ready`, `QueueEnded`, `InterruptionBegan`, or `InterruptionEnded` branches.

- [ ] T026 [US1] In the same file, add a shared private helper and the first public intent:
  ```kotlin
  /** Single funnel for every settings change (T026 / T035 / T036 / T043). */
  private fun updateSettings(transform: (RepetitionSettings) -> RepetitionSettings) {
      val updated = transform(_state.value.settings)
      _state.value = _state.value.copy(settings = updated)
      _state.value.matnId?.let { settingsStore.put(it, updated) }   // no session ⇒ nothing to key on
      if (_state.value.hasSession) refillWindow()
  }

  fun setVerseRepeat(count: RepeatCount) = updateSettings { it.copy(verseRepeat = count) }
  ```
  **The IDLE path is defined and must be honored**: with no session, `matnId` is `null`, so the
  settings live in state only and are flushed to the store by `startSession` (T024) once a matn is
  known. `refillWindow()` is skipped entirely when there is no session. Never call
  `settingsStore.put(null, …)` and never throw when `IDLE` — the contract says these intents are
  callable in every state.

  This helper MUST NOT interrupt the current item (rule 3 in the pre-flight rules above).

- [ ] T027 [US1] In the same file, update `stop()` to also reset `cursor = null`, `window = emptyList()`, and `failedVerseIds.clear()`, while **retaining** the stored settings in `settingsStore` (FR-023). Keep the existing speed-retention behavior.

- [ ] T028 [US1] In the same file, update `next()` and `previous()` to move by **verse** rather than by engine index, abandoning any remaining repetitions (FR-021). Two rules matter here and both are easy to get wrong:

  **Rule A — wrapping happens ONLY inside an A–B loop.** If `settings.loopRange == null`, keep the
  **exact Phase 2 boundary behavior**: `next()` past the last verse goes to `ENDED` with
  `PlaybackNotice.ReachedEnd`, and `previous()` at index 0 restarts the current verse. Only when
  `settings.loopRange != null` does `next()` at the range end wrap to the range start (and
  `previous()` at the range start wrap to the range end). Unconditional wrapping would make every
  ordinary matn loop forever, contradicting FR-021 and Phase 2.

  **Rule B — prefer `seekToTrack` over rebuilding.** Compute the target `PlaybackCursor`, then:
  - If an entry for that cursor's verse **already exists in `window`**, call
    `engine.seekToTrack(thatWindowIndex)`, set the cursor, `applyCursor(...)`, `refillWindow()`, and
    `play()`. This is the common case (next/previous by one verse) and it preserves the Phase 2
    engine-call pattern the guard test asserts (`engine.seekedToTrack`).
  - Only if the target is **not** in the window, rebuild: `window = buildWindow(target)`,
    `engine.setQueue(window.map { it.track }, 0)`, `play()`.

  Preserve the existing `PREVIOUS_RESTART_THRESHOLD_MS` restart-current-verse behavior and the
  existing `enterPlaying()` call unchanged.

- [ ] T029 [US1] In the same file, update `onTrackError(event)`:
  1. Resolve the failed verse **defensively**: `val failed = window.getOrNull(event.index)?.cursor ?: cursor ?: return`. An out-of-range index from the engine must fall back to the current cursor, never crash.
  2. Add that verse's ID to `failedVerseIds`, so the planner's `isPlayable` skips it on **all** later passes (FR-028 / research D8) — not just this one.
  3. Ask the planner for the next step from the failed cursor. If it returns `End`, set `ENDED` with `PlaybackNotice.NoPlayableAudio` and release the wake lock (FR-029).
  4. Otherwise rebuild the window from that next cursor and **call `engine.seekToTrack(windowIndexOfTheNextEntry)` followed by `engine.play()`**, then `applyCursor(...)` and emit `PlaybackNotice.SkippedMissingVerse(failedVerseId)`. Keep the existing `acquireWakeLock()` call on this resumption path.

  Keeping the `seekToTrack` call is required: the guard test asserts `engine.seekedToTrack == 1`
  after a skip, and that assertion is **not** sanctioned for change (rule 1).

### Tests

- [ ] T030 [US1] Create `RepetitionControllerTest.kt` in `shared/src/commonTest/kotlin/com/giraffe/matn/playback/` with tests for rows **C1, C2, C3, C4, C6, C9, C10, C14, C15** from [contracts/repetition-contract.md](./contracts/repetition-contract.md) §4. Drive `FakeAudioEngine` with scripted events using `runTest`, mirroring the existing `PlaybackControllerTest` setup style. **C15 (defaults = Phase 2 behavior) and C2 (playlist stays ≤ 3 items) are mandatory.**

### UI

- [ ] T031 [P] [US1] Extend `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/player/PlayerBarUiState.kt` with `val mode: PlaybackMode = PlaybackMode.NORMAL`, `val repetition: Int = 1`, `val verseRepeatTarget: RepeatCount = RepeatCount.ONE`, `val pass: Int = 1`, `val matnRepeatTarget: RepeatCount = RepeatCount.ONE`, and `val settings: RepetitionSettings = RepetitionSettings()`. All need defaults.

- [ ] T032 [US1] Extend `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/player/PlayerBarViewModel.kt` (depends on T031): map the new `PlaybackState` fields in `toUiState()` and add intent forwarders `fun onVerseRepeatSelected(count: RepeatCount) = controller.setVerseRepeat(count)`. Add no logic beyond forwarding (Principle II).

- [ ] T033 [US1] Extend `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/player/PlayerBar.kt` (depends on T031) to render the repetition indicator in the **stateless content composable**: show `"$repetition / $target"`, where an `Unlimited` target renders as `"∞"`. Add an `@Preview` for the finite case (`2 / 5`). Follow the file's existing stateless-content + stateful-holder split.

- [ ] T034 [US1] Create `DrillPanel.kt` in `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/player/` with a **stateless** `DrillPanelContent(settings: RepetitionSettings, onVerseRepeatChange: (RepeatCount) -> Unit, ...)` plus a thin stateful holder that only collects `PlayerBarViewModel.state` and forwards intents. For this story it need only expose the verse-repeat stepper (1–99) and an ∞ toggle. Add `@Preview`s for: default `1`, finite `7`, and `Unlimited`. **A state-rendering composable without a preview is a blocking review failure (Constitution Principle II).**

**Checkpoint**: US1 is independently demonstrable — set $V_r$ = 3 and hear each verse three times, gaplessly.

---

## Phase 4: User Story 2 — Loop a chosen passage between two verses (Priority: P2)

**Goal**: Mark A and B; playback stays strictly inside that inclusive range and loops until stopped.

**Independent Test**: Mark verses 3 and 5, play, and verify only 3–5 play, the wrap is gapless, the range is visibly marked, and clearing returns to the full matn without a restart.

- [ ] T035 [US2] In `shared/src/commonMain/kotlin/com/giraffe/matn/playback/PlaybackController.kt`, add `fun setLoopStart(verseId: String)` and `fun setLoopEnd(verseId: String)`. Implement both **through the `updateSettings { … }` funnel from T026** — do not persist or refill directly, so the IDLE path and the no-interruption guarantee are inherited. Each updates `settings.loopRange` (creating it if absent — when only one bound is set, the other defaults to the last/first verse) and normalizes an inverted range by swapping. Per research D6, setting a range also sets `matnRepeat = RepeatCount.Unlimited` **unless** the student already chose a `Finite` value other than `ONE`.

- [ ] T036 [US2] In the same file, add `fun clearLoop() = updateSettings { it.copy(loopRange = null) }`, again through the T026 funnel. It MUST NOT stop or restart the session (FR-013).

- [ ] T037 [US2] In the same file, populate `PlaybackState.loopRangeVerseIds` whenever the range changes — the set of verse IDs whose queue index falls inside `activeRange()`. Empty when no range is set.

- [ ] T038 [US2] In the same file, handle the orphaned-playhead case (FR-025 / research D7): after a range change, if the current `cursor.verseIndex` is outside the new `activeRange()`, set the cursor to the range's first index, rebuild the window, `engine.setQueue(window.map { it.track }, 0)`, and `play()`. If the playhead is still inside the range, only the tail is rebuilt — no jump.

- [ ] T039 [US2] Add tests for rows **C5, C7, C8, C17** to `shared/src/commonTest/kotlin/com/giraffe/matn/playback/RepetitionControllerTest.kt`. **C5 must assert that no track outside the range is ever passed to the engine** (SC-006).

- [ ] T040 [P] [US2] Extend `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/details/MatnDetailsUiState.kt` with `val loopRangeVerseIds: Set<String> = emptySet()` and `val loopRange: LoopRange? = null`.

- [ ] T041 [US2] Extend `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/details/MatnDetailsViewModel.kt` (depends on T040) to map the two new fields from `PlaybackController.state` and add forwarders `onSetLoopStart(verseId)`, `onSetLoopEnd(verseId)`, `onClearLoop()`. Forwarding only — no logic.

- [ ] T042 [US2] Extend `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/details/MatnDetailsScreen.kt` (depends on T040) so verse rows whose ID is in `loopRangeVerseIds` are visually distinguished, the A and B boundary rows are marked, and a long-press (or row overflow action) offers "set loop start" / "set loop end" / "clear loop". Add `@Preview`s for a list **with** and **without** an active range.

**Checkpoint**: US1 and US2 both work independently.

---

## Phase 5: User Story 3 — Replay the whole passage a set number of times (Priority: P3)

**Goal**: With $M_r$ = M, the active range plays exactly M complete passes, then stops.

**Independent Test**: Set $M_r$ = 2 with $V_r$ = 1 on a short matn, play to the end, and verify it restarts exactly once more then stops cleanly, with the pass number visible.

> The planner already implements $M_r$ (T009, rules 4–5). This story surfaces and verifies it.

- [ ] T043 [US3] In `shared/src/commonMain/kotlin/com/giraffe/matn/playback/PlaybackController.kt`, add `fun setMatnRepeat(count: RepeatCount) = updateSettings { it.copy(matnRepeat = count) }`, using the same T026 funnel as `setVerseRepeat`.

- [ ] T044 [US3] Add tests for rows **C18** and end-of-drill behavior to `RepetitionControllerTest.kt`: with `Mr = Finite(3)`, assert the range plays exactly 3 passes and the session then reaches `ENDED` (not a fourth pass).

- [ ] T045 [US3] Extend `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/player/DrillPanel.kt` with a matn-repeat stepper alongside the verse stepper, wired to `onMatnRepeatChange`. Add an `@Preview` showing $V_r$ = 5 with $M_r$ = 3.

- [ ] T046 [US3] Extend `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/player/PlayerBar.kt` to render the pass indicator (`"pass 2 / 3"`, `∞` when unlimited) next to the repetition indicator. Update the existing `@Preview` or add one covering a multi-pass session.

**Checkpoint**: US1–US3 all work independently.

---

## Phase 6: User Story 4 — Repeat indefinitely and retune while listening (Priority: P4)

**Goal**: ∞ is an explicit, visible choice, and every counter is adjustable mid-playback without interrupting audio.

**Independent Test**: Set $V_r$ = ∞, verify one verse repeats well past any finite count and never auto-advances; then set a finite value mid-playback and verify it advances without a session restart.

> ∞ is already implemented in the planner (T009, rules 1 and 4) and in `RepeatCount` (T003). This story proves it end to end and finishes the UI.

- [ ] T047 [US4] Add tests to `shared/src/commonTest/kotlin/com/giraffe/matn/playback/RepetitionControllerTest.kt`: with `Vr = Unlimited`, script 50 track transitions and assert the active verse never changes and the window never empties. Then call `setVerseRepeat(RepeatCount.of(2))` mid-run and assert playback advances on the next boundary **without** `setQueue`/`stop` being called.

- [ ] T048 [US4] Add tests for rows **C11, C12, C13, C16** to the same file: repetition count survives an interruption (C11), a failed verse is skipped on later passes with one notice (C12), an all-failed range ends cleanly with `NoPlayableAudio` and no busy loop (C13), and settings are per-matn (C16).

- [ ] T049 [US4] Finish `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/player/DrillPanel.kt`: both steppers must offer ∞ as an explicit selectable value rendered as "∞" — never as blank, `0`, or a missing setting (FR-016 acceptance scenario 4). Add `@Preview`s for $V_r$ = ∞ and $M_r$ = ∞.

- [ ] T050 [US4] Extend `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/player/PlayerBar.kt` to render the derived mode chip from `uiState.mode` (Normal / Memorization / A–B Loop). It must read the derived value only — never store or infer a mode locally (FR-008).

**Checkpoint**: All four user stories are independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T051 [P] Add a `PlayerBarViewModelTest.kt` in `shared/src/commonTest/kotlin/com/giraffe/matn/presentation/` asserting the state projection: mode chip value, `"3 / 7"` formatting, and `∞` rendering for unlimited targets.

- [ ] T052 [P] Review every new/changed composable and confirm each state-rendering one has at least one `@Preview` driven by hand-built sample state (no ViewModel, no DI, no database). This is a **blocking** constitution review item.

- [ ] T053 Re-read `shared/src/androidMain/kotlin/com/giraffe/matn/audio/Media3AudioEngine.kt` and `shared/src/iosMain/kotlin/com/giraffe/matn/audio/AvQueueAudioEngine.kt` and confirm neither contains a single `if` about counters, ranges, modes, or cursors (Principle IV). Move any such logic into `PlaybackController`.

- [ ] T054 Run `./gradlew :shared:allTests` and confirm everything passes, **including every test in `PlaybackControllerTest`**. Then run `git diff shared/src/commonTest/kotlin/com/giraffe/matn/playback/PlaybackControllerTest.kt` and verify the diff contains **exactly the two assertion changes sanctioned by T024 and nothing else** — specifically: no change to any `status`, `activeVerseId`, `activeIndex`, `activeDisplayNumber`, `positionMs`, `notice`, `pauseReason`, or wake-lock assertion, and no test deleted, renamed, or skipped. Any other modification is an SC-010 regression and must be reverted, not accepted.

- [ ] T055 Run `./gradlew :shared:compileKotlinIosSimulatorArm64` and confirm the iOS source set still compiles on Windows.

- [ ] T056 Execute [quickstart.md](./quickstart.md) §B rows 1–22 on an Android device, giving particular attention to rows **2** (gapless repeat), **4** (inaudible live reconfiguration), **7–8** (60-minute ∞ run, flat memory), and **17** (zero Phase 2 regression). Rows **21–22** cover SC-001 and SC-007. Record any row that fails rather than marking the phase done.

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 (Setup)**: no dependencies
- **Phase 2 (Foundational)**: depends on Phase 1 — **blocks every user story**
- **Phase 3 (US1)**: depends on Phase 2. Also builds the sliding window that US2–US4 reuse
- **Phase 4 (US2)**: depends on Phase 2; in practice sequenced after US1 because it reuses `refillWindow()`
- **Phase 5 (US3)**: depends on Phase 2 only — planner rules already exist
- **Phase 6 (US4)**: depends on Phase 2 only — planner rules already exist
- **Phase 7 (Polish)**: depends on all desired stories

### Critical path

T003–T008 (models) → T009 (planner) → T010 (planner tests, **must pass**) → T011/T014 (engine + fake) → T017/T018 (state + DI) → T020–T029 (window) → T030 (controller tests).

### Parallel opportunities

- **T003–T007** — five independent new model files
- **T012 and T013** — Android and iOS engines, different source sets
- **T015 and T016** — store interface then impl (T016 depends on T015)
- **T031, T040** — UI-state files for different screens
- **T051, T052** — independent polish tasks

Within Phase 3, tasks **T020–T029 all edit `PlaybackController.kt`** and therefore must run
**sequentially, in order**. This is the single biggest serialization point in the phase.

### Parallel Example: Phase 2 models

```bash
# Five independent files, no shared edits:
Task: "Create RepeatCount.kt in shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/"
Task: "Create PlaybackMode.kt in shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/"
Task: "Create LoopRange.kt in shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/"
Task: "Create PlaybackCursor.kt in shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/"
Task: "Create RepetitionProgress.kt in shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/"
```

---

## Implementation Strategy

### MVP first (User Story 1 only)

1. Phase 1 — confirm green baseline
2. Phase 2 — foundational (**T010 must be green before writing any controller code**)
3. Phase 3 — US1
4. **STOP and VALIDATE**: set $V_r$ = 3, confirm three gapless repetitions per verse, then confirm
   defaults still behave exactly like Phase 2
5. Demo — this alone is a working memorization tool

### Incremental delivery

Foundation → US1 (drill a verse) → US2 (A–B loop) → US3 ($M_r$) → US4 (∞ + live retune) → Polish.
Each story adds value without breaking the previous ones.

---

## Notes

- **The planner is the correctness core.** If T010's twelve rows pass, most of the phase's logic is
  already proven. Do not start T020 until they are green.
- **Repetitions are counted at track transition, never by position** — this is what makes scrubbing
  safe (FR-019). Never compare `positionMs` to `durationMs` to decide an advance.
- **Unlimited is a type, not a sentinel.** Never write `0`, `-1`, or `null` to mean ∞.
- **Mode is derived.** Never add a `mode` field to any state class or store it anywhere.
- Commit after each task or logical group; stop at any checkpoint to validate a story independently.
