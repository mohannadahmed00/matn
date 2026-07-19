# Contract: Use Cases & Repository Extensions (Phase 1)

Presentation reaches the domain **only** through these use cases (Principle I). All share the
new base contracts (Principle III) and return the existing `Resource`/`Flow` types from Phase 0.

## Base contracts (new, Phase 1-owned)

```
interface UseCase<in P, out R> {
    suspend operator fun invoke(params: P): Resource<R>
}
interface FlowUseCase<in P, out R> {
    operator fun invoke(params: P): Flow<R>
}
// Convention: params = Unit for no-arg use cases.
```

- No coroutine is launched inside a use case; the caller (`BaseViewModel`) owns the scope.
- `FlowUseCase` returns a cold `Flow`; failures inside observation surface as an empty/last-good
  emission or are mapped by the ViewModel (repositories already drop corrupt rows, Phase 0).

## Repository extensions (data layer, additive)

`MatnRepository` gains a derived-summary read; content entity interfaces are otherwise unchanged.

```
interface MatnRepository {                       // + Phase 1 additions
    fun observeLibrarySummaries(): Flow<List<MatnSummary>>   // NEW — card projections (FR-002)
    suspend fun getMatn(id: String): Resource<Matn?>         // existing
    fun observeLibrary(): Flow<List<Matn>>                   // existing
    suspend fun getChapters(matnId: String): Resource<List<Chapter>>  // existing
}

interface ReadingPreferencesRepository {         // NEW — Phase 1-owned setting
    fun observeFontSize(): Flow<ReadingFontSize>
    suspend fun setFontSize(size: ReadingFontSize): Resource<Unit>
}
```

`VerseRepository.observeVerses(matnId)` (existing) supplies the reading list in `displayNumber`
order — unchanged.

## Phase 1 use cases

| Use case | Type | Params | Result | Requirements |
|----------|------|--------|--------|--------------|
| `ObserveLibraryUseCase` | `FlowUseCase` | `Unit` | `List<MatnSummary>` | FR-001, FR-002, FR-004 |
| `GetMatnDetailsUseCase` | `UseCase` | `matnId: String` | `MatnDetails` (matn + chapters + derived totals) | FR-005, FR-013, FR-015 |
| `ObserveVersesUseCase` | `FlowUseCase` | `matnId: String` | `List<Verse>` (displayNumber order) | FR-006, FR-007, FR-008 |
| `GetFontSizeUseCase` | `FlowUseCase` | `Unit` | `ReadingFontSize` | FR-016, FR-017 |
| `SetFontSizeUseCase` | `UseCase` | `ReadingFontSize` | `Unit` | FR-016, FR-017 |

Notes:
- `GetMatnDetailsUseCase` composes `getMatn` + `getChapters` + derived totals and decides
  `showTableOfContents` (STRUCTURED with ≥1 chapter). Missing matn → `Resource.Failure(NotFound)`.
- `ObserveVersesUseCase` passes verse `arabicText` through verbatim (no transformation) so
  diacritics stay byte-identical (FR-008/SC-002).
- Empty library is a valid success (`emptyList`), surfaced by the ViewModel as the empty state
  (FR-004/SC-008), not an error.

## Behavioural contract (Given/When/Then → use case)

- **Library populated** → `ObserveLibraryUseCase()` emits one `MatnSummary` per matn with
  correct `verseCount`/`totalDurationMs`. (US2 AS1)
- **Library empty** → `ObserveLibraryUseCase()` emits `emptyList()`. (US2 AS3)
- **Open matn** → `GetMatnDetailsUseCase(id)` returns header totals; `ObserveVersesUseCase(id)`
  emits all verses in matn-global order with intact Arabic. (US1 AS1–AS3)
- **Structured matn** → details `chapters` non-empty, `showTableOfContents = true`; each chapter
  carries its first-verse `displayNumber`. **Simple matn** → `chapters` empty,
  `showTableOfContents = false`. (US3 AS1/AS3)
- **Change font size** → `SetFontSizeUseCase(size)` persists; `GetFontSizeUseCase()` re-emits the
  new size; reopening the app emits the persisted size (default MEDIUM if unset). (US4 AS1–AS3)
- **Offline** → every use case resolves from the local store with zero network. (US1 AS5)
