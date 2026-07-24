# Contract: Search

**Owner**: `domain/repository/SearchRepository.kt` (interface),
`data/repository/SearchRepositoryImpl.kt` (SQLDelight-backed, in-memory filtering per research D2),
`domain/usecase/SearchLibraryUseCase.kt`.

## Repository interface

```kotlin
interface SearchRepository {
    /** Emits the deterministic result set for [query] and re-emits when content changes.
     *  Blank (whitespace-only) queries emit an empty list — Idle vs NoResults is the
     *  ViewModel's distinction (FR-008/FR-009), keyed on the *raw* query, not the result. */
    fun search(query: String): Flow<List<SearchResult>>
}
```

`SearchLibraryUseCase : FlowUseCase<String, List<SearchResult>>` — thin delegation, injected
into `SearchViewModel`.

## Semantics (binding)

1. **Corpus** (FR-001, clarified): matn titles, chapter titles, verse display numbers, verse
   Arabic text — across *all* متون in the library. Notes are NOT searched.
2. **Matching**: `ArabicNormalizer` containment per normalization-contract.md, applied to every
   corpus field. Verse-number matching: the normalized query (digits unified) matches a verse
   iff it equals or is contained in the decimal rendering of `display_number`.
3. **Ordering** (FR-006): matn groups in library order (title-sorted, same as
   `selectAllMatn`); within a group — MatnMatch, then ChapterMatches by `display_order`, then
   VerseMatches by `display_number`. Byte-for-byte deterministic for identical query + content.
4. **Offline** (FR-004): implementation may touch only the local database.
5. **Reactivity**: content changes (seed updates) re-emit current-query results; SQLDelight
   reactive queries are the invalidation source.
6. **Navigation payloads** (FR-007): `VerseMatch.ref.verseId` → matn route + `focusVerseId`;
   `ChapterMatch.firstVerseId` → same (fallback: plain matn route when a chapter has no verses);
   `MatnMatch.matnId` → matn details route.

## ViewModel state machine (`SearchUiState`)

```
Idle          — raw query blank; results hidden, prompt shown        (FR-008)
Searching     — debounce elapsed, computation in flight (usually sub-frame)
Results(list) — non-empty result set
NoResults     — non-blank query, empty result set                    (FR-009)
```

Debounce ~250 ms, `flatMapLatest` cancellation of stale queries (research D8). No minimum query
length.

## Tests (binding)

- `SearchRepositoryTest` (in-memory driver per `TestDatabase.kt`): corpus coverage — each of the
  four field kinds matches; ordering determinism; blank → empty; chapter-with-no-verses
  fallback; cross-matn grouping.
- **SC-001 perf guard**: seeded ≥1,000-verse corpus, single query completes < 1 s in
  `commonTest` (pattern: existing `data/PerformanceTest.kt`).
- `SearchViewModelTest` (`kotlinx-coroutines-test`): Idle/Searching/Results/NoResults
  transitions, debounce collapse of rapid keystrokes, stale-query cancellation.
