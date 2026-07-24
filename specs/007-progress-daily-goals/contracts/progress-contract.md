# Contract: Progress & Daily-Goal Domain

Behavioral contract for the memorization state, derived progress, daily practice record, and the
daily goal. All are `commonMain` and device-free-testable (Principle V). Maps to FR-001…FR-014.

## Repository interfaces

### `ProgressRepository`

| Method | Guarantee |
|--------|-----------|
| `setVerseMemorized(verseId, memorized): Resource<Unit>` | `true` → upsert a `memorization` row (UUID id, `memorized_at = clock()`) **and** credit today's practice for `verseId` (D3). `false` → delete the `memorization` row only; `daily_practice` is **untouched** (FR-014). Idempotent: setting to the current state is a no-op that still succeeds. |
| `setChapterMemorized(chapterId, memorized): Resource<Unit>` | Applies `setVerseMemorized` semantics to every verse of the chapter in one logical operation. `true` credits each **newly** memorized verse once (already-memorized verses are not re-credited); no double counting in the percentage (FR-003, edge "Bulk chapter mark with partial pre-existing state"). |
| `observeMemorizedVerseIds(matnId): Flow<Set<String>>` | Reactive set of memorized verse ids for the matn; re-emits on any change. Empty set when none. |
| `observeMatnProgress(matnId): Flow<MatnProgress>` | Reactive `(memorizedCount, totalCount)` for one matn from the single aggregate (D4). Zero-verse matn ⇒ `totalCount == 0`, `fraction == 0f` (FR-008). |
| `observeLibraryProgress(): Flow<List<MatnProgress>>` | Reactive progress for **all** متون in a deterministic order (library order), from the same aggregate — so every surface agrees (SC-002). |
| `recordPractice(verseId): Resource<Unit>` | `INSERT OR IGNORE` a `daily_practice` row `(newId, today(), verseId, clock())`. A second call for the same `(today(), verseId)` is a silent success (FR-010 once/day). Never throws on duplicate. |
| `observeTodayPracticeCount(): Flow<Int>` | Reactive `COUNT(*)` of `daily_practice` where `day_epoch == today()`. A new day reads `0` (FR-013). |

**Injected seams** (constructor, matching `BookmarkRepositoryImpl`): `today: () -> Long`
(local epoch-day, D1), `clock: () -> Long` (epoch millis), `newId: () -> String` (UUID). Tests
supply fakes to drive day rollover and fixed ids deterministically.

### `DailyGoalRepository`

| Method | Guarantee |
|--------|-----------|
| `observeGoal(): Flow<Int>` | Reactive read of the `daily_goal` `app_setting`. Absent or unparseable ⇒ **10** (FR-009/Q3), never a crash (font-size precedent). |
| `setGoal(target: Int): Resource<Unit>` | Persists `max(1, target)` as the setting value (FR-009 positive; FR-014 lower bound). Survives restart (SC-004). |

## Use-case contracts

- `ToggleVerseMemorizedUseCase(Params(verseId, memorized))` → forwards to
  `setVerseMemorized`; the ViewModel passes the *target* boolean (toggle computed from current UI
  state), keeping the use case branch-free.
- `MarkChapterMemorizedUseCase(Params(chapterId, memorized))` → forwards to `setChapterMemorized`.
- `ObserveVerseMemorizationUseCase(matnId)` → `observeMemorizedVerseIds`.
- `ObserveMatnProgressUseCase(matnId)` → `observeMatnProgress`.
- `ObserveLibraryProgressUseCase(Unit)` → `observeLibraryProgress`.
- `ObserveDailyProgressUseCase(Unit)` → `combine(progressRepo.observeTodayPracticeCount(),
  goalRepo.observeGoal()) { count, goal -> DailyProgress(count, goal) }`.
- `SetDailyGoalUseCase(target)` → `goalRepo.setGoal(target)`.

## Semantics & edge rules

1. **Recall-only progress** (FR-007): `MatnProgress` is a pure function of `memorization` rows;
   nothing playback-derived contributes. Replaying a verse without marking it changes nothing.
2. **Append-only day** (FR-014): un-marking a verse decreases `memorizedCount` (and the percentage)
   but never the day's `daily_practice` count. A verse marked then un-marked the same day stays
   counted for that day.
3. **Once per day** (FR-010/SC-006): the `UNIQUE(day_epoch, verse_id)` + `OR IGNORE` guarantees a
   verse contributes exactly 1 to the day regardless of how many times it is marked, replayed, or
   completed.
4. **Reset** (FR-013): `observeTodayPracticeCount` keys on `today()`; when the local day advances the
   count reads 0 while `memorization` rows (and percentages) are unaffected.
5. **Goal bounds**: writes below 1 are coerced to 1; the ring never divides by 0.
6. **Audio independence** (FR-004): both tables cascade from `verse`, never `audio_asset`.

## Test obligations (`commonTest`)

- `ProgressRepositoryTest`: toggle on/off; chapter bulk with partial pre-existing state (no double
  count); aggregate for mixed/zero/fully-memorized متون; audio-removal leaves progress intact;
  `recordPractice` dedup within a day; day rollover via fake `today`; un-mark does **not** drop the
  day count (FR-014).
- `DailyGoalRepositoryTest`: default 10 when unset; set/get round-trip; `setGoal(0)`/negative → 1.
- `MigrationV3Test`: seed v3 (متون/verses/bookmarks/notes/sessions), migrate to v4, assert old data
  intact and new tables usable.
- `ObserveDailyProgressUseCaseTest`: fraction and `isComplete` across under/at/over-goal counts.
- `MarkChapterMemorizedUseCaseTest`: bulk credits each newly-memorized verse once.
