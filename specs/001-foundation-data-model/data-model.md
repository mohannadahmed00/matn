# Phase 0 Data Model: Foundation & Data Model

Defines the domain entities, their persisted schema, relationships, validation rules, and the
mapping between them. All domain types live in `commonMain` as pure Kotlin; the schema is a
SQLDelight `.sq` file. Identity is **content-authored UUID** (string), used as the primary key
for every entity (see research Decisions 5–8).

---

## Entity overview

| Entity | Role | Identity | Present for simple متون? |
|--------|------|----------|--------------------------|
| **Matn** | Top-level memorizable text | authored UUID | Yes |
| **Chapter / Section** | Ordered grouping of verses | authored UUID | No (structured only) |
| **Verse** | Atomic unit of text & ordering | authored UUID | Yes |
| **Audio Asset** | Per-verse, per-reciter audio mapping | authored UUID | Yes |

Relationships:

```text
Matn 1 ──< Chapter        (0..N; zero for a simple matn)
Matn 1 ──< Verse          (1..N, ordered by display_number 1..N)
Chapter 1 ──< Verse       (0..N; a verse's owning chapter is nullable)
Verse 1 ──< AudioAsset    (1 per reciter; exactly 1 for the single default reciter in v1)
```

---

## Domain entities (commonMain Kotlin)

### `StructureKind` (enum)
- `SIMPLE` — flat ordered list of verses, no chapters.
- `STRUCTURED` — verses grouped into ordered chapters.

### `Matn`
| Field | Type | Notes |
|-------|------|-------|
| `id` | `String` (UUID) | Content-authored, stable, primary identity (FR-002). |
| `title` | `String` | Display title (Arabic). |
| `author` | `String` | Author/attribution. |
| `description` | `String` | Descriptive text. |
| `coverImageRef` | `String?` | Optional cover image reference. |
| `structureKind` | `StructureKind` | `SIMPLE` or `STRUCTURED` (FR-010). |

> Derivable totals (verse count, estimated total duration) are **computed on read**, not stored
> as authoritative columns, to avoid drift. Download status / file size are future concerns and
> deliberately absent (spec Key Entities; FR-016).

### `Chapter`
| Field | Type | Notes |
|-------|------|-------|
| `id` | `String` (UUID) | Content-authored, stable identity. |
| `matnId` | `String` (UUID) | Owning matn (FK). |
| `title` | `String` | Chapter/section title. |
| `order` | `Int` | Chapter display order within the matn (1..M). |

### `Verse`
| Field | Type | Notes |
|-------|------|-------|
| `id` | `String` (UUID) | Content-authored, stable identity (FR-002). |
| `matnId` | `String` (UUID) | Owning matn (FK). |
| `chapterId` | `String?` (UUID) | Owning chapter; `null` for simple متون (FR-012). |
| `displayNumber` | `Int` | **Matn-global** sequential number 1..N; single source of truth for reading order (FR-003). Unique within matn. |
| `arabicText` | `String` | Arabic text **with all diacritics**, stored verbatim (FR-007). |
| `durationMs` | `Long` | Individual audio duration in milliseconds (metadata; playback is Phase 2). |

### `AudioAsset`
| Field | Type | Notes |
|-------|------|-------|
| `id` | `String` (UUID) | Content-authored, stable identity. |
| `verseId` | `String` (UUID) | The verse this asset belongs to (FK). |
| `reciterId` | `String` (UUID) | Reciter; a single default reciter in v1 (FR-015). |
| `fileRef` | `String` | Per-verse micro-file reference, e.g. `matn_01_verse_005.mp3` (FR-013). |
| `durationMs` | `Long` | Asset duration in milliseconds. |

---

## SQLDelight schema (conceptual)

`shared/src/commonMain/sqldelight/com/giraffe/matn/db/Content.sq`

```sql
CREATE TABLE matn (
    id             TEXT NOT NULL PRIMARY KEY,      -- content-authored UUID
    title          TEXT NOT NULL,
    author         TEXT NOT NULL,
    description    TEXT NOT NULL,
    cover_image_ref TEXT,
    structure_kind TEXT NOT NULL                    -- 'SIMPLE' | 'STRUCTURED'
);

CREATE TABLE chapter (
    id             TEXT NOT NULL PRIMARY KEY,
    matn_id        TEXT NOT NULL,
    title          TEXT NOT NULL,
    display_order  INTEGER NOT NULL,
    FOREIGN KEY (matn_id) REFERENCES matn(id),
    UNIQUE (matn_id, display_order)
);

CREATE TABLE verse (
    id             TEXT NOT NULL PRIMARY KEY,
    matn_id        TEXT NOT NULL,
    chapter_id     TEXT,                            -- NULL for simple متون
    display_number INTEGER NOT NULL,                -- matn-global 1..N
    arabic_text    TEXT NOT NULL,
    duration_ms    INTEGER NOT NULL,
    FOREIGN KEY (matn_id) REFERENCES matn(id),
    FOREIGN KEY (chapter_id) REFERENCES chapter(id),
    UNIQUE (matn_id, display_number)                -- DB backstop for FR-019 order collisions
);

CREATE TABLE audio_asset (
    id             TEXT NOT NULL PRIMARY KEY,
    verse_id       TEXT NOT NULL,
    reciter_id     TEXT NOT NULL,
    file_ref       TEXT NOT NULL,
    duration_ms    INTEGER NOT NULL,
    FOREIGN KEY (verse_id) REFERENCES verse(id),
    UNIQUE (verse_id, reciter_id)                   -- exactly one asset per verse per reciter (FR-013/FR-015)
);

CREATE INDEX verse_by_matn_order ON verse(matn_id, display_number);
CREATE INDEX verse_by_chapter    ON verse(chapter_id);
CREATE INDEX audio_by_verse      ON audio_asset(verse_id);
```

Representative queries (full set in `contracts/repositories.md`):
- `selectMatnById`, `selectAllMatn`
- `selectVersesByMatn` — `WHERE matn_id = ? ORDER BY display_number`
- `selectChaptersByMatn` — `WHERE matn_id = ? ORDER BY display_order`
- `selectVersesByChapter` — `WHERE chapter_id = ? ORDER BY display_number`
- `selectAudioForVerse` — `WHERE verse_id = ? AND reciter_id = ?`
- `upsertMatn` / `upsertChapter` / `upsertVerse` / `upsertAudioAsset` — `ON CONFLICT(id) DO UPDATE` (FR-009)
- `deleteMatnCascade` — used only internally by the loader to guarantee no partial state

---

## Validation rules

Enforced by the `SeedContentLoader` **before** any write (pre-validation, aggregated errors),
with DB constraints as a backstop. On any failure the entire matn load is aborted atomically —
nothing is persisted (FR-019/SC-008).

| # | Rule | Source | Failure surfaced as |
|---|------|--------|---------------------|
| V1 | Every entity has a non-blank, well-formed UUID | FR-002 | `ContentIntegrityError.InvalidId` |
| V2 | Verse `displayNumber` values are unique within the matn | FR-003, edge case | `ContentIntegrityError.DuplicateDisplayNumber(matnId, number)` |
| V3 | Verse `displayNumber` set forms a coherent 1..N sequence (no gaps expected for a full matn) | FR-003/FR-006 | `ContentIntegrityError.NonContiguousSequence` (reported; see note) |
| V4 | Every verse has exactly one audio asset (for the default reciter) | FR-013/FR-019 | `ContentIntegrityError.MissingAudio(verseId)` |
| V5 | No two verses share an audio `fileRef` | FR-013/SC-004 | `ContentIntegrityError.DuplicateAudioRef(fileRef)` |
| V6 | `chapterId`, when present, references a chapter of the same matn | FR-011 | `ContentIntegrityError.OrphanChapterRef(verseId)` |
| V7 | Structured matn has ≥1 chapter; simple matn has 0 chapters | FR-010/FR-012 | `ContentIntegrityError.StructureMismatch` |
| V8 | Arabic text preserved verbatim (no normalization applied anywhere) | FR-007 | (guaranteed by storing raw `String`; verified by round-trip test) |

> **Note on V3**: gap-tolerance is a policy choice. The spec requires uniqueness and a stable
> order; a strict contiguous 1..N check is included as reportable but may be relaxed to
> "unique + monotonic" during tasks if seed content legitimately uses sparse numbering. This is
> the only soft point and does not affect any hard requirement.

---

## Reload / upsert semantics (FR-009)

- Sameness is keyed on the content-authored `id`. Reloading a matn issues `upsert` (`ON
  CONFLICT(id) DO UPDATE`) for the matn and each chapter/verse/asset, so identities remain
  stable and no duplicate rows are created (SC-007).
- To guarantee **no stale children** on reload (e.g., a verse removed from the source), the
  loader performs the reload inside one transaction: it upserts current rows and removes rows
  for that matn whose ids are absent from the incoming payload. All within the same atomic unit
  as validation, preserving atomic-reject on any integrity failure.

---

## Future-proofing notes (FR-016)

- `audio_asset.reciter_id` is a first-class column now → alternate reciters need **no schema
  change** (FR-015).
- UUID string PKs are globally stable → safe for future cross-device sync and remote catalog.
- Later-phase entities (bookmarks, notes, progress, "Continue Learning" state) are **not**
  created here but are unblocked: they will reference `verse.id` / `matn.id` UUIDs directly.
- No column encodes download state, file size, or ordering assumptions that would preclude the
  online/selective-download roadmap.
