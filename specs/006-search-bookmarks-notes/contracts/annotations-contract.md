# Contract: Bookmarks & Notes (Annotations)

**Owner**: `domain/repository/BookmarkRepository.kt` + `NoteRepository.kt` (interfaces),
`data/repository/BookmarkRepositoryImpl.kt` + `NoteRepositoryImpl.kt` (SQLDelight, schema v2),
use cases listed below.

## Repository interfaces

```kotlin
interface BookmarkRepository {
    /** Atomic toggle: bookmarked→removed, unbookmarked→created (fresh UUID, clock timestamp).
     *  Returns the resulting state. Serialized per verse — rapid toggling settles consistently. */
    suspend fun toggle(verseId: String): Resource<Boolean /* nowBookmarked */>
    fun observeAll(): Flow<List<BookmarkEntry>>              // newest-first (created_at DESC)
    fun observeBookmarkedVerseIds(matnId: String): Flow<Set<String>>
}

interface NoteRepository {
    /** Create-or-replace. Blank text → Resource.Error(EmptyNote) — never persists "" (FR-019).
     *  Existing note keeps its UUID; updated_at refreshed from the injected clock. */
    suspend fun save(verseId: String, text: String): Resource<Note>
    suspend fun delete(verseId: String): Resource<Unit>      // explicit action only (FR-019)
    suspend fun get(verseId: String): Resource<Note?>        // editor prefill
    fun observeAll(): Flow<List<NoteEntry>>                  // newest-first (updated_at DESC)
    fun observeNotedVerseIds(matnId: String): Flow<Set<String>>
}
```

Both impls take `(db, clock: () -> Long, newId: () -> String)` — clock and UUID minting
injected (research D4, Principle V).

## Use cases (all thin, following existing `UseCase`/`FlowUseCase` contracts)

| Use case | Contract | Backs |
|----------|----------|-------|
| `ToggleBookmarkUseCase` | `UseCase<String, Boolean>` | Verse card action (FR-010) |
| `ObserveBookmarksUseCase` | `FlowUseCase<Unit, List<BookmarkEntry>>` | Notes-tab bookmarks section (FR-012) |
| `ObserveVerseAnnotationsUseCase` | `FlowUseCase<String /*matnId*/, Map<String, VerseAnnotations>>` | Carousel indicators (FR-011/FR-016); combines the two `observe*VerseIds` flows |
| `SaveNoteUseCase` | `UseCase<SaveNoteParams(verseId, text), Note>` | Editor save (FR-015, FR-019) |
| `DeleteNoteUseCase` | `UseCase<String, Unit>` | Editor delete (FR-015) |
| `GetNoteUseCase` | `UseCase<String, Note?>` | Editor prefill (FR-015) |
| `ObserveNotesUseCase` | `FlowUseCase<Unit, List<NoteEntry>>` | Notes-tab notes section (FR-017) |

## Semantics (binding)

1. **At most one per verse** — enforced by `UNIQUE(verse_id)` (schema) *and* toggle/upsert
   semantics (behavior). Duplicate rows are unrepresentable.
2. **Persistence** (FR-014/FR-018): rows live in the app database; survive restart trivially;
   no reference to `audio_asset` — audio removal cannot touch them.
3. **Global list context**: `observeAll()` JOINs `verse` + `matn` to build
   `AnnotatedVerseRef` — a bookmark/note whose matn context can't be resolved is dropped from
   the list emission (consistent with the project's drop-corrupt-rows convention), never crashes.
4. **Ordering**: both global lists newest-first; deterministic tiebreak by `id`.
5. **Empty note text**: `save` with blank text returns a domain error consumed by the editor
   (keeps its "save" disabled/no-ops); deletion is a distinct explicit call (FR-019).

## Tests (binding)

- `BookmarkRepositoryTest`: toggle on/off/on round-trip (new UUID on re-add), newest-first
  ordering, per-matn id-set correctness, persistence across a second DB connection to the same
  driver, rapid-toggle serialization.
- `NoteRepositoryTest`: create/edit (same id, refreshed timestamp)/delete, blank-save rejection,
  ordering, per-matn id-set, context JOIN correctness.
- `MigrationV2Test`: v1 database with seeded content migrates to v2 with content intact and both
  new tables usable.
- `NotesTabViewModelTest`: combined state (both empty / bookmarks only / notes only / both),
  navigation intents carry `(matnId, verseId)`.
