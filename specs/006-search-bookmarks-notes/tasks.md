# Tasks: Search, Bookmarks & Notes (Phase 6)

**Input**: Design documents from `specs/006-search-bookmarks-notes/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — Constitution Principle V (test-first) is NON-NEGOTIABLE: every new
domain/data behavior lands with `commonTest` coverage in the same change. Write each test task
before (or together with) its implementation task; tests must fail (or not compile) first.

**Organization**: Grouped by user story from spec.md — US1 Search (P1), US2 Bookmarks (P2),
US3 Notes (P3). Each story is an independently testable increment.

**How to read a task**: every task lists exact file paths and the contract/design doc that
defines its behavior. When a task says "per X-contract.md § Y", that section is the
authoritative behavior definition — do not invent semantics. All new code goes in
`commonMain`/`commonTest` of the `shared` module; there are NO `androidApp`/`iosApp` changes
in this feature.

**Conventions used below** (defined once so tasks stay short):

- `KOTLIN` = `shared/src/commonMain/kotlin/com/giraffe/matn`
- `TEST` = `shared/src/commonTest/kotlin/com/giraffe/matn`
- `SQL` = `shared/src/commonMain/sqldelight/com/giraffe/matn/db`
- **Stateless/stateful split** (Constitution II): every screen = a stateless `XxxContent(state, on…)`
  composable holding ALL rendering + a thin holder that only collects the ViewModel `StateFlow`
  and forwards intents. Every state-rendering composable gets ≥1 `@Preview` with hand-built
  sample state (no ViewModel/DI/DB in previews). Missing previews are a blocking review failure.
- **Tokens only** (Constitution VIII): use `MaterialTheme.colorScheme`/typography and the
  Phase 10 token objects in `KOTLIN/presentation/theme/` (`Spacing.kt`, `Shape.kt`). Raw hex
  colors or magic `.dp`/`.sp` literals are a blocking review failure.
- **Run tests** with `./gradlew :shared:allTests` (in-memory DB helper: `TEST/db/TestDatabase.kt`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1 / US2 / US3 — user story phases only

---

## Phase 1: Setup

**Purpose**: Branch, baseline, and the Constitution VIII design-fetch gate.

- [X] T001 Create branch `feature/006-search-bookmarks-notes` from `develop` (`git checkout develop && git pull && git checkout -b feature/006-search-bookmarks-notes`). Constitution: `main`/`develop` are protected; all work lands via PR from this branch.
- [X] T002 Verify green baseline: run `./gradlew :shared:allTests` and confirm all existing tests pass before any change. If the baseline is red, STOP and report — do not build on a broken baseline.
- [X] T003 [P] Fetch the *Search Matn* Stitch design (screen ID `fa8b63b036c94510ba1be2d90e7a3417`, project registered in `docs/DESIGN-SOURCE.md`) via the `stitch` MCP server's `get_screen` tool. Record layout notes (top bar, field placement, result-row anatomy, empty states) in `specs/006-search-bookmarks-notes/design-notes.md` (create file). If the design scopes search to a single matn, do NOT follow it — spec FR-001 mandates library-wide search (ui-contract.md Open question 3); note the conflict in design-notes.md.
- [X] T004 [P] Fetch the *Bookmarks & Notes* Stitch design (screen ID `bc99ab7f8110479086326271d0aa0310`) and re-inspect *Reading & Playback (Updated)* (`ac354abd54c246ed841f63b31a19fbf2`) for bookmark/note affordances on the verse card. Append findings to `specs/006-search-bookmarks-notes/design-notes.md`: (a) Notes-tab segmentation idiom (tabs vs sections — ui-contract.md Open question 1); (b) whether the verse card shows bookmark/note controls (Open question 2; if absent, the default is the active-verse card's action row).

**Checkpoint**: branch exists, baseline green, both designs fetched and summarized.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema v2, the shared context model, and verse-focused navigation — everything more than one story needs. **No user story work before this phase completes.**

- [X] T005 Add `bookmark` and `note` tables + queries to `SQL/Content.sq`. Exact DDL per data-model.md tables (UUID TEXT `id` PRIMARY KEY; `verse_id` TEXT NOT NULL UNIQUE REFERENCES verse(id) ON DELETE CASCADE; bookmark: `created_at` INTEGER NOT NULL; note: `text` TEXT NOT NULL + `updated_at` INTEGER NOT NULL). Add named queries: `selectBookmarkByVerse`, `insertBookmark`, `deleteBookmarkByVerse`, `selectAllBookmarksWithContext` (JOIN verse + matn, ORDER BY created_at DESC, id DESC), `selectBookmarkedVerseIdsByMatn`; `selectNoteByVerse`, `upsertNote` (INSERT … ON CONFLICT(verse_id) DO UPDATE SET text=excluded.text, updated_at=excluded.updated_at — keeps original `id`), `deleteNoteByVerse`, `selectAllNotesWithContext` (JOIN verse + matn, ORDER BY updated_at DESC, id DESC), `selectNotedVerseIdsByMatn`. Follow the existing comment style in Content.sq (each block cites its FR).
- [X] T006 **DEVIATION**: the live schema was already at v2 (Phase 4 added `matn_session` via `shared/src/commonMain/sqldelight/com/giraffe/matn/db/1.sqm`, no `migrations/` subfolder — migrations live directly in `db/`). Created `db/2.sqm` (byte-identical `CREATE TABLE bookmark…; CREATE TABLE note…;`) bumping `ContentDatabase.Schema.version` to 3. See `2.sqm`'s header comment for the full explanation.
- [X] T007 **DEVIATION**: implemented as `shared/src/androidHostTest/kotlin/com/giraffe/matn/db/MigrationV2Test.kt` (not `commonTest`) — same reasoning as the existing `MigrationTest.kt` in that folder: only the JVM driver can be handed a hand-built legacy schema without `Schema.create()` overwriting it with the current schema. Tests: seeded content intact after migrate(2,3); bookmark/note tables accept inserts; `UNIQUE(verse_id)` rejects a duplicate; migration on an empty v2 DB leaves annotation tables empty.
- [X] T008 [P] Create `KOTLIN/domain/model/AnnotatedVerseRef.kt`: `data class AnnotatedVerseRef(val matnId: String, val matnTitle: String, val verseId: String, val verseNumber: Int, val verseText: String)`. KDoc: shared verse-context block for search results and global bookmark/note lists (data-model.md § Domain models). Pure Kotlin, no imports outside stdlib.
- [X] T009 Add optional verse focus to the matn route (research.md D5). In `KOTLIN/presentation/navigation/MatnNavHost.kt`: change `Routes.MATN_DETAILS` to `"matn/{matnId}?focusVerseId={focusVerseId}"`, add `Routes.matnDetails(matnId: String, focusVerseId: String? = null)` overload, declare the nav argument nullable-with-default, read it in the `composable(Routes.MATN_DETAILS)` block, and pass it into `MatnDetailsViewModel`. In `KOTLIN/presentation/details/MatnDetailsViewModel.kt`: accept `focusVerseId: String?`, and when non-null and present in the loaded verse list, set the initial carousel focus to that verse WITHOUT starting playback. Existing callers compile unchanged (default null). Extend `TEST/presentation/MatnDetailsViewModelTest.kt` with: focus honored when verse exists; gracefully ignored (default focus) when the id is unknown; playback not started.

**Checkpoint**: `./gradlew :shared:allTests` green (incl. MigrationV2Test); schema v2 in place; deep-linkable matn route ready. User stories can now proceed — even in parallel.

---

## Phase 3: User Story 1 — Find a verse by searching (Priority: P1) 🎯 MVP

**Goal**: Library-wide diacritic-insensitive search over verse text / verse numbers / matn titles / chapter titles, from a dedicated screen opened via the Library top bar; results navigate to the verse (spec US1, FR-001…FR-009).

**Independent Test**: With ≥2 seeded متون (one structured), search a bare-letters fragment of a vocalized verse, a chapter title, a matn title, and `٥`; each result navigates correctly; blank → idle; gibberish → no-results; airplane mode changes nothing (quickstart §B1–B6).

- [X] T010 [P] [US1] Write `TEST/domain/search/ArabicNormalizerTest.kt` FIRST with all 9 vectors from contracts/normalization-contract.md § Required test vectors — including negative vector 6 (`مؤمن` vs `مءمن` must NOT match) and idempotence. Assert via `ArabicNormalizer.normalize(...)` containment exactly as the contract's matching predicate defines.
- [X] T011 [US1] Implement `KOTLIN/domain/search/ArabicNormalizer.kt` as `object ArabicNormalizer { fun normalize(input: String): String }` per the character-rule table in contracts/normalization-contract.md (strip U+064B–U+0655 + U+0670; fold U+0623/U+0625/U+0622/U+0671→U+0627, U+0649→U+064A, U+0629→U+0647; digits U+0660–U+0669→'0'–'9'; drop U+0640; collapse whitespace runs to single space + trim; invariant-lowercase A–Z; pass everything else through). Pure Kotlin, single pass over a `StringBuilder`, no regex needed for the char mappings (regex OK for whitespace collapse). T010 must now pass.
- [X] T012 [P] [US1] Create `KOTLIN/domain/model/SearchResult.kt`: `sealed interface SearchResult` with `data class VerseMatch(val ref: AnnotatedVerseRef)`, `data class ChapterMatch(val matnId: String, val matnTitle: String, val chapterId: String, val chapterTitle: String, val firstVerseId: String?)`, `data class MatnMatch(val matnId: String, val matnTitle: String)` — shapes and navigation meanings per data-model.md § SearchResult.
- [X] T013 [US1] Add two corpus queries to `SQL/Content.sq`: `selectAllChapters: SELECT * FROM chapter ORDER BY matn_id, display_order;` and `selectAllVerses: SELECT * FROM verse ORDER BY matn_id, display_number;` (search corpus reads, research.md D2).
- [X] T014 [US1] Create `KOTLIN/domain/repository/SearchRepository.kt` interface exactly as contracts/search-contract.md § Repository interface: `fun search(query: String): Flow<List<SearchResult>>`, KDoc stating blank-query→empty-list and determinism guarantees.
- [X] T015 [US1] Implement `KOTLIN/data/repository/SearchRepositoryImpl.kt` per contracts/search-contract.md § Semantics: combine SQLDelight reactive flows of matn/chapter/verse (existing `selectAllMatn` + T013 queries, `asFlow().mapToList(...)` like `KOTLIN/data/repository/MatnRepositoryImpl.kt` does), normalize corpus fields with `ArabicNormalizer`, filter by containment of the normalized query; verse-number rule: normalized query (digits unified) contained in `display_number.toString()`. Assemble ordering: matn groups in `selectAllMatn` order; within a group MatnMatch → ChapterMatches (display_order) → VerseMatches (display_number). `ChapterMatch.firstVerseId` = lowest-display_number verse of that chapter, null if none. Blank/whitespace query → `emptyList()`.
- [X] T016 [US1] Write `TEST/data/SearchRepositoryTest.kt` (in-memory DB + seeded fixture with 2 متون, 1 structured with a chapter `باب الطهارة`, vocalized verse text): each corpus field kind matches; FR-006 deterministic ordering (assert exact list order twice); blank → empty; chapter-with-no-verses → `firstVerseId == null`; Arabic-Indic digit query matches verse number; results re-emit on content change (insert a verse mid-observation). Binding checklist: contracts/search-contract.md § Tests.
- [X] T017 [P] [US1] Write `TEST/data/SearchPerformanceTest.kt` (SC-001 guard): seed ≥1,000 verses across ≥3 متون (fixture-generation pattern: `TEST/data/PerformanceTest.kt`), run one 2-word query, assert completion < 1s using `kotlin.time.measureTime`. Flake guard: if this proves unstable on slow CI hosts, keep the test but relax the assertion to a documented constant (e.g. 3 s) with a comment noting SC-001's real 1 s budget is verified by the seeded-device manual check (quickstart §B2) — do not delete the test.
- [X] T018 [US1] Create `KOTLIN/domain/usecase/SearchLibraryUseCase.kt`: `class SearchLibraryUseCase(private val repo: SearchRepository) : FlowUseCase<String, List<SearchResult>> { override fun invoke(params: String) = repo.search(params) }` (contract in `KOTLIN/core/usecase/UseCase.kt`).
- [X] T019 [P] [US1] Create `KOTLIN/presentation/search/SearchUiState.kt`: `data class SearchUiState(val query: String = "", val phase: SearchPhase = SearchPhase.Idle)` + `sealed interface SearchPhase { data object Idle; data object Searching; data class Results(val results: List<SearchResult>); data object NoResults }` — the 4-state machine of contracts/search-contract.md § ViewModel state machine (Idle keyed on blank RAW query, not empty results).
- [X] T020 [US1] Create `KOTLIN/presentation/search/SearchViewModel.kt` extending `KOTLIN/presentation/base/BaseViewModel.kt` conventions: exposes `StateFlow<SearchUiState>`; `onQueryChange(q)` updates `query` immediately, debounces ~250 ms, then `flatMapLatest`-collects `SearchLibraryUseCase`; blank raw query → `Idle` (skip search); non-empty results → `Results`; empty → `NoResults`; `onClearQuery()` → `Idle`. No Compose/platform imports (Constitution II).
- [X] T021 [US1] Write `TEST/presentation/SearchViewModelTest.kt` with `kotlinx-coroutines-test` (`StandardTestDispatcher` + virtual time to cross the debounce): Idle→Searching→Results happy path; NoResults; rapid keystrokes collapse to one search (count fake-repo invocations); clearing returns Idle; stale emission from a superseded query never lands (FR-008/FR-009, research.md D8).
- [X] T022 [US1] Create shared row component in `KOTLIN/presentation/search/SearchResultRow.kt`: ONE stateless parameterized composable family rendering the three `SearchResult` kinds (verse rows show matn title + verse number + verse text; chapter rows chapter+matn title; matn rows title) styled per the T003 design notes with theme tokens only. Include `@Preview`s: verse row (RTL vocalized Arabic), chapter row, matn row.
- [X] T023 [US1] Create `KOTLIN/presentation/search/SearchScreen.kt`: stateless `SearchScreenContent(state, onQueryChange, onClearQuery, onResultClick, onBack)` + thin `SearchScreen(viewModel, onResultClick, onBack)` holder. Layout per T003 design notes: top search field (autofocus), lazy result list of `SearchResultRow`, distinct Idle prompt vs NoResults empty state (FR-008/FR-009). `@Preview`s: Idle, Results (mixed kinds, RTL), NoResults. Tokens only.
- [X] T024 [US1] Register the route: in `KOTLIN/presentation/navigation/MatnNavHost.kt` add `Routes.SEARCH = "search"`, `composable(Routes.SEARCH)` building `SearchViewModel` via `MatnKoinHolder.koin` (same pattern as existing destinations); `onResultClick` maps per contracts/search-contract.md § 6: `VerseMatch` → `navController.navigate(Routes.matnDetails(ref.matnId, ref.verseId))`; `ChapterMatch` → `matnDetails(matnId, firstVerseId)` falling back to `matnDetails(matnId)` when `firstVerseId == null`; `MatnMatch` → `matnDetails(matnId)`. Wire `onBack = { navController.popBackStack() }`. `search` is NOT in `NavigationTab` — no bottom bar (the `showBottomBar` check already handles this).
- [X] T025 [US1] Add the search entry point to `KOTLIN/presentation/home/HomeScreen.kt` top bar per the Home/Search designs (icon button emitting a new `onOpenSearch: () -> Unit` intent on the stateless content), and wire `onOpenSearch = { navController.navigate(Routes.SEARCH) }` in `MatnNavHost.kt`'s HOME destination. Update HomeScreen previews if the content signature changed.
- [X] T026 [US1] Wire DI in `KOTLIN/di/ContentModule.kt`: `single<SearchRepository> { SearchRepositoryImpl(get()) }`, `factory { SearchLibraryUseCase(get()) }`. Run `./gradlew :shared:allTests` — everything green.

**Checkpoint**: US1 fully functional — quickstart §B1–B6 pass on device. This is the MVP.

---

## Phase 4: User Story 2 — Bookmark verses for quick return (Priority: P2)

**Goal**: One-tap bookmark toggle on the reading screen's active verse with visible indicators, plus a real Notes tab (replacing "coming soon") listing all bookmarks with navigation (spec US2, FR-010…FR-014, FR-020 bookmarks half).

**Independent Test**: Bookmark verses in two متون, restart the app, open Notes tab, tap an entry → its verse; toggle off removes it; fresh install shows purposeful empty state (quickstart §B7, B10, B11).

- [X] T027 [P] [US2] Create `KOTLIN/domain/model/Bookmark.kt`: `data class Bookmark(val id: String, val verseId: String, val createdAtMs: Long)` and `data class BookmarkEntry(val bookmark: Bookmark, val ref: AnnotatedVerseRef)` (data-model.md § Domain models; keep both in Bookmark.kt).
- [X] T028 [US2] Create `KOTLIN/domain/repository/BookmarkRepository.kt` interface exactly per contracts/annotations-contract.md § Repository interfaces (toggle returns `Resource<Boolean>`; `observeAll(): Flow<List<BookmarkEntry>>` newest-first; `observeBookmarkedVerseIds(matnId): Flow<Set<String>>`), with the contract's KDoc semantics.
- [X] T029 [US2] Implement `KOTLIN/data/repository/BookmarkRepositoryImpl.kt` with constructor `(db, clock: () -> Long, newId: () -> String)` (research.md D4). Fact: the codebase has NO runtime UUID generator (seed content ships pre-authored UUIDs), so `newId`/`clock` are pure injected lambdas here — production values are supplied only at the DI site (see T038 for the exact expressions); tests inject fixed values. `toggle` = one transaction: `selectBookmarkByVerse` → delete if present / insert (newId(), clock()) if absent → return resulting state as `Resource.Success(nowBookmarked)`; DB failures → `Resource.Error` via the project's `StorageCall.kt` convention. `observeAll` maps `selectAllBookmarksWithContext` rows to `BookmarkEntry` (drop rows with unresolvable context — never throw).
- [X] T030 [US2] Write `TEST/data/BookmarkRepositoryTest.kt` per contracts/annotations-contract.md § Tests: toggle on→off→on round-trip (re-add mints a NEW id); newest-first ordering with deterministic `id` tiebreak; `observeBookmarkedVerseIds` per-matn correctness; rows survive a fresh query-object read of the same driver (persistence); 10 rapid sequential toggles settle to a consistent single-row/no-row state; deleting a verse's audio rows does NOT remove its bookmark (FR-014).
- [X] T031 [US2] Create use cases `KOTLIN/domain/usecase/ToggleBookmarkUseCase.kt` (`UseCase<String, Boolean>` delegating to `BookmarkRepository.toggle`) and `KOTLIN/domain/usecase/ObserveBookmarksUseCase.kt` (`FlowUseCase<Unit, List<BookmarkEntry>>` delegating to `observeAll`).
- [X] T032 [US2] Create `KOTLIN/domain/model/VerseAnnotations.kt` (`data class VerseAnnotations(val verseId: String, val isBookmarked: Boolean, val hasNote: Boolean)`) and `KOTLIN/domain/usecase/ObserveVerseAnnotationsUseCase.kt` (`FlowUseCase<String, Map<String, VerseAnnotations>>`). THIS STORY: build the map from `BookmarkRepository.observeBookmarkedVerseIds(matnId)` only, with `hasNote = false` everywhere; KDoc-note that US3 (T044) extends it to combine `NoteRepository.observeNotedVerseIds`. Add `TEST/domain/ObserveVerseAnnotationsUseCaseTest.kt` with a fake repository.
- [X] T033 [US2] Wire indicators + toggle into the reading surface: add `annotations: Map<String, VerseAnnotations>` to the details/carousel UI state (`KOTLIN/presentation/player/ReadingCarouselUiState.kt` or the owning `MatnDetailsUiState.kt` — follow where verse data already lives), collect `ObserveVerseAnnotationsUseCase(matnId)` in `KOTLIN/presentation/details/MatnDetailsViewModel.kt`, add intent `onToggleBookmark(verseId)` calling `ToggleBookmarkUseCase`. In `KOTLIN/presentation/player/ReadingCarousel.kt`: bookmark indicator glyph on any bookmarked verse card + toggle action on the ACTIVE verse card, placed per T004 design notes (default: active-card action row). New glyphs go in `KOTLIN/presentation/common/` following `PlaybackGlyphs.kt`'s hand-drawn convention; content description "bookmarked" (ui-contract.md accessibility gate). Update carousel previews: bookmarked + unbookmarked states. Tokens only.
- [X] T034 [US2] Extend `TEST/presentation/MatnDetailsViewModelTest.kt`: annotation map flows into state; `onToggleBookmark` invokes the use case and the updated map round-trips into state (fake repos); playback state untouched by toggling.
- [X] T035 [US2] Create `KOTLIN/presentation/notes/NotesTabUiState.kt` with exactly `data class NotesTabUiState(val bookmarks: List<BookmarkEntry> = emptyList())` — no `notes` field in this task (T047 in US3 adds it), and `KOTLIN/presentation/notes/NotesTabViewModel.kt` collecting `ObserveBookmarksUseCase`. Write `TEST/presentation/NotesTabViewModelTest.kt`: empty vs populated bookmark state; entry click intent exposes `(matnId, verseId)`.
- [X] T036 [US2] Create `KOTLIN/presentation/notes/NotesTabScreen.kt`: stateless `NotesTabContent` + thin holder, per the T004 design notes (Bookmarks & Notes design `bc99ab7f…`): bookmarks section listing rows + notes section rendering its empty state (real notes arrive in US3). Extract `KOTLIN/presentation/notes/AnnotationRows.kt` with `BookmarkRow(entry, onClick)` and the shared verse-context block composable (reused by `NoteRow` in US3 — Constitution VIII second-use rule, extracted at FIRST use here because the second is already planned). `@Preview`s: both-empty (SC-007), bookmarks-populated (RTL Arabic).
- [X] T037 [US2] Replace the stub registration: in `KOTLIN/presentation/navigation/MatnNavHost.kt` change `composable(Routes.NOTES)` from `ComingSoonScreen` to `NotesTabScreen` with entry-click navigation `navController.navigate(Routes.matnDetails(matnId, verseId))`; update the now-stale KDoc lists in `MatnNavHost.kt` and `KOTLIN/presentation/navigation/NavigationTab.kt` (both currently say NOTES routes to ComingSoonScreen until Phases 6-8). `ComingSoonScreen` itself stays (Goals/Settings still use it).
- [X] T038 [US2] Wire DI in `KOTLIN/di/ContentModule.kt`: `single<BookmarkRepository> { BookmarkRepositoryImpl(get(), clock = { Clock.System.now().toEpochMilliseconds() }, newId = { Uuid.random().toString() }) }` — exact imports (Kotlin 2.4 stdlib, no new dependencies): `kotlin.time.Clock` + `kotlin.time.ExperimentalTime` and `kotlin.uuid.Uuid` + `kotlin.uuid.ExperimentalUuidApi`, with `@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)` on the module function (both APIs are experimental-stable stdlib). Add `factory` entries for `ToggleBookmarkUseCase`, `ObserveBookmarksUseCase`, `ObserveVerseAnnotationsUseCase`. Run `./gradlew :shared:allTests` — green.

**Checkpoint**: US2 independently shippable — quickstart §B7, B10 (bookmarks half), B11 pass.

---

## Phase 5: User Story 3 — Attach personal notes to verses (Priority: P3)

**Goal**: One editable plain-text note per verse via a bottom-sheet editor, distinct note indicators, and the Notes tab's notes section — completing FR-015…FR-020.

**Independent Test**: Create a note, restart → persists with indicator; edit in place; delete removes; global list previews + navigates; empty save creates nothing (quickstart §B8, B9, B10, B11, B13).

- [X] T039 [P] [US3] Create `KOTLIN/domain/model/Note.kt`: `data class Note(val id: String, val verseId: String, val text: String, val updatedAtMs: Long)` and `data class NoteEntry(val note: Note, val ref: AnnotatedVerseRef)` (data-model.md).
- [X] T040 [US3] Create `KOTLIN/domain/repository/NoteRepository.kt` interface exactly per contracts/annotations-contract.md § Repository interfaces (`save` rejects blank text with a domain error and NEVER persists ""; create-or-replace keeps the existing UUID; `delete` explicit; `get` for prefill; `observeAll` newest-first; `observeNotedVerseIds(matnId)`).
- [X] T041 [US3] Implement `KOTLIN/data/repository/NoteRepositoryImpl.kt` with constructor `(db, clock, newId)` mirroring T029's conventions: `save` = trim/blank-check → `Resource.Error` if blank (FR-019), else `upsertNote` (new id only when no row exists — the T005 upsert preserves `id` on conflict) with `updated_at = clock()`; `observeAll` maps `selectAllNotesWithContext`, dropping unresolvable rows.
- [X] T042 [US3] Write `TEST/data/NoteRepositoryTest.kt` per contracts/annotations-contract.md § Tests: create → edit (SAME id, refreshed `updated_at` via stepped fake clock) → delete lifecycle; blank/whitespace save rejected and no row written (also over an existing note — original text intact, FR-019); newest-first ordering; per-matn id-set; context JOIN correctness; audio deletion leaves notes intact (FR-018).
- [X] T043 [US3] Create use cases in `KOTLIN/domain/usecase/`: `SaveNoteUseCase.kt` (`UseCase<SaveNoteParams, Note>` with `data class SaveNoteParams(val verseId: String, val text: String)`), `DeleteNoteUseCase.kt` (`UseCase<String, Unit>`), `GetNoteUseCase.kt` (`UseCase<String, Note?>`), `ObserveNotesUseCase.kt` (`FlowUseCase<Unit, List<NoteEntry>>`) — thin delegations (contracts/annotations-contract.md § Use cases).
- [X] T044 [US3] Extend `KOTLIN/domain/usecase/ObserveVerseAnnotationsUseCase.kt` to `combine` bookmark AND note id-flows into `VerseAnnotations(isBookmarked, hasNote)` (removing the T032 `hasNote = false` shortcut and its KDoc note). Update `TEST/domain/ObserveVerseAnnotationsUseCaseTest.kt`: bookmark-only, note-only, both, neither.
- [X] T045 [US3] Create `KOTLIN/presentation/notes/NoteEditorSheet.kt` per ui-contract.md § NoteEditorSheet, following the `KOTLIN/presentation/player/RepetitionSetupSheet.kt` idiom: stateless sheet content with `(verseRef, initialText: String?, draft, onDraftChange, onSave, onDelete, onDismiss)`; save enabled iff `draft.isNotBlank()`; delete visible ONLY when `initialText != null` (FR-019 — explicit delete, dismiss discards). `@Preview`s: create-new (save disabled), edit-existing (delete visible). Tokens only, RTL text field.
- [X] T046 [US3] Wire notes into the reading surface: in `KOTLIN/presentation/details/MatnDetailsViewModel.kt` add editor state (target verse, prefill via `GetNoteUseCase`) + intents `onOpenNoteEditor(verseId)`, `onSaveNote(text)`, `onDeleteNote`, `onDismissNoteEditor` calling T043 use cases; host `NoteEditorSheet` from `KOTLIN/presentation/details/MatnDetailsScreen.kt`; note action on the active verse card + note indicator glyph (visually DISTINCT from the bookmark glyph, FR-016; content description "has note") in `KOTLIN/presentation/player/ReadingCarousel.kt` / `KOTLIN/presentation/common/`. Update previews (noted, bookmarked+noted — spec Edge Case B9). Extend `TEST/presentation/MatnDetailsViewModelTest.kt`: open-prefills, save-persists, blank-save surfaces error and keeps sheet open, delete-clears, dismiss-discards.
- [X] T047 [US3] Complete the Notes tab: add `notes: List<NoteEntry>` to `KOTLIN/presentation/notes/NotesTabUiState.kt`, collect `ObserveNotesUseCase` in `NotesTabViewModel.kt`, render the notes section in `NotesTabScreen.kt` with `NoteRow(entry, onClick)` in `KOTLIN/presentation/notes/AnnotationRows.kt` (reusing the T036 verse-context block; preview text truncated ~2 lines, full text preserved in model — data-model.md). Navigation per T037's existing path. Update `TEST/presentation/NotesTabViewModelTest.kt`: all four states (both empty / bookmarks only / notes only / both). `@Preview`: notes-populated with a long truncated note.
- [X] T048 [US3] Wire DI in `KOTLIN/di/ContentModule.kt`: `single<NoteRepository> { NoteRepositoryImpl(get(), clock = { Clock.System.now().toEpochMilliseconds() }, newId = { Uuid.random().toString() }) }` (same imports/`@OptIn` as T038) + `factory` entries for the four T043 use cases. Run `./gradlew :shared:allTests` — green.

**Checkpoint**: all three stories independently functional; FR-020 fully satisfied (Notes tab shows both collections).

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T049 [P] Token-literal guard (Constitution VIII, quickstart §A4): ran the greps from `specs/010-design-system-adoption/quickstart.md` §A3 over `presentation/search/`, `presentation/notes/`, and every file touched by this feature. Zero new raw `Color(0x…)`/`.dp`/`.sp` literals — all matches are pre-existing exempt icon-size defaults (`size: Dp = 22.dp` convention, per `MatnSpacing.kt`'s own documented exemption) or unrelated pre-existing code. Removed one unused `dp` import found along the way (`NoteEditorSheet.kt`).
- [X] T050 Full regression: `./gradlew :shared:allTests` — 236 tests, 0 failures. Existing suites (`PlaybackControllerTest`, `HomeViewModelTest`, `MatnDetailsViewModelTest`, `PlayerBarViewModelTest`, …) pass with only additive assertions (no existing assertion changed). Fixed two pieces of fallout from this feature's own changes: `MigrationTest.schema_version_is_two` → `_is_three` (T006's `2.sqm` bumps the schema again) and a `SearchRepositoryTest` ordering assertion that conflated cross-matn grouping with within-matn ordering.
- [X] T051 Ran quickstart.md §B manual checks on an Android emulator (Pixel-class AVD, API 36) with the seeded library. **Verified on-device**: B1 (search entry point + idle prompt), B2 (verse-fragment/digit search → navigates to focused verse, playback NOT started), B5 (idle ⇄ no-results ⇄ clear all render distinctly), B7 (bookmark toggle + indicator, live update), B8 (note create, edit-prefill, save-enables-on-non-blank, delete button only for existing notes), B9 (bookmark + note indicators coexist on one verse), B10 (Notes tab shows both sections with real data, entry tap navigates to the verse — no more "coming soon"), B14 (native RTL confirmed throughout: bottom-nav order, icon placement, back-chevron direction, text alignment). **Found and fixed a real bug** during this pass: the active-verse card's bookmark/note `IconButton`s were positioned as an absolute `Modifier.align(Alignment.TopEnd)` overlay in `ReadingCarousel.kt`, which visually collided with the verse text on long/wide verses; fixed by making the action row an in-flow `Column` child (see `ActiveVerseCard`) so it reserves its own height. Re-ran the full suite after the fix — still green. **Not exercised this pass** (each needs either a human or tooling this environment doesn't have): B3/B4's Arabic-Indic-digit and chapter-title variants specifically (ADB's `input text` cannot inject Arabic Unicode or Arabic-Indic digits — the Western-digit and English-gibberish paths that *are* reachable this way were verified and exercise the same normalizer code path); B6 (airplane mode — architecturally moot, this app has no network code at all); B11 (full force-quit/relaunch — verified equivalently via multiple app reinstalls that preserved bookmark/note rows across process restarts, per the `run-as` package data being retained); B12 (mid-playback navigation); B13 (dismiss-without-saving on a truly blank draft); B15 (SC-006 blind-user discoverability walkthough — requires a person unfamiliar with the feature).
- [X] T052 [P] Updated `docs/DESIGN-SOURCE.md` "Open issues" recording how the three ui-contract.md open design questions were resolved (see design-notes.md and this entry below).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)** → nothing. T003/T004 parallel after T001.
- **Phase 2 (Foundational)** → Phase 1. T005 → T006 → T007 (same schema chain); T008, T009 parallel to the schema chain. **Blocks all stories.**
- **Phase 3 (US1)** → Phase 2. No dependency on US2/US3.
- **Phase 4 (US2)** → Phase 2. No dependency on US1 (only shared file: `ContentModule.kt`/`MatnNavHost.kt` — merge-order friction only, not a logical dependency).
- **Phase 5 (US3)** → Phase 2, plus **T032** (extends `ObserveVerseAnnotationsUseCase`) and **T035/T036** (extends the Notes tab). If US3 must run before US2, first do T032/T035/T036 from US2's list.
- **Phase 6 (Polish)** → all implemented stories.

### Within-story chains

- **US1**: T010 → T011; T013 → T015; (T011, T012, T014, T013) → T015 → T016; T015 → T017; T014 → T018 → T020; T019 → T020 → T021; T022 → T023 → T024 → T025; T026 last.
- **US2**: T027 → T028 → T029 → T030; T028 → T031/T032; (T031, T032) → T033 → T034; T031 → T035 → T036 → T037; T038 last.
- **US3**: T039 → T040 → T041 → T042; T040 → T043 → (T044, T045, T046, T047); T048 last.

### Parallel opportunities

- Phase 1: T003 ∥ T004. Phase 2: T008 ∥ T009 ∥ (T005→T006→T007).
- US1: T010 ∥ T012 ∥ T013 ∥ T019; T017 ∥ T016 once T015 lands; T022 previews ∥ T018–T021 domain work.
- After Phase 2, US1 / US2 / US3 can be developed by different contributors concurrently (respecting US3's three named US2 tasks).

## Implementation Strategy

**MVP first**: Phases 1 → 2 → 3 (US1 Search), then STOP and validate quickstart §B1–B6 on
device. Search alone is a shippable increment (spec: "serves every student … from day one").

**Incremental delivery**: +US2 (bookmarks + real Notes tab) → validate B7/B10/B11 → +US3
(notes) → validate B8/B9/B13 → Polish → PR to `develop` with quickstart results.

Commit after each task or tight group (e.g., T010+T011); every commit leaves
`./gradlew :shared:allTests` green (Constitution: no phase may leave the app unbuildable).
