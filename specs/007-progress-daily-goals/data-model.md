# Data Model: Progress & Daily Goals

Derived from the spec's Key Entities + research decisions. New persistence is two additive tables
plus one reused `app_setting` key; new in-memory domain models are all derived projections.

## 1. Persistence (SQLDelight — `Content.sq`, migration `3.sqm`, schema v3 → v4)

### 1.1 `memorization` (NEW)

Explicit per-verse recall self-report (spec: *Memorization Status*).

| Column | Type | Notes |
|--------|------|-------|
| `id` | TEXT PK | UUID minted at creation (Constitution VI — sync-safe identity), independent of `verse_id`. |
| `verse_id` | TEXT | `UNIQUE REFERENCES verse(id) ON DELETE CASCADE`. Enforces ≤1 status/verse; doubles as lookup index; CASCADE ties lifetime to verse text, never audio (FR-004). |
| `memorized_at` | INTEGER | Epoch millis when marked (injected `clock`). Ordering + basis for the daily practice credit. |

Presence of a row = memorized. Un-marking deletes the row. (Mirrors the `bookmark` shape.)

### 1.2 `daily_practice` (NEW)

Append-only record of distinct verses practiced per local day (spec: *Daily Practice Record*).

| Column | Type | Notes |
|--------|------|-------|
| `id` | TEXT PK | UUID minted at creation (Constitution VI). |
| `day_epoch` | INTEGER | Local calendar day as epoch-day (research D1). The reset boundary (FR-013): a new day = a new value = a fresh count. |
| `verse_id` | TEXT | `REFERENCES verse(id) ON DELETE CASCADE`. |
| `practiced_at` | INTEGER | Epoch millis of the first credit that day (injected `clock`). |
| — | — | `UNIQUE(day_epoch, verse_id)` — at most once per verse per day (FR-010). Insert uses `OR IGNORE` so replays/re-marks are silent no-ops. |

Rows are **never** deleted by un-marking a verse (FR-014, append-only). They cascade only if the
verse text row itself is removed.

### 1.3 `app_setting` (REUSED — no schema change)

The daily goal is stored under key `daily_goal`, value = the integer as a string (research D5).
Absent/unparseable → default **10** (FR-009). The existing `selectSetting`/`upsertSetting`/
`deleteSetting` queries are reused; no new column.

### 1.4 New queries (in `Content.sq`)

```
-- Memorization
selectMemorizedVerseIdsByMatn:  -- Set<verseId> for one matn (indicators + per-matn %)
insertMemorization / deleteMemorizationByVerse / selectMemorizationByVerse

-- Progress aggregate (research D4) — one source for every surface
selectMatnProgress:             -- (matn_id, total_verses, memorized_verses) for ALL متون
selectMatnProgressById:         -- same, filtered to one matn (details header)
  --   FROM matn
  --   LEFT JOIN verse        ON verse.matn_id = matn.id
  --   LEFT JOIN memorization ON memorization.verse_id = verse.id
  --   GROUP BY matn.id      -- zero-verse matn ⇒ total 0, memorized 0 (FR-008)

-- Chapter bulk (mark entire chapter) — reuse existing selectVersesByChapter for the verse set

-- Daily practice
insertDailyPractice:            -- INSERT OR IGNORE (day_epoch, verse_id) dedup (FR-010)
selectDailyPracticeCount:       -- COUNT(*) WHERE day_epoch = ?  (today's ring numerator)
```

All read queries are reactive (`asFlow`/`mapToList`/`mapToOne`) so any memorization/practice write
re-emits current progress (SC-001/SC-005).

## 2. Domain models (`commonMain/domain/model`) — all derived, none persisted as-is

### 2.1 `MatnProgress` (NEW)

```
data class MatnProgress(
    val matnId: String,
    val memorizedCount: Int,
    val totalCount: Int,
) {
    val fraction: Float get() = if (totalCount == 0) 0f else memorizedCount.toFloat() / totalCount
    val percent: Int    get() = (fraction * 100).roundToInt()
}
```
`totalCount == 0` ⇒ `fraction == 0f` (FR-008, no divide-by-zero). Same instance feeds the details
header, the library card, and the Goals dashboard (SC-002).

### 2.2 `DailyProgress` (NEW)

```
data class DailyProgress(
    val practicedToday: Int,
    val goal: Int,               // ≥ 1
) {
    val fraction: Float  get() = (practicedToday.toFloat() / goal).coerceIn(0f, 1f)
    val isComplete: Bool get() = practicedToday >= goal
}
```
`practicedToday` comes from `selectDailyPracticeCount(today())`; `goal` from the `daily_goal`
setting. `fraction` is clamped so an over-target day still reads as a full ring (FR-012 edge).

### 2.3 In-memory memorization set

`Set<String>` of memorized verse ids for a matn (from `selectMemorizedVerseIdsByMatn`), used by the
reading carousel to render the per-verse indicator (FR-002) and available for the header count.

## 3. Repository interfaces (`commonMain/domain/repository`)

### 3.1 `ProgressRepository`

```
interface ProgressRepository {
    // Memorization state
    suspend fun setVerseMemorized(verseId: String, memorized: Boolean): Resource<Unit>
    suspend fun setChapterMemorized(chapterId: String, memorized: Boolean): Resource<Unit>
    fun observeMemorizedVerseIds(matnId: String): Flow<Set<String>>

    // Derived progress (single source, research D4)
    fun observeMatnProgress(matnId: String): Flow<MatnProgress>
    fun observeLibraryProgress(): Flow<List<MatnProgress>>

    // Daily practice record (append-only)
    suspend fun recordPractice(verseId: String): Resource<Unit>   // credits today() once; OR IGNORE
    fun observeTodayPracticeCount(): Flow<Int>                    // re-keys on today(); see D9
}
```
Implementation injects `today: () -> Long`, `clock: () -> Long`, `newId: () -> String` (the
established `BookmarkRepositoryImpl` constructor shape) and `dayCheckIntervalMs: Long = 60_000`
(research D9). `setVerseMemorized(true)` also calls the practice-credit path (research D3);
`setVerseMemorized(false)` deletes only the memorization row. `observeTodayPracticeCount()` must
re-evaluate `today()` on the poll interval rather than capturing it at subscription, so the count
resets at local midnight even while a collector stays open (D9).

### 3.2 `DailyGoalRepository`

```
interface DailyGoalRepository {
    fun observeGoal(): Flow<Int>                 // default 10 when unset/invalid
    suspend fun setGoal(target: Int): Resource<Unit>   // coerced to ≥ 1
}
```

## 4. Use cases (`commonMain/domain/usecase`)

| Use case | Contract | Purpose |
|----------|----------|---------|
| `ToggleVerseMemorizedUseCase` | `UseCase<Params, Unit>` (verseId, memorized) | Set state; ON also credits today (D3). |
| `MarkChapterMemorizedUseCase` | `UseCase<Params, Unit>` (chapterId, memorized) | Bulk mark/un-mark; ON credits each newly-memorized verse once. |
| `ObserveVerseMemorizationUseCase` | `FlowUseCase<String, Set<String>>` | Memorized verse ids for a matn (carousel indicators). |
| `ObserveMatnProgressUseCase` | `FlowUseCase<String, MatnProgress>` | Details-header progress. |
| `ObserveLibraryProgressUseCase` | `FlowUseCase<Unit, List<MatnProgress>>` | Library cards + Goals dashboard list. |
| `ObserveDailyProgressUseCase` | `FlowUseCase<Unit, DailyProgress>` | `combine(todayCount, goal)` → ring state (Home + Goals). |
| `SetDailyGoalUseCase` | `UseCase<Int, Unit>` | Bounded (≥1) goal write. |

## 5. Presentation state (see contracts/goals-ui-contract.md)

- `GoalsUiState` — `dailyProgress: DailyProgress?`, `matnProgress: List<MatnProgress>`,
  `isLoading`, `isEmpty` (zero متون), plus the current goal for the editor.
- `DailyGoalUiState` (MODIFY) — replace the placeholder with real fields
  (`practiced: Int`, `goal: Int`, `fraction: Float`, `isComplete: Boolean`); the `isPlaceholder`
  flag is retired once wired.
- `HomeUiState` (MODIFY) — add `progressByMatn: Map<String, Float>` for card overlays.
- `MatnDetailsUiState.MatnHeader` (MODIFY) — add `memorizedCount: Int` + `progressFraction: Float`;
  `MatnDetailsUiState` gains a `memorizedVerseIds: Set<String>` for the carousel.

## 6. State transitions

- **Memorized(verse)**: absent → present (`setVerseMemorized(true)`: insert row + credit today);
  present → absent (`setVerseMemorized(false)`: delete row, daily_practice untouched).
- **Daily count**: `0` at the first read of a new `day_epoch`; monotonically non-decreasing within a
  day (each distinct practiced verse `+1`, dedup by UNIQUE); resets when `today()` advances (FR-013)
  — including mid-session, because the observing flow re-reads `today()` on a poll and re-keys the
  query via `flatMapLatest` (research D9). Never decremented (FR-014).
- **Goal**: default `10` → any `≥1` via `setGoal`; persists across restart (SC-004).

## 7. Invariants

1. `memorizedCount ≤ totalCount`; `fraction ∈ [0,1]`; `totalCount == 0 ⇒ fraction == 0`.
2. Same `MatnProgress` source on all surfaces ⇒ they always agree (SC-002).
3. `practicedToday ≥ 0`, bounded by distinct verses actually practiced that day (FR-014); each verse
   contributes ≤ 1/day (FR-010/SC-006).
4. Progress depends only on `memorization` rows, never on playback counters (FR-007).
5. `memorization`/`daily_practice` cascade from `verse`, never from `audio_asset` (FR-004).
