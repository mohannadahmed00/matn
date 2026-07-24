---

description: "Task list for Phase 10 — Design System Adoption"
---

# Tasks: Phase 10 — Design System Adoption

**Input**: Design documents from `/specs/010-design-system-adoption/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Included but scoped down per Constitution Principle V — this phase changes no
playback/repetition/progress *behavior* (spec FR-005, FR-012), so no new domain-behavior tests are
required. What IS required: (a) every existing behavioral suite keeps passing unchanged
(`PlaybackControllerTest`, `RepetitionControllerTest`, `RepetitionPlannerTest`,
`MatnDetailsViewModelTest`, `PlayerBarViewModelTest`), (b) the small new derived-state functions
(carousel windowing, sheet `canStart`) get pure `commonTest` coverage per Principle V, and (c)
every new/changed state-rendering composable ships a `@Preview` per Principle II — that is this
phase's substitute for UI tests.

**Organization**: Grouped by user story. Phase 2 (Foundational) blocks all stories.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US4, mapping to spec.md's prioritized user stories
- Every task names its exact file path

---

## 🛑 Read this before starting any task

1. **This phase changes no domain/data code.** If a task seems to require touching
   `domain/model/RepeatCount.kt`, `domain/model/RepetitionSettings.kt`, `playback/RepetitionPlanner.kt`,
   or `playback/PlaybackController.kt`, stop — that's out of scope (FR-005) and means the UI is
   being wired wrong, not that the domain needs to change.
2. **Zero raw literals rule (Constitution VIII).** No `Color(0x...)`, no bare `.dp`/`.sp` outside
   `theme/`. Every task that touches a screen file ends with the token-literal grep from
   `quickstart.md` §A3 returning clean for that file.
3. **Second-use extraction rule (Constitution VIII).** If a task's markup duplicates something
   already built in an earlier task (e.g. a card shape, a stepper control), extract it into
   `presentation/common/` instead of copy-pasting — don't wait for a third use.
4. **RTL is not optional.** Every new layout (carousel, bottom sheet, nav bar) must be verified
   right-to-left, not just visually similar to the Stitch screenshot in LTR.

---

## Phase 1: Setup

**Purpose**: Bundle the two new font families every later phase's typography depends on.

- [X] T001 [P] Add Source Serif 4 (Regular/SemiBold, SIL OFL) font files to
      `shared/src/commonMain/composeResources/font/` — bundled as a single variable-weight file
      (`SourceSerif4-Variable.ttf`); Google Fonts ships no static per-weight instances upstream
- [X] T002 [P] Add Plus Jakarta Sans (Regular/SemiBold, SIL OFL) font files to
      `shared/src/commonMain/composeResources/font/` — same variable-weight-file note as T001
- [X] T003 [P] Add font license files (`OFL.txt` per family) alongside the bundled fonts, matching
      how the existing Amiri license is tracked (`<Family>-OFL.txt` naming, matching `Amiri-OFL.txt`)

**Checkpoint**: Font assets present; nothing references them yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared token layer every screen in every user story reads from. No user-story
task may start before this phase is done.

**⚠️ CRITICAL**: No screen work can begin until this phase is complete.

- [X] T004 Replace the color values in `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/theme/Color.kt`
      with the M3 role set from `docs/DESIGN-SOURCE.md` § Design tokens (per
      [contracts/design-tokens-contract.md](./contracts/design-tokens-contract.md) § 1)
- [X] T005 Extend `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/theme/Type.kt` with
      Source Serif 4 and Plus Jakarta Sans `FontFamily`s (T001/T002) and map them onto
      `Typography` roles per [contracts/design-tokens-contract.md](./contracts/design-tokens-contract.md) § 2,
      keeping Amiri on the Arabic verse-text roles — **deviation from the literal contract**: every
      concrete `headline-lg`/`body-md` usage in the fetched Stitch HTML turned out to be Arabic
      text, and Source Serif 4 has no Arabic glyphs, so `headlineMedium`/`bodyLarge`/etc. stay on
      Amiri (unchanged from pre-Phase-10) rather than moving to Source Serif 4; `labelLarge/Medium/
      Small` move to Plus Jakarta Sans instead (was "platform default"); Source Serif 4 is bundled
      and exposed via `uiSerifFontFamily()` for the first genuinely Latin-only context, and a new
      `arabicLabelSmall()` style covers Arabic text at label scale. See Type.kt KDoc + Constitution
      Principle VIII ("constitution outranks the design" for genuine technical necessity).
- [X] T006 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/theme/Shape.kt`
      with `MatnShapes` (`lg`/`xl`/`full`) per
      [contracts/design-tokens-contract.md](./contracts/design-tokens-contract.md) § 3
- [X] T007 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/theme/Spacing.kt`
      with `MatnSpacing` (`unit`/`gutter`/`marginMobile`/`marginDesktop`) per
      [contracts/design-tokens-contract.md](./contracts/design-tokens-contract.md) § 4
- [X] T008 Wire `Shape.kt`/`Spacing.kt` into
      `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/theme/MatnTheme.kt` alongside the
      existing `MaterialTheme` color/type wrap (depends on T004–T007)
- [X] T009 [P] Add `ReadingFontSize` → `Typography` role resolution check in `FontScale.kt` so the
      font-size slider still composes correctly with the new type roles — verified as a **no-op**:
      `ReadingFontSize.toSp()` only ever produces a raw size applied directly to a `Text`'s
      `fontSize`, entirely independent of `Typography`/`FontFamily` role resolution, so nothing
      needed to change

**Checkpoint**: `MatnTheme` fully reflects the canonical token set. No screen has been touched
yet — every subsequent task is additive/replacement work on top of this theme.

---

## Phase 3: User Story 1 - Read and listen with the focused verse view (Priority: P1) 🎯 MVP

**Goal**: Reading/playback becomes the previous/active/next 3-verse carousel with a floating
control bar, with zero regression to specs/003's playback behavior.

**Independent Test**: Open a matn with ≥3 verses, start playback, verify exactly 3 verses are
visible with the active one distinguished, and every transport control still behaves as before.

### Tests for User Story 1

- [X] T010 [P] [US1] Unit tests for the carousel windowing derivation (previous/active/next
      selection, `null` at first/last verse per spec Edge Cases) in
      `shared/src/commonTest/kotlin/com/giraffe/matn/presentation/ReadingCarouselStateTest.kt`
      — pure function, no fakes, write first and confirm it fails before T013 — 6/6 passing

### Implementation for User Story 1

- [X] T011 [P] [US1] Define `ReadingCarouselUiState`/`VerseDisplay` per
      [data-model.md](./data-model.md) in
      `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/player/ReadingCarouselUiState.kt`
      — reuses `details.VerseRow` directly as the display type instead of a redundant new
      `VerseDisplay`, since it already carries id/displayNumber/arabicText/durationMs/chapterId
- [X] T012 [US1] Implement the windowing derivation (previous/active/next from the existing verse
      list + playback position) that T010 tests, in the same file as T011 (depends on T010, T011)
- [X] T013 [US1] Build the stateless `ReadingCarousel` content composable (muted previous, glowing
      active card, muted next, verse-label meta row) in
      `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/player/ReadingCarousel.kt`, driven
      only by `ReadingCarouselUiState` (depends on T012)
- [X] T014 [US1] Add `@Preview`s for `ReadingCarousel` covering: mid-matn (both neighbors present),
      first verse (no previous), last verse (no next) — Principle II requirement (depends on T013)
- [X] T015 [US1] Rework `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/player/PlayerBar.kt`
      into the floating glass-panel control bar (scrub bar, speed pill, prev/play-pause/next
      transport, stop) per the canonical "Reading & Playback (Updated)" design, re-skinned with
      tokens from Phase 2 — same `PlaybackController` intents as today, no new transport behavior.
      **Deviation**: the repeat-entry-point and audio-settings icons shown in the Stitch mockup are
      deliberately not rendered — User Story 2 (repetition setup sheet, not yet implemented) is
      what would give the repeat icon something to open; a dead icon was judged worse than omitting
      it. `DrillPanel` stays docked above the bar unchanged (its US2 replacement is a later phase).
- [X] T016 [US1] Add/update `@Preview`s for the reworked `PlayerBar` (playing / paused / stopped
      states) (depends on T015)
- [X] T017 [US1] Replace the verse-list + docked-bar composition in
      `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/details/MatnDetailsScreen.kt`'s
      active-playback path with `ReadingCarousel` + the reworked `PlayerBar` (depends on T013, T015)
      — the browse/details verse list itself (US4) is unaffected; only the *active playback*
      presentation changes. Gated on `windowVersesForCarousel(state.verses, state.activeVerseId) !=
      null`: no active verse → unchanged browse list; active verse → carousel.
- [X] T018 [US1] Verify `MatnDetailsViewModelTest`/`PlayerBarViewModelTest` still pass with only
      selector/structure updates, never assertion changes (Constitution Check §V; depends on T017)
      — confirmed via `:shared:testAndroidHostTest`: 12/12 and 4/4 passing, 0 failures, plus
      `PlaybackControllerTest` 23/23, `RepetitionControllerTest` 20/20, `RepetitionPlannerTest`
      12/12 — full zero-regression check, not just the two directly-touched suites.
- [X] T019 [US1] Manual RTL + gapless-transition check per quickstart.md §B2/B3/B9 (depends on T017)
      — done on a Pixel 6 (AVD): installed via `:androidApp:installDebug`, launched, opened "الأجرومية",
      tapped a verse's play button. **Confirmed on-device**: the carousel replaces the browse list
      while a session is active; the active-verse card renders large/highlighted with the leading
      accent bar on the correct (right/RTL-start) side; the verse-label meta row ("Verse 1" +
      hairline dividers) is centered; a muted/de-emphasized neighbor verse renders below it; the
      first-verse edge case renders with **no** previous-verse slot (confirms B3); the floating
      glass-panel `PlayerBar` (scrub row, time labels, stop/speed/transport) renders correctly
      including its loading state; falling back to the browse list when no session is active also
      confirmed. **Not confirmed live**: the both-neighbors (mid-matn) visual and a real
      audio-to-audio gapless transition — this emulator's seeded matn audio files are corrupt/
      unplayable placeholders (`ExoPlaybackException` /
      `UnrecognizedInputFormatException` in logcat, pre-existing seed-data issue, unrelated to this
      phase), so `PlaybackController` auto-skips every verse and the session ends before a second
      verse's audio can load. The both-neighbors windowing case is exhaustively covered by
      `ReadingCarouselStateTest` (6/6 passing) and uses the exact same `NeighborVerse` composable
      already confirmed rendering correctly for the next-verse slot, so this is a low-risk gap, not
      an unknown — but it's flagged here rather than silently claimed as seen.

**Checkpoint**: Reading/playback is fully on the new carousel and independently demoable/testable.

---

## Phase 4: User Story 2 - Configure repetition from one consolidated screen (Priority: P2)

**Goal**: A–B toggle, range, per-verse/segment counts, and an explicit start action all live in
one bottom sheet, replacing the scattered `DrillPanel` + long-press menu, with the same underlying
`RepetitionPlanner`/`RepeatCount` behavior as specs/004.

**Independent Test**: Open repetition setup, configure A–B range + both counts, tap start, and
verify playback honors exactly that configuration (cross-checked against specs/004's own tests).

### Tests for User Story 2

- [X] T020 [P] [US2] Unit tests for `RepetitionSetupUiState.canStart` derivation (invalid range,
      A-B on with missing end verse, valid finite/infinite combinations) in
      `shared/src/commonTest/kotlin/com/giraffe/matn/presentation/RepetitionSetupUiStateTest.kt` —
      6/6 passing

### Implementation for User Story 2

- [X] T021 [P] [US2] Define `RepetitionSetupUiState` per [data-model.md](./data-model.md) in
      `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/player/RepetitionSetupUiState.kt`,
      reusing `domain/model/RepeatCount.kt` unchanged
- [X] T022 [US2] Implement the `canStart` derivation that T020 tests, in the same file as T021
      (depends on T020, T021)
- [X] T023 [US2] Build the stateless `RepetitionSetupSheet` content composable (A-B toggle,
      start/end verse steppers, verse-repeat counter, segment-repeat counter, infinite checkbox,
      start button) per
      [contracts/repetition-setup-contract.md](./contracts/repetition-setup-contract.md) § 1, in
      `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/player/RepetitionSetupSheet.kt`
      (depends on T022) — no separate infinite *checkbox*; infinite is a step of the existing
      `RepeatCountStepper` (∞ past the max finite value), consistent with how `DrillPanel` already
      represented it — introducing a second, differently-shaped control for the same concept would
      have been inconsistent, not more complete.
- [X] T024 [US2] Wire `onStart` to the existing `PlaybackController.setVerseRepeat`/
      `setMatnRepeat`/`setLoopStart`/`setLoopEnd` intents per
      [contracts/repetition-setup-contract.md](./contracts/repetition-setup-contract.md) § 2, and
      `onDismiss` to discard the draft per § 3 (depends on T023) — via `RepetitionSetupHost`, a
      stateful holder that owns the draft as local Compose state (not a ViewModel — see its KDoc)
      and forwards to the same `MatnDetailsViewModel`/`PlayerBarViewModel` intents `DrillPanel` and
      the verse-row long-press menu used before.
- [X] T025 [US2] Add `@Preview`s for `RepetitionSetupSheet` covering: A-B off, A-B on with valid
      range, A-B on with invalid/incomplete range (start disabled) (depends on T023)
- [X] T026 [US2] Replace `DrillPanel` usage in `MatnDetailsScreen.kt`/`PlayerBar.kt` with an entry
      point that opens `RepetitionSetupSheet`, and remove the long-press loop-boundary context menu
      from verse rows in `MatnDetailsScreen.kt` now that the sheet owns range selection (depends on
      T024) — added a new `RepeatGlyph` to `PlaybackGlyphs.kt` (hand-drawn, matching the file's
      existing no-system-emoji convention) for the entry-point icon in `PlayerBar`'s transport row.
- [X] T027 [US2] Delete `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/player/DrillPanel.kt`
      once nothing references it (depends on T026) — `RepeatCountStepper` was moved (not deleted)
      into `RepetitionSetupSheet.kt` and re-skinned with Phase 10 tokens, since the sheet reuses it
      for both the verse- and segment-repeat rows (Constitution III/VIII reuse rule).
- [X] T028 [US2] Verify `RepetitionControllerTest`/`RepetitionPlannerTest`/`PlayerBarViewModelTest`
      still pass unchanged in assertions (depends on T026) — confirmed via `:shared:testAndroidHostTest`:
      20/20, 12/12, 4/4 passing; `MatnDetailsViewModelTest` 12/12 also re-checked (verse-row long-press
      removal touches that screen) — 0 failures across the board.
- [X] T029 [US2] Manual check per quickstart.md §B4/B5/B6/B9 (depends on T026) — **partially done**
      on the same Pixel 6 (AVD). **Confirmed on-device**: the repeat-entry-point icon renders
      correctly in the transport row (RTL-correct position), `DrillPanel` is gone, tapping a verse
      still starts the carousel. **Not confirmed live**: actually opening the sheet and interacting
      with it — the same broken/unplayable seed-audio issue noted in T019 means the transport row
      spends nearly all its time in the loading state (spinner, no icons) rather than settling long
      enough to reliably tap the repeat icon; several timed-tap attempts landed mid-reload instead
      of on the icon. Compensated with real `@Preview`s of the full `RepetitionSetupSheet` (not just
      its sub-components) added in T025, which compile and cover A-B off / valid range / invalid
      range — plus 6/6 passing unit tests on `canStart`. The dismiss-discards-draft guarantee (Edge
      Case) is by construction (`RepetitionSetupHost`'s draft is `remember(visible)`-scoped local
      state — see its KDoc) rather than device-confirmed. Flagged rather than claimed as seen.

**Checkpoint**: Repetition setup is fully consolidated and independently demoable/testable.

---

## Phase 5: User Story 3 - Navigate the app through a persistent bottom nav (Priority: P3)

**Goal**: A 4-tab bottom nav (Library/Goals/Notes/Settings) wraps top-level screens; unbuilt
destinations show a shared placeholder; in-progress playback survives tab switches.

**Independent Test**: From Home, verify all 4 tabs render; tap each non-Library tab and confirm a
placeholder appears; start playback, switch tabs and back, confirm playback never stopped.

### Implementation for User Story 3

- [X] T030 [P] [US3] Define `NavigationTab` enum per [data-model.md](./data-model.md) in
      `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/navigation/NavigationTab.kt` —
      also added `NavGlyphs.kt` (Library/Goals/Notes/Settings hand-drawn icons, matching
      `PlaybackGlyphs.kt`'s existing no-system-icon-font convention)
- [X] T031 [P] [US3] Build the stateless `ComingSoonScreen` composable (per
      [contracts/navigation-contract.md](./contracts/navigation-contract.md) § 4) with a
      `@Preview` per stubbed tab, in
      `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/navigation/ComingSoonScreen.kt`
- [X] T032 [US3] Add `Routes.GOALS`/`Routes.NOTES`/`Routes.SETTINGS` and their `composable{}`
      entries (all → `ComingSoonScreen`) to
      `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/navigation/MatnNavHost.kt` per
      [contracts/navigation-contract.md](./contracts/navigation-contract.md) § 1 (depends on T030, T031)
- [X] T033 [US3] Wrap the nav graph in a `Scaffold` with a bottom `NavigationBar` (visible only on
      the 4 top-level routes per § 2) using `launchSingleTop`/`restoreState`/`saveState` per
      [contracts/navigation-contract.md](./contracts/navigation-contract.md) § 3, in
      `MatnNavHost.kt` (depends on T032)
- [X] T034 [US3] Manual check per quickstart.md §B7/B8/B9 (depends on T033) — done on the same
      Pixel 6 (AVD), using `adb shell uiautomator dump` for exact tap coordinates rather than
      estimating from screenshots (screenshot pixels are scaled ~0.83× vs. physical device pixels
      in this environment — an early coordinate mismatch cost real time here). **Confirmed
      on-device**: all 4 tabs render with correct RTL ordering and the current tab highlighted;
      tapping Goals navigates to `ComingSoonScreen` (icon, title, message, "Back to Library"
      button all render correctly); "Back to Library"/Library-tab round-trip returns to Home with
      its Continue Learning state intact (`saveState`/`restoreState` working); the bottom nav
      correctly disappears on the Matn Details/reading screen (§ 2). **Found and fixed a real bug
      live**: the English `coming_soon_message` string used Android's `\'` apostrophe-escaping
      convention, which Compose Resources (JetBrains' resource system, not AAPT) does not
      interpret the same way — it rendered the literal backslash on-device. Fixed by using a plain
      typographic apostrophe (’) instead of escaping. **Not exhaustively confirmed live**: the
      Notes and Settings tabs individually (identical code path to Goals — same `ComingSoonScreen`,
      same routing — so a low-risk gap) and a real audio-survives-tab-switch check (blocked by the
      same broken seed-audio issue as T019/T029; Home's ViewModel state visibly surviving the
      round-trip is the closest available evidence).

**Checkpoint**: The bottom nav shell is live and independently demoable/testable.

---

## Phase 6: User Story 4 - See the redesigned Home and Matn Details screens (Priority: P4)

**Goal**: Home and Matn Details are re-skinned to the token set from Phase 2, with all pre-existing
elements (Continue Learning card, matn grid, header, table of contents, verse list, loop-boundary
controls now superseded by US2) preserved and functioning.

**Independent Test**: Open Home and a matn's Details screen; verify every color/font/spacing value
traces to a token (quickstart.md §A3 grep clean) and every pre-existing element still works.

### Implementation for User Story 4

- [X] T035 [P] [US4] Define `DailyGoalUiState` per [data-model.md](./data-model.md) in
      `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/home/DailyGoalUiState.kt`
- [X] T036 [P] [US4] Extract `MatnCard` out of
      `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/home/HomeScreen.kt` into
      `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/common/MatnCard.kt` as a
      stateless, parameterized component (Constitution VIII second-use rule — it's already
      repeated across the grid) with a `@Preview`
- [X] T037 [US4] Add the top app bar and the daily-goal placeholder ring to `HomeScreen.kt`, wired
      to `DailyGoalUiState` (FR-010: placeholder, not fabricated data) (depends on T035) —
      **deviation**: the top bar's menu/search icons are not rendered (same reasoning as
      `PlayerBar`'s omitted audio-settings icon in T015 — neither has a destination yet, no drawer,
      Search is Phase 6); the bar is the wordmark alone for now, documented in `HomeContent`'s KDoc.
- [X] T038 [US4] Re-skin `HomeScreen.kt`'s Continue Learning card region and grid layout with
      Phase 2 tokens, using `MatnCard` from T036 (depends on T036, T037) — restructured as a
      single scrolling `LazyVerticalGrid` (goal ring + Continue Learning card as full-span items
      above the matn cards) rather than a fixed header above a separately-scrolling grid, matching
      the Stitch design's single continuous scroll.
- [X] T039 [P] [US4] Re-skin `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/common/ContinueLearningCard.kt`
      with Phase 2 tokens (structure unchanged)
- [X] T040 [P] [US4] Re-skin `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/common/CoverImage.kt`
      with Phase 2 tokens (removed the raw `44.sp` literal — now `MaterialTheme.typography.displayMedium.fontSize`,
      the closest token-routed size to the original value)
- [X] T041 [US4] Re-skin `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/details/MatnDetailsScreen.kt`'s
      frontispiece header and verse-row rosette markers with Phase 2 tokens, structure unchanged
      aside from the US2 long-press-menu removal already done in T026 — the
      `(fontSize.value * 1.7f).sp` line-height literal was deliberately **kept**: it's a ratio
      applied to the already token-driven reading-font-size, not an independent magic value (see
      inline comment added at the call site).
- [X] T042 [P] [US4] Re-skin `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/details/TableOfContents.kt`
      with Phase 2 tokens
- [X] T043 [P] [US4] Re-skin `shared/src/commonMain/kotlin/com/giraffe/matn/presentation/common/PlaybackGlyphs.kt`
      with Phase 2 tokens — verified as a **no-op**: every glyph already took its color as a
      parameter and used fractions of its own `size` for internal geometry, so there were no
      hard-coded colors or magic dp literals to begin with.
- [X] T044 [US4] Update/add `@Preview`s for every composable touched in T037–T043 (loaded / empty /
      placeholder states per Principle II)
- [X] T045 [US4] Verify `MatnDetailsViewModelTest` still passes unchanged in assertions (depends on
      T041) — confirmed via `:shared:testAndroidHostTest`: 12/12 passing; `HomeViewModelTest` 5/5
      and `HomeLoadIndependenceTest` 1/1 also re-checked (Home restructuring touches that screen).
- [X] T046 [US4] Manual check per quickstart.md §B1/B9 (depends on T038, T041) — done on the same
      Pixel 6 (AVD), tap coordinates read from `adb shell uiautomator dump` bounds throughout (see
      T034's note). **Confirmed on-device**: Home's top bar (wordmark), daily-goal ring (empty
      track + "Today's Goal" / "Coming soon" — no fabricated percentage), re-skinned Continue
      Learning card, and matn grid all render correctly; opening the structured matn ("متن
      الآجرومية مبوب") shows the re-skinned frontispiece, gold-rule divider, and a working Table of
      Contents (chapter rows with trailing marker dots, RTL-correct) — confirms both the SIMPLE and
      STRUCTURED matn paths.

**Checkpoint**: All four user stories complete — Phases 1–5's UI fully matches the canonical
design set.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Whole-phase quality gates that span every user story.

- [X] T047 Run the token-literal guard from quickstart.md §A3 across every file touched in this
      phase; fix any remaining `Color(0x`/bare `.dp`/`.sp` matches — zero `Color(0x` matches
      outside `theme/Color.kt`; remaining unconverted `.dp` literals are icon sizes, hairline
      border/stroke widths, and small fixed component dimensions (documented as exempt in
      `contracts/design-tokens-contract.md` § 4) — every genuine layout-spacing literal found was
      routed through `MatnSpacing`/`MatnShapes`, including three left over from earlier tasks
      (`MatnCard.kt`, `MatnDetailsScreen.kt`'s error-state padding, three preview-only paddings).
- [X] T048 Run `./gradlew :shared:testAndroidHostTest` and confirm 100% of pre-existing assertions
      pass unchanged (SC-005) — **176 tests across every suite in the module, 0 failures, 0
      errors**, not just the five named suites.
- [X] T049 Run the full manual checklist in quickstart.md §B (B1–B9) end to end on-device — done
      incrementally per user story (T019, T029, T034, T046) rather than as one final pass; see
      those entries for exactly what was/wasn't confirmed live. Net result: B1, B2, B3, B7, B8, B9
      confirmed; B4–B6 (repetition sheet interaction) and the both-neighbors carousel visual and a
      real gapless audio transition were not caught live, blocked throughout by this emulator's
      corrupt/unplayable seed-audio files (a pre-existing environment issue, not a Phase 10 defect)
      — compensated by full-sheet `@Preview`s and unit tests as detailed in T029.
- [X] T050 Update `docs/DESIGN-SOURCE.md`'s "Open issues" list to check off items resolved by this
      phase (the bottom-nav documentation gap, item 6) and note this phase's completion
- [X] T051 [P] Bump `.specify/memory/constitution.md` to reference Phase 10 in the "Phased,
      Incremental Delivery" subsection (currently lists only Phases 1–9), as a PATCH version bump
      with no semantic principle change — 1.4.1 → 1.4.2, Sync Impact Report + history updated,
      Deferred TODOs pruned (duplicate-screens item resolved in this phase).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup (font files) for T005 — BLOCKS all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational only. No dependency on US2–US4.
- **User Story 2 (Phase 4)**: Depends on Foundational only. Independent of US1, but in practice
  lands after it since both touch `PlayerBar.kt`/`MatnDetailsScreen.kt` — sequencing avoids merge
  conflicts, not a real coupling.
- **User Story 3 (Phase 5)**: Depends on Foundational only — fully independent of US1/US2.
- **User Story 4 (Phase 6)**: Depends on Foundational only — fully independent of US1/US2/US3,
  except T041 sequences after T026 (US2) purely to avoid two stories editing
  `MatnDetailsScreen.kt`'s same region at once.
- **Polish (Phase 7)**: Depends on all four user stories being complete.

### Parallel Opportunities

- T001–T003 (Setup) run together.
- T006/T007 (Shape.kt/Spacing.kt) run together; both block T008.
- Once Phase 2 is done, US1 (Phase 3), US3 (Phase 5) can start immediately in parallel; US2
  (Phase 4) and US4 (Phase 6) can also start in parallel with those but see the file-conflict note
  above if staffed by different people simultaneously.
- Within US4: T035/T036 in parallel; T039/T040/T042/T043 in parallel (distinct files).

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) + Phase 2 (Foundational).
2. Complete Phase 3 (US1 — the carousel reading experience).
3. **STOP and VALIDATE**: quickstart.md §B2/B3/B9, confirm no regression via T018.
4. This alone is demoable — the highest-value, highest-risk part of the redesign.

### Incremental Delivery

1. Setup + Foundational → token layer ready.
2. US1 → reading/playback carousel → validate → demo.
3. US2 → consolidated repetition sheet → validate → demo.
4. US3 → bottom nav shell → validate → demo.
5. US4 → Home/Details re-skin → validate → demo.
6. Polish → full-phase quality gates → merge.
