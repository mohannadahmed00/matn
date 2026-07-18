# Contract: Seed Content Format

Seed content is the input to `ContentSeedLoader.load(...)`. It is JSON (parsed via
kotlinx-serialization into data-layer DTOs) carrying **content-authored UUIDs** for every
entity. The store trusts and persists these UUIDs as-is (FR-002). This contract defines the
shape and the integrity rules the loader enforces atomically.

---

## JSON shape

```jsonc
{
  "id": "b3f1e2a4-0000-4000-8000-000000000001",   // matn UUID (authored)
  "title": "الأجرومية",
  "author": "ابن آجُرُّوم",
  "description": "متن مختصر في علم النحو",
  "coverImageRef": "covers/ajurrumiyya.jpg",        // optional, may be null/absent
  "structureKind": "STRUCTURED",                     // "SIMPLE" | "STRUCTURED"
  "defaultReciterId": "9c0a...-reciter-uuid",        // single reciter for v1

  "chapters": [                                       // [] or omitted for SIMPLE متون
    {
      "id": "c1...-uuid",
      "title": "باب الكلام",
      "order": 1
    }
  ],

  "verses": [
    {
      "id": "v1...-uuid",
      "chapterId": "c1...-uuid",                      // null/absent for SIMPLE متون
      "displayNumber": 1,                             // matn-global 1..N, unique in matn
      "arabicText": "الكَلامُ هُوَ اللَّفظُ المُرَكَّبُ المُفيدُ بِالوَضعِ",
      "durationMs": 8200,
      "audio": {                                       // exactly one per verse (v1 reciter)
        "id": "a1...-uuid",
        "fileRef": "ajurrumiyya_verse_001.mp3",
        "durationMs": 8200
      }
    }
  ]
}
```

## Field rules

| Field | Required | Rule |
|-------|----------|------|
| `id` (all entities) | Yes | Non-blank, well-formed UUID; persisted verbatim as PK. |
| `structureKind` | Yes | `SIMPLE` ⇒ `chapters` empty/omitted and every verse `chapterId` null. `STRUCTURED` ⇒ ≥1 chapter and every verse `chapterId` set (V6/V7). |
| `chapters[].order` | Yes (structured) | Unique within matn; chapter display order. |
| `verses[].displayNumber` | Yes | Matn-global, unique within matn (V2). Defines reading order (FR-003). |
| `verses[].arabicText` | Yes | Stored byte-for-byte, no normalization (FR-007). |
| `verses[].chapterId` | Structured only | Must reference a chapter in this same matn (V6). |
| `verses[].audio` | Yes | Exactly one asset; `fileRef` matn-relative and unique **within the matn** (V4/V5). |
| `verses[].audio.fileRef` | Yes | Matn-relative file name following the v1 convention (below); not an absolute path. |
| `defaultReciterId` | Yes | The v1 reciter id applied to each verse's audio asset. |

## v1 audio packaging & naming convention

Audio is **bundled per matn** in v1. Each matn owns an audio directory; every verse maps to one
micro-file named by that verse's **index within its matn** — the matn-global `displayNumber`,
zero-padded to a fixed width — e.g. `01.mp3`, `02.mp3`, … `NN.mp3`.

- `audio.fileRef` carries only the **matn-relative** reference (e.g. `"01.mp3"`), never an
  absolute or device path. The full location is composed downstream (playback Phase 2, download
  Phase 7) as `<audioRoot>/<matn audio dir>/<fileRef>`.
- `fileRef` uniqueness (V5) is scoped **within the matn** (per-matn folders), not globally.
- The numeric stem of `fileRef` is expected to equal the verse's `displayNumber`. This
  index↔file coupling is the v1 authoring contract; enforcing it as a hard integrity rule is
  deferred to the Phase 7 asset-packaging spec so early seed content can use descriptive names
  during development without failing the loader.

## Integrity rules (atomic-reject — FR-019 / SC-008)

Validation runs **before any write**; the load then commits in a single transaction. Any
failure ⇒ nothing persisted for the matn ⇒ `Failure(ContentIntegrityError.Aggregate([...]))`
listing every problem. Mapped rules (see `data-model.md` V1–V8):

1. Malformed/blank UUID anywhere → `InvalidId`.
2. Duplicate `displayNumber` within the matn → `DuplicateDisplayNumber`.
3. A verse missing its `audio` object → `MissingAudio`.
4. Two verses sharing an audio `fileRef` → `DuplicateAudioRef`.
5. `chapterId` referencing a non-existent / other-matn chapter → `OrphanChapterRef`.
6. `structureKind` inconsistent with chapters/verse links → `StructureMismatch`.

## Reload behavior (FR-009 / SC-007)

Re-submitting a payload with the same matn `id` upserts every row by its authored id
(`ON CONFLICT(id) DO UPDATE`) and removes this-matn rows whose ids are absent from the new
payload — all inside the same atomic, validated transaction. No duplicate matn/chapter/verse
records; identities stay stable across reloads.

## Test fixtures

`commonTest/.../fixtures/` provides at minimum:
- `simple_matn.json` — flat, no chapters, a handful of diacritic-heavy verses.
- `structured_matn.json` — multiple chapters, verses grouped and globally numbered.
- `invalid_duplicate_order.json` — two verses with the same `displayNumber` (expects reject).
- `invalid_missing_audio.json` — a verse with no `audio` (expects reject).

These fixtures drive the acceptance-scenario tests listed in `contracts/repositories.md`.
