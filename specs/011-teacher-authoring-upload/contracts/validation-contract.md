# Contract: Validation

**Feature**: `specs/011-teacher-authoring-upload` | Satisfies FR-026, FR-027, FR-028, FR-029, FR-030

FR-026 requires the tool enforce *exactly* the rules the app's ingestion path already enforces — one
rule set, not two. This file pins which rules exist, which block publishing in this phase, and the
contract the extraction refactor must hold to.

---

## 1. The extraction (research D8)

**Verified current state**: the rules live in a `private fun validate(payload: SeedMatn)` inside
`shared/src/commonMain/kotlin/com/giraffe/matn/data/seed/ContentSeedLoaderImpl.kt`. The error types
they produce (`domain/error/ContentIntegrityError`) are already domain types.

**Target state**:

```text
domain/catalog/ContentIntegrityValidator.kt      # the rules, over MatnDraft
   ▲                                    ▲
   │                                    │
data/seed/ContentSeedLoaderImpl         domain/usecase/ValidateMatnUseCase
   (maps SeedMatn → MatnDraft,           (the teacher tool's entry point)
    delegates, unchanged behaviour)
```

The validator takes the **domain** `MatnDraft`, not `SeedMatn`. A validator in `domain/` taking a
`data.seed` DTO would invert Principle I's dependency arrow, and would also drag Phase 8's delivery
fields (`packId`, `isStarter`) into an authoring tool that has no concept of them.

### 1.1 Behaviour-preservation contract

Non-negotiable, and the reason this refactor is sequenced first:

1. `ContentSeedLoaderImpl`'s public signature and behaviour are **unchanged**: same aggregate
   `Resource.Failure(ContentIntegrityError.Aggregate(problems))` on invalid input, same atomic reject
   before any database write.
2. The existing `ContentSeedLoader` tests pass **unchanged** — not adapted, not re-recorded. That is
   the proof the extraction is behaviour-preserving.
3. Problem **ordering** within the aggregate is preserved. Existing tests may assert on it, and a
   reordered list is a silent test-semantics change.
4. The refactor lands as its own reviewable commit, with no new feature code depending on it yet.

---

## 2. Rule set

Every rule below already exists in `validate()`. None is invented here.

| ID | Error type | Condition | Phase 11 |
|----|-----------|-----------|----------|
| V1 | `InvalidId` | Any matn, chapter, verse, or audio id is blank | **Blocking** |
| V1b | `DuplicateId` | Any id appears twice across all entities in the matn | **Blocking** |
| V2 | `DuplicateDisplayNumber` | Two verses share a `displayNumber` | **Blocking** |
| V2b | `DuplicateChapterOrder` | Two chapters share an `order` | **Blocking** |
| V4 | `MissingAudio` | A verse has no audio | **Deferred** (FR-027) |
| V5 | `DuplicateAudioRef` | Two verses share an audio `fileRef` | **Deferred** (FR-027) |
| V6 | `OrphanChapterRef` | A verse's `chapterId` names no existing chapter | **Blocking** |
| V7 | `StructureMismatch` | `STRUCTURED` with no chapters or a chapter-less verse; `SIMPLE` with chapters or a verse carrying a `chapterId` | **Blocking** |

Two rules are added by this phase, both required by the spec rather than by the loader:

| ID | Error type | Condition | Phase 11 |
|----|-----------|-----------|----------|
| V8 | `EmptyMatn` | `verses` is empty | **Blocking** (FR-030) |
| V9 | `DocumentTooLarge` | Projected document size > ~900 KB | **Blocking** (research D2 guard) |

V8 and V9 are new `ContentIntegrityError` cases. They are *additive* — `ContentSeedLoaderImpl` never
produces them (a bundled matn is never empty and never oversized), so §1.1's unchanged-tests contract
still holds.

---

## 3. Blocking vs deferred (FR-027)

```kotlin
data class ValidationReport(
    val blocking: List<ContentIntegrityError>,
    val deferred: List<ContentIntegrityError>,
) {
    val canPublish: Boolean get() = blocking.isEmpty()
}
```

**The audio rules are evaluated, not skipped.** V4 and V5 run and their results are reported — they
are simply routed to `deferred`, where the UI shows them as outstanding work rather than errors
(FR-028, and User Story 4 scenario 2). This matters: skipping them would mean the teacher has no
signal about what is still missing, and Phase 12 would be inheriting an unevaluated rule rather than
flipping a flag.

**Phase 12 flips exactly one thing**: V4 and V5 move from `deferred` to `blocking`. No rule is
written, no message changes, no call site moves.

**`ContentSeedLoaderImpl` treats both buckets as blocking**, exactly as it does today — the student
app's ingestion path has always rejected an audio-less matn and continues to. The bucketing is the
teacher tool's policy, not the validator's, so the loader passes a flag or reads
`blocking + deferred`. This is the one place where the two callers of a shared rule set legitimately
differ, and keeping it explicit is why `ValidationReport` splits rather than filters.

---

## 4. Teacher-facing messages (FR-028)

FR-028 requires every problem name its subject in language the teacher can act on. Errors carry ids;
the UI resolves ids to what the teacher sees on screen (verse number, chapter title) — the teacher
never sees a UUID.

| Error | Message shape (both languages) |
|-------|-------------------------------|
| `InvalidId` | Internal problem with «subject» — report this |
| `DuplicateId` | Two entries share an identifier — remove and re-add the later one |
| `DuplicateDisplayNumber` | Verses «n» and «m» have the same number |
| `DuplicateChapterOrder` | Chapters «title A» and «title B» are both at position «k» |
| `MissingAudio` | Verse «n» has no recording yet *(outstanding work, not an error)* |
| `DuplicateAudioRef` | Verses «n» and «m» point at the same recording *(outstanding work)* |
| `OrphanChapterRef` | Verse «n» belongs to a chapter that no longer exists |
| `StructureMismatch` | «detail», rendered as a sentence naming what disagrees |
| `EmptyMatn` | This matn has no verses yet |
| `DocumentTooLarge` | This matn is too large to publish — «size» of the «limit» allowed |

Each message must be **clickable to its subject**: selecting a problem scrolls to and focuses that
verse or chapter. That is what makes "in one pass, each naming the specific verse" (FR-028) useful
rather than merely truthful on a 500-verse matn.

---

## 5. When validation runs

| Trigger | Scope | Blocks? |
|---------|-------|---------|
| Explicit "check" action | Full | No — reports |
| Publish (FR-029) | Full | **Yes** — refuses on any blocking problem |
| Save a **published** matn (FR-030) | Full | **Yes** for V8 — refuses to leave a published matn empty |
| Save a draft | Field-level only (required fields, FR-018) | Only on the empty fields |
| Autosave | None | Never — an in-progress draft is expected to be invalid, and FR-031c forbids interrupting editing |

Autosaving an invalid draft is correct and intended: a draft is work in progress. Validation is a
publishing gate, not an editing gate.

---

## 6. Tests (Principle V, SC-005)

All in `commonTest`, no network, no device.

| Test | Assertion |
|------|-----------|
| One case per rule V1–V9 | The specific error type appears, in the correct bucket |
| Valid text-only matn | `blocking` empty, `deferred` contains one `MissingAudio` per verse, `canPublish == true` |
| Multiple simultaneous problems | All reported in one pass, none swallowed (FR-028) |
| Aggregate ordering | Matches the pre-extraction order (§1.1 rule 3) |
| `SeedMatn` → `MatnDraft` → `SeedMatn` | Round-trips every field a matn can carry |
| Existing `ContentSeedLoader` suite | Passes unchanged |

SC-005 requires 100% of blocking problems be caught before publishing; the per-rule tests plus the
publish-refusal test are what measure it. SC-006 — every published matn accepted by the app's
ingestion rules — follows from the shared rule set plus the round-trip test.
