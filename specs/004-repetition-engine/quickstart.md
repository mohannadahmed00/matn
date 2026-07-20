# Quickstart & Validation: Repetition Engine

**Feature**: Phase 3 — Repetition Engine | **Date**: 2026-07-20 | **Plan**: [plan.md](./plan.md)

How to run and validate the phase. Section A runs anywhere (Windows included) and covers the
correctness core; Section B needs an Android device/emulator; Section C needs macOS + Xcode.

---

## Prerequisites

- The Phase 0 seeded content and bundled per-verse audio (already in the repo).
- JDK 17+, Android SDK (compileSdk 36) for §B; macOS + Xcode for §C.
- No network required at any point — the whole phase is offline (FR-032 / SC-011).

---

## A. Shared logic — run on any host

```bash
./gradlew :shared:allTests          # commonTest: planner + controller + ViewModels
./gradlew :shared:compileKotlinIosSimulatorArm64   # iOS source still compiles on Windows
```

This is where the phase's correctness lives. Expect the following to pass with **no device, no
emulator, and no real audio files** (Principle V):

| Suite | Proves |
|-------|--------|
| `RepetitionPlannerTest` | All 12 planner rows (P1–P12) in [repetition-contract.md](./contracts/repetition-contract.md) §3 — pure table tests, no fakes |
| `RepetitionControllerTest` | All 18 controller rows (C1–C18) in §4, driven by scripted `FakeAudioEngine` events |
| `PlaybackControllerTest` (existing) | **Still green** — the Phase 2 regression guard for SC-010 |
| `PlayerBarViewModelTest` | Mode chip, "3 / 7" and ∞ rendering, drill-panel seeding |

**The single most important check**: every **observable-state** assertion in `PlaybackControllerTest`
(`status`, `activeVerseId`, `activeIndex`, `notice`, wake lock) must pass **unchanged**. If one needed
editing, default-configuration behavior diverged from Phase 2 and SC-010 is broken. Its
**engine-mechanics** assertions (`lastQueue`, `startIndex`, `seekedToTrack`) may differ only where the
window model requires it — exactly two assertions, enumerated in tasks.md T024.

---

## B. Android device validation

```bash
./gradlew :androidApp:installDebug
```

| # | Scenario | Expected | Requirement |
|---|----------|----------|-------------|
| 1 | Set $V_r$ = 3, play | Each verse plays exactly 3× before advancing; indicator counts 1/3 → 2/3 → 3/3 | FR-001, SC-002 |
| 2 | **Listen closely at each repetition boundary** | No click, pop, or silence when a verse repeats into itself | **FR-017, SC-004** |
| 3 | Set $M_r$ = 2 on a short matn, play to the end | Range replays exactly once more, then stops cleanly | FR-002, SC-003 |
| 4 | Raise $V_r$ from 3 to 7 **while playing** | Takes effect with no audible interruption; audio never stutters | FR-005, SC-005 |
| 5 | Mark A = verse 3, B = verse 5, play | Only verses 3–5 play; range is visibly marked in the list | FR-011, FR-014, SC-006 |
| 6 | Let the loop wrap from 5 back to 3 | Wrap is gapless, same as any advance | FR-018, SC-004 |
| 7 | Set $V_r$ = ∞, leave running **60 minutes** | Verse still repeating, controls still responsive, no drift or slowdown | FR-016, FR-030, SC-008 |
| 8 | During #7, check memory in Android Studio Profiler | Flat — playlist stays ~3 items, no unbounded growth | FR-030 |
| 9 | Tap next at repetition 2 of 5 | Remaining repetitions abandoned; next verse starts at 1/5 | FR-021 |
| 10 | Tap next on the last verse of an A–B range | Wraps to the range's first verse, does not leave the range | FR-021 |
| 11 | Scrub backward mid-verse, let it finish | Exactly one repetition consumed — indicator does not skip or double-count | FR-019, SC-009 |
| 12 | Phone call at repetition 4 of 9; end the call | Pauses gracefully; resumes at repetition 4 | FR-026, SC-009 |
| 13 | Pause at 2/5, wait, resume | Continues within repetition 2 — count not restarted | FR-022 |
| 14 | Remove/corrupt one verse's audio, run a 3-pass drill | Verse skipped, one notice, drill continues; not re-attempted on later passes | FR-028, D8 |
| 15 | Move B so the playing verse falls outside the range | Playback relocates into the new range | FR-025 |
| 16 | Set $V_r$ = 7 for matn A, open matn B | Matn B starts at 1/1; reopening A restores 7 | FR-007 |
| 17 | Leave both counters at 1, play a matn end to end | Identical to Phase 2: one pass, then stop | **SC-010** |
| 18 | Airplane mode, repeat #1 and #5 | Everything works; zero network requests | FR-032, SC-011 |
| 19 | Background the app mid-drill | Repetition continues; notification controls still work | FR-026, Phase 2 SC-007 |
| 20 | Check the mode chip while changing counters | Always matches the configuration — never contradicts it | FR-008 |
| 21 | From the reading screen, count taps to set both counters and start playback | **4 taps or fewer** | SC-001 |
| 22 | While a drill runs, watch the repetition indicator at each boundary | Updates within **1 second** of the new repetition starting | SC-007 |

---

## C. iOS validation (macOS + Xcode)

⚠️ **Carried-forward Phase 2 gap.** `AvQueueAudioEngine` is still a documented stub — its transport
body is authored and validated on macOS, since the Windows Kotlin/Native sysroot does not expose the
`AVPlayer` playback symbols. The two new primitives (`replaceUpcoming`, `dropConsumed`) inherit that
constraint. **This phase does not close that gap**; these rows are validated on macOS together with
the outstanding Phase 2 iOS work (T038/T040).

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Rows B1–B6 on device | Same behavior as Android |
| 2 | Repetition boundary listening test | Gapless — verify a **fresh `AVPlayerItem` per repetition**; a played item cannot be re-enqueued |
| 3 | Live counter change | Queue tail replaced without disturbing `currentItem` |
| 4 | 60-minute ∞ run | Stable; `AVQueuePlayer` item count stays bounded |

---

## Definition of done

- [ ] §A green on Windows, with `PlaybackControllerTest`'s observable-state assertions unchanged and
      only T024's two sanctioned engine-mechanics assertions differing (SC-010)
- [ ] §B rows 1–20 pass on an Android device, with rows **2, 4, 7, 17** given particular attention —
      they are the phase's headline promises (gapless repeat, inaudible reconfiguration, unlimited
      stability, zero Phase 2 regression)
- [ ] §C authored; validated on macOS with the outstanding Phase 2 iOS work
- [ ] No new third-party dependency introduced
- [ ] No database schema change
