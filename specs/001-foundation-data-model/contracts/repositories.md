# Contract: Repository & Seed-Loader Interfaces

The domain layer exposes these **interfaces** (in `com.giraffe.matn.domain.repository`); the
data layer provides the SQLDelight-backed implementations. All fallible operations return the
shared `Result`/`Resource` type (Principle III) rather than throwing or returning bare
nullables. These signatures are the contract the later phases (reading, playback, progress)
depend on. Types shown are conceptual Kotlin.

---

## Shared result type (`com.giraffe.matn.core`)

```kotlin
sealed interface Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>
    data class Failure(val error: AppError) : Resource<Nothing>
}
```

`AppError` includes `ContentIntegrityError` (see below) and a generic `Storage`/`NotFound`
case. Reads that legitimately return "nothing" use an explicit empty/`null` payload wrapped in
`Success`, not `Failure` — e.g. querying an unloaded matn yields `Success(null)` (edge case:
clear "not found", not an error).

---

## `MatnRepository`

| Operation | Signature (conceptual) | Contract |
|-----------|------------------------|----------|
| Get one matn | `suspend fun getMatn(id: String): Resource<Matn?>` | Returns the matn or `Success(null)` if not loaded (edge case). Never throws for a missing id. |
| List library | `fun observeLibrary(): Flow<List<Matn>>` | Emits the current set of loaded متون; re-emits on change (FR-008). Offline (SC-005). |
| Get chapters | `suspend fun getChapters(matnId: String): Resource<List<Chapter>>` | Returns chapters ordered by `display_order`. **Empty list** for a simple matn — not an error (FR-012). |

## `VerseRepository`

| Operation | Signature | Contract |
|-----------|-----------|----------|
| All verses of a matn | `fun observeVerses(matnId: String): Flow<List<Verse>>` | Ordered by matn-global `display_number` (FR-006/FR-003). Stable, independent of row/UUID order (SC-001). Full sequence for both simple and structured متون. |
| Verses of a chapter | `suspend fun getVersesByChapter(chapterId: String): Resource<List<Verse>>` | Only that chapter's verses, ordered by `display_number` — a contiguous slice of the global sequence (FR-011/SC-002). |
| Single verse | `suspend fun getVerse(id: String): Resource<Verse?>` | `Success(null)` if absent. |

## `AudioAssetRepository`

| Operation | Signature | Contract |
|-----------|-----------|----------|
| Resolve verse audio | `suspend fun getAudioForVerse(verseId: String, reciterId: String = DEFAULT_RECITER): Resource<AudioAsset?>` | Returns **exactly one** asset for the verse+reciter (SC-004/FR-014), or `Success(null)` if the verse/reciter pair is unknown. |

## `ContentSeedLoader`

| Operation | Signature | Contract |
|-----------|-----------|----------|
| Load / re-load a matn | `suspend fun load(payload: SeedMatn): Resource<Matn>` | **Atomic**: validates first, then upserts all rows in one transaction. On any integrity violation, persists **nothing** for that matn and returns `Failure(ContentIntegrityError…)` naming the specific problem(s) (FR-019/SC-008). On success, the matn is fully retrievable. Re-loading the same UUID upserts in place — no duplicates, stable ids (FR-009/SC-007). |

`SeedMatn` is the data-layer DTO (see `seed-content.md`); the loader maps it to domain entities
and rows. `DEFAULT_RECITER` is the single v1 reciter id.

---

## `ContentIntegrityError` (domain)

```kotlin
sealed interface ContentIntegrityError : AppError {
    data class DuplicateDisplayNumber(val matnId: String, val number: Int) : ContentIntegrityError
    data class MissingAudio(val verseId: String) : ContentIntegrityError
    data class DuplicateAudioRef(val fileRef: String) : ContentIntegrityError
    data class OrphanChapterRef(val verseId: String) : ContentIntegrityError
    data class StructureMismatch(val matnId: String, val detail: String) : ContentIntegrityError
    data class InvalidId(val detail: String) : ContentIntegrityError
    data class Aggregate(val problems: List<ContentIntegrityError>) : ContentIntegrityError
}
```

The loader aggregates all detected problems into `Aggregate` so a single failed load reports
every issue at once, not just the first.

---

## Behavioral guarantees (traceability)

| Guarantee | Requirement | Verified by (quickstart / test) |
|-----------|-------------|---------------------------------|
| Simple matn round-trips, correct order | FR-005/FR-006/SC-001 | `SimpleMatnRoundTripTest` |
| Diacritics preserved byte-for-byte | FR-007/SC-003 | `DiacriticRoundTripTest` |
| Structured matn: ordered chapters + grouped verses | FR-011/SC-002 | `StructuredMatnTest` |
| Simple matn chapter query returns empty | FR-012 | `SimpleMatnNoChaptersTest` |
| Exactly one audio asset per verse, unique refs | FR-013/FR-014/SC-004 | `AudioResolutionTest` |
| Multi-reciter extensibility (no schema change) | FR-015 | `MultiReciterModelTest` |
| Atomic reject on duplicate display number | FR-019/SC-008 | `AtomicRejectDuplicateOrderTest` |
| Atomic reject on missing audio | FR-019/SC-008 | `AtomicRejectMissingAudioTest` |
| Reload dedup, stable ids | FR-009/SC-007 | `ReloadDedupTest` |
| All reads offline, no network | FR-004/FR-017/SC-005 | in-memory driver; no network dependency exists in the module |
| Unloaded matn → clear not-found | edge case | `NotFoundTest` |
