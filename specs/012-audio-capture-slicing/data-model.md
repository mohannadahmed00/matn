# Data Model: Phase 12 — Audio Capture & Slicing

Extends Phase 11's model (`specs/011-teacher-authoring-upload/data-model.md`). Nothing here replaces
an existing entity; the audio fields it reserved are filled in, and a set of **transient** authoring
types is added that must never reach storage.

The dividing line runs through this document and is the phase's central invariant:

| Persisted | Transient (authoring only) |
|-----------|----------------------------|
| `DraftAudio` — one per verse | `SplitPlan`, `VerseRange`, `SourceRecording`, `WaveformPeaks` |
| `AudioProfile` (as three fields on `DraftAudio`) | `VerseAudioPlan`, `UploadProgress` |

Anything in the right-hand column that appears in a `MatnRow`, a `SeedMatn`, or the storage bucket is
a defect, not a feature (FR-018, FR-001).

---

## 1. `DraftAudio` — changed

```kotlin
data class DraftAudio(
    val id: String,          // UUID, stable, generated once (FR-002)
    val fileRef: String,     // object path — see §2
    val durationMs: Long,    // measured from the stored bytes, never entered (FR-002)
    val sizeBytes: Long,     // NEW — stored object's byte length (FR-007)
    val sampleRate: Int,     // NEW — from the first frame header (FR-005b)
    val channels: Int,       // NEW — 1 mono, 2 stereo (FR-005b)
)
```

**Invariants**

| # | Invariant | Enforced by |
|---|-----------|-------------|
| A1 | `fileRef` is unique across the verses of a matn | V5 `DuplicateAudioRef` (now blocking), and structurally by the `verseId` in the path |
| A2 | `durationMs` is the sum of the stored file's frame durations, not a teacher input | `AudioProbe`; no UI writes it |
| A3 | `sizeBytes` equals the stored object's length | Set from the uploaded payload; re-checked against `listWithSizes` during resume |
| A4 | `(sampleRate, channels)` equals every other audio-bearing verse in the same matn | `ResolveAudioProfile` + rejection at attach time (FR-005b) |
| A5 | `id` survives replacement of the recording — replacing audio changes `fileRef`, `durationMs`, `sizeBytes`, never `id` | `AttachVerseAudio` |

A5 matters for FR-036: unpublish/republish and re-record must not orphan identifiers.

## 2. `fileRef` grammar

```text
matns/{matnId}/verses/{verseId}-{tag}.mp3
                                  └─ 16 lowercase hex chars: FNV-1a-64 of the stored bytes
```

Properties this buys (research D4):

- **Immutable** — a given name always holds the same bytes, so caches never go stale.
- **Atomic to swap** — new audio is a new object; the row write is the only commit point (FR-035).
- **Self-describing for resume** — the name is computable before uploading (FR-019b).
- **Per-verse by construction** — `verseId` in the path makes A1 structural rather than merely
  validated.

Cover images keep Phase 11's `matns/{matnId}/cover.{ext}` unchanged.

## 3. `MatnDraft` — changed behaviour, same shape

```kotlin
val declaredSizeBytes: Long
    get() = textBytes + verses.sumOf { it.audio?.sizeBytes ?: 0L }
```

Phase 11's assumption "declared size covers text only" is discharged (FR-007). `verseCount`,
`publicationState`, `remoteRevision`, and the chapter/verse collections are untouched.

`DraftVerse.durationMs` mirrors `audio?.durationMs ?: 0L`. It exists because `SeedVerse` has it; it
is derived, never independently authored.

## 4. `AudioCompleteness` — unchanged code, newly reachable states

`AudioCompleteness.of(verses)` already computes NONE / PARTIAL / COMPLETE from `verse.audio != null`
and needs no change. Phase 11 could only ever produce NONE. Now:

```text
NONE ──attach first verse──▶ PARTIAL ──attach last remaining──▶ COMPLETE
  ▲                             │  ▲                                │
  └────remove last audio────────┘  └───────remove any one───────────┘
```

**Gates driven by this state**

| State | Publish (FR-028) | Save while published (FR-030) |
|-------|------------------|-------------------------------|
| NONE | Refused | Refused |
| PARTIAL | Refused | Refused |
| COMPLETE | Allowed if no other blocking problem | Allowed |

A matn with zero verses is NONE and refused by V8 `EmptyMatn` as in Phase 11.

## 5. `AudioProfile` — derived, not stored as its own record

```kotlin
data class AudioProfile(val sampleRate: Int, val channels: Int)
```

`MatnDraft.audioProfile: AudioProfile?` = the profile of the first audio-bearing verse in list
order, or `null` when the matn holds no audio. Comparison is exact equality; there is no tolerance
and no conversion (FR-005a).

Rejection message names both profiles, per the spec's edge case: *"this matn's audio is 44.1 kHz
mono; this file is 48 kHz stereo."*

## 6. Transient: `SourceRecording`

```kotlin
data class SourceRecording(
    val localPath: String,     // where the teacher picked it; never copied, never uploaded
    val sizeBytes: Long,
    val durationMs: Long,
    val profile: AudioProfile,
    val frameCount: Int,
)
```

Lives for one splitting session. **Never** serialized. The frame index that accompanies it
(`Mp3FrameIndex`) is likewise in-memory only.

## 7. Transient: `VerseRange` and `SplitPlan`

```kotlin
data class VerseRange(val verseId: String, val startMs: Long, val endMs: Long)

data class SplitPlan(
    val source: SourceRecording,
    val scopeVerseIds: List<String>,   // contiguous run in list order, defaults to all (FR-013a)
    val ranges: List<VerseRange>,
)
```

**Invariants** (checked continuously by `SplitPlanValidator`, see `contracts/split-contract.md`)

| # | Invariant | Severity |
|---|-----------|----------|
| S1 | Every verse in `scopeVerseIds` has exactly one range | Blocking |
| S2 | `startMs < endMs` | Blocking |
| S3 | `endMs - startMs ≥ 300 ms` | Blocking |
| S4 | `endMs ≤ source.durationMs` | Blocking |
| S5 | No two ranges overlap | Blocking |
| S6 | No range names a verse outside `scopeVerseIds` | Blocking |
| S7 | Stretches of the source covered by no range | Warning |

Warnings do not prevent the split (FR-016) — silence between verses is normal.

`SplitPlan` is discarded when the screen closes, when the source is replaced (FR-020), and after a
successful split. It has no persisted representation anywhere.

## 8. Transient: `VerseAudioPlan` — the commit unit

Computed once per save, for both authoring paths (Principle III — one type, one commit routine):

```kotlin
data class VerseAudioPlan(
    val uploads: List<PendingUpload>,   // objectPath + bytes + resulting DraftAudio
    val skips: List<String>,            // object paths already present (research D5)
    val deletes: List<String>,          // referenced before, not after (research D6)
)
```

**Execution order is the contract**, not an implementation detail:

```text
1. list the matn's audio prefix        → decide uploads vs skips
2. upload every PendingUpload          → nothing user-visible has changed yet
3. write the matn row once             ← THE COMMIT (FR-019, FR-035)
4. delete every stale object           → failure here is logged, never surfaced (FR-033b)
```

An interruption before step 3 leaves the matn exactly as it was, plus some unreferenced objects that
a re-run will recognize and skip.

## 9. `CatalogRepository` — three new operations

```kotlin
suspend fun attachVerseAudio(draft: MatnDraft, verseId: String, audio: DraftAudio): Resource<MatnDraft>
suspend fun removeVerseAudio(draft: MatnDraft, verseId: String): Resource<MatnDraft>
suspend fun applySplit(draft: MatnDraft, results: List<VerseAudioResult>): Resource<MatnDraft>
```

All three go through the §8 ordering and carry Phase 11's `revision` precondition unchanged, so
FR-037's conflict detection covers audio writes with no new machinery.

## 10. `SeedMatn` correspondence — FR-009 re-checked

| `DraftAudio` | `SeedAudio` | Note |
|--------------|-------------|------|
| `id` | `id` | unchanged |
| `fileRef` | `fileRef` | now an object path with a content tag; still an opaque string to the app |
| `durationMs` | `durationMs` | unchanged |
| `sizeBytes` | `sizeBytes = 0L` | **added**, defaulted |
| `sampleRate` | `sampleRate = 0` | **added**, defaulted |
| `channels` | `channels = 0` | **added**, defaulted |

Defaults are what keep the change additive: existing bundled seed JSON omits the three fields and
parses byte-identically, so no student behaviour changes (FR-042, SC-015). Phase 13 will read them
to state a download size before downloading.

## 11. What is deliberately absent

- **No timestamp, offset, or range field anywhere in `MatnRow`, `SeedMatn`, or the bucket.** The
  per-verse lock is enforced by there being nothing to express the rejected model with.
- **No `sourceRecordingRef`.** The continuous file has no persisted identity at all.
- **No upload-progress table, column, or file** (FR-019b).
- **No per-verse reciter field.** Phase 11's single implicit reciter is unchanged; the shape still
  permits adding one later (FR-013 of Phase 11).
