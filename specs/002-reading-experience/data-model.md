# Phase 1 Data Model: Reading Experience (static)

Phase 1 introduces **no new persisted content entities** — it reads the Phase 0 content model
(`Matn`, `Chapter`, `Verse`, `AudioAsset`) through the existing repository interfaces. This
document defines (a) the one new Phase 1-owned persisted setting, (b) the small derived/read
projections the presentation layer consumes, and (c) the immutable UI-state models the
ViewModels expose. Storage entities and UI-state models are deliberately separated
(Clean Architecture, Principle I).

## 1. Reused Phase 0 entities (read-only)

Defined and persisted in Phase 0; Phase 1 only reads them. Summarized here for reference.

| Entity | Key fields used by Phase 1 | Read via |
|--------|----------------------------|----------|
| `Matn` | `id`, `title`, `author`, `description`, `coverImageRef`, `structureKind` | `MatnRepository.getMatn`, `MatnRepository.observeLibrary` |
| `Chapter` | `id`, `matnId`, `title`, `order` | `MatnRepository.getChapters` |
| `Verse` | `id`, `matnId`, `chapterId`, `displayNumber`, `arabicText`, `durationMs` | `VerseRepository.observeVerses` |

`structureKind ∈ {SIMPLE, STRUCTURED}` (existing `StructureKind` enum) decides whether a table
of contents is shown (FR-013/FR-015). Verses are always read in **matn-global `displayNumber`
order** (Phase 0 guarantee, FR-006).

## 2. New persisted state — reading display preference (Phase 1-owned)

The only Phase 1-owned persisted state (FR-017). A single **global** value, not per-matn.

### 2.1 Domain type

```
enum class ReadingFontSize { SMALL, MEDIUM, LARGE, XLARGE }   // default = MEDIUM
```

- Maps to a concrete `sp` scale value for verse text in the presentation theme
  (e.g. SMALL≈18, MEDIUM≈22, LARGE≈26, XLARGE≈30 — final values tuned during implementation so
  every step stays legible with heavy diacritics and no clipping, FR-016/SC-007).
- Default is `MEDIUM` when nothing is stored.

### 2.2 Persistence (SQLDelight key/value row — additive, no content-schema change)

New table in the existing `ContentDatabase`:

```
CREATE TABLE app_setting (
    key   TEXT NOT NULL PRIMARY KEY,
    value TEXT NOT NULL
);
-- reading font size stored under key = 'reading_font_size', value = enum name
```

| Field | Rule |
|-------|------|
| `key` | Stable string identifier; `reading_font_size` for FR-017. |
| `value` | Enum constant name (`SMALL`…`XLARGE`); unknown/absent → default `MEDIUM`. |

- **Validation**: reading an unrecognized or missing value yields `MEDIUM` (never crashes).
- **Write semantics**: persisted immediately on every change (Principle VI), so the choice
  survives app restart (SC-007). Observed as a `Flow` so a change re-renders live (FR-016).

### 2.3 Domain contract

```
interface ReadingPreferencesRepository {
    fun observeFontSize(): Flow<ReadingFontSize>
    suspend fun setFontSize(size: ReadingFontSize): Resource<Unit>
}
```

Implemented in the data layer over `app_setting`; wired in `contentModule()` (Koin). Reuses the
Phase 0 `Resource`/`AppError` types.

## 3. Derived read projections (data layer)

Totals are **derived, not stored** (Assumptions). Added as read-only queries over existing
columns — no schema change to content tables.

### 3.1 `MatnSummary` (library card projection)

```
data class MatnSummary(
    val matn: Matn,
    val verseCount: Int,        // COUNT(verse WHERE matn_id = matn.id)
    val totalDurationMs: Long,  // SUM(verse.duration_ms WHERE matn_id = matn.id)
)
```

- Backs each Home library card (cover/title/author/verseCount/duration, FR-002).
- Produced by the data layer via aggregate queries (`SELECT COUNT(*)`, `SELECT SUM(duration_ms)`
  grouped by `matn_id`) joined onto `observeLibrary()`.
- The details header reuses the same two derived totals (FR-005).

New `Content.sq` queries (additive): `countVersesByMatn`, `sumVerseDurationByMatn` (or a single
grouped `selectLibrarySummaries`). Exposed through an extended `MatnRepository`
(e.g. `observeLibrarySummaries(): Flow<List<MatnSummary>>`) so ViewModels never run SQL.

### 3.2 Duration formatting

Total and per-verse `durationMs` are formatted for display (e.g. `mm:ss` / `h m`) by a shared
presentation-layer formatter util (DRY, Principle III). Formatting is a UI concern; the domain
carries raw milliseconds.

## 4. UI-state models (presentation layer, immutable)

Each screen exposes exactly one immutable state object via `StateFlow` (Principle II). All are
pure `data class`/sealed types with no framework/platform types.

### 4.1 Home / library

```
data class HomeUiState(
    val isLoading: Boolean = true,
    val items: List<MatnSummary> = emptyList(),
    val isEmpty: Boolean = false,      // store has zero متون → empty state (FR-004)
    val error: AppError? = null,
)
```

- `isEmpty` true → render the localized empty state, not a blank/error screen (SC-008).
- Tap on an item → navigation intent to `matn/{id}` (FR-003).

### 4.2 Matn details / reading

```
data class MatnDetailsUiState(
    val isLoading: Boolean = true,
    val header: MatnHeader? = null,          // cover, title, author, description, totals (FR-005)
    val verses: List<VerseRow> = emptyList(),
    val chapters: List<ChapterRow> = emptyList(),  // empty for SIMPLE متون (FR-015)
    val showTableOfContents: Boolean = false,      // true only for STRUCTURED (FR-013)
    val fontSize: ReadingFontSize = ReadingFontSize.MEDIUM,  // live-applied (FR-016)
    val error: AppError? = null,
)

data class MatnHeader(
    val coverImageRef: String?, val title: String, val author: String,
    val description: String, val verseCount: Int, val totalDurationMs: Long,
)
data class VerseRow(
    val id: String, val displayNumber: Int, val arabicText: String,
    val durationMs: Long, val chapterId: String?,
)
data class ChapterRow(
    val id: String, val title: String, val order: Int,
    val firstVerseDisplayNumber: Int,   // scroll target for TOC selection (FR-014)
)
```

- `showTableOfContents = (structureKind == STRUCTURED && chapters.isNotEmpty())`.
- Selecting a `ChapterRow` scrolls the list to `firstVerseDisplayNumber` (FR-014); computed as
  the minimum `displayNumber` among that chapter's verses.
- `fontSize` flows from `ReadingPreferencesRepository`; changing it updates this state and the
  rendered verse text immediately, and persists (FR-016/FR-017).
- `arabicText` is passed through **verbatim** from the store — no normalization, trimming, or
  transformation anywhere in the presentation path (FR-008/SC-002).

## 5. Relationships & invariants (Phase 1 view)

```
MatnSummary ──1:1── Matn
Matn ──1:N── Verse            (ordered by displayNumber, matn-global)
Matn ──1:N── Chapter          (STRUCTURED only; ordered by order)
Chapter ──1:N── Verse         (contiguous displayNumber slice; nullable link for SIMPLE)
app_setting['reading_font_size'] ── global, independent of any matn/verse
```

- **Reading order**: verses always ordered by matn-global `displayNumber` (never row/insertion
  order) — Phase 0 guarantee upheld (FR-006/SC-002).
- **TOC derivation**: chapters ordered by `order`; each chapter's scroll target is its first
  verse by `displayNumber`. Simple متون expose no chapters and no TOC (FR-015).
- **Offline**: every field above resolves from the local SQLDelight store; zero network
  (FR-018/SC-006).
- **Preference isolation**: the font-size setting is global UI state, decoupled from content
  and from later "Continue Learning" learning state (Assumptions).

## 6. What Phase 1 does NOT add

No audio/playback state, no active-verse or position tracking, no progress/memorized state, no
bookmarks/notes/search indexes, no per-matn preferences — all deferred by FR-020 to later
phases. The content schema (`matn`/`chapter`/`verse`/`audio_asset`) is unchanged; the only DB
addition is the `app_setting` key/value table plus read-only aggregate queries.
