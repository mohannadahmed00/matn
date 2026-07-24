# Implementation Plan: Search, Bookmarks & Notes

**Branch**: `006-search-bookmarks-notes` | **Date**: 2026-07-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/006-search-bookmarks-notes/spec.md`

## Summary

Add three additive capabilities on top of the Phase 1–5 + 10 foundation: (1) a dedicated
full-screen search experience, opened from the Library tab's top bar, matching verse text,
verse numbers (Western + Arabic-Indic digits), matn titles, and chapter titles with
diacritic-insensitive, letter-form-folded Arabic matching; (2) per-verse bookmarks toggled from
the reading screen; (3) one editable plain-text note per verse. Bookmarks and notes aggregate in
a real Notes tab that replaces the current "coming soon" stub, and every result/bookmark/note
navigates to its verse in the existing reading screen. Technical approach: a pure-Kotlin
`ArabicNormalizer` in `commonMain` + in-memory filtering over SQLDelight-backed content (no FTS,
no schema change for search), two new UUID-keyed tables (`bookmark`, `note`) added via the
project's first SQLDelight migration, new domain repositories/use cases following the existing
`UseCase`/`FlowUseCase`/`Resource` contracts, and three new presentation surfaces (Search screen,
Notes tab, note-editor sheet) built from the registered Stitch designs.

## Technical Context

**Language/Version**: Kotlin 2.x, Compose Multiplatform (Material 3)

**Primary Dependencies**: SQLDelight (existing `MatnDatabase`, `Content.sq`), Koin 4.0
(`di/ContentModule.kt`), Jetbrains `navigation-compose` (`MatnNavHost.kt`), coroutines/Flow.
No new third-party dependencies.

**Storage**: SQLDelight — two new tables (`bookmark`, `note`), additive migration from schema
v1 → v2. No changes to existing tables; search requires no schema change (in-memory
normalization, see research.md D1/D2).

**Testing**: `kotlin.test` + `kotlinx-coroutines-test` in `commonTest` (normalizer, repositories
via in-memory driver per `TestDatabase.kt`, use cases, ViewModels); Compose `@Preview` per
Principle II for every new state-rendering composable.

**Target Platform**: Android + iOS via the KMP `shared` module; no new platform (`expect`/
`actual`) code required.

**Project Type**: Mobile app (KMP) — single `shared` module holding domain/data/presentation.

**Performance Goals**: SC-001 — results within 1 s of typing pause on a ≥1,000-verse library
(in-memory scan of the whole corpus is O(corpus) per query; budget measured in
`commonTest` per the existing `PerformanceTest.kt` pattern). SC-005 — bookmark toggle visible
within 1 s (SQLDelight reactive query round-trip).

**Constraints**: Offline-first (zero network); RTL-native for all new surfaces; Arabic matching
per FR-002 (diacritic stripping + أ/إ/آ/ٱ→ا, ى→ي, ة→ه folding) and FR-003 (٠–٩ ≈ 0–9);
one bookmark and one note max per verse; annotations keyed by verse UUID and independent of
audio assets (survive audio removal).

**Scale/Scope**: ~1 normalizer, 3 repositories, ~8 use cases, 2 new tables + 1 migration,
2 new screens (Search, Notes tab), 1 note-editor sheet, verse-level indicator/action wiring in
the reading carousel, 1 new route + 1 route gaining an optional argument. Corpus scale:
low thousands of verses, tens of chapters/متون.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Clean Architecture & Layer Boundaries** — PASS. New domain surface
  (`ArabicNormalizer`, models, `SearchRepository`/`BookmarkRepository`/`NoteRepository`
  interfaces, use cases) is pure Kotlin in `commonMain/domain`; SQLDelight implementations stay
  in `data/repository`; screens reach domain only through use cases injected via Koin.
- **II. MVVM Presentation** — PASS by construction. `SearchViewModel`, `NotesTabViewModel`, and
  the annotation additions to the reading screen expose immutable UI-state via `StateFlow`
  through `BaseViewModel`; every new screen ships a stateless content composable + thin stateful
  holder, with `@Preview`s for loaded/empty/idle/no-results states (enumerated in
  contracts/ui-contract.md).
- **III. DRY via Base Abstractions** — PASS. New use cases implement the existing
  `UseCase`/`FlowUseCase` contracts with `Resource` for fallible one-shots; normalization logic
  exists exactly once (`ArabicNormalizer`) and is shared by search and any future consumer;
  the Notes tab's two lists share one row-component family rather than duplicating.
- **IV. Shared-First Multiplatform** — PASS. Everything lands in `commonMain`; no
  `expect`/`actual` additions.
- **V. Test-First & Testable Design** — PASS. Normalizer, search filtering, bookmark/note CRUD,
  and ViewModel state are all device-free `commonTest` targets; timestamps come from an injected
  clock (`() -> Long`), never `System.currentTimeMillis()` in domain/data logic (research D4).
- **VI. Offline-First & Future-Proof Data** — PASS. `bookmark` and `note` rows carry their own
  UUID `id` (constitution names bookmarks/notes explicitly) plus the verse UUID they attach to;
  fully local; schema is sync-safe (stable IDs, last-modified timestamp on notes).
- **VII. Experience Fidelity: Audio, RTL & Accessibility** — PASS with gates: new surfaces must
  render native RTL; navigation from search/bookmarks/notes into a matn must not corrupt
  playback state (spec edge case) — reuses the existing details-screen entry path rather than
  poking `PlaybackController`.
- **VIII. Design Fidelity & Reusable Composables** — PASS with gates: implementers MUST fetch
  the registered Stitch screens before UI work — *Search Matn* `fa8b63b036c94510ba1be2d90e7a3417`
  and *Bookmarks & Notes* `bc99ab7f8110479086326271d0aa0310` (docs/DESIGN-SOURCE.md) — and build
  with Phase 10 tokens (`theme/Color.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt`); zero raw
  hex/`.dp`/`.sp` literals; repeated units (result row, bookmark row, note row, empty state,
  indicator glyphs) extracted as shared stateless components at second use.

**Post-design re-check (after Phase 1)**: PASS — no violations introduced; Complexity Tracking
left empty.

## Project Structure

### Documentation (this feature)

```text
specs/006-search-bookmarks-notes/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── normalization-contract.md
│   ├── search-contract.md
│   ├── annotations-contract.md
│   └── ui-contract.md
└── tasks.md             # Phase 2 output (/speckit-tasks — not created by /speckit-plan)
```

### Source Code (repository root)

```text
shared/src/commonMain/
├── sqldelight/com/giraffe/matn/db/
│   ├── Content.sq                       # MODIFY — add bookmark/note tables + queries
│   └── migrations/1.sqm                 # NEW — v1→v2 additive migration (bookmark, note)
└── kotlin/com/giraffe/matn/
    ├── domain/
    │   ├── search/
    │   │   └── ArabicNormalizer.kt      # NEW — pure normalization (diacritics, letter folding, digits)
    │   ├── model/
    │   │   ├── Bookmark.kt              # NEW
    │   │   ├── Note.kt                  # NEW
    │   │   ├── VerseAnnotations.kt      # NEW — per-verse bookmark/note flags for the reading screen
    │   │   ├── AnnotatedVerseRef.kt     # NEW — verse + matn context for global lists
    │   │   └── SearchResult.kt          # NEW — sealed: VerseMatch / MatnMatch / ChapterMatch
    │   ├── repository/
    │   │   ├── SearchRepository.kt      # NEW — interface
    │   │   ├── BookmarkRepository.kt    # NEW — interface
    │   │   └── NoteRepository.kt        # NEW — interface
    │   └── usecase/
    │       ├── SearchLibraryUseCase.kt          # NEW
    │       ├── ToggleBookmarkUseCase.kt         # NEW
    │       ├── ObserveBookmarksUseCase.kt       # NEW — global list w/ context
    │       ├── ObserveVerseAnnotationsUseCase.kt # NEW — per-matn indicator map
    │       ├── SaveNoteUseCase.kt               # NEW — create/replace; empty text = no-op
    │       ├── DeleteNoteUseCase.kt             # NEW
    │       ├── GetNoteUseCase.kt                # NEW — editor prefill
    │       └── ObserveNotesUseCase.kt           # NEW — global list w/ context + preview
    ├── data/repository/
    │   ├── SearchRepositoryImpl.kt      # NEW — reads content, filters via ArabicNormalizer
    │   ├── BookmarkRepositoryImpl.kt    # NEW — SQLDelight-backed
    │   └── NoteRepositoryImpl.kt        # NEW — SQLDelight-backed
    ├── di/ContentModule.kt              # MODIFY — register new repos + use cases
    └── presentation/
        ├── navigation/
        │   ├── MatnNavHost.kt           # MODIFY — search route; Notes tab gets real screen;
        │   │                            #          matn route gains optional focusVerseId arg
        │   └── NavigationTab.kt         # unchanged (NOTES tab already exists)
        ├── search/
        │   ├── SearchScreen.kt          # NEW — stateless content + stateful holder
        │   ├── SearchResultRow.kt       # NEW — shared row family for the 3 match kinds
        │   ├── SearchUiState.kt         # NEW
        │   └── SearchViewModel.kt       # NEW
        ├── notes/
        │   ├── NotesTabScreen.kt        # NEW — bookmarks + notes lists (Stitch bc99ab7f…)
        │   ├── AnnotationRows.kt        # NEW — BookmarkRow/NoteRow + shared verse-context block
        │   ├── NotesTabUiState.kt       # NEW
        │   ├── NotesTabViewModel.kt     # NEW
        │   └── NoteEditorSheet.kt       # NEW — create/edit/delete bottom sheet
        ├── home/HomeScreen.kt           # MODIFY — top-bar search entry point
        ├── details/MatnDetailsScreen.kt # MODIFY — accept focus verse; host editor sheet
        └── player/ReadingCarousel.kt    # MODIFY — bookmark/note indicators + actions on active verse

shared/src/commonTest/kotlin/com/giraffe/matn/
├── domain/search/ArabicNormalizerTest.kt        # NEW
├── data/SearchRepositoryTest.kt                 # NEW — incl. SC-001 perf guard (1,000+ verses)
├── data/BookmarkRepositoryTest.kt               # NEW
├── data/NoteRepositoryTest.kt                   # NEW
├── data/MigrationV2Test.kt                      # NEW — v1 data survives migration
└── presentation/
    ├── SearchViewModelTest.kt                   # NEW
    └── NotesTabViewModelTest.kt                 # NEW
```

**Structure Decision**: Single `shared` KMP module, mirroring the exact layering already in
place (`domain` / `data` / `presentation` + `di`). New feature packages `presentation/search`
and `presentation/notes` sit beside the existing `home`/`details`/`player` packages. No
`androidApp`/`iosApp` changes — the shell already hosts `MatnNavHost`.

## Complexity Tracking

No constitution violations to justify — table intentionally empty.
