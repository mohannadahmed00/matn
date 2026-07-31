# Contract: Validation Changes

Phase 11 established one rule set for the teacher tool and the student ingestion path
(`specs/011-teacher-authoring-upload/contracts/validation-contract.md`). This phase changes the
classification of exactly two rules and adds no new ones.

## 1. The flip

| Rule | Phase 11 | Phase 12 | Requirement |
|------|----------|----------|-------------|
| V4 `MissingAudio` — a verse has no audio | deferred (reported as outstanding work) | **blocking** | FR-028 |
| V5 `DuplicateAudioRef` — two verses share a `fileRef` | deferred | **blocking** | FR-028 |

Every other rule (V1 `InvalidId`, V1b `DuplicateId`, V2 `DuplicateDisplayNumber`,
V3 `DuplicateChapterOrder`, V6 `OrphanChapterRef`, V7 `StructureMismatch`, V8 `EmptyMatn`,
V9 `DocumentTooLarge`) keeps its Phase 11 classification.

After this change `ValidationReport.deferred` is empty for every matn. The field stays — it is the
mechanism by which a future phase can defer a rule again, and removing it would be churn.

## 2. Blast radius on shipped student code — none

`ContentSeedLoaderImpl.validate()` flattens both lists before filtering:

```kotlin
return (report.blocking + report.deferred).filterNot {
    it is ContentIntegrityError.EmptyMatn || it is ContentIntegrityError.DocumentTooLarge
}
```

Moving an error between the two lists is therefore invisible to it. Student ingestion treats a
missing audio reference exactly as it does today.

**Proof obligation** (`tasks.md` must carry it): `ContentIntegrityValidatorTest`,
the `ContentSeedLoader` tests, and `ProgressRepositoryTest` pass **unchanged**. If any of them needs
editing to accommodate the flip, this contract is wrong and the classification must become a
caller-supplied policy parameter instead of a constant.

## 3. Gates the flip drives

| Gate | Rule | Behaviour |
|------|------|-----------|
| Publish (`PublishMatnUseCase`) | any blocking problem | refused; every problem listed, each naming its verse (FR-029) |
| Save while published (`SaveDraftUseCase`) | V4 present | refused — a published matn may not become incomplete (FR-030) |
| Save while draft | none | allowed at any completeness; a draft is work in progress |
| Autosave (draft only, Phase 11 FR-031a/b) | unchanged | still never runs for a published matn |

The asymmetry between the last two rows is deliberate: a draft is allowed to be half-recorded — that
is the normal state of a matn being worked on — while a published matn is never allowed to leave
completeness.

## 4. Teacher-facing messages

| Error | Arabic / English message subject | Action offered |
|-------|----------------------------------|----------------|
| `MissingAudio(verseId)` | "verse N has no recording" | jump to that verse row |
| `DuplicateAudioRef(fileRef)` | "verses N and M share one recording" | jump to the first of them |

Both message keys already exist in `TeacherStrings` from Phase 11's deferred-note rendering; only
their severity styling changes (warning → error), so the panel needs no new string, per Principle
VIII's reuse rule.

## 5. Split validation is separate

`SplitPlanValidator` (`split-contract.md` §2) is a different rule set operating on a transient
`SplitPlan`, not on `MatnDraft`. It never runs during publish, and its problems never enter
`ValidationReport`. Keeping them apart is what stops authoring-time concerns (overlapping ranges)
from leaking into the content-integrity rules that Phase 13's ingestion also relies on.
