# Quickstart: Progress & Daily Goals — Validation Guide

Runnable checks that prove the feature end-to-end. Prefer the device-free `commonTest` suite; the
manual walkthrough covers the UI surfaces. Details live in the contracts and data-model — this file
is the run/validate guide.

## Prerequisites

- Phases 1–6 + 10 in place (reading carousel, playback + repetition engine, app shell with the
  Goals tab, Phase 10 tokens). Branch `007-progress-daily-goals`.
- Migration `3.sqm` present (schema v4); `kotlinx-datetime` on the `shared` classpath.
- Seeded content with at least two متون, one of them structured (chapters), totaling ≥ ~1,000 verses
  for the performance check.

## Automated validation (`commonTest` — device-free)

```
./gradlew :shared:commonTest        # or the platform host-test task the repo uses
```

Green means the contracts hold. Key suites and what they prove:

| Suite | Proves | Spec |
|-------|--------|------|
| `data/ProgressRepositoryTest` | toggle on/off; chapter bulk (no double count); aggregate for mixed/zero/full متون; audio-removal keeps progress; daily dedup; day rollover; **un-mark does not drop the day count** | FR-002/003/004/007/008/010/014 |
| `data/DailyGoalRepositoryTest` | default 10; set/get; `setGoal(0)`→1 | FR-009 |
| `data/MigrationV3Test` | v3 data (متون/bookmarks/notes/sessions) survives v3→v4; new tables usable | (migration) |
| `playback/PracticeSignalRecorderTest` | recall-mode completion credits once/day; **Normal mode credits nothing**; A–B loop credits; skip via `next()` no credit; day rollover; recorder never mutates the controller | FR-011/SC-006 |
| `domain/ObserveDailyProgressUseCaseTest` | fraction + `isComplete` under/at/over goal | FR-012 |
| `domain/MarkChapterMemorizedUseCaseTest` | bulk credits each newly-memorized verse once | FR-003 |
| `presentation/GoalsViewModelTest` | loading→populated; zero state; goal edit persists | FR-015/017 |
| `presentation/HomeViewModelTest` (ext.) | ring state from `DailyProgress`; card progress merged without gating the grid | FR-006/012 |
| `presentation/MatnDetailsViewModelTest` (ext.) | header progress; `onToggleMemorized` flips + recounts | FR-006/002 |

## Manual walkthrough (Android or iOS)

1. **Mark & per-matn % (US1 / SC-001, SC-002, SC-003)**: open a matn, mark several verses memorized
   from the carousel → each shows the memorized indicator and the header % rises. Open the library →
   the same matn's card shows the same %. Open the Goals tab → the same % appears in its row. Replay
   a verse repeatedly without marking → % unchanged.
2. **Un-mark (SC-002, FR-014)**: un-mark a verse → header/card/Goals % all drop together; today's
   ring count does **not** drop.
3. **Chapter bulk (FR-003)**: on a structured matn, "mark entire chapter" → every verse in it shows
   memorized and the % jumps; un-mark reverses it.
4. **Daily ring (US2 / SC-005, SC-006)**: set a small goal (e.g., 2) on the Goals tab. Practice
   verses in **Memorization or A–B Loop mode** until the Home ring fills; confirm it reads complete
   at the goal. Replay an already-counted verse → ring does not advance past one credit for it.
   Practice in **Normal continuous mode** → ring does **not** advance.
5. **Reset (SC-005 / FR-013)**: advance the device's local date (or use the injected `today` in a
   test) → the ring resets to empty; memorized % and the goal are unchanged.
6. **Goal edit (US3)**: change the goal on the Goals tab → return to Home; the ring rescales to the
   new goal.
7. **Placeholder gone (SC-007)**: the Goals tab shows real content or a purposeful zero state — the
   "coming soon" screen no longer appears for Goals.
8. **Persistence (SC-004)**: fully close and reopen the app → memorized states, %, goal, and today's
   count are all intact.
9. **Performance (SC-001)**: on the ≥1,000-verse library, marking a verse updates the % within ~1 s
   with no visible freeze.

## Design-fidelity gate (Principle VIII)

Before implementing any Goals/Home ring UI, fetch *Progress & Goals*
`f059cccd2f634bc9ba2cf4d620e5df80` and the ring region of *Home / Library*
`618643f891144557b5a4ddf4bbad0c03` via the `stitch` MCP server; build with Phase 10 tokens; no raw
hex/`.dp`/`.sp` literals; `DailyGoalRing`/`MatnProgressBar`/`MemorizedGlyph` extracted as shared
stateless components, each with a `@Preview`.
