# Tasks: Progress & Daily Goals (Phase 7)

**Input**: Design documents from `specs/007-progress-daily-goals/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — Constitution Principle V (test-first) is NON-NEGOTIABLE: every new
domain/data behavior lands with `commonTest` coverage in the same change.

**Organization**: Grouped by user story from spec.md — US1 Mark memorized + per-matn progress (P1),
US2 Daily goal + completion ring (P2), US3 Goals tab dashboard (P3).

---

## HOW TO EXECUTE THIS FILE (read once, then follow literally)

1. Do tasks **in numeric order**. Do not skip ahead. Do not batch unrelated tasks.
2. Every task names its **exact file path** and the **exact code** or a **file to copy the pattern
   from**. When a task says "copy the shape of `X.kt`", open `X.kt` first and mirror its structure,
   imports, and KDoc style.
3. **Never invent behavior.** If a task says "per progress-contract.md § Semantics", that section is
   authoritative. If something is genuinely undefined, STOP and ask — do not guess.
4. After each task, run `./gradlew :shared:allTests`. It must be **green** before the next task
   (except where a task explicitly says "this test must fail first").
5. Commit after each task or tight pair (e.g. a test + its implementation).
6. If a task's described code does not compile because the surrounding code differs from what the
   task assumed, **STOP and report the mismatch** rather than redesigning.

**Path shorthand used below** (expand it yourself when creating files):

- `KOTLIN` = `shared/src/commonMain/kotlin/com/giraffe/matn`
- `TEST`   = `shared/src/commonTest/kotlin/com/giraffe/matn`
- `SQL`    = `shared/src/commonMain/sqldelight/com/giraffe/matn/db`

**Two rules that cause blocking review failures if broken**:

- **Stateless/stateful split** (Constitution II): each screen = a stateless `XxxContent(state, on…)`
  composable holding ALL rendering + a thin `Xxx(viewModel, …)` holder that only collects the
  ViewModel `StateFlow` and forwards intents. Every state-rendering composable needs ≥1 `@Preview`
  built from hand-written sample state (no ViewModel, no DI, no database in previews).
- **Tokens only** (Constitution VIII): use `MaterialTheme.colorScheme` / `MaterialTheme.typography`
  and the token objects in `KOTLIN/presentation/theme/` (`MatnSpacing`, `MatnShapes`). Raw hex
  colors (`Color(0xFF…)`) or magic `.dp`/`.sp` numbers inside screen composables are forbidden.
  (Exception, already used in this repo: a glyph's `size: Dp = 22.dp` default parameter.)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different file, no dependency on an unfinished task)
- **[Story]**: US1 / US2 / US3 — user-story phases only

---

## Phase 1: Setup

**Purpose**: Branch, green baseline, the one new dependency, and the Constitution VIII design gate.

- [X] T001 Create the working branch: `git checkout develop && git pull && git checkout -b feature/007-progress-daily-goals`. Constitution: `main`/`develop` are protected; this feature merges via PR.
- [X] T002 Verify a green baseline: run `./gradlew :shared:allTests` and confirm every existing test passes BEFORE changing anything. If the baseline is red, STOP and report — never build on a broken baseline.
- [X] T003 Add the `kotlinx-datetime` dependency (research.md D1 — the only new dependency in this phase). In `gradle/libs.versions.toml`: add `kotlinxDatetime = "0.6.2"` under `[versions]`, and `kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinxDatetime" }` under `[libraries]`. In `shared/build.gradle.kts`, inside `commonMain.dependencies { … }`, add `implementation(libs.kotlinx.datetime)` next to the existing `implementation(libs.kotlinx.coroutines.core)` line. Run `./gradlew :shared:allTests` to confirm the project still syncs and builds. **If dependency resolution fails**, try version `0.7.1`, then `0.6.1`; if none resolve, STOP and report — do not hand-roll date math.
- [X] T004 [P] Fetch the *Progress & Goals* Stitch design (screen ID `f059cccd2f634bc9ba2cf4d620e5df80`; project registered in `docs/DESIGN-SOURCE.md`) via the `stitch` MCP server's `get_screen` tool, and re-inspect the daily-goal ring region of *Home / Library* (`618643f891144557b5a4ddf4bbad0c03`). Write what you find into a NEW file `specs/007-progress-daily-goals/design-notes.md`: ring anatomy (size, stroke, where the number/label sits), the dashboard's section order, the per-matn row anatomy, and the zero/empty state. Follow the format of `specs/006-search-bookmarks-notes/design-notes.md`. **If the `stitch` MCP server is unavailable**, write that fact in `design-notes.md`, and build the UI from the Phase 10 tokens + the layout described in contracts/goals-ui-contract.md — then flag it in the PR description so the design can be reconciled later.

**Checkpoint**: branch exists, baseline green, `kotlinx-datetime` resolves, design notes recorded.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema v4, the progress models, and the one repository that all three stories read from.
**No user-story work may begin until this phase is complete and green.**

- [X] T005 Add the two new tables to `SQL/Content.sq`. Append them near the existing `bookmark`/`note` table definitions, each with a short comment citing its FRs (match the file's existing comment style). Use this DDL **verbatim**:
  ```sql
  -- Phase 7 (FR-001…FR-005): explicit per-verse recall self-report. `id` is a UUID minted at
  -- creation (Constitution VI); UNIQUE(verse_id) enforces one status per verse and doubles as the
  -- lookup index. CASCADE from verse (text identity), never audio_asset, so removing downloaded
  -- audio can never erase progress (FR-004).
  CREATE TABLE memorization (
      id           TEXT NOT NULL PRIMARY KEY,
      verse_id     TEXT NOT NULL UNIQUE REFERENCES verse(id) ON DELETE CASCADE,
      memorized_at INTEGER NOT NULL
  );

  -- Phase 7 (FR-010…FR-014): append-only record of distinct verses practiced per LOCAL day.
  -- UNIQUE(day_epoch, verse_id) enforces at-most-once-per-verse-per-day; rows are never deleted
  -- by un-marking a verse (FR-014).
  CREATE TABLE daily_practice (
      id           TEXT NOT NULL PRIMARY KEY,
      day_epoch    INTEGER NOT NULL,
      verse_id     TEXT NOT NULL REFERENCES verse(id) ON DELETE CASCADE,
      practiced_at INTEGER NOT NULL,
      UNIQUE (day_epoch, verse_id)
  );
  ```
- [X] T006 Add the named queries to `SQL/Content.sq` (append at the end of the file). Use this SQL **verbatim** — the aggregate is the single source of truth for every progress surface (research.md D4):
  ```sql
  -- Phase 7: memorization queries (progress-contract.md § Repository interfaces).

  selectMemorizationByVerse:
  SELECT * FROM memorization WHERE verse_id = ?;

  insertMemorization:
  INSERT OR IGNORE INTO memorization(id, verse_id, memorized_at) VALUES (?, ?, ?);

  deleteMemorizationByVerse:
  DELETE FROM memorization WHERE verse_id = ?;

  selectMemorizedVerseIdsByMatn:
  SELECT memorization.verse_id AS verse_id
  FROM memorization
  JOIN verse ON verse.id = memorization.verse_id
  WHERE verse.matn_id = ?;

  -- Phase 7 (FR-006/FR-008, research D4): the ONE progress aggregate. LEFT JOINs keep a
  -- zero-verse matn present with total 0 / memorized 0 (no divide-by-zero downstream).
  selectAllMatnProgress:
  SELECT
      matn.id AS matn_id,
      COUNT(verse.id) AS total_verses,
      COUNT(memorization.verse_id) AS memorized_verses
  FROM matn
  LEFT JOIN verse ON verse.matn_id = matn.id
  LEFT JOIN memorization ON memorization.verse_id = verse.id
  GROUP BY matn.id
  ORDER BY matn.title;

  selectMatnProgressById:
  SELECT
      matn.id AS matn_id,
      COUNT(verse.id) AS total_verses,
      COUNT(memorization.verse_id) AS memorized_verses
  FROM matn
  LEFT JOIN verse ON verse.matn_id = matn.id
  LEFT JOIN memorization ON memorization.verse_id = verse.id
  WHERE matn.id = ?
  GROUP BY matn.id;

  -- Phase 7: daily practice (append-only; OR IGNORE absorbs same-day duplicates, FR-010).

  insertDailyPractice:
  INSERT OR IGNORE INTO daily_practice(id, day_epoch, verse_id, practiced_at) VALUES (?, ?, ?, ?);

  selectDailyPracticeCount:
  SELECT COUNT(*) AS practiced_count FROM daily_practice WHERE day_epoch = ?;
  ```
- [X] T007 Create the migration `SQL/3.sqm` (note: migrations live directly in `db/`, NOT in a `migrations/` subfolder — see the existing `SQL/1.sqm` and `SQL/2.sqm`). It migrates FROM schema version 3, bumping `ContentDatabase.Schema.version` to 4. Content = a header comment (mirror `SQL/2.sqm`'s header explaining the numbering convention) followed by the **byte-identical** `CREATE TABLE memorization (…);` and `CREATE TABLE daily_practice (…);` statements from T005. Copying the DDL exactly is what makes the migration test meaningful.
- [X] T008 Write the migration test at `shared/src/androidHostTest/kotlin/com/giraffe/matn/db/MigrationV3Test.kt`. **It goes in `androidHostTest`, not `commonTest`** — copy the structure of the existing `shared/src/androidHostTest/kotlin/com/giraffe/matn/db/MigrationV2Test.kt` exactly (only the JVM driver can be handed a hand-built legacy schema without `Schema.create()` overwriting it). Assert: (a) content seeded at v3 (matn, verse, bookmark, note, matn_session rows) is fully intact after `migrate(3, 4)`; (b) the `memorization` and `daily_practice` tables accept inserts afterwards; (c) `UNIQUE(day_epoch, verse_id)` rejects (or ignores) a duplicate same-day row; (d) migrating an empty v3 DB leaves both new tables empty. Also update the existing `MigrationTest.kt` assertion `schema_version_is_three` → `schema_version_is_four` (T007 bumps the version again).
- [X] T009 [P] Create `KOTLIN/domain/model/MatnProgress.kt` with exactly this content (plus KDoc citing data-model.md §2.1 and FR-006/FR-008):
  ```kotlin
  data class MatnProgress(
      val matnId: String,
      val memorizedCount: Int,
      val totalCount: Int,
  ) {
      val fraction: Float get() = if (totalCount == 0) 0f else memorizedCount.toFloat() / totalCount
      val percent: Int get() = (fraction * 100).roundToInt()
  }
  ```
  Import `kotlin.math.roundToInt`. Pure Kotlin — no other imports.
- [X] T010 [P] Create `KOTLIN/domain/model/DailyProgress.kt` with exactly this content (KDoc citing data-model.md §2.2 and FR-012):
  ```kotlin
  data class DailyProgress(
      val practicedToday: Int,
      val goal: Int,
  ) {
      val fraction: Float get() = if (goal <= 0) 0f else (practicedToday.toFloat() / goal).coerceIn(0f, 1f)
      val isComplete: Boolean get() = goal > 0 && practicedToday >= goal
  }
  ```
  Pure Kotlin — no imports.
- [X] T011 Create `KOTLIN/domain/repository/ProgressRepository.kt` — the interface exactly as listed in contracts/progress-contract.md § `ProgressRepository`, with that table's guarantees as KDoc on each method:
  ```kotlin
  interface ProgressRepository {
      suspend fun setVerseMemorized(verseId: String, memorized: Boolean): Resource<Unit>
      suspend fun setChapterMemorized(chapterId: String, memorized: Boolean): Resource<Unit>
      fun observeMemorizedVerseIds(matnId: String): Flow<Set<String>>
      fun observeMatnProgress(matnId: String): Flow<MatnProgress>
      fun observeLibraryProgress(): Flow<List<MatnProgress>>
      suspend fun recordPractice(verseId: String): Resource<Unit>
      fun observeTodayPracticeCount(): Flow<Int>
  }
  ```
- [X] T012 Implement `KOTLIN/data/repository/ProgressRepositoryImpl.kt`. **Copy the shape of `KOTLIN/data/repository/BookmarkRepositoryImpl.kt`** (same `storageCall` wrapper, same `asFlow().mapToList(Dispatchers.Default)` reactive-read style). Constructor: `(private val db: ContentDatabase, private val today: () -> Long, private val clock: () -> Long, private val newId: () -> String, private val dayCheckIntervalMs: Long = 60_000)`. Behavior, exactly per contracts/progress-contract.md:
  - `setVerseMemorized(verseId, true)` → inside ONE `db.transactionWithResult { }`: if `selectMemorizationByVerse(verseId)` returns null, call `insertMemorization(newId(), verseId, clock())` **and** `insertDailyPractice(newId(), today(), verseId, clock())`. If a row already exists, do nothing (idempotent success).
  - `setVerseMemorized(verseId, false)` → `deleteMemorizationByVerse(verseId)` only. **Never touch `daily_practice`** (FR-014, append-only).
  - `setChapterMemorized(chapterId, memorized)` → in one transaction, read the chapter's verses via the existing `selectVersesByChapter(chapterId)` query, then apply the per-verse rule above to each. When `memorized = true`, only verses that were **not already** memorized get an `insertDailyPractice` call (contract: "already-memorized verses are not re-credited").
  - `observeMemorizedVerseIds(matnId)` → `selectMemorizedVerseIdsByMatn(matnId).asFlow().mapToList(Dispatchers.Default).map { it.toSet() }`.
  - `observeMatnProgress(matnId)` → `selectMatnProgressById(matnId).asFlow().mapToOneOrNull(Dispatchers.Default).map { row -> if (row == null) MatnProgress(matnId, 0, 0) else MatnProgress(matnId, row.memorized_verses.toInt(), row.total_verses.toInt()) }`. (SQLDelight types `COUNT(...)` as `Long` — convert with `.toInt()`.)
  - `observeLibraryProgress()` → `selectAllMatnProgress().asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { MatnProgress(it.matn_id, it.memorized_verses.toInt(), it.total_verses.toInt()) } }`.
  - `recordPractice(verseId)` → `insertDailyPractice(newId(), today(), verseId, clock())` inside `storageCall`. The `OR IGNORE` makes a repeat call a silent success.
  - `observeTodayPracticeCount()` → **must re-key on the day, not bind it once** (research D9 — this is the single most important detail in this task). Write it exactly like this:
    ```kotlin
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeTodayPracticeCount(): Flow<Int> =
        flow {
            while (true) {
                emit(today())
                delay(dayCheckIntervalMs)
            }
        }
            .distinctUntilChanged()
            .flatMapLatest { day ->
                db.contentQueries.selectDailyPracticeCount(day)
                    .asFlow()
                    .mapToOne(Dispatchers.Default)
                    .map { it.toInt() }
            }
    ```
    Imports: `kotlinx.coroutines.flow.flow`, `delay`, `distinctUntilChanged`, `flatMapLatest`, `kotlinx.coroutines.ExperimentalCoroutinesApi`. **Do NOT** simplify this to `selectDailyPracticeCount(today())…` — capturing the day once means a session left open past local midnight keeps reporting yesterday's count (and a new practice row re-runs the query with the *stale* day, so the wrong number looks authoritative). That is exactly the FR-013 / "Day rollover mid-session" defect this shape prevents.
  All suspend methods return `Resource` via the shared `storageCall({ "…" }) { … }` helper in `KOTLIN/data/repository/StorageCall.kt`.
- [X] T013 Write `TEST/data/ProgressRepositoryTest.kt` covering contracts/progress-contract.md § Test obligations. Use the in-memory DB helper `TEST/db/TestDatabase.kt` and the fixtures in `TEST/data/ContentFixtures.kt` — **copy the setup style of `TEST/data/BookmarkRepositoryTest.kt`**. Inject fakes: `today = { fakeDay }` (a `var` the test mutates to simulate rollover), `clock = { fixedMillis }`, `newId = { "id-${counter++}" }`, and `dayCheckIntervalMs = 1_000` (small, so virtual time can cross it). Tests: (1) mark → `observeMemorizedVerseIds` contains it, `observeMatnProgress` reports 1/N; (2) un-mark → removed, progress back to 0/N, **and today's practice count is UNCHANGED** (FR-014); (3) marking also credits today's practice exactly once; (4) marking the same verse twice credits once (FR-010); (5) chapter bulk marks every verse, and with 1 of 3 already memorized credits only the 2 new ones; (6) zero-verse matn → `MatnProgress(total=0)` and `fraction == 0f` with no exception (FR-008); (7) fully memorized matn → `fraction == 1f`; (8) deleting a verse's `audio_asset` rows leaves memorization intact (FR-004); (9) day rollover across a **fresh** subscription: mutate the fake `today`, assert the count reads 0 while memorization is unchanged (FR-013); (10) `recordPractice` twice same day → one row, different days → two rows.
- [X] T013a Add four more cases to `TEST/data/ProgressRepositoryTest.kt` (they are listed separately because each guards a specific spec edge case that is easy to omit — see contracts/progress-contract.md § Test obligations): (a) **rollover while collecting** (research D9, the C1 defect): start collecting `observeTodayPracticeCount()` inside `runTest`, record a practice (count 1), then mutate the fake `today` forward one day and `advanceTimeBy(dayCheckIntervalMs + 1)`; assert the SAME open collector re-emits `0` with no re-subscription — then record a practice and assert `1`, not `2`; (b) **clock / time-zone change** (spec edge): the same mechanism with `today` jumping *backwards* one day re-emits that day's count instead of throwing or sticking; (c) **rapid toggling** (spec edge): 10 rapid sequential `setVerseMemorized` alternations on one verse settle to a single consistent final state — never a duplicate row or a phantom flag (mirrors the 10-toggle case in `TEST/data/BookmarkRepositoryTest.kt`); (d) **restart persistence** (SC-004): after marking and practicing, read both the memorized set and today's count through a **fresh query object over the same driver** and assert both are intact.
- [X] T013b [P] Write `TEST/data/ProgressPerformanceTest.kt` — the automated SC-001 guard ("marking a verse updates the percentage within 1 second"). **Copy the fixture-generation and timing pattern of `TEST/data/SearchPerformanceTest.kt`** (which exists for Phase 6's identical SC-001): seed ≥1,000 verses across ≥3 متون, subscribe to `observeMatnProgress(matnId)`, call `setVerseMemorized(verseId, true)`, and assert the updated emission arrives within budget using `kotlin.time.measureTime`. Flake policy (same as the Phase 6 precedent): if this proves unstable on slow CI hosts, keep the test but relax the assertion to a documented constant (e.g. 3 s) with a comment noting that SC-001's real 1 s budget is verified by the quickstart device check (step 9) — **do not delete the test**.
- [X] T014 Register the repository in `KOTLIN/di/ContentModule.kt`. Add inside `module { … }`:
  ```kotlin
  single<ProgressRepository> {
      ProgressRepositoryImpl(
          get(),
          today = { Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays().toLong() },
          clock = { Clock.System.now().toEpochMilliseconds() },
          newId = { Uuid.random().toString() },
      )
  }
  ```
  The file already has `@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)` on `contentModule()` and imports `kotlin.time.Clock`, `kotlin.uuid.Uuid` — reuse them. Add imports `kotlinx.datetime.TimeZone` and `kotlinx.datetime.todayIn`. **If `todayIn` does not resolve against `kotlin.time.Clock`** (kotlinx-datetime version differences), use `kotlinx.datetime.Clock.System.todayIn(...)` with an explicit import alias instead, and note the deviation in a code comment. Run `./gradlew :shared:allTests`.

**Checkpoint**: schema v4 in place and migrated safely; the single progress source exists and is fully tested. User stories may now proceed.

---

## Phase 3: User Story 1 — Mark verses as memorized and see per-matn progress (Priority: P1) 🎯 MVP

**Goal**: Per-verse "mark as memorized" (plus a mark-whole-chapter action) with a visible indicator on
the reading screen, and a per-matn progress percentage shown identically on the matn details header
and the library card (spec US1, FR-001…FR-008).

**Independent Test**: Open a matn, mark several verses → indicators appear and the header % rises;
the library card shows the same %; un-mark → both drop; replay a verse without marking → no change;
restart the app → everything persists (quickstart §1–3, 8).

- [X] T015 [P] [US1] Create the four thin use cases in `KOTLIN/domain/usecase/`, each in its own file, each delegating to `ProgressRepository` with no branching. Follow the one-line style of `KOTLIN/domain/usecase/ToggleBookmarkUseCase.kt` and the contracts in `KOTLIN/core/usecase/UseCase.kt`:
  - `ToggleVerseMemorizedUseCase.kt` — `class ToggleVerseMemorizedUseCase(private val repo: ProgressRepository) : UseCase<ToggleVerseMemorizedUseCase.Params, Unit>` with `data class Params(val verseId: String, val memorized: Boolean)`; calls `repo.setVerseMemorized(params.verseId, params.memorized)`.
  - `MarkChapterMemorizedUseCase.kt` — same shape with `data class Params(val chapterId: String, val memorized: Boolean)` → `repo.setChapterMemorized(...)`.
  - `ObserveVerseMemorizationUseCase.kt` — `FlowUseCase<String, Set<String>>` → `repo.observeMemorizedVerseIds(params)`.
  - `ObserveMatnProgressUseCase.kt` — `FlowUseCase<String, MatnProgress>` → `repo.observeMatnProgress(params)`.
- [X] T016 [P] [US1] Create `ObserveLibraryProgressUseCase.kt` in `KOTLIN/domain/usecase/`: `FlowUseCase<Unit, List<MatnProgress>>` → `repo.observeLibraryProgress()`.
- [X] T017 [P] [US1] Write `TEST/domain/MarkChapterMemorizedUseCaseTest.kt` using a hand-written fake `ProgressRepository` (in-file, implementing the interface with in-memory sets — do NOT use the database here): assert the use case forwards `chapterId`/`memorized` unchanged, and that bulk-marking a chapter whose verses are partly memorized results in every verse memorized with each newly-memorized verse credited exactly once (FR-003).
- [X] T018 [US1] Add a `MemorizedGlyph` to the EXISTING file `KOTLIN/presentation/common/AnnotationGlyphs.kt` (do not create a new file). Signature must match the two glyphs already there: `@Composable fun MemorizedGlyph(color: Color, filled: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null)`. Draw a **distinct shape** — a check/tick inside a circle — using `Canvas` like the neighbouring glyphs. It must NOT be a recolor of `BookmarkGlyph` (ribbon) or `NoteGlyph` (page): FR-002 requires it read as distinct even to a colorblind user. `filled = true` = memorized (solid), `false` = outline-only action.
- [X] T019 [US1] Create the shared progress bar `KOTLIN/presentation/common/MatnProgressBar.kt`: a stateless `@Composable fun MatnProgressBar(fraction: Float, modifier: Modifier = Modifier, label: String? = null)` rendering a horizontal determinate progress indicator plus an optional label/percent, using Material 3 + `MatnSpacing`/`MatnShapes` tokens only. It is used by BOTH the details header (T021) and the Goals dashboard (T033) — that is why it is extracted now (Constitution VIII, extract at second use). Add `@Preview`s: 0%, 45%, 100%.
- [X] T020 [US1] Extend the details UI state in `KOTLIN/presentation/details/MatnDetailsUiState.kt`: add `val memorizedVerseIds: Set<String> = emptySet()` and `val progress: MatnProgress? = null` to `MatnDetailsUiState`, and add `val memorizedCount: Int = 0` + `val progressFraction: Float = 0f` to the `MatnHeader` data class. (`progress` is the latest emission held independently of the header — see T021's ordering hazard.) Defaults keep every existing constructor call and test compiling.
- [X] T021 [US1] Wire progress into `KOTLIN/presentation/details/MatnDetailsViewModel.kt`: accept two new constructor params **with defaults so existing tests still compile** — `observeMatnProgress: FlowUseCase<String, MatnProgress>? = null` and `observeVerseMemorization: FlowUseCase<String, Set<String>>? = null` — plus `toggleVerseMemorized: UseCase<ToggleVerseMemorizedUseCase.Params, Unit>? = null` and `markChapterMemorized: UseCase<MarkChapterMemorizedUseCase.Params, Unit>? = null`. In `init`, collect each non-null flow into state with its own `.onEach { }.launchIn(viewModelScope)` (follow the existing collector style in this file and in `HomeViewModel.kt`) — progress fills `header.memorizedCount`/`header.progressFraction`, memorization fills `memorizedVerseIds`. **Ordering hazard — handle it explicitly**: `header` is `MatnHeader?` and starts `null`, and the progress emission can arrive *before* the details load builds the header. Do NOT write `it.copy(header = it.header!!.copy(...))`. Instead keep the latest `MatnProgress` in the UI state (add `val progress: MatnProgress? = null` to `MatnDetailsUiState` in T020) and derive the header's two fields wherever the header is built or re-built, so neither arrival order loses data. Equivalent alternative if you prefer: apply the copy only when `header != null` **and** re-apply the stored progress right after the header is created — but the stored-progress approach is less error-prone. Add intents `onToggleMemorized(verseId: String)` (reads current membership in `stateValue.memorizedVerseIds` to compute the target boolean, then calls the use case) and `onMarkChapterMemorized(chapterId: String, memorized: Boolean)`. Then render the header progress in `KOTLIN/presentation/details/MatnDetailsScreen.kt` with `MatnProgressBar` from T019.
- [X] T022 [US1] Add the memorized indicator + action to `KOTLIN/presentation/player/ReadingCarousel.kt`: show `MemorizedGlyph(filled = true)` on any memorized verse card, and an outline `MemorizedGlyph` toggle action on the ACTIVE verse card, placed in the same in-flow action row that already holds the bookmark/note actions (see `ActiveVerseCard` — the row is an in-flow `Column` child, NOT an absolute overlay; keep it that way). Pass a new `memorizedVerseIds: Set<String>` parameter and an `onToggleMemorized: (String) -> Unit` intent down from `MatnDetailsScreen`. Content description: "memorized". Update the carousel `@Preview`s to include a memorized verse and a verse that is memorized + bookmarked + noted at once.
- [X] T023 [US1] Add the "mark entire chapter" action (FR-003). Put it where chapter context already exists in the reading screen — `KOTLIN/presentation/details/TableOfContents.kt` chapter rows are the natural host. Add an action per chapter row that calls `onMarkChapterMemorized(chapterId, memorized)`, where `memorized` is the inverse of "all verses in this chapter are already memorized". Keep the row stateless (parameters + lambdas only) and add a `@Preview` showing both an all-memorized and a partly-memorized chapter row.
- [X] T024 [US1] Extend `TEST/presentation/MatnDetailsViewModelTest.kt` (do not rewrite existing assertions — add new ones): progress flow lands in `header.memorizedCount`/`progressFraction`; memorization flow lands in `memorizedVerseIds`; `onToggleMemorized` on an unmemorized verse invokes the use case with `memorized = true` and on a memorized verse with `false`; `onMarkChapterMemorized` forwards both arguments; playback state is untouched by any of it. Use hand-written fakes for the new use cases.
- [X] T025 [US1] Show progress on the library card. In `KOTLIN/presentation/home/HomeUiState.kt` add `val progressByMatn: Map<String, Float> = emptyMap()`. In `KOTLIN/presentation/home/HomeViewModel.kt` accept `observeLibraryProgress: FlowUseCase<Unit, List<MatnProgress>>? = null` (defaulted, like the existing optional Phase-4 collaborators at the bottom of that file) and collect it in its OWN `.onEach { }.launchIn(viewModelScope)` — it MUST NOT gate `isLoading` (the grid paints independently; this is the existing Home load-independence rule, see `TEST/presentation/HomeLoadIndependenceTest.kt`). In `KOTLIN/presentation/common/MatnCard.kt` add an optional `progressFraction: Float? = null` parameter and render a compact progress affordance when non-null. Update `MatnCard` previews: with and without progress.
- [X] T026 [US1] Register US1's use cases in `KOTLIN/di/ContentModule.kt` — `factory { ToggleVerseMemorizedUseCase(get()) }`, `factory { MarkChapterMemorizedUseCase(get()) }`, `factory { ObserveVerseMemorizationUseCase(get()) }`, `factory { ObserveMatnProgressUseCase(get()) }`, `factory { ObserveLibraryProgressUseCase(get()) }` — and pass them into the ViewModels in `KOTLIN/presentation/navigation/MatnNavHost.kt` (the `MatnDetailsViewModel` block in `composable(Routes.MATN_DETAILS)` and the `HomeViewModel` block in `composable(Routes.HOME)`), following the `koin.get<…>()` pattern already used there. Run `./gradlew :shared:allTests` — green.

**Checkpoint**: US1 fully functional and shippable — mark/un-mark works, and the same percentage appears on the details header and the library card. **This is the MVP.**

---

## Phase 4: User Story 2 — Set a daily goal and track it with a completion ring (Priority: P2)

**Goal**: A verses-per-day goal (default 10) and a Home completion ring that fills as distinct verses
are practiced today, resets each local day, and counts each verse at most once (spec US2,
FR-009…FR-014).

**Independent Test**: Set a small goal, practice verses in Memorization or A–B Loop mode until the
ring fills; replaying a counted verse does not advance it; Normal continuous mode never advances it;
changing the goal rescales the ring; a new local day resets it (quickstart §4–6).

- [X] T027 [P] [US2] Create `KOTLIN/domain/repository/DailyGoalRepository.kt`: `interface DailyGoalRepository { fun observeGoal(): Flow<Int>; suspend fun setGoal(target: Int): Resource<Unit> }` with KDoc stating the default-10 and ≥1 guarantees from contracts/progress-contract.md.
- [X] T028 [US2] Implement `KOTLIN/data/repository/DailyGoalRepositoryImpl.kt`. **Copy `KOTLIN/data/repository/ReadingPreferencesRepositoryImpl.kt` almost line for line** — it is the same `app_setting` key/value pattern. Use `private const val KEY_DAILY_GOAL = "daily_goal"` and `private const val DEFAULT_DAILY_GOAL = 10`. `observeGoal()` = `selectSetting(KEY_DAILY_GOAL).asFlow().mapToOneOrNull(Dispatchers.Default).map { it?.toIntOrNull()?.takeIf { v -> v >= 1 } ?: DEFAULT_DAILY_GOAL }` (absent OR unparseable OR < 1 → 10, never a crash). `setGoal(target)` = `storageCall({ "Failed to persist daily goal" }) { db.contentQueries.upsertSetting(KEY_DAILY_GOAL, target.coerceAtLeast(1).toString()) }`.
- [X] T029 [US2] Write `TEST/data/DailyGoalRepositoryTest.kt` (mirror `TEST/data/ReadingPreferencesRepositoryTest.kt`): unset → 10; `setGoal(25)` → 25; `setGoal(0)` and `setGoal(-3)` → stored as 1; a garbage stored value (`upsertSetting("daily_goal", "abc")`) → 10; value survives re-reading through a fresh query object.
- [X] T030 [P] [US2] Create two use cases in `KOTLIN/domain/usecase/`: `SetDailyGoalUseCase.kt` (`UseCase<Int, Unit>` → `goalRepo.setGoal(params)`) and `ObserveDailyProgressUseCase.kt` (`FlowUseCase<Unit, DailyProgress>`) whose `invoke` returns `combine(progressRepo.observeTodayPracticeCount(), goalRepo.observeGoal()) { count, goal -> DailyProgress(count, goal) }`. Constructor of the latter takes both repositories.
- [X] T031 [P] [US2] Write `TEST/domain/ObserveDailyProgressUseCaseTest.kt` with fake repositories (in-file, `MutableStateFlow`-backed): count 0/goal 10 → `fraction == 0f`, `isComplete == false`; 5/10 → `0.5f`; 10/10 → `1f`, complete; 12/10 → `fraction` clamped to `1f`, complete; changing the goal flow re-emits a rescaled `DailyProgress`.
- [X] T032 [US2] Add the natural-completion marker to playback, per contracts/practice-signal-contract.md § 1. Two files:
  - `KOTLIN/domain/model/PlaybackState.kt`: add `val lastCompletedVerseId: String? = null` and `val completionTick: Long = 0` at the END of the constructor parameter list (defaults keep every existing call site compiling).
  - `KOTLIN/playback/PlaybackController.kt`: add a private field `private var suppressCompletionOnNextTransition = false`. In the `AudioEngineEvent.TrackTransition` branch of `startEventCollection()`, capture `val completed = _state.value.activeVerseId` BEFORE `applyCursor(entry.cursor)`; after `applyCursor`, if `suppressCompletionOnNextTransition` is true then set it to `false` and do nothing, else if `completed != null` set `_state.value = _state.value.copy(lastCompletedVerseId = completed, completionTick = _state.value.completionTick + 1)`. In the `AudioEngineEvent.QueueEnded` branch, include `lastCompletedVerseId = s.activeVerseId, completionTick = s.completionTick + 1` in the existing `s.copy(...)` when `s.activeVerseId != null`. Set `suppressCompletionOnNextTransition = true` at the START of `moveToVerse(...)` (user next/previous), in the `PlanStep.Advance` branch of `onTrackError(...)` (an error skip is not a completion), and in the relocation branch of `updateSettings(...)` that calls `engine.setQueue(...)`. **Change nothing else about playback** — the marker must not influence any decision.
- [X] T032a [US2] Extend the EXISTING `TEST/playback/PlaybackControllerTest.kt` with direct assertions on T032's marker (Constitution V — changed playback behavior lands with its own tests; do not rely only on the recorder test). Additive cases only: (a) a scripted `TrackTransition` increments `completionTick` and sets `lastCompletedVerseId` to the **outgoing** verse; (b) `QueueEnded` increments the tick and names the final active verse; (c) `next()` followed by a `TrackTransition` leaves the tick **unchanged** (the suppress flag); (d) an error skip (`TrackError` → `PlanStep.Advance`) leaves the tick unchanged; (e) `pause()`/`seekTo()`/`setSpeed()` never touch either field. Change no existing assertion in this file.
- [X] T033 [US2] Create `KOTLIN/playback/PracticeSignalRecorder.kt`. **Copy the structure of `KOTLIN/playback/SessionStateRecorder.kt`** — constructor is exactly `(private val state: StateFlow<PlaybackState>, private val repository: ProgressRepository, private val scope: CoroutineScope)` (**three parameters — no clock, no `today`**: the repository owns the day, research D1/D9), same `fun start()` launching a collector, same swallow-failures rule. Behavior per contracts/practice-signal-contract.md § 2: collect `state`, keep the last seen `completionTick`; when a NEW (higher) tick arrives with a non-null `lastCompletedVerseId`, evaluate the recall-mode predicate `settings.loopRange != null || settings.verseRepeat != RepeatCount.ONE || settings.matnRepeat != RepeatCount.ONE`; if true, call `repository.recordPractice(verseId)` and ignore any failure (never disturb playback). If false (Normal continuous), do nothing. The recorder must never call back into `PlaybackController`, and holds no day/clock state of its own.
- [X] T034 [US2] Write `TEST/playback/PracticeSignalRecorderTest.kt` per contracts/practice-signal-contract.md § 4. **Copy the harness of `TEST/playback/SessionStateRecorderTest.kt`** (real `PlaybackController` + `FakeAudioEngine` + `kotlinx-coroutines-test`; emit scripted `AudioEngineEvent`s). Use a fake `ProgressRepository` recording `recordPractice` calls. Tests: (1) Memorization mode (`verseRepeat > ONE`) + a scripted `TrackTransition` → exactly one `recordPractice` for the completed verse; (2) Normal mode (`Vr = 1`, `Mr = 1`, no loop) + the same transition → **zero** calls; (3) A–B loop set → completion credits; (4) two completions of the same verse → recorder calls twice but the real repository would dedup (assert the recorder does not crash and passes the same verse id — dedup is proven in T013); (5) user `next()` followed by a `TrackTransition` → no credit (the suppress flag from T032); (6) `QueueEnded` in a recall session → credits the final verse; (7) the recorder never mutates controller state (assert `PlaybackState` is unchanged except for the marker fields). **Do not test day rollover here** — the recorder holds no day state; rollover is covered at the repository level by T013/T013a (contracts/progress-contract.md § Test obligations).
- [X] T035 [US2] Create the shared ring `KOTLIN/presentation/common/DailyGoalRing.kt`: a stateless `@Composable fun DailyGoalRing(fraction: Float, practiced: Int, goal: Int, isComplete: Boolean, modifier: Modifier = Modifier)` drawing a circular determinate ring (Material 3 `CircularProgressIndicator` or a `Canvas` arc — follow the T004 design notes) with the `practiced / goal` figure inside. Used by BOTH Home (T037) and the Goals tab (T041). Tokens only. `@Preview`s: empty (0/10), partial (4/10), complete (10/10).
- [X] T036 [US2] Replace the placeholder state in `KOTLIN/presentation/home/DailyGoalUiState.kt`: delete `isPlaceholder` and make it `data class DailyGoalUiState(val practiced: Int = 0, val goal: Int = 10, val fraction: Float = 0f, val isComplete: Boolean = false)`. Update the KDoc — it currently says "always `true` until specs/007"; that is now resolved. Fix any compile errors this causes in `HomeUiState.kt`/`HomeScreen.kt`/tests.
- [X] T037 [US2] Wire the real ring into Home: in `KOTLIN/presentation/home/HomeViewModel.kt` accept `observeDailyProgress: FlowUseCase<Unit, DailyProgress>? = null` (defaulted) and collect it in its OWN collector into `state.dailyGoal` — again it MUST NOT gate `isLoading`. In `KOTLIN/presentation/home/HomeScreen.kt` render `DailyGoalRing` in the top-bar region per the T004 design notes, replacing whatever placeholder is drawn today. Update the Home previews to show a partial and a complete ring.
- [X] T038 [US2] Register US2 wiring in `KOTLIN/di/ContentModule.kt`: `single<DailyGoalRepository> { DailyGoalRepositoryImpl(get()) }`, `factory { SetDailyGoalUseCase(get()) }`, `factory { ObserveDailyProgressUseCase(get(), get()) }`, and `single { PracticeSignalRecorder(get<PlaybackController>().state, get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }` (mirror the existing `SessionStateRecorder` registration one line above). Then start it where `SessionStateRecorder.start()` is called — find that call site in `KOTLIN/di/MatnKoinStarter.kt` (`initMatnKoin`) and add `koin.get<PracticeSignalRecorder>().start()` beside it. Pass `observeDailyProgress` into `HomeViewModel` in `KOTLIN/presentation/navigation/MatnNavHost.kt`. Run `./gradlew :shared:allTests` — green.
- [X] T039 [US2] Extend `TEST/presentation/HomeViewModelTest.kt` (additive only): a `DailyProgress(4, 10)` emission lands in `state.dailyGoal` as `practiced = 4, goal = 10, fraction = 0.4f, isComplete = false`; a `DailyProgress(10, 10)` sets `isComplete = true`; and the library grid still paints when the daily-progress flow never emits (extends the existing load-independence rule).

**Checkpoint**: US2 independently shippable — the Home ring reflects real practice, resets daily, and honours the goal.

---

## Phase 5: User Story 3 — Review overall progress on the Goals tab (Priority: P3)

**Goal**: A real Goals tab showing today's ring, the editable daily goal, and a per-matn memorized
breakdown — replacing the "coming soon" placeholder (spec US3, FR-015…FR-018, SC-007).

**Independent Test**: Memorize verses in two متون, set a goal, open the Goals tab → ring, editable
goal, and per-matn rows matching each matn's details screen; edit the goal → Home ring rescales; an
empty library shows a purposeful zero state (quickstart §6, 7).

- [X] T040 [P] [US3] Create `KOTLIN/presentation/goals/GoalsUiState.kt`: `data class GoalsUiState(val isLoading: Boolean = true, val dailyProgress: DailyProgress? = null, val matnProgress: List<MatnProgress> = emptyList(), val isEmpty: Boolean = false)`. KDoc per contracts/goals-ui-contract.md § 1. `isEmpty` means the library itself has no متون.
- [X] T041 [US3] Create `KOTLIN/presentation/goals/GoalsViewModel.kt` extending `BaseViewModel<GoalsUiState>`: constructor `(observeDailyProgress: FlowUseCase<Unit, DailyProgress>, observeLibraryProgress: FlowUseCase<Unit, List<MatnProgress>>, private val setDailyGoal: UseCase<Int, Unit>)`. In `init` collect both flows in separate `.onEach { }.launchIn(viewModelScope)` collectors (the `HomeViewModel` pattern); the library-progress emission clears `isLoading` and sets `isEmpty = list.isEmpty()`. Expose `fun onGoalChanged(target: Int)` calling `setDailyGoal` inside `viewModelScope.launch`. No Compose or platform imports (Constitution II).
- [X] T042 [US3] Create `KOTLIN/presentation/goals/GoalsScreen.kt`: stateless `GoalsScreenContent(state: GoalsUiState, onGoalChanged: (Int) -> Unit, modifier: Modifier = Modifier)` + a thin `GoalsScreen(viewModel: GoalsViewModel)` holder that only collects the state and forwards the intent. Layout per the T004 design notes and contracts/goals-ui-contract.md § 1: `DailyGoalRing` (T035) at the top with a goal editor beneath it (a stepper or slider producing whole numbers ≥ 1 — reuse the counter/stepper idiom from `KOTLIN/presentation/player/RepetitionSetupSheet.kt` rather than inventing one), then a list of per-matn rows each using `MatnProgressBar` (T019) with the matn's title. Zero state (nothing memorized / empty library) must be purposeful and inviting — **never** the "coming soon" text (SC-007). Tokens only. `@Preview`s: populated (2 متون, partial ring, RTL Arabic titles), zero state, and loading.
- [X] T043 [US3] Write `TEST/presentation/GoalsViewModelTest.kt` with fake use cases: initial `isLoading = true`; after both emissions → `isLoading = false`, `dailyProgress` and `matnProgress` populated; an empty library-progress list → `isEmpty = true`; `onGoalChanged(15)` invokes `SetDailyGoalUseCase` with 15; a later goal emission rescales `dailyProgress`.
- [X] T044 [US3] Swap the route in `KOTLIN/presentation/navigation/MatnNavHost.kt`: change `composable(Routes.GOALS)` from `ComingSoonScreen(tab = NavigationTab.GOALS, …)` to build `GoalsViewModel` via `MatnKoinHolder.koin` (copy the `composable(Routes.NOTES)` block's structure exactly) and render `GoalsScreen`. Then update the now-stale KDoc in BOTH `MatnNavHost.kt` (the route list says Goals routes to `ComingSoonScreen` "until Phases 7-8 land") and `KOTLIN/presentation/navigation/NavigationTab.kt` (says `GOALS`/`SETTINGS` route to `ComingSoonScreen`) so only Settings is described as a placeholder. **Do not delete `ComingSoonScreen.kt`** — Settings still uses it; also delete its now-unused `ComingSoonGoalsPreview` only if it no longer compiles (otherwise leave it).
- [X] T045 [US3] Register the Goals ViewModel's dependencies in `KOTLIN/di/ContentModule.kt` if anything is still missing (`ObserveLibraryProgressUseCase` from T026, `ObserveDailyProgressUseCase`/`SetDailyGoalUseCase` from T038 should already be there — verify, do not duplicate registrations). Run `./gradlew :shared:allTests` — green.

**Checkpoint**: all three stories independently functional; the Goals tab shows real content and the "coming soon" placeholder is gone for Goals.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T046 [P] Token-literal guard (Constitution VIII): grep every file this feature touched for `Color(0x`, `.dp`, and `.sp` — `grep -rnE "Color\(0x|[0-9]+\.dp|[0-9]+\.sp" shared/src/commonMain/kotlin/com/giraffe/matn/presentation/goals/ shared/src/commonMain/kotlin/com/giraffe/matn/presentation/common/DailyGoalRing.kt shared/src/commonMain/kotlin/com/giraffe/matn/presentation/common/MatnProgressBar.kt`. The only acceptable matches are glyph `size: Dp = 22.dp` default parameters. Replace anything else with `MatnSpacing`/`MatnShapes`/`MaterialTheme` tokens.
- [X] T047 [P] Preview-coverage check (Constitution II): confirm every new state-rendering composable has ≥1 `@Preview` — `DailyGoalRing` (empty/partial/complete), `MatnProgressBar` (0/45/100%), `MemorizedGlyph` (filled + outline), `GoalsScreenContent` (populated/zero/loading), plus the updated Home, carousel, TOC-row, and `MatnCard` previews. Add any that are missing. **RTL gate (FR-019 / Constitution VII)**: at least one preview of `GoalsScreenContent` must render inside a `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)` wrapper with Arabic matn titles, so the dashboard's RTL layout is inspectable without a device (follow whatever RTL-preview idiom the existing screens already use; if none exists, this establishes it).
- [ ] T047a [P] RTL device pass (FR-019 / Constitution VII): run quickstart.md manual step 10 on an Arabic/RTL device or emulator, covering every surface this feature adds — memorized glyph + verse-card action row, details-header progress bar, library-card progress, Home ring, and the Goals dashboard (ring, goal editor/stepper, per-matn rows, zero state). Confirm right-aligned text, progress filling from the right, a non-mirrored-into-nonsense stepper, and no clipped or overlapping labels. Record the result in the PR description. Phase 6's equivalent pass (`specs/006-search-bookmarks-notes/tasks.md` T051, item B14) is the bar for scrutiny.
  **DEFERRED — no Android/iOS device, emulator, or `adb` is available in this execution environment** (headless background job). The RTL-preview guard in T047 (a `GoalsScreenContent` preview under `LocalLayoutDirection.Rtl` with Arabic titles) is in place and inspectable in Android Studio, but a real on-device pass has not been run. Needs a human with a device/emulator before merge.
- [X] T048 Full regression: run `./gradlew :shared:allTests`. All suites must pass. Existing tests should need only **additive** changes; the one expected edit is `MigrationTest.kt`'s schema-version assertion (T008). If any pre-existing assertion had to change for another reason, explain why in the commit message — it may indicate a real behavior regression.
- [ ] T049 Run the quickstart.md manual walkthrough (steps 1–11) on an Android emulator with the seeded library, and record the outcome of each step in the PR description. Pay special attention to: step 4 (**Normal mode must NOT advance the ring**; Memorization/A–B Loop must), step 5 (day rollover resets the ring but not the percentages), and **step 5a** (rollover with the app left open — the ring must fall to 0 on its own within ~a minute; this is the D9 defect and the single most likely thing to be silently broken). Step 10 (RTL) is owned by T047a — reference its result rather than repeating it. For **step 11 (SC-008 goal discoverability)**: if no person unfamiliar with the feature is available, record SC-008 as *deferred — requires a human unfamiliar with the feature* rather than claiming it passed. Note honestly any other step you could not exercise and why, following the precedent in `specs/006-search-bookmarks-notes/tasks.md` T051.
  **DEFERRED — no Android emulator/device is available in this execution environment** (headless background job; no `adb`/`emulator` tooling, no `androidApp` build target run). Steps 1–9 and 5a (the D9 rollover behavior) are covered by the automated `commonTest` suite instead (`ProgressRepositoryTest`'s rollover-while-collecting case is the direct analog of step 5a). Step 10 (RTL) is deferred alongside T047a. Step 11 (SC-008) is deferred per its own documented fallback — no person unfamiliar with the feature was available either. All of steps 1–11 need a human with a device/emulator before merge.
- [X] T050 [P] Update `docs/DESIGN-SOURCE.md`: the *Progress & Goals* screen (`f059cccd…`) is now implemented; record any deviation between the fetched design and what was built (from `design-notes.md`) under "Open issues", and note that the Goals tab is no longer a "coming soon" stub in issue #6's description.

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 (Setup)** → nothing. T004 can run parallel to T003.
- **Phase 2 (Foundational)** → Phase 1. Chain: T005 → T006 → T007 → T008; T009 ∥ T010 can run beside that chain; T011 → T012 → T013 → T013a → T014, with T013b ∥ T013a. **Blocks all user stories.**
- **Phase 3 (US1)** → Phase 2 complete.
- **Phase 4 (US2)** → Phase 2 complete. Independent of US1 (shared files `ContentModule.kt`, `MatnNavHost.kt`, `HomeViewModel.kt` create merge-order friction only, not a logical dependency).
- **Phase 5 (US3)** → Phase 2, **plus T035** (`DailyGoalRing`), **T019** (`MatnProgressBar`), **T030/T038** (daily-progress + goal use cases). If US3 must be built before US2, do T027–T031, T035, T038 first.
- **Phase 6 (Polish)** → all implemented stories.

### Within-story chains

- **US1**: (T015 ∥ T016 ∥ T017) → T021; T018 → T022; T019 → T021; T020 → T021 → T022 → T023 → T024; T025 after T016; T026 last.
- **US2**: T027 → T028 → T029; T030 → T031; T032 → T032a; T032 → T033 → T034; T035 → T036 → T037; T038 → T039.
- **US3**: T040 → T041 → T042 → T043 → T044 → T045.

### Parallel opportunities

- Phase 1: T003 ∥ T004.
- Phase 2: (T009 ∥ T010) run beside the T005→T008 schema chain; T013b ∥ T013a once T012 lands.
- US1: T015 ∥ T016 ∥ T017 ∥ T018 ∥ T019 at the start.
- US2: T027/T028/T029 (goal storage) ∥ T032/T032a/T033/T034 (practice signal) — different files entirely.
- After Phase 2, US1 / US2 / US3 can be split across contributors (respecting US3's named prerequisites).

---

## Implementation Strategy

**MVP first**: Phases 1 → 2 → 3 (US1). Stop and validate quickstart steps 1–3 and 8 on device.
Marking verses with an honest per-matn percentage is a shippable increment on its own — it is the
phase's owning invariant (recall-based progress, Constitution § Phased Delivery).

**Incremental delivery**: +US2 (goal + ring, incl. the practice signal) → validate steps 4–6 →
+US3 (Goals dashboard) → validate steps 6–7 → Polish → PR to `develop` with the quickstart results.

Commit after each task or tight pair (e.g. T012+T013). Every commit must leave
`./gradlew :shared:allTests` green — no phase may leave the app unbuildable or untestable.

---

## Notes

- `[P]` = different file, no dependency on an unfinished task.
- The **three clarified rules** from spec.md are easy to get wrong — re-read them before US2:
  1. Only **marking memorized** or a **full playthrough in Memorization/A–B Loop mode** credits the
     daily goal. Normal continuous playback never does.
  2. The daily count is **append-only** — un-marking a verse lowers the percentage but never the ring.
  3. The default goal is **10 verses/day**.
- **The day boundary is the other easy-to-miss trap** (T012 / research D9): `observeTodayPracticeCount()`
  must re-read `today()` on a poll and re-key the query with `flatMapLatest`. Binding the day once at
  subscription compiles, passes a naive test, and then silently shows yesterday's count to anyone who
  leaves the app open overnight. T013a case (a) is the guard.
- Progress must never be derived from playback counters (FR-007). If you find yourself reading a
  repetition counter to compute a percentage, you have taken a wrong turn.
