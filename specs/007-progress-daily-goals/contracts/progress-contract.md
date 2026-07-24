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
| `observeTodayPracticeCount(): Flow<Int>` | Reactive `COUNT(*)` of `daily_practice` where `day_epoch == today()`. **`today()` is re-evaluated on a poll interval and drives the query via `flatMapLatest` (research D9)** — the flow MUST NOT bind the day once at subscription. A collector held open across local midnight therefore re-emits `0` for the new day without re-subscription (FR-013, spec edge "Day rollover mid-session"). |

**Injected seams** (constructor, matching `BookmarkRepositoryImpl`): `today: () -> Long`
(local epoch-day, D1), `clock: () -> Long` (epoch millis), `newId: () -> String` (UUID), and
`dayCheckIntervalMs: Long = 60_000` (D9 poll cadence). Tests supply fakes to drive day rollover and
fixed ids deterministically, and shrink the interval to run the rollover under virtual time.

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
4. **Reset** (FR-013): `observeTodayPracticeCount` keys on `today()`, re-read on the D9 poll — the
   count reads 0 once the local day advances, **including while a collector stays subscribed across
   midnight**, and `memorization` rows (and percentages) are unaffected. Re-reading `today()` also
   covers a manual clock or time-zone change (spec edge "Device clock / time-zone change").
5. **Goal bounds**: writes below 1 are coerced to 1; the ring never divides by 0.
6. **Audio independence** (FR-004): both tables cascade from `verse`, never `audio_asset`.

## Test obligations (`commonTest`)

- `ProgressRepositoryTest`: toggle on/off; chapter bulk with partial pre-existing state (no double
  count); aggregate for mixed/zero/fully-memorized متون; audio-removal leaves progress intact;
  `recordPractice` dedup within a day; day rollover via fake `today`; un-mark does **not** drop the
  day count (FR-014). Plus:
  - **Rollover while collecting** (D9/FR-013): with a shrunken `dayCheckIntervalMs`, hold a
    `observeTodayPracticeCount()` collector open, advance the fake `today`, advance virtual time past
    the interval, and assert the flow re-emits `0` — without re-subscribing.
  - **Time-zone / clock change** (spec edge): the same mechanism with `today` jumping backwards one
    day re-emits that day's count rather than throwing or sticking.
  - **Rapid toggling** (spec edge): 10 rapid sequential `setVerseMemorized` alternations settle to a
    single consistent final state — never a duplicate row or a phantom memorized flag.
  - **Restart persistence** (SC-004): both memorization rows *and* the current day's practice count
    are still correct when read through a fresh query object over the same driver.
- `ProgressPerformanceTest` (SC-001 guard): with ≥1,000 verses across ≥3 متون, marking one verse
  produces an updated `observeMatnProgress` emission within the budget. Follow the flake policy of
  `SearchPerformanceTest` — if unstable on slow CI hosts, relax the constant and document that
  SC-001's real 1 s budget is verified by the quickstart device check, but do not delete the test.
- `DailyGoalRepositoryTest`: default 10 when unset; set/get round-trip; `setGoal(0)`/negative → 1.
- `MigrationV3Test`: seed v3 (متون/verses/bookmarks/notes/sessions), migrate to v4, assert old data
  intact and new tables usable.
- `ObserveDailyProgressUseCaseTest`: fraction and `isComplete` across under/at/over-goal counts.
- `MarkChapterMemorizedUseCaseTest`: bulk credits each newly-memorized verse once.
