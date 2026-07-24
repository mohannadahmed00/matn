---

description: "Task list for Phase 4 — Continue Learning & State Persistence"
---

# Tasks: Phase 4 — Continue Learning & State Persistence

**Input**: Design documents from `/specs/005-continue-learning/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/session-persistence.md)

**Tests**: REQUIRED. Constitution Principle V is NON-NEGOTIABLE — new domain/data behavior must land with tests in the same change.

**Organization**: Grouped by user story. Phase 2 (Foundational) blocks all stories.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US3, mapping to the spec's prioritized user stories
- Every task names its exact file path

---

## 🛑 Read this before starting any task

These eight rules apply to **every** task. Each one, if broken, fails a requirement the spec
measures directly.

1. **Do not modify any existing test file.** Phases 0–3 tests must pass **unchanged**. If one
   fails, your new code is wrong — fix your code, not the test. There is **no** sanctioned test
   edit in this phase.

2. **`PlaybackController` may be changed in exactly three tasks — T031, T031a, and T035a — and
   nowhere else.** It is 602 lines; every other line stays as it is. The three sanctioned changes,
   in full:

   | Task | Sanctioned change | Nothing else |
   |---|---|---|
   | **T031** | Add a defaulted `startPositionMs: Long = 0` to `playFromVerse` / `startSession`, and one `engine.seekTo(...)` call inside the existing `Resource.Success` branch | — |
   | **T031a** | Gate the automatic resume start on audio focus (FR-022a) | — |
   | **T035a** | Recompute `loopRangeVerseIds` in `startSession` (FR-020) | — |

   Any change to this file outside these three is a defect. If you believe a fourth is needed,
   **stop and ask** — do not improvise.

3. **Never persist the playback mode.** `RepetitionSettings.mode` is a computed property with no
   backing field. If you find yourself writing `mode` to the database, stop — you have
   misunderstood (FR-005).

4. **Never persist in-flight repetition progress** (`PlaybackCursor`, repetition index, pass index)
   or **playing-vs-paused**. Resume always plays (FR-006, FR-021).

5. **`RepeatCount.Unlimited` must never become `0`, `null`, or `1` in storage.** It round-trips as
   the literal string `"UNLIMITED"` (FR-007). A test asserts this.

6. **No new dependency. No new `expect`/`actual`.** Everything is `commonMain`. If a task seems to
   need a wall clock, re-read — nothing persisted is time-derived, by design.

7. **All database writes go through `storageCall { }`** (`data/repository/StorageCall.kt`) so
   failures become `Resource.Failure(AppError.Storage)` instead of throwing. A failed write must
   never disturb playback (FR-011).

8. **Result type is `Resource.Success` / `Resource.Failure`** — *not* `Resource.Error`. Error type
   is `AppError.Storage(message)` / `AppError.NotFound`.

### Reference files — read these before writing similar code

| You are writing | Copy the pattern from |
|---|---|
| A repository over the DB | `data/repository/ReadingPreferencesRepositoryImpl.kt` |
| A `.sq` query | `commonMain/sqldelight/com/giraffe/matn/db/Content.sq` |
| A use case | `domain/usecase/ObserveLibraryUseCase.kt` |
| A ViewModel | `presentation/home/HomeViewModel.kt` |
| A stateless composable + preview | `presentation/common/CoverImage.kt` |
| A DB test | any test using `newTestDatabase()` |

---

## Phase 1: Setup

**Purpose**: Establish a known-green baseline so any later failure is attributable to this phase.

- [X] T001 Run `./gradlew :shared:allTests` and confirm it passes **before** changing anything. Record the passing test count; T045 compares against it.

**Checkpoint**: Baseline green.

---

## Phase 2: Foundational (BLOCKS ALL USER STORIES)

**Purpose**: Schema, migration, domain types, and persistence. No user-visible behavior yet.

### Schema & migration

- [X] T002 Add the `matn_session` table to `shared/src/commonMain/sqldelight/com/giraffe/matn/db/Content.sq`. Append this **exactly**, after the `app_setting` table and before the `CREATE INDEX` lines:

```sql
-- Phase 4 (FR-001): one saved session row per matn. Deliberately stores NO mode (derived,
-- FR-005), NO repetition progress (FR-006), and NO playing/paused flag (resume always plays).
-- last_verse_display_number is the ordinal anchor that keeps "nearest surviving verse"
-- computable after the verse row is deleted (FR-026).
CREATE TABLE matn_session (
    matn_id                    TEXT NOT NULL PRIMARY KEY,
    last_verse_id              TEXT NOT NULL,
    last_verse_display_number  INTEGER NOT NULL,
    position_ms                INTEGER NOT NULL,
    verse_repeat               TEXT NOT NULL,
    matn_repeat                TEXT NOT NULL,
    loop_start_verse_id        TEXT,
    loop_end_verse_id          TEXT,
    FOREIGN KEY (matn_id) REFERENCES matn(id) ON DELETE CASCADE
);
```

  No extra index — `matn_id` is the PRIMARY KEY and is already indexed.

- [X] T003 Append these queries to the end of the same `Content.sq` file:

```sql
selectSession:
SELECT * FROM matn_session WHERE matn_id = ?;

upsertSession:
INSERT INTO matn_session(matn_id,last_verse_id,last_verse_display_number,position_ms,verse_repeat,matn_repeat,loop_start_verse_id,loop_end_verse_id)
VALUES (?,?,?,?,?,?,?,?)
ON CONFLICT(matn_id) DO UPDATE SET
  last_verse_id=excluded.last_verse_id,
  last_verse_display_number=excluded.last_verse_display_number,
  position_ms=excluded.position_ms,
  verse_repeat=excluded.verse_repeat,
  matn_repeat=excluded.matn_repeat,
  loop_start_verse_id=excluded.loop_start_verse_id,
  loop_end_verse_id=excluded.loop_end_verse_id;

deleteSetting:
DELETE FROM app_setting WHERE key = ?;
```

  `deleteSetting` is what dismiss uses (FR-017a) — it must delete **only** the key it is given.

- [X] T004 Create the migration file `shared/src/commonMain/sqldelight/com/giraffe/matn/db/1.sqm` containing **only** the same `CREATE TABLE matn_session (...)` statement from T002 (no comments needed, no queries). The filename `1.sqm` means "migrate schema version 1 → 2". This file is what reaches **already-installed** apps; `Content.sq` only reaches fresh installs. Both are required — see [research.md D3](./research.md).

- [X] T005 Run `./gradlew :shared:generateCommonMainContentDatabaseInterface` (or `./gradlew :shared:build`) and confirm SQLDelight generates without error and that a `MatnSession` type now exists in the generated `com.giraffe.matn.db` package. If generation complains about migrations, do **not** add config flags blindly — re-read T004; the file must sit in the same folder as `Content.sq`.

- [X] T006 Create `shared/src/commonTest/kotlin/com/giraffe/matn/db/MigrationTest.kt`. Assert `ContentDatabase.Schema.version` is now `2`, and that after creating a fresh schema the `matn_session` table is queryable (a `selectSession("nope")` returns no row rather than throwing "no such table"). This is the only test that can catch the upgrade-path bug; fresh-install tests structurally cannot.

### Domain types

- [X] T007 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/SavedMatnSession.kt`:

```kotlin
package com.giraffe.matn.domain.model

/** One matn's persisted session (data-model.md §2.1). Embeds Phase 3's [RepetitionSettings]
 *  rather than re-declaring counters/range (Principle III). */
data class SavedMatnSession(
    val matnId: String,
    val lastVerseId: String,
    val lastVerseDisplayNumber: Int,
    val positionMs: Long,
    val settings: RepetitionSettings,
)
```

- [X] T008 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/ResumeTarget.kt`:

```kotlin
package com.giraffe.matn.domain.model

/** Resolved outcome of "what should tapping Continue Learning do?" (data-model.md §2.2). */
sealed interface ResumeTarget {
    data class Resolved(
        val matnId: String,
        val verseId: String,
        val positionMs: Long,
        val settings: RepetitionSettings,
        val substituted: Boolean,
    ) : ResumeTarget
    data object None : ResumeTarget
}
```

- [X] T009 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/ContinueLearningEntry.kt`:

```kotlin
package com.giraffe.matn.domain.model

/** Derived Home-screen offer — never stored (FR-015). */
data class ContinueLearningEntry(
    val matnId: String,
    val matnTitle: String,
    val verseDisplayNumber: Int,
    val verseId: String,
)
```

- [X] T010 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/model/RepeatCountCodec.kt` — the storage encoding for `RepeatCount` (data-model.md §1.3). Decoding must be **total**: it never throws, and unknown input returns `RepeatCount.ONE`.

```kotlin
package com.giraffe.matn.domain.model

private const val UNLIMITED_TOKEN = "UNLIMITED"

/** FR-007: unlimited stays distinguishable from every finite value and from unset. */
fun RepeatCount.encode(): String = when (this) {
    is RepeatCount.Unlimited -> UNLIMITED_TOKEN
    is RepeatCount.Finite -> value.toString()
}

/** Total decode — never throws; unparseable/absent input falls back to [RepeatCount.ONE]. */
fun decodeRepeatCount(raw: String?): RepeatCount = when {
    raw == null -> RepeatCount.ONE
    raw == UNLIMITED_TOKEN -> RepeatCount.Unlimited
    else -> raw.toIntOrNull()?.let { RepeatCount.of(it) } ?: RepeatCount.ONE
}
```

- [X] T011 [P] Create `shared/src/commonTest/kotlin/com/giraffe/matn/domain/RepeatCountCodecTest.kt`. Cover: `Unlimited` → `"UNLIMITED"` → `Unlimited`; `Finite(7)` round trip; `null` → `ONE`; `"garbage"` → `ONE`; `"0"` → clamped to `Finite(1)`; `"999"` → clamped to `Finite(99)`. **Explicitly assert `decodeRepeatCount("UNLIMITED") != RepeatCount.of(1)`** — this is the FR-007 guard.

### Persistence

- [X] T012 Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/repository/SessionStateRepository.kt` — the interface exactly as specified in [contracts §1](./contracts/session-persistence.md). Six members: `getSession`, `putSession`, `getLastListenedMatnId`, `setLastListenedMatnId`, `clearLastListenedMatnId`, `observeContinueLearning`.

- [X] T013 Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/repository/SessionStateRepositoryImpl.kt`. Constructor takes `db: ContentDatabase`. Follow `ReadingPreferencesRepositoryImpl` exactly for style.
  - Setting key constant: `private const val KEY_LAST_LISTENED_MATN_ID = "last_listened_matn_id"`.
  - All writes wrapped in `storageCall { }` (rule 7).
  - `getSession` maps the row → `SavedMatnSession`, rebuilding `RepetitionSettings` via `decodeRepeatCount` and rebuilding `LoopRange` **only when both** `loop_start_verse_id` and `loop_end_verse_id` are non-null (otherwise `null`).
  - `observeContinueLearning()` observes the setting row, then resolves pointer → session → matn title. Emit `null` (never an error) when the pointer is absent, the session row is missing, or the matn no longer exists (P4, FR-015, FR-025).
  - `clearLastListenedMatnId()` calls `deleteSetting(KEY_LAST_LISTENED_MATN_ID)` and **nothing else** (P2, FR-017a).

- [X] T014 Create `shared/src/commonTest/kotlin/com/giraffe/matn/data/SessionStateRepositoryTest.kt` against `newTestDatabase()`. Assert P1–P5 from the contract. The two that matter most:
  - **`Unlimited` survives a full DB round trip** as `Unlimited` (S4).
  - **`clearLastListenedMatnId()` leaves every `matn_session` row readable** (P2). This is the test that stops a future refactor turning dismissal into data loss.
  - Also: `getSession` on an unknown matn returns `null` without throwing; a session with no loop returns `settings.loopRange == null`.

- [X] T015 Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/repository/PersistentRepetitionSettingsStore.kt` implementing the **existing, unchanged** `RepetitionSettingsStore` interface.
  - **Do not change the interface.** Phase 3 promised substitution "without touching a single caller"; `get`/`put` stay synchronous.
  - Hold an in-memory cache map so `get` can answer synchronously; `put` updates the cache and launches a write on an injected `CoroutineScope`.
  - `get` on an unknown matn returns `RepetitionSettings()` — never null, never throws (S1).
  - Provide a `suspend fun warmCache(matnId: String)` (or load-on-first-touch) so settings saved in a previous app run are available; call it from the resume path in T033.
  - **Keep `InMemoryRepetitionSettingsStore.kt`** — existing tests use it.

- [X] T016 Create `shared/src/commonTest/kotlin/com/giraffe/matn/data/PersistentRepetitionSettingsStoreTest.kt`. Assert S1–S5: defaults for unknown matn; per-matn isolation (matn A's put never affects matn B); `Unlimited` round trip; a `put` with no prior row creates one (FR-002b).

- [X] T017 Wire DI in `shared/src/commonMain/kotlin/com/giraffe/matn/di/ContentModule.kt`:
  - Add `single<SessionStateRepository> { SessionStateRepositoryImpl(get()) }`.
  - **Replace** line 55's `single<RepetitionSettingsStore> { InMemoryRepetitionSettingsStore() }` with the persistent implementation.
  - Leave the use-case bindings for now; **T028** adds them after T025–T027 create those files.
  - Do not reorder or alter any other binding.

**Checkpoint**: `./gradlew :shared:allTests` green. Nothing user-visible yet; Phases 0–3 tests unchanged and passing.

---

## Phase 3: User Story 1 — Pick up exactly where I left off, in one tap (P1) 🎯 MVP

**Goal**: A Continue Learning entry appears on Home naming the last-listened matn and verse; one tap
resumes playback from the exact saved millisecond position.

**Independent test**: Listen partway into a matn, force-close the app, reopen — the entry appears;
tapping it lands on that verse and audio resumes mid-verse. Fully offline.

### The pure resolver

- [X] T018 [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/session/VerseRef.kt`:

```kotlin
package com.giraffe.matn.domain.session

/** Minimal ordering projection the resolver needs — no full Verse required. */
data class VerseRef(val id: String, val displayNumber: Int)
```

- [X] T019 [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/session/ResumeTargetResolver.kt` implementing **all** of rules R1–R9 in [data-model.md §3](./data-model.md). Signature:

```kotlin
object ResumeTargetResolver {
    fun resolve(session: SavedMatnSession?, orderedVerses: List<VerseRef>): ResumeTarget
}
```

  Implement in this order — the order matters:
  1. `session == null` or `orderedVerses.isEmpty()` → `ResumeTarget.None` (R1, R6).
  2. Find `orderedVerses.firstOrNull { it.id == session.lastVerseId }`.
     - Found → verse = it, `positionMs = session.positionMs`, `substituted = false` (R3).
     - Not found → nearest **preceding** by display number: the entry with the largest
       `displayNumber < session.lastVerseDisplayNumber`. If none, nearest **following**: the entry
       with the smallest `displayNumber > session.lastVerseDisplayNumber`. Then `positionMs = 0`
       and `substituted = true` (R4, R5).
  3. Loop validation: if `settings.loopRange != null` and **either** endpoint id is absent from
     `orderedVerses`, clear the loop but **keep the counters** (R7).
  4. If the loop survived but the resolved verse is outside `[start..end]` by display number, clear
     the loop (R8).
  5. Return `ResumeTarget.Resolved(...)`.

  **Pure function only** — no coroutines, no I/O, no repository, no logging.

- [X] T020 [US1] Create `shared/src/commonTest/kotlin/com/giraffe/matn/domain/session/ResumeTargetResolverTest.kt` as a **table test with no fakes**. One case per row of the contract table in [contracts §4](./contracts/session-persistence.md): verse present · verse gone with predecessor · first verse deleted (falls forward) · matn emptied · loop endpoint gone · resolved verse outside its loop · `Unlimited` preserved · null session. Assert `substituted` and `positionMs == 0` on every substitution case.

### Writing state

- [X] T021 [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/playback/DurableSnapshot.kt` — the projection that decides what is worth persisting:

```kotlin
package com.giraffe.matn.playback

import com.giraffe.matn.domain.model.PlaybackState
import com.giraffe.matn.domain.model.RepetitionSettings

/** The subset of PlaybackState that is persisted. Everything absent here is deliberately
 *  NOT persisted: status, speed, notice, pauseReason, cursor (FR-005, FR-006). */
data class DurableSnapshot(
    val matnId: String,
    val verseId: String,
    val displayNumber: Int,
    val positionMs: Long,
    val settings: RepetitionSettings,
)

/** Null when there is no resumable session. */
fun PlaybackState.toDurableSnapshot(): DurableSnapshot? {
    val m = matnId ?: return null
    val v = activeVerseId ?: return null
    val n = activeDisplayNumber ?: return null
    return DurableSnapshot(m, v, n, positionMs, settings)
}
```

- [X] T022 [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/playback/SessionStateRecorder.kt` per [contracts §3](./contracts/session-persistence.md). Constructor: `(state: StateFlow<PlaybackState>, repository: SessionStateRepository, scope: CoroutineScope)`. **No clock** (rule 6).
  - `start()` launches **two** collectors on `scope`:
    - **Structural**: `state.map { it.toDurableSnapshot() }.distinctUntilChanged { a, b -> a?.matnId == b?.matnId && a?.verseId == b?.verseId && a?.settings == b?.settings }` → write immediately (W1).
    - **Position**: the same snapshot flow, throttled to at most one write per `THROTTLE_MS` (use `sample(THROTTLE_MS)`), `private const val THROTTLE_MS = 5_000L` (W2).
  - The first write for a matn also calls `setLastListenedMatnId(matnId)` (W4). Because the recorder only ever sees state that has an `activeVerseId`, this is inherently playback-gated (FR-002a).
  - `suspend fun flush()` writes the current snapshot immediately; idempotent and a no-op when the snapshot is null (W6).
  - **Swallow write failures** — log at most; never rethrow, never surface to UI (W7, FR-011).

- [X] T023 [US1] Create `shared/src/commonTest/kotlin/com/giraffe/matn/playback/SessionStateRecorderTest.kt`. Drive a `MutableStateFlow<PlaybackState>` against a fake `SessionStateRepository` that records calls. Use `runTest` + the virtual-time scheduler for the throttle — **no real delays, no clock**. Assert W1–W7, plus these three specific rows:
  - **W3 (the important one)**: a state differing only in `status`, `speed`, `notice`, `pauseReason`, or `cursor` produces **zero** writes. This is the guard against throttling regressing into per-tick I/O.
  - **FR-016 — the entry tracks the newest position**: emit a snapshot at verse 5, then one at verse 9; assert the stored session ends at verse 9, not verse 5. Resuming and listening further must move the entry forward.
  - **SC-009b — browsing never moves the pointer**: emit a `PlaybackState` with `matnId` set but `activeVerseId == null` (a matn opened but never played); assert **zero** writes and **no** `setLastListenedMatnId` call. This is the automated counterpart to quickstart B3 and the direct guard on FR-002a.

### Reading state back

- [X] T024 [US1] Add `suspend fun getVersesByMatn(matnId: String): Resource<List<Verse>>` to `shared/src/commonMain/kotlin/com/giraffe/matn/domain/repository/VerseRepository.kt` and implement it in `shared/src/commonMain/kotlin/com/giraffe/matn/data/repository/VerseRepositoryImpl.kt`.
  - **Copy the existing `getVersesByChapter` method exactly** — same `storageCall` wrapper, same mapper — swapping the query for the **already-existing** `selectVersesByMatn`. **Do not add a new `.sq` query**; it is already in `Content.sq`.
  - The caller (T026) maps `Verse` → `VerseRef(id, displayNumber)`. Both fields already exist on `Verse`.
  - Rationale: the interface currently exposes only `observeVerses` (a Flow), and the resolver needs a one-shot read.

- [X] T025 [P] [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/ObserveContinueLearningUseCase.kt` implementing `FlowUseCase<Unit, ContinueLearningEntry?>`. Delegates to `repository.observeContinueLearning()`. No logic beyond delegation.

- [X] T026 [P] [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/ResolveResumeTargetUseCase.kt` implementing `UseCase<String, ResumeTarget>` (param = matnId). Fetches the session and the verse refs, calls `ResumeTargetResolver.resolve(...)`, returns `Resource.Success(target)`. **All branching lives in the resolver** — this use case contains no `if` about missing verses.

- [X] T027 [P] [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/DismissContinueLearningUseCase.kt` implementing `UseCase<Unit, Unit>`. Calls `repository.clearLastListenedMatnId()` and nothing else (FR-017a).

- [X] T028 [US1] Return to `di/ContentModule.kt` and add the three `factory` bindings for T025–T027.

### UI

- [X] T029 [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/common/ContinueLearningCard.kt` as a **reusable, stateless, parameterized** composable (Constitution Principle VIII):

```kotlin
@Composable
fun ContinueLearningCard(
    entry: ContinueLearningEntry,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
```

  - **No ViewModel, no DI, no repository** inside it — parameters and lambdas only.
  - **Use theme tokens only** from `presentation/theme/` — **no hard-coded hex colors and no magic `.dp`/`.sp`** (Principle VIII, blocking review item).
  - Implement it from the **Home / Library** Stitch design's Continue Learning region, screen id `618643f891144557b5a4ddf4bbad0c03`, registered in `docs/DESIGN-SOURCE.md`. Fetch it with the `stitch` MCP server. **Do not invent a layout.** If the design is unavailable, stop and ask rather than guessing.
  - Live in `presentation/common/` (shared), **not** inline in `HomeScreen.kt`.

- [X] T030 [US1] Add `@Preview`s to `ContinueLearningCard.kt` driven by hand-built sample state, no ViewModel (Principle II, blocking review item): a normal entry, and a very long matn title to prove truncation. Follow `presentation/common/CoverImage.kt` for preview style.

- [X] T031 [US1] Add the resume position parameter to `shared/src/commonMain/kotlin/com/giraffe/matn/playback/PlaybackController.kt`. **This is the only permitted change to this file.**
  - Change line 79 to `fun playFromVerse(matnId: String, verseId: String, startPositionMs: Long = 0)` and pass it into `startSession`.
  - Change `private fun startSession(matnId: String, startVerseId: String?)` to take `startPositionMs: Long = 0`.
  - Inside `startSession`, in the `is Resource.Success` branch **only**, after the existing `applyCursor(startCursor)` and before `engine.play()`, add:
    `if (startPositionMs > 0) engine.seekTo(startPositionMs)`
  - The default of `0` keeps **every** existing caller and test source-compatible. Do not change `playFromStart`, `pause`, `resume`, `stop`, `next`, `previous`, `seekTo`, `setSpeed`, or any repetition method.

- [X] T031a [US1] Gate the automatic resume start on audio focus, in `shared/src/commonMain/kotlin/com/giraffe/matn/playback/PlaybackController.kt` (**sanctioned by Rule 2**). This implements **FR-022a**, which Constitution Principle VII makes contractual: *"pause and allow resume, never silently die."*
  - **Why this is needed**: T031 makes resume start audio automatically. If another app already holds audio focus (an active call), starting playback must not play over it and must not fail silently — the session must restore fully but sit **paused**.
  - Phase 2 configures `handleAudioFocus = true` on the Media3 player, so the engine will refuse to actually play without focus. The gap is that `PlaybackState` would still report `PLAYING`, so the UI would lie about what the user hears.
  - Note `onInterruptionBegan` (line ~351) begins with `if (_state.value.status != PlaybackStatus.PLAYING) return`. A focus denial arriving during session start can therefore be dropped. Ensure a denial that arrives while status is `LOADING` or during start is still honoured — the resulting state must be `PAUSED` with `pauseReason = NON_TRANSIENT_INTERRUPTION`, not `PLAYING`.
  - **Do not** add a second focus-handling path. Reuse the existing `PauseReason` / interruption machinery (research D7).

- [X] T031b [US1] Add a focus-denial test to `shared/src/commonTest/kotlin/com/giraffe/matn/playback/SessionResumeFocusTest.kt` (**new file — do not edit the existing `PlaybackControllerTest.kt`**). Using `FakeAudioEngine`, simulate a resume that begins while an interruption is active, and assert final state is `PAUSED` with `pauseReason == NON_TRANSIENT_INTERRUPTION`, that the matn, verse, position, and settings all restored correctly, and that `engine` is not left playing (FR-022a, E3).

- [X] T032 [US1] Extend `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/home/HomeUiState.kt` with **one** nullable field: `val continueLearning: ContinueLearningEntry? = null`. Do not alter or reorder the existing four fields.

- [X] T033 [US1] Extend `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/home/HomeViewModel.kt`:
  - Take `observeContinueLearning`, `resolveResumeTarget`, `dismissContinueLearning`, and the `PlaybackController` as constructor parameters.
  - Collect `observeContinueLearning` in `init` (same `onEach { setState { ... } }.launchIn(viewModelScope)` shape as the existing library collector) and map into `continueLearning`.
  - Add `fun onResumeClicked()`: resolve the target; on `Resolved`, warm the settings-store cache for that matn (T015), then call `playbackController.playFromVerse(matnId, verseId, positionMs)` and navigate to the reading screen. On `None`, do nothing visible.
  - Add `fun onDismissClicked()` calling the dismiss use case.
  - **SC-005 — the continue-learning collector MUST be independent of the library collector.** It is its own `.onEach { }.launchIn(viewModelScope)`, and it MUST NOT gate `isLoading`. Never `combine`, `zip`, or otherwise await it before the grid renders: the library must paint as soon as its own first emission arrives, whether or not the entry has resolved. `continueLearning` simply stays `null` until it does, and `null` renders nothing (FR-015).
  - **No business logic in the ViewModel** — it forwards to use cases (Principle II).

- [X] T034 [US1] Wire the card into `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/home/HomeScreen.kt`. Render `ContinueLearningCard` **above** the library grid when `state.continueLearning != null`; render **nothing at all** when it is null — no placeholder, no empty card, no reserved space (FR-015). Keep the stateless-content / thin-holder split the file already uses.

**Checkpoint**: US1 is independently testable — listen, force-close, reopen, tap, land mid-verse.

---

## Phase 4: User Story 2 — Resume the drill, not just the verse (P2)

**Goal**: Counters and A–B loop range come back configured, per matn, so the student continues the
same drill rather than rebuilding it.

**Independent test**: Configure verse repeat 7 + loop 12–18, force-close, resume — counters and
range are still set and the mode indicator reads A–B Loop. Configure a second matn differently and
confirm each restores its own.

**Note**: Most of this behavior arrives via the T015/T017 store swap. These tasks make it *true on
the resume path* and prove it.

- [X] T035 [US2] Ensure the resume path applies the resolved `settings`, in `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/home/HomeViewModel.kt` (`onResumeClicked`, added in T033). The settings from `ResumeTarget.Resolved` must be in the store **before** `playFromVerse` runs — `PlaybackController.startSession` reads `settingsStore.get(matnId)` at line 118, so a cold cache would silently yield defaults. Warm the cache (T015) or `put` the resolved settings first. **This ordering is the single most likely bug in the phase.**

- [X] T035a [US2] Recompute `loopRangeVerseIds` when a session starts, in `shared/src/commonMain/kotlin/com/giraffe/matn/playback/PlaybackController.kt` (**sanctioned by Rule 2**). This implements the second half of **FR-020** — *"its verses are visually marked as the range."*
  - **The bug**: `loopRangeVerseIds` is assigned in exactly one place — line 285, inside `updateSettings()`. `startSession` copies settings at line 121 (`_state.value.copy(settings = settings)`) but **never** recomputes `loopRangeVerseIds`, so it stays `emptySet()`.
  - **Consequence**: a restored A–B loop *restricts* playback correctly but its verses are **not highlighted**. Resume looks like it worked; the range is invisible. Easy to mistake for cosmetic — it is FR-020 unmet.
  - **Fix**: at line 121, change the copy to also set `loopRangeVerseIds = loopRangeVerseIds(settings)`, reusing the existing private helper at line 305. One line. Do not touch `updateSettings`.
  - **This is a latent Phase 3 bug**, not one Phase 4 introduces — re-opening a matn mid-run already hits the same path. Phase 4 makes it universal, because now every launch restores a loop from storage.

- [X] T035b [US2] Add an assertion to `shared/src/commonTest/kotlin/com/giraffe/matn/playback/SessionResumeFocusTest.kt` (or a sibling new test file): starting a session for a matn whose stored settings contain a `LoopRange` yields a non-empty `state.loopRangeVerseIds` containing exactly the verse IDs inside the inclusive range. Without this, T035a can silently regress.

- [X] T036 [US2] Confirm opening a matn **directly** (not via Continue Learning) also restores its saved settings (FR-023). This should require no new code — `shared/src/commonMain/kotlin/com/giraffe/matn/playback/PlaybackController.kt` already calls `settingsStore.get(matnId)` in `startSession`. Verify the cache in `shared/src/commonMain/kotlin/com/giraffe/matn/data/repository/PersistentRepetitionSettingsStore.kt` is populated for a matn opened in a *later app run*; if it is not, fix that file (T015), **not** `PlaybackController`.

- [X] T037 [US2] Create `shared/src/commonTest/kotlin/com/giraffe/matn/data/SettingsRestorePathTest.kt`. Simulate: put settings for matn A, discard the in-memory cache (construct a fresh store over the same database — this stands in for an app restart), then `get(matnA)`. Assert the saved counters and loop range return, and that `get(matnB)` still returns defaults (SC-009 zero cross-matn leakage).

- [X] T038 [US2] Verify the restored mode is **derived**, not stored. Add to `shared/src/commonTest/kotlin/com/giraffe/matn/data/SettingsRestorePathTest.kt`: a session restored with `verseRepeat = 7` reports `settings.mode == PlaybackMode.MEMORIZATION`, and one restored with a loop range reports `A_B_LOOP`. Then confirm by inspection that `shared/src/commonMain/sqldelight/com/giraffe/matn/db/Content.sq` contains no `mode` column anywhere (FR-005).

**Checkpoint**: US1 + US2 both work independently.

---

## Phase 5: User Story 3 — Survives however the app closed, and fails safely (P3)

**Goal**: State survives abrupt termination, and unhonourable state degrades quietly instead of
showing a broken entry or crashing.

**Independent test**: Force-close mid-session and confirm the saved place is intact; then invalidate
the saved target and confirm the app opens cleanly with no broken entry.

- [X] T039 [US3] Call `SessionStateRecorder.flush()` when the app is backgrounded, so an abrupt kill loses at most the current verse's partial position (FR-009, FR-010).
  - **There is no existing backgrounding hook** — this was verified; you are adding the first one. On Android, hook `onStop` in `androidApp/src/main/kotlin/com/giraffe/matn/MainActivity.kt`. On iOS, hook the equivalent scene/app-lifecycle callback in `iosApp`.
  - Also flush on pause and stop.
  - Each hook body is **one call to `flush()` and nothing else** — no business logic in platform shells (Principle IV).

- [X] T040 [US3] Wire `SessionStateRecorder.start()` at app startup. Add it to `di/ContentModule.kt` as a `single` and start it where Koin is initialized (`di/MatnKoinStarter.kt`). It must observe the same `PlaybackController` singleton the UI uses — not a new instance.

- [X] T041 [US3] Confirm FR-025 (missing matn) is handled at **two** levels: `ON DELETE CASCADE` removes the orphaned session row, and `observeContinueLearning` emits `null` when the matn is absent. Add a test to `SessionStateRepositoryTest.kt` that deletes a matn and asserts both the session row is gone and the observed entry is `null` — not an error, not a crash.

- [X] T042 [US3] Add a corrupt-state test to `SessionStateRepositoryTest.kt`: write a row with an unparseable `verse_repeat` value directly via SQL, then read it back. Assert `getSession` returns a session with `RepeatCount.ONE` rather than throwing (FR-029, P1). The app must never fail to launch because of bad stored data.

**Checkpoint**: All three stories work; failure modes degrade quietly.

---

## Phase 6: Polish & Cross-Cutting

- [X] T043 [P] Verify Principle VIII compliance in `ContinueLearningCard.kt`: no hard-coded hex colors, no magic `.dp`/`.sp` literals, component is stateless and parameterized, previews present, and it lives in `presentation/common/` rather than inline in the screen. This is a blocking review item.

- [X] T043a [P] Verify **SC-005** (Continue Learning adds ≤200 ms to Home load) structurally rather than by stopwatch. In `shared/src/commonTest/kotlin/com/giraffe/matn/presentation/HomeLoadIndependenceTest.kt` (new file), construct `HomeViewModel` with a library flow that emits immediately and a continue-learning flow that **never emits**. Assert `isLoading` still becomes `false` and `items` populate. If the grid can render while the entry never resolves at all, the entry cannot be adding measurable latency — which is the property SC-005 actually cares about. Pair with the device observation in [quickstart.md §B1](./quickstart.md).

- [X] T044 [P] Confirm no new dependency was added: `git diff gradle/libs.versions.toml shared/build.gradle.kts` must be **empty** (except the SQLDelight migration folder, if any config proved necessary in T005).

- [X] T045 Run `./gradlew :shared:allTests`. Confirm the Phase 0–3 test count from T001 still passes with **zero edits to any pre-existing test file** (`git diff --stat` over existing test files must be empty). A pre-existing test needing an edit means an unintended behavior change — a blocking review failure.

- [ ] T046 Execute the device validation in [quickstart.md §B](./quickstart.md) — B1–B8 on Android, B1/B2/B6 on iOS. **B8 (upgrade in place) is mandatory**: install the pre-Phase-4 build, then install this build over it without uninstalling. A crash there means the T004 migration is wrong, and no fresh-install test can catch it.

---

## Dependencies

```
Phase 1 (T001)
   └─> Phase 2 Foundational (T002–T017)          ← BLOCKS EVERYTHING
          ├─> Phase 3 US1 (T018–T034, incl. T031a/T031b)   ← MVP
          │      └─> Phase 4 US2 (T035–T038, incl. T035a/T035b)
          │             └─> Phase 5 US3 (T039–T042)
          └─────────────────> Phase 6 Polish (T043–T046)
```

**Sanctioned `PlaybackController` changes** (Rule 2) are T031 → T031a → T035a, in that order. Each is
a small, separately-testable edit; do not batch them into one pass.

**Within Phase 2**: T002 → T003 → T004 → T005 → T006 are strictly sequential (schema before
generation before test). T007–T011 are `[P]` — different files, no shared state. T012 → T013 → T014
sequential. T015 → T016 sequential.

**Within Phase 3**: T018 → T019 → T020 sequential. T021 → T022 → T023 sequential. T025/T026/T027 are
`[P]`. T029 → T030 sequential. T031 → T031a → T031b sequential (all touch resume behavior). T032 →
T033 → T034 sequential.

**Within Phase 4**: T035 → T035a → T035b sequential.

**Story independence**: US2 and US3 build on US1's resume path, so they are *not* fully independent
here — this is inherent to the feature (there is nothing to resume before US1 exists), not a
sequencing mistake.

## Parallel execution examples

**Phase 2 domain types** — four agents, four files, zero conflicts:

```
T007 SavedMatnSession.kt · T008 ResumeTarget.kt · T009 ContinueLearningEntry.kt · T010 RepeatCountCodec.kt
```

**Phase 3 use cases** — three agents, three files:

```
T025 ObserveContinueLearningUseCase.kt · T026 ResolveResumeTargetUseCase.kt · T027 DismissContinueLearningUseCase.kt
```

**Phase 6 checks** — T043 and T044 are independent inspections.

## Implementation strategy

**MVP = Phase 1 + Phase 2 + Phase 3 (T001–T034, including T031a/T031b).** That delivers the phase's
headline promise: the entry appears and one tap resumes mid-verse. It is demonstrable and shippable
on its own. T031a is **not** optional polish — Constitution Principle VII makes interruption
handling contractual, so resume-during-a-call must behave correctly in the MVP.

**Then** Phase 4 (the drill resumes — what makes it a memorization tool rather than a bookmark),
**then** Phase 5 (durability and safe failure), **then** Phase 6.

**Highest-risk task: T035.** `startSession` reads `settingsStore.get(matnId)` at line 118, so if the
persistent store's cache is cold at that moment, the resume silently falls back to default counters
— landing on the right verse with the wrong drill. That is exactly the bookmark-instead-of-drill
outcome US2 exists to prevent, and it will *look* like it works. Test it after an actual restart,
not just within one run.
