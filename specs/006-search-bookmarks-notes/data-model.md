# Data Model: Search, Bookmarks & Notes (Phase 6)

**Date**: 2026-07-24 | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

## Persisted entities (SQLDelight, schema v1 → v2 via `migrations/1.sqm`)

### `bookmark` *(new)*

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | TEXT | PRIMARY KEY | UUID, minted at creation (Constitution VI) |
| `verse_id` | TEXT | NOT NULL, UNIQUE, FK → `verse(id)` ON DELETE CASCADE | At most one bookmark per verse (spec Key Entities) |
| `created_at` | INTEGER | NOT NULL | Epoch millis from injected clock (research D4); orders the global list |

### `note` *(new)*

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | TEXT | PRIMARY KEY | UUID, minted at creation; **stable across edits** (edits update `text`/`updated_at` only) |
| `verse_id` | TEXT | NOT NULL, UNIQUE, FK → `verse(id)` ON DELETE CASCADE | At most one note per verse |
| `text` | TEXT | NOT NULL, never empty | FR-019: empty save is a no-op at the domain layer; the row never holds "" |
| `updated_at` | INTEGER | NOT NULL | Epoch millis; set on create and every edit; orders the global list |

**Lifecycle rules**

- Bookmark: toggle-on inserts (fresh UUID), toggle-off deletes the row. Re-bookmarking mints a
  new identity — history is not retained.
- Note: create inserts; edit updates `text` + `updated_at` in place (same `id`); delete removes
  the row. Saving empty text over an existing note is **not** a delete (FR-019) — the editor
  surfaces an explicit delete action; domain rejects empty saves.
- Both survive audio-asset removal by construction: no relationship to `audio_asset`
  (FR-014/FR-018). CASCADE only fires if the verse row itself (text content) is deleted.
- Rapid toggling (spec edge case): `UNIQUE(verse_id)` makes duplicate bookmarks impossible at
  the schema level; repository serializes toggle as read-then-write in one transaction.

**Indexes**: `UNIQUE(verse_id)` on each table doubles as the per-verse lookup index. Global
lists read all rows (tens-scale) joined to `verse`/`matn` — no additional index needed.

**Migration `1.sqm`**: `CREATE TABLE bookmark …; CREATE TABLE note …;` — purely additive; v1
data untouched (guarded by `MigrationV2Test`).

## Domain models (`commonMain/domain/model`)

| Type | Shape | Purpose |
|------|-------|---------|
| `Bookmark` | `(id: String, verseId: String, createdAtMs: Long)` | Row mirror |
| `Note` | `(id: String, verseId: String, text: String, updatedAtMs: Long)` | Row mirror |
| `VerseAnnotations` | `(verseId: String, isBookmarked: Boolean, hasNote: Boolean)` | Per-verse indicator state for the reading carousel (FR-011/FR-016) |
| `AnnotatedVerseRef` | `(matnId: String, matnTitle: String, verseId: String, verseNumber: Int, verseText: String)` | Shared context block for global bookmark/note list rows (FR-012/FR-017) |
| `BookmarkEntry` | `(bookmark: Bookmark, ref: AnnotatedVerseRef)` | Global bookmarks list item |
| `NoteEntry` | `(note: Note, ref: AnnotatedVerseRef)` | Global notes list item; UI truncates preview, model carries full text |
| `SearchResult` | sealed interface | See below |

### `SearchResult` (sealed, transient — never persisted)

```
SearchResult
├── VerseMatch(ref: AnnotatedVerseRef)                                  → navigate matn+focusVerseId
├── ChapterMatch(matnId, matnTitle, chapterId, chapterTitle,
│                firstVerseId: String?)                                 → navigate matn+focusVerseId (null firstVerseId → plain matn route)
└── MatnMatch(matnId, matnTitle)                                        → navigate matn details
```

Result set assembly (FR-006): grouped by matn in library order (existing `ORDER BY title`),
within a matn: title match first, then chapter matches in `display_order`, then verse matches in
`display_number`. Deterministic for identical query + content.

## Normalization (domain logic, not data)

`ArabicNormalizer.normalize(s: String): String` — pure; applied to both query and corpus text.
Character rules are the FR-002/FR-003 set (see contracts/normalization-contract.md for the
authoritative table and test vectors). Normalized text is **never persisted** (research D2).

## Relationships (unchanged tables shown for context)

```
matn 1─* chapter 1─* verse 1─0..1 bookmark
                       │
                       └─0..1 note
verse 1─* audio_asset          (no link to bookmark/note — annotations audio-independent)
```
