# Contract: The Stored Audio Artifact

What Phase 12 writes, where, and under what guarantees. This is the contract Phase 13 consumes, so
it is written for a reader who has never seen the authoring tool.

## 1. The artifact

A published matn's audio is **exactly one MP3 object per verse**, in the `matn-content` bucket:

```text
matns/{matnId}/verses/{verseId}-{tag}.mp3
```

| Part | Rule |
|------|------|
| `matnId` | The matn's UUID. Same prefix as its cover image. |
| `verseId` | The verse's UUID. Guarantees two verses never share an object (FR-006). |
| `tag` | 16 lowercase hex chars — FNV-1a-64 over the exact bytes stored. |
| extension | Always `.mp3`; content type always `audio/mpeg` (FR-003, FR-005a). |

There is **no** object for a matn as a whole, no object outside a matn prefix, and no persisted
description of how a verse relates to any other audio. A consumer that has a `fileRef` has
everything it needs.

## 2. Invariants a reviewer can test

| # | Invariant | How to check |
|---|-----------|--------------|
| I1 | Every object under `matns/*/verses/` matches the path grammar in §1 | List the bucket; regex |
| I2 | No object exists whose duration exceeds one verse's | Durations in the row vs object sizes |
| I3 | No row field anywhere expresses an offset into a shared file | `MATN_FULL_COLUMNS` has no such column; `VerseRow`/`AudioRow` have no such field |
| I4 | Two verses never share a `fileRef` | V5 `DuplicateAudioRef`, blocking from this phase |
| I5 | Every audio-bearing verse in a matn reports the same `sampleRate`/`channels` | `ResolveAudioProfile` over the row |
| I6 | The source recording is absent from storage | No object outside `verses/` and `cover.*` under any matn prefix |

I1, I3, and I6 together are the testable form of the constitution's per-verse audio lock. I6 is the
one an implementation could plausibly violate by "temporarily" uploading the source — it must not,
under any circumstance, including as a resume aid (FR-018).

## 3. Content tag

```text
tag(bytes) = fnv1a64(bytes) rendered as %016x
fnv1a64: h = 0xcbf29ce484222325; for each byte b: h = (h xor b) * 0x100000001b3   (unsigned 64-bit)
```

Pure Kotlin, `commonMain`, no dependency. It is an identity key, not a security primitive — a
collision would mean an upload is skipped when it should not be, which the byte-size check in §5
catches in practice.

The tag is computed over the bytes **after** slicing and tag-stripping, i.e. exactly what will be
stored, so a slice's name is reproducible from the same source and the same range.

## 4. Commit ordering

The only ordering permitted for any operation that changes audio:

```text
1. plan     — list the prefix, compute uploads / skips / deletes
2. upload   — write new objects; nothing referenced yet, nothing user-visible changed
3. commit   — write the matn row exactly once, with the revision precondition
4. clean    — delete objects the new row no longer references
```

**Guarantees this produces**

- **FR-035 (no torn read)**: a reader resolves either every old `fileRef` or every new one, because
  they change together in step 3, and old objects still exist until step 4.
- **FR-019 (all-or-nothing split)**: an interruption before step 3 changes nothing observable.
- **FR-033a (delete only after commit)**: step 4 cannot run before step 3 by construction.
- **FR-033b (a failed delete is not an error)**: step 4's failures are logged and dropped; the
  operation has already succeeded.
- **FR-037 (conflict)**: step 3 carries Phase 11's `revision` filter, so a concurrent change is
  reported rather than overwritten — and because step 3 is the only write, a conflict costs only the
  uploads, which the next attempt will skip.

## 5. Resume predicate

An expected upload is skipped when **both** hold:

1. an object exists at the expected content-tagged path, and
2. its listed size equals the payload's byte length.

Condition 1 alone implements FR-019a/c: identical plan ⇒ identical names ⇒ skip; changed plan ⇒
different names ⇒ no skip. Condition 2 guards a truncated or partially-written object.

Nothing else is consulted, and nothing is recorded (FR-019b).

## 6. Profile rule

- A matn's profile is the `(sampleRate, channels)` of its first audio-bearing verse in list order.
- A matn with no audio has no profile; the next attachment establishes one.
- An attachment or split whose profile differs is **rejected before upload**, naming both profiles.
- Nothing is ever converted (FR-005a).

## 7. What a Phase 13 consumer may rely on

- `fileRef` is a stable, immutable object path. Cache by name without invalidation.
- `sizeBytes` summed over verses is the audio component of `declaredSizeBytes`, so a download size
  can be shown before downloading.
- `durationMs` is measured from the bytes, so a queue can be built without probing files.
- A matn with `audio_completeness = COMPLETE` has an object for every verse — that is what the
  publish gate enforces (FR-028), and what lets Phase 13 filter on the state rather than re-checking.
- Anonymous read of an audio object succeeds exactly when the matn is published
  (`contracts/storage-contract.md` §3).
