# Contract: Firestore Schema

**Feature**: `specs/011-teacher-authoring-upload` | Satisfies FR-007, FR-009, FR-010, FR-011, FR-012, FR-013

Domain types are in [../data-model.md](../data-model.md); this is the wire shape they map to.

---

## 1. Collections

| Path | Purpose | Written by | Read by |
|------|---------|-----------|---------|
| `matns/{matnId}` | One document per matn — metadata, chapters, verses | Teacher only | Teacher (all), anonymous (published only) |
| `teachers/{uid}` | Authorization marker; existence = may write (research D14) | Console, by hand | Security rules only |

`{matnId}` is the domain UUID (FR-008), so the document path is stable across unpublish/republish.

---

## 2. `matns/{matnId}` document

Field names match `SeedMatn`'s where a counterpart exists (FR-009), so Phase 13's sync is a
projection.

### 2.1 Top-level fields — the catalog overview (FR-012)

Everything a list row needs sits at the top level, so a reader can fetch it with a field mask and
never transfer verse text.

| Field | Firestore type | Domain source | Notes |
|-------|----------------|---------------|-------|
| `id` | `stringValue` | `MatnDraft.id` | Duplicated inside the document as well as in the path, so a masked read carries it |
| `title` | `stringValue` | `MatnDraft.title` | Required, non-blank |
| `author` | `stringValue` | `MatnDraft.author` | Required, non-blank |
| `description` | `stringValue` | `MatnDraft.description` | May be empty |
| `coverImageRef` | `stringValue` \| `nullValue` | `MatnDraft.coverImageRef` | Storage object path, not a download URL |
| `structureKind` | `stringValue` | `MatnDraft.structureKind.name` | `"SIMPLE"` \| `"STRUCTURED"` — the exact strings `ContentSeedLoaderImpl` compares against |
| `defaultReciterId` | `stringValue` | `MatnDraft.defaultReciterId` | The FR-007a constant; never empty |
| `published` | `booleanValue` | `publicationState == PUBLISHED` | **The visibility gate (FR-039).** Stored as a boolean, not a string, because the rules test it |
| `audioCompleteness` | `stringValue` | derived | `"NONE"` \| `"PARTIAL"` \| `"COMPLETE"`. Always `"NONE"` in Phase 11 |
| `verseCount` | `integerValue` | `verses.size` | Derived, so a masked read needs no verse array |
| `declaredSizeBytes` | `integerValue` | derived | Text-only this phase |
| `createdAt` | `timestampValue` | `MatnDraft.createdAt` | Set once |
| `updatedAt` | `timestampValue` | `MatnDraft.updatedAt` | Set on every write |

`integerValue` is a JSON **string** on the wire (`"integerValue": "500"`) — a Firestore REST quirk the
codec (research D10) handles and unit-tests.

### 2.2 `chapters` — `arrayValue` of `mapValue`

| Field | Type | Notes |
|-------|------|-------|
| `id` | `stringValue` | UUID |
| `title` | `stringValue` | Non-blank |
| `order` | `integerValue` | Distinct within the matn |

Empty array for a `SIMPLE` matn.

### 2.3 `verses` — `arrayValue` of `mapValue`

| Field | Type | Notes |
|-------|------|-------|
| `id` | `stringValue` | UUID |
| `chapterId` | `stringValue` \| `nullValue` | Non-null iff `STRUCTURED` |
| `displayNumber` | `integerValue` | `1..n`, no gaps |
| `arabicText` | `stringValue` | Verbatim, diacritics preserved |
| `durationMs` | `integerValue` | `0` in Phase 11 |
| `audio` | `mapValue` \| `nullValue` | **Always `nullValue` in Phase 11** (FR-045). Shape reserved: `{ id, fileRef, durationMs }` |

Array order is the authoritative verse order; `displayNumber` mirrors it and is what the app reads.

---

## 3. `teachers/{uid}` document

Existence is the whole contract. Fields are informational only and no rule reads them.

| Field | Type | Notes |
|-------|------|-------|
| `displayName` | `stringValue` | Convenience for a human browsing the console |
| `createdAt` | `timestampValue` | |

Provisioning is manual (Assumptions, research D14). A second teacher later is a second document —
which is how FR-013 stays true without a schema change.

---

## 4. Reads

### 4.1 Overview list (FR-035, and Phase 13's catalog sync)

```http
GET /v1/projects/{projectId}/databases/(default)/documents/matns
      ?mask.fieldPaths=id&mask.fieldPaths=title&mask.fieldPaths=author
      &mask.fieldPaths=description&mask.fieldPaths=coverImageRef
      &mask.fieldPaths=published&mask.fieldPaths=audioCompleteness
      &mask.fieldPaths=verseCount&mask.fieldPaths=declaredSizeBytes
      &mask.fieldPaths=updatedAt
      &pageSize=100
```

The mask is what makes FR-012 real: verse arrays are never transferred for a listing.

**Anonymous readers must additionally constrain the query to `published == true`** via `runQuery`
with a `fieldFilter`. A rule that gates on `resource.data.published` cannot be satisfied by an
unconstrained collection listing — Firestore evaluates rules against the query, not the results. This
is a Phase 13 concern but is specified here because it is a property of the schema, and getting it
wrong looks like "the rules are broken" rather than "the query is wrong".

### 4.2 Full document (opening a matn for editing, FR-036)

```http
GET /v1/projects/{projectId}/databases/(default)/documents/matns/{matnId}
```

The response's `updateTime` is retained as `MatnDraft.remoteUpdateTime` — the concurrency token
(research D4).

---

## 5. Writes

Every write is a single-document operation, which is atomic in Firestore with no transaction. That is
what discharges FR-033.

### 5.1 Save (create or update)

```http
PATCH /v1/projects/{projectId}/databases/(default)/documents/matns/{matnId}
      ?currentDocument.updateTime={remoteUpdateTime}      # omitted only on first create
      &currentDocument.exists=false                       # first create only
```

- Full document body — no partial-field updates, so the document is never a mixture of two edits.
- A stale `updateTime` returns `FAILED_PRECONDITION` → `RemoteError.Conflict` (FR-043).
- Publish and unpublish are the same call with `published` flipped; they are not separate endpoints,
  so they inherit the same atomicity and the same conflict check.

### 5.2 Ordering against the cover image (research D3)

Cover image uploads to Storage **first**, then the document is patched with its path. A failure
between the two leaves an unreferenced Storage object — inert, and overwritten by the next attempt at
the same deterministic path. The reverse order would leave a document pointing at nothing.

---

## 6. Indexes

| Field | Setting | Why |
|-------|---------|-----|
| `published` | Indexed (default) | The anonymous catalog query filters on it |
| `title`, `author`, `updatedAt` | Indexed (default) | Listing and ordering |
| `verses` | **Single-field index exemption — disable indexing** | 500 verses × ~6 sub-fields would write thousands of index entries per document for data nothing queries. Exempting it cuts write cost and document overhead |
| `chapters` | **Single-field index exemption — disable indexing** | Same reasoning, smaller scale |

No composite index is required by this phase. Phase 13's catalog query
(`published == true`, ordered by `updatedAt`) may need one; it is Phase 13's to declare.

---

## 7. Size budget against the 1 MiB document limit

| Component | 500-verse estimate |
|-----------|--------------------|
| Verse text (~120 Arabic chars ≈ 240 B UTF-8) | ~120 KB |
| Verse ids (36 B) + `chapterId` (36 B) + numeric fields | ~45 KB |
| Firestore per-field type-wrapper overhead | ~35 KB |
| Chapters, top-level metadata | ~2 KB |
| **Phase 11 total** | **~200 KB** |
| Phase 12 adds `audio` map + duration per verse | ~+60 KB |
| **Projected Phase 12 total** | **~260 KB** |

Roughly a quarter of the limit with audio included. `ValidateMatn` still computes the projected
document size and refuses to publish above ~900 KB, so an implausibly large matn fails with a clear
message rather than a REST rejection (research D2).

---

## 8. What is deliberately absent

| Absent | Why |
|--------|-----|
| A schema-version field | Flagged as a Deferred item in the clarify session. Phase 12 adds audio fields and Phase 13 reads them; the additive-only shape above means an old reader ignores new fields. Worth adding before a *breaking* change, which this is not |
| `packId` | A Phase 8 delivery slug. Phase 13 derives it from the Storage path prefix; the authoring tool never invents delivery values |
| `isStarter` | The starter path is deleted outright in Phase 13 |
| Ownership / `authorUid` | Single-teacher scope. Adding it later is additive and does not invalidate existing documents (FR-013) |
