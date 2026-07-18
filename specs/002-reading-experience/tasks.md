---
description: "Task list for Phase 1 — Reading Experience (static)"
---

# Tasks: Phase 1 — Reading Experience (static)

**Input**: Design documents from `/specs/002-reading-experience/`
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Included. The constitution's Principle V (Test-First & Testable Design,
NON-NEGOTIABLE) requires `commonTest` unit tests for all changed domain/data/ViewModel logic in
the same change. UI-only Compose visuals are validated manually per [quickstart.md](./quickstart.md).

**Organization**: Grouped by user story (US1–US4) so each is independently implementable and
testable. Priority order from spec.md: US1 (P1, MVP) → US2 (P2) → US3 (P3) → US4 (P4).

## Conventions for the implementer (read first)

- **Base package / root**: all Kotlin below lives under
  `shared/src/commonMain/kotlin/com/giraffe/matn/` unless a path says otherwise.
- **Reuse Phase 0, do not redefine it**: `Matn`, `Chapter`, `Verse`, `StructureKind`,
  `Resource`/`AppError`, `MatnRepository`, `VerseRepository`, `ContentDatabase`, and
  `contentModule()` already exist. Only extend them where a task says so.
- **Exact types**: `Resource<T>` = `com.giraffe.matn.core.Resource` (`Success(data)` /
  `Failure(error)`); `AppError.NotFound` and `AppError.Storage(msg)` exist.
- **DI retrieval**: Koin provides use cases/repositories. In Composables get a use case with
  `org.koin.core.context.GlobalContext.get().get<TheUseCase>()`, then build the ViewModel with
  `androidx.lifecycle.viewmodel.compose.viewModel { TheViewModel(...) }` (that artifact is
  already on the classpath). Do **not** add `koin-compose`.
- **ViewModel base**: `BaseViewModel` extends `androidx.lifecycle.ViewModel` and uses
  `viewModelScope`; screens read only its `StateFlow`.
- **RTL & font**: never use `left`/`right` — only `start`/`end`, `Arrangement`, `TextAlign`.
  Verse text always uses the Amiri `FontFamily`. Pass `Verse.arabicText` through **verbatim**
  (no trim/normalize).
- Contracts to follow: [contracts/use-cases.md](./contracts/use-cases.md) and
  [contracts/ui-contract.md](./contracts/ui-contract.md). Data shapes:
  [data-model.md](./data-model.md).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add the new dependencies and asset/resource scaffolding used by every story.

- [ ] T001 Add navigation dependency: in `gradle/libs.versions.toml` add version
  `navigationCompose = "2.9.2"` — this is the `org.jetbrains.androidx.navigation` version
  bundled with Compose Multiplatform 1.11.0 (verified against the CMP 1.11.0 release notes; it
  also pairs with the repo's existing `androidx-lifecycle = "2.11.0-beta01"`). Add library
  `navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "navigationCompose" }`. Then in `shared/build.gradle.kts` under
  `commonMain.dependencies` add `implementation(libs.navigation.compose)`. Run
  `./gradlew :shared:compileKotlinMetadata` (or a Gradle sync) to confirm it resolves; if the
  build ever moves off CMP 1.11.x, re-check the matching navigation version against that CMP
  release's notes before changing this pin.
- [ ] T002 [P] Add the bundled Arabic reading font: place `Amiri-Regular.ttf` and
  `Amiri-Bold.ttf` (SIL OFL, from the Amiri project) into
  `shared/src/commonMain/composeResources/font/`. Include the OFL license text alongside as
  `shared/src/commonMain/composeResources/font/Amiri-OFL.txt`.
- [ ] T003 [P] Create interface-chrome string resources (FR-021). Create
  `shared/src/commonMain/composeResources/values/strings.xml` with **Arabic** values (the base
  = fallback) and `shared/src/commonMain/composeResources/values-en/strings.xml` with the
  matching **English** values. Include at least these keys: `app_title`, `library_empty`,
  `toc_header`, `back`, `verses_count` (e.g. "%d verses"/"%d بيت"), `font_size`, `font_small`,
  `font_medium`, `font_large`, `font_xlarge`, `cover_placeholder_desc`. Both files must contain
  the same key set. **Plurals (finding A1)**: use a single simple form for `verses_count`
  (e.g. "%d بيت" / "%d verses") in Phase 1 — Arabic dual/plural grammatical forms are
  intentionally out of scope here and can be revisited in a later localization pass.

**Checkpoint**: Project builds with navigation available, the Amiri font, and both string files.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared base types, theme, navigation shell, and common composables that ALL
stories depend on.

**⚠️ CRITICAL**: No user story work may begin until this phase is complete.

- [ ] T004 [P] Create the use-case base contracts in `core/usecase/UseCase.kt`:
  `interface UseCase<in P, out R> { suspend operator fun invoke(params: P): Resource<R> }` and
  `interface FlowUseCase<in P, out R> { operator fun invoke(params: P): kotlinx.coroutines.flow.Flow<R> }`.
  (See [contracts/use-cases.md](./contracts/use-cases.md).)
- [ ] T005 [P] Create `domain/model/ReadingFontSize.kt`:
  `enum class ReadingFontSize { SMALL, MEDIUM, LARGE, XLARGE; companion object { val DEFAULT = MEDIUM } }`.
- [ ] T006 [P] Create the derived-read domain models: `domain/model/MatnSummary.kt`
  (`data class MatnSummary(val matn: Matn, val verseCount: Int, val totalDurationMs: Long)`) and
  `domain/model/MatnDetails.kt`
  (`data class MatnDetails(val matn: Matn, val chapters: List<Chapter>, val showTableOfContents: Boolean)`).
- [ ] T007 Create `presentation/base/BaseViewModel.kt`:
  `abstract class BaseViewModel<S>(initial: S) : androidx.lifecycle.ViewModel()` holding a
  `private val _state = MutableStateFlow(initial)`, exposing `val state: StateFlow<S> = _state.asStateFlow()`,
  a `protected fun setState(reduce: (S) -> S)`, and using `viewModelScope` for collection.
  No Compose/Context/platform types (Principle II).
- [ ] T008 Create the theme in `presentation/theme/`: `Type.kt` builds the Amiri
  `FontFamily` from the Compose font resources and a `verseFontFamily`; `FontScale.kt` maps
  `ReadingFontSize` → verse `TextUnit` (SMALL=18.sp, MEDIUM=22.sp, LARGE=26.sp, XLARGE=30.sp —
  these are the concrete defaults; keep them and confirm no clipping/overlap at SMALL and
  XLARGE during T037/T043, SC-007); `MatnTheme.kt` wraps `MaterialTheme` (Material 3, light) and
  forces RTL via
  `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }`
  (FR-010).
  - **Font resource id check (resolves finding A2)**: Compose codegen turns the T002 filenames
    into `Res.font.*` identifiers by replacing non-alphanumerics with `_`. After adding the
    fonts, build once and use the exact generated names (e.g. `Res.font.Amiri_Regular`,
    `Res.font.Amiri_Bold`); if codegen produces different identifiers, match the generated names
    rather than assuming these.
- [ ] T009 [P] Create `presentation/common/DurationFormatter.kt`: pure function
  `fun formatDuration(ms: Long): String` → `m:ss` (and `h:mm:ss` when ≥ 1h). No platform APIs.
- [ ] T010 [P] Create `presentation/common/CoverImage.kt`: a composable
  `CoverImage(coverImageRef: String?, modifier: Modifier)` that renders the bundled cover when
  the ref is non-null and a shared placeholder box (icon + `cover_placeholder_desc`) when it is
  null/blank (missing-cover edge case, SC-008). No network loading (FR-018).
- [ ] T011 Create the navigation shell `presentation/navigation/MatnNavHost.kt`: a `NavHost`
  with `startDestination = "home"`; route `composable("home") { HomePlaceholder() }` and
  `composable("matn/{matnId}") { backStackEntry -> MatnDetailsPlaceholder(backStackEntry.arguments?.getString("matnId").orEmpty()) }`.
  Add tiny private `HomePlaceholder`/`MatnDetailsPlaceholder` composables (each just a centered
  `Text`) in this file for now; US1/US2 replace them. Include a `navController` and expose a
  navigation lambda so cards can call `navController.navigate("matn/$id")`.
- [ ] T012 Replace the body of `App.kt` so it renders `MatnTheme { MatnNavHost() }` (remove the
  Phase 0 sample "Click me!" content). Keep the `@Composable fun App()` signature so
  `MainActivity` and `MainViewController` still call it unchanged.

**Checkpoint**: App launches on Android and iOS showing the placeholder Home, RTL, no crash.

---

## Phase 3: User Story 1 — Read a matn from start to finish (Priority: P1) 🎯 MVP

**Goal**: Open a matn and read its header + full verse list in correct matn-global order, RTL,
Amiri typeface, diacritics intact, fully offline.

**Independent Test**: With a seeded matn, open its details screen and verify header
(title/author/description/count/duration) and every verse in `displayNumber` order with Arabic +
all diacritics, RTL, no network. (For manual reachability before US2 exists, temporarily set
`MatnNavHost` `startDestination = "matn/<seededMatnId>"`, then revert in T027.)

### Implementation for User Story 1

- [ ] T013 [P] [US1] Create `domain/usecase/ObserveVersesUseCase.kt`:
  `class ObserveVersesUseCase(private val repo: VerseRepository) : FlowUseCase<String, List<Verse>> { override fun invoke(params: String) = repo.observeVerses(params) }`.
- [ ] T014 [P] [US1] Create `domain/usecase/GetMatnDetailsUseCase.kt`:
  `class GetMatnDetailsUseCase(private val matnRepo: MatnRepository) : UseCase<String, MatnDetails>`.
  It calls `matnRepo.getMatn(id)`; if `Success(null)` → `Resource.Failure(AppError.NotFound)`;
  else calls `matnRepo.getChapters(id)`, builds `MatnDetails` with
  `showTableOfContents = (matn.structureKind == StructureKind.STRUCTURED && chapters.isNotEmpty())`
  (FR-013/FR-015). Propagate any `Resource.Failure`.
- [ ] T015 [US1] Create `presentation/details/MatnDetailsUiState.kt` with `MatnDetailsUiState`,
  `MatnHeader`, `VerseRow`, `ChapterRow` exactly as in [data-model.md](./data-model.md) §4.2
  (include `fontSize: ReadingFontSize = ReadingFontSize.MEDIUM` and
  `showTableOfContents: Boolean = false`).
- [ ] T016 [US1] Create `presentation/details/MatnDetailsViewModel.kt` extending
  `BaseViewModel<MatnDetailsUiState>`. Constructor takes `matnId`, `GetMatnDetailsUseCase`,
  `ObserveVersesUseCase`. On init: call details use case → set `header` and
  `showTableOfContents`; collect verses into `verses` mapped to `VerseRow` (pass `arabicText`
  verbatim). Map `AppError.NotFound`/`Storage` into `state.error`. Leave `fontSize` at `MEDIUM`
  (US4 wires the real preference).
  - **Deliberate derivation split (resolves analysis finding I1)**: on the details screen the
    full verse list is already streamed here, so derive the header totals **in this ViewModel**
    from that list — `verseCount = verses.size`, `totalDurationMs = verses.sumOf { it.durationMs }`
    — rather than issuing the SQL aggregate used for the library grid (T020/T021). This is the
    sanctioned choice from research.md Decision 6 (avoid a second query for data already in
    memory); it is *not* accidental drift from data-model §3.1. The library grid, which must not
    load every verse, keeps using the SQL aggregate. Both paths sum the same
    `Verse.durationMs`/count, so they agree.
  - **Sequencing note (resolves finding S1)**: `showTableOfContents` is set from
    `MatnDetails` here, but the TOC composable and the `chapters` list are populated in **US3**
    (T028–T030). If US1 ships alone as the MVP, a structured matn simply renders no TOC panel
    (nothing is shown until US3) — this is harmless and intended, not a bug.
- [ ] T017 [US1] Create `presentation/details/MatnDetailsScreen.kt`: a `Column` with the header
  (via `CoverImage` + title/author/description + `verseCount`/`formatDuration(totalDurationMs)`)
  then a `LazyColumn` of verses **keyed by `verse.id`** (FR-011). Each row shows the display
  number, the Arabic text (Amiri `verseFontFamily`, size from `FontScale(state.fontSize)`,
  `TextAlign.Start`, wraps fully — FR-008/FR-012), and `formatDuration(durationMs)`. Screen
  reads `viewModel.state` via `collectAsStateWithLifecycle()`. Show a loading indicator while
  `isLoading`, and an error message on `error`.
- [ ] T018 [US1] Wire it up: in `di/ContentModule.kt` add
  `factory { ObserveVersesUseCase(get()) }` and `factory { GetMatnDetailsUseCase(get()) }`. In
  `MatnNavHost.kt` replace `MatnDetailsPlaceholder` with the real screen, creating the ViewModel
  via `viewModel { MatnDetailsViewModel(matnId, GlobalContext.get().get(), GlobalContext.get().get()) }`.

### Tests for User Story 1

- [ ] T019 [P] [US1] Create `shared/src/commonTest/kotlin/com/giraffe/matn/presentation/MatnDetailsViewModelTest.kt`
  using fake `MatnRepository`/`VerseRepository` (reuse Phase 0 `ContentFixtures` where useful).
  Assert: verses appear in ascending `displayNumber` order (FR-006); each `VerseRow.arabicText`
  equals the stored string byte-for-byte incl. diacritics (FR-008/SC-002); header
  `verseCount`/`totalDurationMs` correct (FR-005); opening a missing matn yields
  `state.error == AppError.NotFound`. Use `runTest` + `kotlinx-coroutines-test`.

**Checkpoint**: US1 fully functional — a matn opens and reads correctly, RTL, offline. MVP done.

---

## Phase 4: User Story 2 — Browse the library and choose a matn (Priority: P2)

**Goal**: Home grid of matn cards (cover/title/author/verse count/duration); tap opens the
reading screen; empty store shows an empty state; RTL.

**Independent Test**: With ≥1 seeded matn, open Home and see one card per matn with all fields;
tap a card → its reading screen opens. With an empty store → empty state, not a blank/crash.

### Implementation for User Story 2

- [ ] T020 [US2] Add derived-total reads to
  `shared/src/commonMain/sqldelight/com/giraffe/matn/db/Content.sq`: a grouped query
  `selectLibrarySummaries` returning each matn row plus `COUNT(verse.id)` and
  `SUM(verse.duration_ms)` via `LEFT JOIN verse ... GROUP BY matn.id ORDER BY matn.title`
  (LEFT JOIN so a matn with zero verses still appears with count 0). No schema change.
- [ ] T021 [US2] Extend the library read: add
  `fun observeLibrarySummaries(): Flow<List<MatnSummary>>` to `MatnRepository` (interface) and
  implement it in `MatnRepositoryImpl` using `selectLibrarySummaries().asFlow().mapToList(...)`,
  mapping each row → `MatnSummary` (reuse/extend `data/mapper/ContentMappers.kt`; treat a null
  `SUM` as 0). Follow the existing `observeLibrary()` style.
- [ ] T022 [P] [US2] Create `domain/usecase/ObserveLibraryUseCase.kt`:
  `class ObserveLibraryUseCase(private val repo: MatnRepository) : FlowUseCase<Unit, List<MatnSummary>> { override fun invoke(params: Unit) = repo.observeLibrarySummaries() }`.
- [ ] T023 [US2] Create `presentation/home/HomeUiState.kt` per [data-model.md](./data-model.md)
  §4.1 (`isLoading`, `items: List<MatnSummary>`, `isEmpty`, `error`).
- [ ] T024 [US2] Create `presentation/home/HomeViewModel.kt` extending
  `BaseViewModel<HomeUiState>` with `ObserveLibraryUseCase`. On init: collect the flow → set
  `items`; when the emitted list is empty set `isEmpty = true`, `isLoading = false` (FR-004).
- [ ] T025 [US2] Create `presentation/home/HomeScreen.kt`: `LazyVerticalGrid` (2 columns) of
  cards **keyed by `matn.id`**, each card = `CoverImage` + title + author + `verses_count` +
  `formatDuration(totalDurationMs)`. Card `onClick` invokes an `onOpenMatn(id)` lambda. When
  `state.isEmpty`, render a centered localized empty state (`library_empty`) instead of the grid
  (SC-008). RTL throughout.
- [ ] T026 [US2] Wire it: in `di/ContentModule.kt` add `factory { ObserveLibraryUseCase(get()) }`.
  In `MatnNavHost.kt` replace `HomePlaceholder` with the real `HomeScreen`, creating the
  ViewModel via `viewModel { HomeViewModel(GlobalContext.get().get()) }` and passing
  `onOpenMatn = { id -> navController.navigate("matn/$id") }`. Ensure `startDestination = "home"`
  (revert any US1 temporary change).

### Tests for User Story 2

- [ ] T027 [P] [US2] Create `.../commonTest/.../presentation/HomeViewModelTest.kt` with a fake
  `ObserveLibraryUseCase`/repo: populated store → `items` has one `MatnSummary` per matn with
  correct `verseCount`/`totalDurationMs` (FR-002); empty store → `isEmpty == true`, no error
  (FR-004). Add a data-layer test that `selectLibrarySummaries` returns correct COUNT/SUM
  against the in-memory driver (reuse Phase 0 `TestDatabase`).

**Checkpoint**: US1 + US2 work — browse the library and open any matn to read it.

---

## Phase 5: User Story 3 — Navigate a structured matn by its table of contents (Priority: P3)

**Goal**: Structured matn shows a TOC of chapters in order; selecting a chapter scrolls to its
first verse. Simple matn shows no TOC.

**Independent Test**: Open a structured matn → chapters listed in order; select one → view moves
to that chapter's first verse. Open a simple matn → no TOC, one continuous list.

### Implementation for User Story 3

- [ ] T028 [US3] In `MatnDetailsViewModel` build the `chapters: List<ChapterRow>` for the state:
  for each `Chapter` (already loaded via `GetMatnDetailsUseCase`), compute
  `firstVerseDisplayNumber` = the minimum `displayNumber` among the observed verses whose
  `chapterId == chapter.id` (FR-014); order by `chapter.order`. Set `showTableOfContents` from
  `MatnDetails.showTableOfContents`. (Recompute when verses arrive.)
- [ ] T029 [US3] Create `presentation/details/TableOfContents.kt`: a composable listing
  `ChapterRow`s (localized `toc_header` title) with an `onChapterSelected(chapterRow)` lambda,
  RTL. In `MatnDetailsScreen`, render it **only when `state.showTableOfContents`** (FR-015);
  simple matn shows nothing here (not an empty control).
- [ ] T030 [US3] Implement scroll-to in `MatnDetailsScreen`: hold a `rememberLazyListState()`;
  on `onChapterSelected`, launch `listState.animateScrollToItem(index)` where `index` is the
  position of the first verse whose `displayNumber == chapterRow.firstVerseDisplayNumber` (mind
  any header items offset). SC-004: brings the chapter's first verse into view.

### Tests for User Story 3

- [ ] T031 [P] [US3] Extend `MatnDetailsViewModelTest`: structured fixture →
  `showTableOfContents == true`, `chapters` ordered by `order`, each `firstVerseDisplayNumber`
  equals the min `displayNumber` of that chapter's verses; simple fixture →
  `showTableOfContents == false` and empty `chapters`.

**Checkpoint**: Structured matn navigable by TOC; simple matn reads as one list.

---

## Phase 6: User Story 4 — Adjust Arabic reading font size (Priority: P4)

**Goal**: A control changes the Arabic verse size across Small/Medium/Large/X-Large live; the
choice is a single global preference persisted across sessions.

**Independent Test**: On a reading screen, set size to largest then smallest → text resizes live,
legible, no clipping; reopen the app/matn → chosen size retained.

### Implementation for User Story 4

- [ ] T032 [US4] Add the settings store to `Content.sq`: `CREATE TABLE app_setting (key TEXT NOT
  NULL PRIMARY KEY, value TEXT NOT NULL);` plus `selectSetting: SELECT value FROM app_setting
  WHERE key = ?;` and `upsertSetting: INSERT INTO app_setting(key,value) VALUES (?,?) ON
  CONFLICT(key) DO UPDATE SET value = excluded.value;`. (Additive; content tables unchanged.)
- [ ] T033 [US4] Create `domain/repository/ReadingPreferencesRepository.kt`:
  `fun observeFontSize(): Flow<ReadingFontSize>` and
  `suspend fun setFontSize(size: ReadingFontSize): Resource<Unit>`.
- [ ] T034 [US4] Create `data/repository/ReadingPreferencesRepositoryImpl.kt` over
  `ContentDatabase`, key `"reading_font_size"`. `observeFontSize` = `selectSetting` as a Flow,
  mapping the stored name → `ReadingFontSize` and **falling back to `ReadingFontSize.DEFAULT`**
  when absent or unrecognized (never throw). `setFontSize` = `upsertSetting(key, size.name)`
  wrapped in the existing `storageCall {}` helper → `Resource<Unit>` (persist immediately,
  Principle VI).
- [ ] T035 [P] [US4] Create `domain/usecase/GetFontSizeUseCase.kt`
  (`FlowUseCase<Unit, ReadingFontSize>` → `repo.observeFontSize()`) and
  `domain/usecase/SetFontSizeUseCase.kt` (`UseCase<ReadingFontSize, Unit>` → `repo.setFontSize`).
- [ ] T036 [US4] Extend `MatnDetailsViewModel`: take `GetFontSizeUseCase`/`SetFontSizeUseCase`;
  collect `GetFontSizeUseCase()` into `state.fontSize` (live, FR-016); add an
  `onFontSizeChanged(size)` function calling `SetFontSizeUseCase(size)` (persist, FR-017). Verse
  text already reads `state.fontSize`, so it updates automatically.
- [ ] T037 [US4] Add the font-size control to `MatnDetailsScreen`: e.g. a top-bar action opening
  a small chooser of the four steps (localized `font_small`/`font_medium`/`font_large`/
  `font_xlarge`), calling `viewModel.onFontSizeChanged(...)`. Ensure no clipping/overlap at
  SMALL and XLARGE (SC-007).
- [ ] T038 [US4] Wire DI: in `di/ContentModule.kt` add
  `single<ReadingPreferencesRepository> { ReadingPreferencesRepositoryImpl(get()) }`,
  `factory { GetFontSizeUseCase(get()) }`, `factory { SetFontSizeUseCase(get()) }`, and update
  the `MatnDetailsViewModel` construction in `MatnNavHost.kt` to inject the two new use cases.

### Tests for User Story 4

- [ ] T039 [P] [US4] Create `.../commonTest/.../data/ReadingPreferencesRepositoryTest.kt` (or
  extend a VM test) against the in-memory driver: default is `MEDIUM` when unset;
  `setFontSize(LARGE)` then `observeFontSize()` emits `LARGE`; a fresh repository over the same
  DB still reads `LARGE` (persistence, SC-007); an unrecognized stored value falls back to
  `MEDIUM`.

**Checkpoint**: All four user stories independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T040 [P] Localization sweep: confirm every user-facing string comes from the string
  resources (no hardcoded chrome text in any Composable), and that `values/strings.xml` (Arabic)
  and `values-en/strings.xml` (English) have identical key sets (FR-021).
- [ ] T041 [P] Performance check: confirm the verse `LazyColumn` and library `LazyVerticalGrid`
  are keyed by stable `id`; run a ~500-verse fixture through `MatnDetailsViewModelTest` and
  confirm mapping stays O(n) (SC-003). No eager `Column` of all verses.
- [ ] T042 Scope guard: verify no FR-020 out-of-scope element leaked in (no play/pause,
  highlight/auto-scroll, progress bar, search, bookmarks, notes, dark-mode toggle, tablet layout)
  and that screens don't crash on rotation. Confirm `./gradlew :shared:allTests` passes and the
  app builds for Android and iOS.
- [ ] T043 Run the full [quickstart.md](./quickstart.md) validation: Section A (`commonTest`
  matrix green) and Section B (manual RTL / Amiri / TOC / font-size / offline / locale
  walkthrough on Android and iOS). **Zero-network is a deliberate manual gate (finding C1)**:
  FR-018/SC-006 are asserted here by the airplane-mode walkthrough (Section B step 5), not by an
  automated test — network access can't be meaningfully asserted headlessly, and by design the
  app declares no networking APIs and bundles no image/network loader (see T010), so there is no
  network surface to exercise. Treat this manual step as the binding check.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: no dependencies — start immediately.
- **Foundational (Phase 2)**: depends on Setup — **blocks all user stories**.
- **User stories (Phases 3–6)**: all depend on Foundational. After that they may proceed in
  priority order (P1→P2→P3→P4) or in parallel by different people.
- **Polish (Phase 7)**: after the desired stories are complete.

### Story dependencies & independence

- **US1 (P1)**: needs only Foundational. Fully standalone (reachable directly for testing).
- **US2 (P2)**: needs Foundational; navigates *into* US1's screen but is testable on its own
  (grid + empty state). Adds the library summary query/repo/use case.
- **US3 (P3)**: builds on US1's details ViewModel/screen (extends them). No new data layer.
- **US4 (P4)**: needs Foundational; extends US1's details ViewModel/screen and adds the
  preference table/repo/use cases. Independently testable via the preference persistence tests.

### Within a story

- Domain models → use cases → ViewModel → screen → DI wiring → tests.
- US3 and US4 both **edit** `MatnDetailsViewModel`/`MatnDetailsScreen`, so run them sequentially
  (not in parallel with each other) to avoid same-file conflicts.

### Parallel opportunities

- Setup: T002, T003 in parallel.
- Foundational: T004, T005, T006 in parallel; then T007/T008/T009/T010; T011→T012 last.
- US1: T013, T014 in parallel; then T015→T016→T017→T018; T019 after.
- US2: T022 parallel with the data tasks; T027 after.
- Test tasks (T019, T027, T031, T039) are [P] relative to other stories' work.

---

## Parallel Example: User Story 1

```text
# After Foundational is done, launch the two US1 use cases together:
Task T013: "Create ObserveVersesUseCase in domain/usecase/ObserveVersesUseCase.kt"
Task T014: "Create GetMatnDetailsUseCase in domain/usecase/GetMatnDetailsUseCase.kt"
# Then sequentially: T015 (state) → T016 (ViewModel) → T017 (screen) → T018 (DI/nav) → T019 (test)
```

---

## Implementation Strategy

### MVP first (US1 only)

1. Phase 1 Setup → 2. Phase 2 Foundational → 3. Phase 3 US1 → **STOP & VALIDATE** (T019 green +
manual read of a seeded matn) → demo the reading surface. This is the smallest slice that proves
the phase.

### Incremental delivery

Setup + Foundational → US1 (MVP: read a matn) → US2 (browse + open) → US3 (TOC) → US4 (font
size). Each story adds value and is independently testable without breaking the previous ones.

---

## Notes

- `[P]` = different files, no dependency on incomplete tasks.
- `[USn]` maps a task to its user story for traceability.
- Every domain/data/ViewModel change lands with its `commonTest` in the same commit (Principle V).
- Never redefine Phase 0 content types; only extend `MatnRepository`, `Content.sq`, and
  `contentModule()` where a task says so.
- Commit after each task or logical group; stop at any checkpoint to validate a story.
