# Quickstart: Validating Design System Adoption

**Feature**: Phase 10 — Design System Adoption | **Date**: 2026-07-24

How to prove this phase works. §A is automated and gates the PR. §B is visual/manual, because
matching a design and confirming RTL/animation feel cannot be asserted in `commonTest`.

Contracts referenced here live in [contracts/](./contracts/); types in
[data-model.md](./data-model.md). Canonical designs: `docs/DESIGN-SOURCE.md`.

---

## Prerequisites

- The repo builds on `develop` as it stands (Phases 1–5 merged).
- At least one seeded matn with 3+ verses (to exercise the carousel's previous/next slots) and
  one with exactly 1–2 verses (to exercise the Edge Case: missing previous/next).
- No network required — offline-first, same as every prior phase.

---

## A. Automated — `commonTest`

```bash
./gradlew :shared:allTests
```

### A1. No-regression guarantee (FR-012, SC-005)

Every existing behavioral test suite MUST still pass with unchanged assertions:
`PlaybackControllerTest`, `RepetitionControllerTest`, `RepetitionPlannerTest`,
`MatnDetailsViewModelTest`, `PlayerBarViewModelTest`. A failing assertion here (as opposed to a
failing *selector* because a composable moved) is a phase-scope violation — this phase must not
change behavior, only presentation (spec Overview, Constitution Check §I).

### A2. New state-shape coverage

`ReadingCarouselUiState`, `RepetitionSetupUiState`'s derivation logic (e.g. `canStart`,
previous/next-verse nullability at matn boundaries) should have unit coverage under
`commonTest` — pure functions of the existing verse list / `RepeatCount`, no fakes needed, same
treatment as `RepetitionPlannerTest`.

### A3. Token literal guard (Constitution VIII)

```bash
grep -rn "Color(0x" shared/src/commonMain/kotlin/com/giraffe/matn/presentation --include="*.kt" | grep -v "theme/Color.kt"
grep -rnE "\.dp\b" shared/src/commonMain/kotlin/com/giraffe/matn/presentation/{home,details,player,navigation,common} --include="*.kt" | grep -vE "MatnSpacing|MatnShapes"
```

Expect zero matches in files touched by this phase (pre-existing untouched files may still have
literals — those are tracked as follow-up, not a blocker for this phase's own screens).

---

## B. Manual / on-device — required before merge

| # | Check | Expected |
|---|---|---|
| B1 | Open Home screen | Top bar, daily-goal placeholder ring, Continue Learning card, matn grid all render with the new palette/type/spacing; placeholder ring shows a clearly-non-fabricated state (FR-010) |
| B2 | Open a matn with 3+ verses, start playback | Reading screen shows exactly 3 verses (muted previous, highlighted active, muted next); advancing a verse shifts the stack, no jank, RTL-correct |
| B3 | Same matn, seek to verse 1 | No previous-verse slot rendered/crashes; same check at the last verse for no next-verse slot (Edge Case) |
| B4 | Open repetition setup from the reading screen | Bottom sheet shows A-B toggle, start/end verse steppers, verse-repeat count, segment-repeat count, infinite option, one start button; RTL-correct |
| B5 | Configure A-B range + counts, tap start | Playback begins honoring the configured range/counts — cross-check against `specs/004-repetition-engine/quickstart.md`'s own manual checks for the underlying behavior |
| B6 | Open the sheet, change values, dismiss without starting | Previously active repetition settings unchanged; reopening the sheet does not show the discarded draft (Edge Case) |
| B7 | Tap each of the 4 bottom-nav tabs | Library shows Home; Goals/Notes/Settings show the "coming soon" placeholder with a way back; current tab indicated |
| B8 | Start playback on Library, switch to another tab and back | Playback keeps running (audio doesn't stop), Home/Details screen state intact — no regression of specs/003 background playback or specs/005 continue-learning |
| B9 | Every screen in this phase, device RTL check | Arabic text direction, icon mirroring, and control-bar layout all correct right-to-left (Principle VII) |

B2, B4, B7, B9 are the phase's own new surfaces and are **mandatory**, matching how prior phases
(e.g. specs/005 quickstart §B8) treat their own highest-risk manual check as non-optional.
