# Phase 1 — Data Model: Teacher Authoring Tool

**Feature**: `specs/011-teacher-authoring-upload` | **Date**: 2026-07-26

Domain models live in `:shared/commonMain/kotlin/com/giraffe/matn/domain/`. All of them are plain
Kotlin — no framework, no platform type, no Compose (Principle I, IV). The wire shape they map to is
in [contracts/firestore-schema.md](./contracts/firestore-schema.md).

---

## 1. `MatnDraft` — the authoring aggregate

`domain/catalog/MatnDraft.kt`. The unit the teacher edits, validates, saves, and publishes. Owns its
chapters and verses; nothing else may hold them.

| Field | Type | Notes |
|-------|------|-------|
| `id` | `String` | UUID, generated once at creation, never reassigned — including across unpublish/republish (FR-008) |
| `title` | `String` | Required, non-blank (FR-018) |
| `author` | `String` | Required, non-blank (FR-018) |
| `description` | `String` | May be empty |
| `coverImageRef` | `String?` | Storage path, `null` until an image is attached (FR-015) |
| `structureKind` | `StructureKind` | `SIMPLE` \| `STRUCTURED`. Required (FR-018) |
| `defaultReciterId` | `String` | Constant, assigned by the tool, never empty, never edited (FR-007a) |
| `chapters` | `List<DraftChapter>` | Empty for `SIMPLE` |
| `verses` | `List<DraftVerse>` | Ordered; list position **is** the order |
| `publicationState` | `PublicationState` | `DRAFT` \| `PUBLISHED` (FR-010) |
| `createdAt` | `Long` | Epoch millis, set once (FR-010) |
| `updatedAt` | `Long` | Epoch millis, set on every write (FR-010) |
| `remoteUpdateTime` | `String?` | Server `updateTime` from the last read; the concurrency token (FR-043). `null` for a never-saved draft |

**Derived, never stored as an editable field**:

| Property | Derivation |
|----------|------------|
| `verseCount` | `verses.size` (FR-012) |
| `audioCompleteness` | From how many verses carry audio — see §4 (FR-011) |
| `declaredSizeBytes` | Sum of stored content bytes. Text-only this phase; grows with audio in Phase 12 |

**Invariants** (enforced by `ContentIntegrityValidator`, §6):
- `id`, every `chapters[].id`, and every `verses[].id` are non-blank and globally distinct.
- `verses[].displayNumber` values are distinct within the matn.
- `chapters[].order` values are distinct within the matn.
- `STRUCTURED` ⇒ at least one chapter, and every verse has a `chapterId`.
- `SIMPLE` ⇒ no chapters, and no verse has a `chapterId`.
- Every non-null `verses[].chapterId` names an existing chapter.
- `PUBLISHED` ⇒ `verses` is non-empty (FR-030).

---

## 2. `DraftChapter`

`domain/catalog/MatnDraft.kt`.

| Field | Type | Notes |
|-------|------|-------|
| `id` | `String` | UUID, stable (FR-008) |
| `title` | `String` | Required, non-blank |
| `order` | `Int` | Distinct within the matn (FR-017) |

---

## 3. `DraftVerse`

`domain/catalog/MatnDraft.kt`.

| Field | Type | Notes |
|-------|------|-------|
| `id` | `String` | UUID, stable (FR-008) |
| `chapterId` | `String?` | Non-null iff the matn is `STRUCTURED` (FR-022) |
| `displayNumber` | `Int` | Derived from list position, never typed by the teacher (FR-020) |
| `arabicText` | `String` | Stored verbatim, diacritics and punctuation intact (FR-021, FR-023a) |
| `audio` | `DraftAudio?` | **Always `null` in Phase 11** (FR-045). Present so Phase 12 adds no schema change |
| `durationMs` | `Long` | `0` in Phase 11; Phase 12's concern |

`DraftAudio` is declared now (`id`, `fileRef`, `durationMs`) and left unwritten, so the document
shape Phase 13 reads is settled before there are two writers.

---

## 4. `AudioCompleteness` — derived state (FR-011)

`domain/catalog/AudioCompleteness.kt`.

| Value | Condition |
|-------|-----------|
| `NONE` | No verse has audio. **Always the value in Phase 11** |
| `PARTIAL` | Some but not all verses have audio |
| `COMPLETE` | Every verse has audio, and there is at least one verse |

Computed from `verses`, never set by hand, so it cannot drift from reality. Written to the document on
every save so a reader can filter on it without reading verses — which is how Phase 13 avoids ever
offering a student a silent matn.

---

## 5. `PublicationState` and its transitions (FR-010, FR-032, FR-034, FR-038)

`domain/catalog/PublicationState.kt` — `DRAFT` | `PUBLISHED`.

```text
        create                publish (validated, non-empty)
  (none) ──────► DRAFT ──────────────────────────────────────► PUBLISHED
                   ▲                                              │
                   │                  unpublish                   │
                   └──────────────────────────────────────────────┘

  DRAFT     : autosaved (D12), invisible to anonymous readers
  PUBLISHED : explicit saves only (FR-031b), readable by anyone
```

Rules on the transitions:
- **create → DRAFT**: requires title, author, structure kind (FR-018).
- **DRAFT → PUBLISHED**: requires zero *blocking* validation problems (FR-029) and ≥1 verse (FR-030).
  Audio problems are deferred and do not block (FR-027).
- **PUBLISHED → DRAFT** (unpublish): always permitted. Identifiers are preserved, so republishing
  orphans nothing (FR-038).
- **PUBLISHED edit-in-place**: stays `PUBLISHED`; the write is atomic, so no reader sees a partial
  correction (FR-037, FR-033). Saving a published matn with zero verses is refused (FR-030).
- There is no delete transition — withdrawal is unpublish (Assumptions).

---

## 6. `ContentIntegrityValidator` and `ValidationReport`

`domain/catalog/ContentIntegrityValidator.kt`, `domain/catalog/ValidationReport.kt`.

Extracted from `ContentSeedLoaderImpl.validate()` per research D8. Pure function:
`validate(draft: MatnDraft): ValidationReport`. Returns the existing
`domain/error/ContentIntegrityError` types — no new error vocabulary.

```kotlin
data class ValidationReport(
    val blocking: List<ContentIntegrityError>,   // refuse publish (FR-029)
    val deferred: List<ContentIntegrityError>,   // report as outstanding work (FR-027)
) {
    val canPublish: Boolean get() = blocking.isEmpty()
}
```

Rule-to-bucket assignment is in
[contracts/validation-contract.md](./contracts/validation-contract.md); the short version is that
`MissingAudio` and `DuplicateAudioRef` are deferred in this phase and everything else blocks.

---

## 7. `CatalogEntry` — the overview projection (FR-012, FR-035)

`domain/catalog/CatalogEntry.kt`. What a list row needs, and nothing more, so the catalog can be
listed without downloading verse text.

| Field | Type |
|-------|------|
| `id` | `String` |
| `title` | `String` |
| `author` | `String` |
| `description` | `String` |
| `coverImageRef` | `String?` |
| `verseCount` | `Int` |
| `declaredSizeBytes` | `Long` |
| `publicationState` | `PublicationState` |
| `audioCompleteness` | `AudioCompleteness` |
| `updatedAt` | `Long` |

Read with a Firestore field mask. This is the type Phase 13's catalog sync consumes — it is specified
here so the producer exercises it first.

---

## 8. `TeacherSession`

`domain/auth/TeacherSession.kt`.

| Field | Type | Notes |
|-------|------|-------|
| `uid` | `String` | Firebase user id; also the `teachers/{uid}` marker key (research D14) |
| `displayName` | `String` | Shown in the portal (FR-001 scenario 1) |
| `email` | `String` | |
| `idToken` | `String` | ~1 hour lifetime. **In memory only** |
| `refreshToken` | `String` | The only value persisted, via `SecretStore` (FR-003a, D7) |
| `idTokenExpiresAt` | `Long` | Epoch millis; drives proactive refresh (FR-003) |

---

## 9. Repository interfaces (domain)

All in `domain/`, implemented in `data/remote` + `data/repository`. Every method returns
`Resource<T>` per Principle III.

```kotlin
// domain/auth/TeacherAuthRepository.kt
interface TeacherAuthRepository {
    suspend fun signIn(email: String, password: String): Resource<TeacherSession>
    suspend fun restoreSession(): Resource<TeacherSession>       // from SecretStore (FR-003)
    suspend fun signOut(): Resource<Unit>                        // clears SecretStore (FR-004)
    fun observeSession(): Flow<TeacherSession?>
}

// domain/catalog/CatalogRepository.kt
interface CatalogRepository {
    fun observeAuthored(): Flow<List<CatalogEntry>>              // FR-035
    suspend fun load(matnId: String): Resource<MatnDraft>        // FR-036
    suspend fun save(draft: MatnDraft): Resource<MatnDraft>      // FR-031/033/043; returns new updateTime
    suspend fun publish(draft: MatnDraft): Resource<MatnDraft>   // FR-032
    suspend fun unpublish(matnId: String): Resource<MatnDraft>   // FR-038
    suspend fun uploadCover(matnId: String, bytes: ByteArray, ext: String): Resource<String>  // FR-014
}

// domain/secret/SecretStore.kt
interface SecretStore {
    suspend fun put(key: String, value: String): Resource<Unit>
    suspend fun get(key: String): Resource<String?>
    suspend fun clear(key: String): Resource<Unit>
    val isProtected: Boolean    // false ⇒ surface the fallback warning (FR-003a)
}
```

---

## 10. `RemoteError` (FR-005, FR-030, FR-032)

`domain/error/RemoteError.kt`, extending the existing `AppError`. Each case carries whether retrying
is worthwhile, which is what FR-005's messaging and FR-032's "report the failure as retryable or not"
need.

| Case | Cause | `retryable` |
|------|-------|-------------|
| `Network` | No connection, DNS, timeout | `true` |
| `Unauthorized` | Missing/expired/rejected credential | `false` — re-authenticate |
| `Forbidden` | Authenticated but rules refused | `false` |
| `Conflict` | `updateTime` precondition failed (D4) | `false` — reload and re-apply |
| `QuotaExceeded` | Storage or write quota | `false` |
| `Server` | 5xx | `true` |
| `Decode` | Response did not match the expected shape | `false` |

---

## 11. FR-009: field-for-field correspondence with `SeedMatn`

FR-009 requires the stored shape correspond one-to-one with what the app's ingestion path already
accepts, so Phase 13's sync is a projection, not a translation. **Verified** against
`shared/src/commonMain/kotlin/com/giraffe/matn/data/seed/SeedContent.kt`:

| `SeedMatn` field | Source in this phase | Note |
|------------------|----------------------|------|
| `id` | `MatnDraft.id` | Identical |
| `title` | `MatnDraft.title` | Identical |
| `author` | `MatnDraft.author` | Identical |
| `description` | `MatnDraft.description` | Identical |
| `coverImageRef` | `MatnDraft.coverImageRef` | Storage path instead of a bundled resource name |
| `structureKind` | `MatnDraft.structureKind.name` | Same `"SIMPLE"`/`"STRUCTURED"` strings the loader compares against |
| `defaultReciterId` | `MatnDraft.defaultReciterId` | The FR-007a constant |
| `chapters[]` | `DraftChapter` → `SeedChapter` | `id`/`title`/`order`, identical |
| `verses[]` | `DraftVerse` → `SeedVerse` | `id`/`chapterId`/`displayNumber`/`arabicText`/`durationMs`/`audio` |
| `verses[].audio` | `DraftAudio` → `SeedAudio` | Always `null` in Phase 11 |
| `packId` | **Not authored** | Phase 8 delivery slug. Phase 13 derives it from the Storage path prefix |
| `declaredSizeBytes` | `MatnDraft.declaredSizeBytes` | Derived, not typed |
| `isStarter` | **Not authored** | Phase 13 deletes this field and the whole starter path |

The two unauthored fields are the ones Phase 13 removes or derives, which is the correct outcome: the
authoring tool never invents delivery-mechanism values. `SeedMatnProjection.kt` in `data/seed/` holds
both directions and is unit-tested for round-trip fidelity on every field a matn can carry.

---

## 12. Pure helpers (Principle IV/V — the testable core)

| Helper | File | Contract |
|--------|------|----------|
| `MatnDraftFactory.newDraft/newChapter/newVerse` | `domain/catalog/MatnDraftFactory.kt` | The **only** place a matn, chapter, or verse comes into existence. Assigns the `INSTITUTIONAL_RECITER_ID` constant (FR-007a), the UUIDs (FR-008), and `createdAt`/`updatedAt` (FR-010). Takes `newId` and `nowMillis` as injected lambdas so it is testable, matching how `ContentModule.kt` already injects `newId`/`clock` into the bookmark and note repositories |
| `VerseOrdering.move(verses, from, to)` | `domain/catalog/VerseOrdering.kt` | Reorders and renumbers so display numbers are `1..n` with no gaps (FR-020) |
| `VerseOrdering.renumber(verses)` | same | Applied after add and delete (FR-019, FR-020) |
| `VerseTextImport.parse(bytes)` | `domain/catalog/VerseTextImport.kt` | UTF-8 decode, BOM strip, both line endings, blank lines skipped silently, per-line index for unreadable lines (FR-023a, FR-023b) |
| `AudioCompleteness.of(verses)` | `domain/catalog/AudioCompleteness.kt` | §4 |
| `DraftAutosaveScheduler` | `domain/catalog/DraftAutosaveScheduler.kt` | Injected clock + scope; 5 s idle, 60 s ceiling, coalesced, drafts only (D12) |
| `FirestoreValue` codec | `data/remote/firestore/` | D10 |

Every one is a pure function or takes its collaborators by interface, so the whole risky core is
covered in `commonTest` with no network, emulator, or device.
