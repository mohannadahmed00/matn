# Contract: UI Surfaces & Navigation

**Design authority** (Constitution VIII): implementers MUST fetch these Stitch screens via the
`stitch` MCP server *before* building UI, and use only Phase 10 tokens
(`theme/Color.kt`/`Type.kt`/`Shape.kt`/`Spacing.kt`) — zero raw hex/`.dp`/`.sp` literals:

| Surface | Stitch screen | ID |
|---------|--------------|-----|
| Search screen | *Search Matn* | `fa8b63b036c94510ba1be2d90e7a3417` |
| Notes tab | *Bookmarks & Notes* | `bc99ab7f8110479086326271d0aa0310` |
| Verse actions / note editor | no dedicated screen — see Open design questions | — |

## Navigation changes (`MatnNavHost.kt` / `Routes`)

| Route | Change | Notes |
|-------|--------|-------|
| `search` | NEW | Pushed from Home top-bar entry point; no bottom bar (focused surface, same rule as the matn route) |
| `matn/{matnId}?focusVerseId={id}` | MODIFY | Optional arg (research D5); absent → current behavior. Focus only — never auto-starts playback |
| `notes` | MODIFY registration | `ComingSoonScreen` → `NotesTabScreen` (FR-020). `NavigationTab`, glyph, label unchanged |

Back behavior: search and annotation navigation push onto the stack normally; system back
returns to the originating surface (search results / Notes tab preserved via existing
state-restoration pattern).

## Screens (each = stateless content composable + thin stateful holder, Principle II)

### `SearchScreen` (`presentation/search/`)

- **State**: `SearchUiState` — `Idle / Searching / Results(List<SearchResultRow>) / NoResults`
  (see search-contract.md). Raw query text is part of the state.
- **Intents**: `onQueryChange(String)`, `onClearQuery`, `onResultClick(SearchResult)`, `onBack`.
- **Previews (blocking, Principle II)**: Idle, Results (mixed verse/chapter/matn rows, RTL
  Arabic sample), NoResults.
- **Result row** is a shared stateless component (`SearchResultRow`) — one parameterized row
  family for the three match kinds, not three near-duplicates (Principle VIII).

### `NotesTabScreen` (`presentation/notes/`)

- **State**: `NotesTabUiState(bookmarks: List<BookmarkEntry>, notes: List<NoteEntry>)` with
  per-section empty flags derived, not stored.
- **Intents**: `onBookmarkClick(entry)`, `onNoteClick(entry)` → navigate
  `matn/{matnId}?focusVerseId=…`; section switching per the Stitch design's own idiom.
- **Previews**: both-empty (SC-007 purposeful empty states), bookmarks-only, both-populated
  (RTL, long-note truncation).
- Row components (`BookmarkRow`, `NoteRow`) share the verse-context block
  (`AnnotatedVerseRef` rendering) as one extracted composable.

### `NoteEditorSheet` (`presentation/notes/`, hosted by `MatnDetailsScreen`)

- **State**: `(verseRef, initialText: String?, canSave: Boolean, canDelete: Boolean)` — pure
  function of prefill + current draft; `canSave = draft.isNotBlank()`.
- **Intents**: `onSave(text)`, `onDelete`, `onDismiss`. Delete is explicit (FR-019) and only
  visible for an existing note.
- **Previews**: create-new (empty draft, save disabled), edit-existing (delete visible).
- Idiom: bottom sheet, consistent with `RepetitionSetupSheet`.

### `ReadingCarousel` / `MatnDetailsScreen` (MODIFY)

- Active-verse card gains bookmark-toggle and note actions; all verse cards render
  bookmark/note indicator glyphs from `Map<String, VerseAnnotations>` (new field on the
  existing UI state, fed by `ObserveVerseAnnotationsUseCase`).
- Indicators MUST be visually distinct from each other (FR-016) and token-driven.
- `focusVerseId` handling: initial carousel focus positions on that verse (no playback start).
- Updated previews: bookmarked verse, noted verse, both.

### `HomeScreen` (MODIFY)

- Top-bar search entry point (per *Search Matn* / *Home* Stitch designs) emitting an
  `onOpenSearch` intent → navigate `search`.

## Open design questions (resolve against fetched Stitch designs at implementation, per Principle VIII — raise, don't invent)

1. Notes tab segmentation: tabs/segmented control vs. stacked sections — follow
   `bc99ab7f…` as rendered.
2. Exact placement/iconography of bookmark & note actions on the active verse card — if
   `ac354abd…` (Reading & Playback) and `fa8b63b0…`/`bc99ab7f…` show no explicit affordance,
   default to the active-verse card's action row and flag the gap in
   docs/DESIGN-SOURCE.md "Open issues".
3. Whether the *Search Matn* design scopes search to one matn or the whole library — the spec
   (FR-001, clarified) mandates library-wide; if the design conflicts, the spec + constitution
   win and the conflict is recorded (Constitution VIII).

## Accessibility / RTL gates (Constitution VII)

- All three new surfaces render native RTL with Arabic-first content; previews use Arabic
  sample data.
- Indicator glyphs carry content descriptions (bookmarked / has-note) for screen readers.
- Touch targets for verse-card actions meet the platform minimum (via token-driven sizing).
