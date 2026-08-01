# Quickstart: Phase 12 — Audio Capture & Slicing

How to prove this phase works. Scenarios map to the spec's user stories; each states what to run and
what to observe.

## 1. Prerequisites

- The Phase 11 setup, working: a Supabase project, a provisioned teacher account, and
  `supabase/supabase.local.properties` (or the env overrides) filled in. See
  `specs/011-teacher-authoring-upload/quickstart.md` §1.
- The audio bucket migration applied:
  ```bash
  supabase db push        # or run 20260801000000_matn_content_audio_limits.sql in the SQL editor
  ```
- Two MP3s to hand:
  - `verse.mp3` — a few seconds, one verse.
  - `continuous.mp3` — one recording covering several verses of the same matn, **same sample rate
    and channel layout** as `verse.mp3`.
- A third MP3 in a *different* profile (e.g. 48 kHz stereo when the others are 44.1 kHz mono) for the
  rejection check.

## 2. Automated checks

```bash
./gradlew :shared:jvmTest :shared:testAndroidHostTest :teacherApp:test
```

Covers, without network or audio hardware:

| Area | What is asserted |
|------|------------------|
| `Mp3FrameIndexTest` | frame offsets, durations, ID3v2 skip, Xing detection — over **synthesized** headers |
| `SliceOffsetTest` | ms → frame snapping stays within one frame; slices contain whole frames only |
| `SplitPlanValidatorTest` | R1–R7 one case per rule, and that R7 alone does not block |
| `AudioContentTagTest` | stable hash; different bytes ⇒ different tag |
| `VerseAudioPlanTest` | uploads/skips/deletes computed correctly; delete set = referenced-before − referenced-after |
| `VerseAudioUploaderTest` | progress, cancellation, resume skip, and **commit-then-delete ordering** against a fake storage client |
| `AudioProfileTest` | first audio-bearing verse establishes the profile; mismatch rejected; empty matn has none |
| `ContentIntegrityValidatorTest` | V4/V5 now blocking; **existing cases unchanged** |
| `ContentSeedLoader` + `ProgressRepositoryTest` | pass **unchanged** — the proof that the flip did not reach student ingestion |
| `EditorViewModelTest`, `SplitViewModelTest` | attach/remove/progress and the split state machine on virtual time |

RLS cases (`storage-contract.md` §4) need a local stack:

```bash
supabase start
SUPABASE_TEST_URL=http://localhost:54321 ./gradlew :shared:jvmTest
```

Without `SUPABASE_TEST_URL` they skip, as in Phase 11.

## 3. Manual walkthrough

```bash
./gradlew :teacherApp:run
```

### 3.1 Attach one recording (US1 → FR-008–FR-012)

1. Sign in, open a matn with verse text.
2. On verse 1's row, attach `verse.mp3`. Observe: progress, then the duration on the row.
3. Play it from the row. Stop it.
4. Attach the **different-profile** file to verse 2. Observe: rejected, message naming both
   profiles; verse 2 still has no audio.
5. Replace verse 1's recording with another take. Observe: duration updates.
6. Remove verse 1's audio. Observe: the row returns to empty and the library badge falls back.

**Verify server-side**: the bucket holds `matns/{id}/verses/{verseId}-{tag}.mp3` — one object per
attached verse, and after step 6 the removed verse's object is gone (deleted *after* the row write —
check the row no longer references it either way).

### 3.2 Split one recording (US2 → FR-013–FR-021)

1. Open the split screen, pick `continuous.mp3`. Observe: waveform, total duration.
2. Leave the scope at the whole matn if the recording covers it; otherwise set the run it covers.
3. Place a range per verse in scope. Deliberately create an **overlap**. Observe: reported
   immediately, naming both verses, split disabled.
4. Fix the overlap; leave a **gap** between two verses. Observe: reported as a warning, split still
   allowed.
5. Clear one verse's range. Observe: blocking, that verse named.
6. Restore it and split. Observe: per-verse upload progress, then the editor rows showing durations.

**Verify the artifact** — the phase's central claim:

- One object per verse in scope; **no object holding the whole recording**.
- Each verse's duration matches its range within ~50 ms.
- The matn row contains **no** start/end/offset field anywhere (check `verses` jsonb).
- `continuous.mp3` is nowhere in the bucket.

### 3.3 Interrupt and resume (FR-019a–c, SC-010)

1. Start a split of many verses; kill the app partway.
2. Reopen, re-load the same recording, re-create the same ranges, split again.
3. Observe: already-uploaded verses transfer instantly (skipped); only the remainder uploads.
4. Now change one range materially and split again. Observe: that verse re-uploads under a new
   object name rather than being skipped.

### 3.4 Preview (US3 → FR-022–FR-026)

1. With every verse recorded, start preview from verse 1. Observe: continuous playback across verse
   boundaries with **no audible gap**, and the sounding verse indicated throughout.
2. Pause, resume, jump to another verse.
3. Remove one verse's audio and preview again. Observe: that verse is named as missing; skip or stop
   offered — no silence without explanation.
4. Edit verse text while previewing. Observe: preview stops cleanly.

### 3.5 Publish (US4 → FR-027–FR-031)

1. With one verse missing audio, run the check. Observe: that verse named, **blocking**, publish
   refused.
2. Attach the last recording; re-check; publish. Observe: state published, completeness "complete".
3. In the library, confirm partial matns show a count ("12 of 109 recorded").
4. Read the published matn's declared size. Observe: it now includes audio.

**Verify anonymously** (FR-038): fetch an audio object with no auth header — 200 for a published
matn. Unpublish it and repeat — refused.

### 3.6 Correct after publishing (US5 → FR-032–FR-037)

1. Replace one verse's recording on a published matn and save. Observe: still published; only that
   verse's `fileRef` changed; all other objects untouched.
2. Try removing a verse's audio on a published matn. Observe: refused (FR-030).
3. Reorder verses and confirm each recording followed its verse, not its position (FR-032).
4. Delete a verse that has audio; save. Observe: its object is gone and nothing else changed.

## 4. What cannot be checked here

Carried forward honestly, as Phase 11 did:

- **Frame-time observations** (FR-041, SC-013) need a running GUI with a frame HUD; the automated
  proxy is the pure-function timing in `SplitPlanValidatorTest`/`VerseOrdering` plus the structural
  guarantee that no verse row owns text or progress state.
- **"No audible gap"** (SC-008) is ultimately an ear test. The structural guarantee is one
  `SourceDataLine` for the whole preview (research D9); a regression would show up as a second line
  being opened.
- **The bit-reservoir artifact** (`split-contract.md` §4) is audible only in principle; note it if a
  teacher ever reports a click at a verse start.

### T096 — not executed live in this session (recorded, not claimed)

§3's manual walkthrough needs three things this environment has none of: a running GUI (no display),
a live Supabase project with the migration applied and a provisioned teacher account, and real MP3
files at two different profiles. None were available, so §3.1–§3.6 were not run end to end.
What was verified instead, as the automated substitute per area:

- **§2's automated suite** (`./gradlew :shared:jvmTest :teacherApp:test`) ran green in full,
  including every case in the table above.
- **§3.1/§3.2's server-side artifact claims** (one object per verse, content-tagged paths, no
  object holding the whole recording, no offset field in the row) are exactly what
  `SupabaseCatalogRepositoryAudioTest`, `VerseAudioPlanTest`, and `AudioContentTagTest` assert
  against a mocked HTTP layer — the wire shape is proven, the live bucket state is not.
- **§3.3's resume/re-split behavior** is `ApplySplitUseCaseTest`'s "a re-run with identical ranges
  produces identical object paths" / "a materially changed range produces a different object path"
  pair.
- **§3.4's gapless-preview and stop-on-edit claims** are `PreviewStateTest` (missing-verse reporting,
  start-index) and the FR-026 assertion inside it (an edit stops the player); the "no audible gap"
  half is inherently an ear test (§4 above already says so).
- **§3.5's publish gate** is `PublishMatnUseCaseTest`'s refuse/succeed pair plus
  `ContentIntegrityValidatorTest`'s V4/V5-blocking cases; the anonymous-read-by-publish-state half
  is `RlsPolicyTest`'s A1/A2, written and compiling but stack-gated (see below).
- **§3.6's post-publish correction claims** are `SupabaseCatalogRepositoryAudioTest`'s published-matn
  cases (T089) and `VerseAudioOrderingTest` (T091a).
- **RLS (§2's second block, and A1–A7)**: no local Supabase stack (`supabase start`) exists here, so
  these skip via `requireLocalStack`, exactly as Phase 11's R/W-series did in its own quickstart
  pass. `.github/workflows/rls-policy-tests.yml` remains the actual enforcement point.
- **The bucket migration itself** (`20260801000000_matn_content_audio_limits.sql`) was written and
  reviewed against `contracts/storage-contract.md` §2 but not applied to a live project in this
  session.
