# Implementation Plan: Phase 12 — Audio Capture & Slicing

**Branch**: `012-audio-capture-slicing` (git: `feature/012-audio-capture-slicing`) | **Date**: 2026-07-31 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/012-audio-capture-slicing/spec.md`

## Summary

Phase 11 left every matn text-complete and audio-empty. This phase fills the audio fields its
schema already reserved, by two teacher-facing paths that converge on one artifact — **one MP3 per
verse in `matn-content`**:

1. **Per-verse attach** — one already-cut file per verse row.
2. **Split-from-continuous** — one long recording, a waveform, per-verse ranges, and a save that
   **cuts the source at MP3 frame boundaries** and uploads the slices. The recording, the markers,
   and the ranges are never persisted.

Plus **preview playback** (the assembled matn, gapless, before publishing) and the **validation
flip**: `MissingAudio` and `DuplicateAudioRef` move from Phase 11's deferred list to blocking, so a
matn cannot be published with a silent verse.

Three technical choices carry most of the design:

- **Cut, never re-encode** (spec Q1). Slicing is a byte copy between frame offsets, so the phase
  needs an MP3 *decoder* only for the waveform and preview — no encoder — and the stored bytes are
  the teacher's bytes. Frame indexing is pure Kotlin in `commonMain`; only PCM decoding needs a
  library, and only on the JVM.
- **Content-tagged object paths** — `matns/{matnId}/verses/{verseId}-{tag}.mp3`, where `tag` is a
  stable hash of the exact bytes being stored. New audio is a new object, so the row write is the
  commit (FR-035), resume is "does the expected object already exist" with nothing recorded
  (FR-019b), a re-run of a *different* plan produces different names and cannot false-skip
  (FR-019c), and the superseded object is deleted only after the write succeeds (FR-033a).
- **One profile per matn, enforced by rejection** (FR-005b). Because every verse file in a matn
  shares a sample rate and channel layout, preview can stream all of them into one audio line and
  is gapless by construction rather than by pre-buffering tricks.

No student-client change: `:androidApp`, `:iosApp`, and `:desktopApp` keep their current behaviour
and their current dependency graphs.

## Technical Context

**Language/Version**: Kotlin 2.4.10; JVM 11 bytecode target; Compose Multiplatform 1.11.1 /
Material 3 1.11.0-alpha07 — unchanged from Phase 11.

**Primary Dependencies**:
- **New**: `javazoom:jlayer:1.0.1` — pure-Java MPEG-1/2 Layer III decoder, declared by
  **`:teacherApp` only**. Used for two things: decoding to PCM for waveform peaks, and decoding for
  preview playback. Not used for cutting, not used for duration, and never added to `:shared` — so
  no student module's dependency graph changes (see research D1, Complexity Tracking).
- **Reused**: Ktor 3.2.3 (`StorageRestClient` gains `download`/`delete`/`list`), kotlinx-serialization,
  kotlinx-coroutines, Koin 4.2.1, the Phase 10 token set, `BaseViewModel`, `UseCase`,
  `Resource`/`AppError`/`RemoteError`, `PortalShell`, `TeacherStrings`.
- **Not used**: any MP3 *encoder*; ffmpeg in any form; `mp3spi`/`tritonus` (their
  `javax.sound.sampled` SPI registration is global, and the point of D1 is to keep MP3 support out
  of the shared audio engine the student desktop app uses).

**Storage**:
- **Remote**: `public.matns` — unchanged shape; the `verses` jsonb gains populated `audio` objects
  (`id`, `fileRef`, `durationMs`, plus new `sizeBytes`, `sampleRate`, `channels`). No migration for
  the table — jsonb.
- **Remote binary**: `matn-content` bucket, `matns/{matnId}/verses/{verseId}-{tag}.mp3`. One
  migration widens the bucket to admit `audio/mpeg` and raises `file_size_limit` from 5 MB to 10 MB
  (FR-004b).
- **Local, teacher machine**: the continuous source recording is read from wherever the teacher
  picked it, never copied into the app's own storage and never uploaded (FR-018). A preview cache
  of downloaded verse files lives under the existing app-data dir, keyed by the immutable
  content-tagged object name.

**Testing**:
- `commonTest`: frame indexing over synthesized MP3 headers, slice offset arithmetic, range-plan
  validation rule by rule, the content-tag hash, the resume predicate, profile comparison,
  `declaredSizeBytes` with audio, and the validator's blocking/deferred flip.
- `:teacherApp` `test`: split/attach ViewModel state machines on `StandardTestDispatcher`, the
  upload orchestrator against a fake storage client (progress, cancellation, resume skip,
  commit-then-delete ordering), and JLayer probe/decode against a small real MP3 fixture.
- `jvmTest`, stack-gated: the RLS matrix extended with the audio-object cases (FR-038).
- `@Preview` per Principle II, two per screen (Arabic/RTL and English/LTR) per FR-039.

**Target Platform**: `:teacherApp`, desktop JVM. Nothing in this phase runs on Android or iOS.

**Project Type**: Kotlin Multiplatform monorepo; no new module.

**Performance Goals**: waveform peaks for a 4-hour recording extracted in a single streaming pass
with bounded memory; marker drag and 500-verse list scroll during uploads with no frame over 32 ms
(FR-041, SC-013); slicing a 100-verse recording completes without blocking the UI thread.

**Constraints**:
- **Never hold decoded PCM for a whole recording.** A 4-hour stereo 44.1 kHz decode is ~2.5 GB;
  peaks must be accumulated while streaming and the PCM discarded.
- **Never load a 300 MB source into memory to cut it.** Slicing reads byte ranges on demand.
- **The source recording must not reach storage** (FR-018) — no "temporary" upload, not even a
  scratch object.
- **No student-visible change** (FR-042, SC-015): `:shared`'s existing public surface, the student
  modules' dependency graphs, and `DesktopAudioEngine` are untouched.

**Scale/Scope**: 1 new teacher screen (split) + an audio column on the existing editor rows + a
preview transport; 4 new domain interfaces; 5 new use cases plus one pure validator object;
3 new `StorageRestClient` methods; 1 Supabase migration; 1 validator policy change; ~8 new shared
pure-logic units. Profile resolution is an extension function on `MatnDraft`, not a use case — a use
case wrapping a pure derivation would add a layer and no behaviour.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Clean Architecture & Layer Boundaries** — PASS with one placement rule. MP3 frame parsing is
  a *format* concern, so `Mp3FrameIndex`/`Mp3Header` live in `:shared/commonMain/data/audio`, and
  the domain sees only interfaces (`AudioProbe`, `AudioSlicer`, `VerseAudioUploader`,
  `PreviewPlayer`). Use cases depend on the interfaces; nothing in `domain/` imports a decoder or a
  file API. The split-plan rules are pure domain (`SplitPlanValidator`, beside
  `ContentIntegrityValidator`).
  **The Phase 11 DI rule still binds**: teacher-side classes added to `:shared` MUST NOT carry Koin
  annotations, or `ContentModule`'s `@ComponentScan("com.giraffe.matn")` drags them into every
  student graph and the Koin compiler fails the build with `KOIN-D001`. New shared audio classes are
  constructed by `:teacherApp`'s `TeacherModule` with explicit providers.
- **II. MVVM Presentation (NON-NEGOTIABLE)** — PASS, with the same 500-row discipline as Phase 11
  plus one new risk: waveform peaks and marker positions are large-ish state. They stay in the
  ViewModel as immutable snapshots; the waveform composable is a pure function of
  `(peaks, ranges, viewport)` drawn on `Canvas`. Upload progress is per-row state from the
  ViewModel, never a per-row `remember`. Previews: idle / peaks-loaded / with-ranges /
  invalid-ranges, each in both languages.
- **III. DRY via Base Abstractions** — PASS. Both audio paths converge on one `VerseAudioUploader`
  and one commit-then-delete routine, so per-verse attach and split share the atomicity, resume, and
  cleanup logic rather than each implementing it. `AudioCompleteness`, `ValidationReport`,
  `Resource`, `RemoteError`, `PortalShell`, and `TeacherStrings` are reused unchanged.
- **IV. Shared-First Multiplatform** — PASS. Frame indexing, slice arithmetic, the content tag, the
  resume predicate, profile comparison, split-plan validation, and every use case are `commonMain`.
  `:teacherApp`'s JVM code is confined to genuine edges: JLayer decoding, the audio output line,
  file byte ranges, and the file chooser. Phase 13 needs none of the authoring half but inherits the
  storage-client methods.
- **V. Test-First & Testable Design (NON-NEGOTIABLE)** — PASS. Everything above is a pure function
  or takes an injected collaborator. MP3 frame headers can be *synthesized* byte-for-byte in
  `commonTest`, so frame indexing and slice offsets are tested with no fixture file and no decoder.
  The uploader is tested against a fake storage client for progress, cancellation, resume, and
  ordering.
- **VI. Offline-First & Future-Proof Data** — PARTIAL, inherited and unchanged. The producer tool is
  deliberately online-only; the principle's offline guarantee is about the student's downloaded
  library on a student device. Same justification as Phase 11, recorded again in Complexity
  Tracking. Audio ids are UUIDs (FR-002), and the stored shape still admits multi-reciter mapping.
- **VII. Experience Fidelity: Audio, RTL & Accessibility** — PASS, and this is the principle the
  phase most directly serves. The per-verse lock is upheld by construction: FR-018 forbids
  persisting the source or any offset, and the only thing that reaches storage is per-verse files.
  **The authoring-time carve-in in the constitution's audio-asset clause is exactly this phase**, so
  no amendment is needed. Gapless preview (FR-023) is real gapless — one output line, uniform
  profile — not a crossfade. RTL: the waveform timeline stays left-to-right in both interface
  languages, a deliberate deviation recorded in this feature's design notes (research D11).
- **VIII. Design Fidelity & Reusable Composables (NON-NEGOTIABLE)** — PASS with two obligations
  carried into `tasks.md`. (a) *Upload Matn (Timestamp Map)* MUST be fetched through the `stitch`
  MCP server before any split UI is written, and the per-verse audio column of *Upload Matn
  (Per-Verse)* (`4f1bee3d7518487b986c7c63cb3c07ff`) — explicitly deferred by Phase 11's
  `design-notes.md` — is this phase's to implement. (b) That screen's *architecture* (verses as
  ranges within one shipped file) remains rejected; only its layout is adopted, and the deviation
  must be recorded in design notes as Phase 11 did. Tokens only, no raw literals.

**Technology & Architecture Constraints**
- **Stack list** — no new client module; the constitution's list is unchanged.
- **Per-verse audio lock** — upheld, and this phase is the carve-in's first use. `contracts/` states
  the invariant as testable rules (nothing but per-verse objects in the bucket; no offset field
  anywhere in the row).
- **Dependency justification** — JLayer is new and itemized in Complexity Tracking, discharging the
  constitution's outstanding deferred TODO (b) for Phase 12. It lands narrower than that TODO
  anticipated: decode only, `:teacherApp` only, no encoder, no ffmpeg.

**Gate result: PASS.** One partial (Principle VI), inherited from Phase 11 with the same recorded
justification. No unjustified violations.

## Project Structure

### Documentation (this feature)

```text
specs/012-audio-capture-slicing/
├── plan.md              # This file
├── research.md          # Phase 0 output — 12 decisions
├── data-model.md        # Phase 1 output — audio entities, transient split state, invariants
├── quickstart.md        # Phase 1 output — runnable validation walkthrough
├── contracts/           # Phase 1 output
│   ├── audio-artifact-contract.md   # the stored artifact: paths, tags, profile, atomicity
│   ├── split-contract.md            # range rules, frame snapping, slice output
│   ├── storage-contract.md          # new REST methods, bucket migration, RLS matrix delta
│   ├── validation-contract.md       # the blocking flip and its blast radius
│   └── teacher-ui-contract.md       # audio slot, split screen, preview transport, previews
├── checklists/
│   └── requirements.md  # spec quality checklist (16/16)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
gradle/libs.versions.toml                 # + jlayer 1.0.1

shared/src/commonMain/kotlin/com/giraffe/matn/
├── domain/
│   ├── catalog/
│   │   ├── MatnDraft.kt                  # CHANGED — DraftAudio gains sizeBytes/sampleRate/channels;
│   │   │                                 #   declaredSizeBytes includes audio (FR-007)
│   │   ├── ContentIntegrityValidator.kt  # CHANGED — V4/V5 move deferred → blocking (FR-028)
│   │   └── CatalogRepository.kt          # CHANGED — + attachVerseAudio/removeVerseAudio/applySplit
│   ├── audio/                            # NEW (authoring contracts, no platform types)
│   │   ├── AudioProbe.kt                 #   duration + profile + streamed peaks, interface
│   │   ├── AudioSlicer.kt                #   ranges → per-verse byte payloads, interface
│   │   ├── VerseAudioUploader.kt         #   batch upload + progress + resume, interface
│   │   ├── PreviewPlayer.kt              #   gapless sequential preview, interface
│   │   ├── AudioProfile.kt               #   sampleRate + channels + MatnDraft.audioProfile()
│   │   ├── SplitPlan.kt                  #   source ref + scope + ranges (transient)
│   │   ├── VerseRange.kt
│   │   ├── SplitPlanValidator.kt         #   pure rules (FR-015/FR-016)
│   │   ├── AudioContentTag.kt            #   pure stable hash → object name (FR-006, D4)
│   │   └── VerseAudioPlan.kt             #   what to upload / skip / delete for one commit
│   └── usecase/                          # NEW: AttachVerseAudio, RemoveVerseAudio,
│                                         #   LoadSplitSource, ApplySplit, PreviewMatnAudio
├── data/
│   ├── audio/                            # NEW — format concerns, still commonMain & pure
│   │   ├── Mp3FrameIndex.kt              #   frame offsets/durations from bytes (no decode)
│   │   ├── Mp3Header.kt                  #   sample rate / channels / frame length
│   │   ├── Mp3TagStripper.kt             #   drop ID3v2 + Xing/Info/VBRI from a slice
│   │   ├── ByteSource.kt                 #   read-a-range abstraction; JVM impl is RandomAccessFile
│   │   ├── FrameAccurateSlicer.kt        #   AudioSlicer impl — byte-range copy, no decode
│   │   └── DefaultVerseAudioUploader.kt  #   VerseAudioUploader impl over StorageRestClient
│   ├── remote/storage/StorageRestClient.kt      # CHANGED — + download, delete, listWithSizes
│   ├── remote/postgrest/MatnRow.kt              # CHANGED — AudioRow gains the three fields
│   ├── repository/SupabaseCatalogRepository.kt  # CHANGED — audio writes, commit-then-delete
│   └── seed/SeedContent.kt               # CHANGED — SeedAudio gains the same optional fields
│                                         #   (defaults preserve existing bundled JSON)

teacherApp/src/main/kotlin/com/giraffe/matn/teacher/
├── platform/
│   ├── JLayerAudioProbe.kt               # NEW — JVM decode: duration, profile, streamed peaks
│   ├── JvmAudioSlicer.kt                 # NEW — byte-range reads over the picked file
│   ├── JvmPreviewPlayer.kt               # NEW — one SourceDataLine, sequential verses
│   ├── PreviewCache.kt                   # NEW — downloaded verse files, keyed by object name
│   └── JvmFileChooser.kt                 # CHANGED — audio filters + a no-copy source handle
└── presentation/
    ├── common/VerseRow.kt                # CHANGED — audio slot: empty / uploading / loaded
    ├── editor/EditorViewModel.kt         # CHANGED — attach/remove/progress state
    ├── split/                            # NEW — SplitScreen, SplitViewModel, WaveformCanvas,
    │                                     #   MarkerLayer, RangeFieldsRow, ScopePicker
    └── preview/                          # NEW — PreviewBar (transport + sounding verse)

supabase/migrations/
└── 20260801000000_matn_content_audio_limits.sql   # NEW — audio/mpeg + 10 MB ceiling
```

**Structure Decision**: no new module. Authoring logic that is pure goes to `:shared/commonMain` (so
it is tested without a JVM), while everything that decodes, plays, or touches the filesystem stays in
`:teacherApp`. The one deliberate asymmetry is JLayer: declared by `:teacherApp` alone, so `:shared`
— which every student client depends on — gains no MP3 dependency and `DesktopAudioEngine`'s
documented "no MP3 codec" behaviour is unchanged. That is what makes SC-015 provable by inspection
rather than by argument.

## Phase 0 — Research

See [research.md](./research.md). Twelve decisions; the load-bearing ones:

| # | Question | Decision |
|---|----------|----------|
| D1 | MP3 support on JVM without ffmpeg | `javazoom:jlayer:1.0.1`, `:teacherApp` only, decode only. Cutting and duration need no library — pure frame parsing in `commonMain` |
| D2 | Frame-boundary cutting fidelity | Snap each boundary to the nearest frame start (≤26.1 ms at 44.1 kHz, inside SC-004's 50 ms). Strip ID3v2 and Xing/Info/VBRI from slices or their declared duration lies |
| D3 | Where the encoding profile is recorded | Three new fields on the audio object in the `verses` jsonb — no migration. Mirrored on `SeedAudio` with defaults so bundled JSON still parses |
| D4 | Object naming & atomicity | `…/{verseId}-{tag}.mp3` with `tag` = stable hash of the stored bytes. New content ⇒ new object ⇒ the row write is the commit |
| D5 | Resume without a progress record | List the matn's audio prefix once; an expected object already present at its content-tagged name is done. A changed plan yields different names, so it cannot false-skip |
| D6 | Deleting superseded objects | Always after the row write commits; a failed delete is logged, never surfaced as an error (FR-033a/b) |
| D7 | Waveform for a 4-hour file | Stream-decode, accumulate min/max per bucket, discard PCM. The peaks array is bounded by the viewport bucket count, not the file length |
| D8 | Slicing a 300 MB source | Frame index of offsets only; slices copied by byte range on demand, one at a time |
| D9 | Gapless preview | One `SourceDataLine` fed by successive verse decodes. Legal because FR-005 guarantees a uniform profile within a matn |
| D10 | Bucket limits (FR-004b) | Widen `matn-content` to `audio/mpeg` and 10 MB. The 300 MB continuous ceiling never applies server-side — the source is never uploaded |
| D11 | Waveform direction under RTL | Timeline is left-to-right in both interface languages; the numeric range fields follow the UI direction. Recorded as a design deviation |
| D12 | Making V4/V5 blocking without breaking student ingestion | The validator's classification changes; `ContentSeedLoaderImpl` already flattens `blocking + deferred`, so its behaviour is bit-identical either way. Proven by its existing tests passing unchanged |

## Phase 1 — Design & Contracts

Artifacts produced:

- **[data-model.md](./data-model.md)** — the audio object's fields and invariants, the transient
  `SplitPlan`/`VerseRange`/`AudioProfile` types that must never be persisted, the extended
  `declaredSizeBytes`, the `AudioCompleteness` transitions audio now drives, and the updated
  `SeedMatn` correspondence table that keeps FR-009 true.
- **[contracts/audio-artifact-contract.md](./contracts/audio-artifact-contract.md)** — object-path
  grammar, the content tag, the commit ordering that makes FR-035 hold, the profile rule, and the
  invariants a reviewer can test the per-verse lock against.
- **[contracts/split-contract.md](./contracts/split-contract.md)** — the range rule set with each
  rule marked blocking or warning, frame snapping, what a slice contains, and the scope semantics
  from FR-013a.
- **[contracts/storage-contract.md](./contracts/storage-contract.md)** — the three new REST methods,
  the bucket migration, and the RLS matrix rows this phase adds.
- **[contracts/validation-contract.md](./contracts/validation-contract.md)** — the V4/V5 flip, its
  blast radius on the student ingestion path, and the publish/save gates it now drives.
- **[contracts/teacher-ui-contract.md](./contracts/teacher-ui-contract.md)** — the verse row's audio
  slot states, the split screen's states, the preview transport, and the required preview matrix.

**Agent context update**: this repo's `.specify/scripts/powershell/` provides no agent-context script
(only `check-prerequisites`, `common`, `create-new-feature`, `setup-plan`, `setup-tasks`), so that
step is a no-op, exactly as in Phase 11. No root `CLAUDE.md` exists; guidance lives in
`.specify/memory/constitution.md` and `docs/`.

### Post-design Constitution re-check

Re-evaluated after the artifacts above — **PASS**, same single justified partial. Three design
choices were made specifically to keep it there:

- **D1's narrowing** (decode-only, `:teacherApp`-only) keeps the new dependency out of every student
  binary, so Principle IV's "platform code is a thin edge" and FR-042's no-student-change are both
  provable by reading `:shared`'s dependency block.
- **D4's content-tagged paths** turn FR-035's atomicity from a runtime ordering promise into a
  property of the naming scheme, which is what lets a split stay all-or-nothing (FR-019) without a
  transaction spanning two services.
- **Frame parsing in `commonMain`, decoding in `:teacherApp`** confines the only
  untestable-without-a-JVM code to the decoder edge; every rule that can be wrong in an interesting
  way is a pure function with `commonTest` coverage.

Two notes for `/speckit-tasks`:

1. The `SeedAudio` field addition touches shipped student code. Sequence it first, land it with the
   existing seed/loader tests passing **unchanged**, and keep it additive-with-defaults so bundled
   JSON parses byte-identically.
2. **Two** Stitch fetches are required (Principle VIII), not one, and each blocks its own UI work:
   *Upload Matn (Per-Verse)* (`4f1bee3d7518487b986c7c63cb3c07ff`) for the verse row's audio column,
   which Phase 11 deliberately left unimplemented, and *Upload Matn (Timestamp Map)* for the split
   screen. Both must be explicit tasks, with deviations recorded in this feature's design notes.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| **New dependency: `javazoom:jlayer:1.0.1`** (LGPL 2.1, `:teacherApp` only) | The waveform (FR-013) and preview (FR-022–FR-024) need PCM, and `javax.sound.sampled` ships no MP3 codec — `DesktopAudioEngine`'s own KDoc records this. Cutting and duration were removed from the requirement by writing the frame parser ourselves, so the dependency is decode-only. | **ffmpeg bundled**: ~70 MB per platform inside an MSI/DMG/DEB, three platform binaries to ship and update, and a subprocess boundary for what is a pure computation. **`mp3spi` + `tritonus`**: three jars instead of one, and it registers an MP3 codec *globally* through the `javax.sound.sampled` SPI — if it ever reached `:shared`'s classpath it would silently change `DesktopAudioEngine`'s documented behaviour in the student desktop app, which SC-015 forbids. **Writing a decoder**: thousands of lines of DSP and a correctness surface worse than the rest of the phase combined. LGPL is satisfied by consuming the published jar unmodified, which is what a Gradle dependency does — no fork, no static relinking. |
| **Bucket ceiling relaxed 5 MB → 10 MB** for `matn-content` | FR-004 admits a 10 MB per-verse recording, and FR-004b requires the server to be at least as permissive as the tool, or a file the tool accepted would fail on upload. A bucket has exactly one `file_size_limit`. | **A second bucket for audio** contradicts the recorded one-bucket decision (Phase 11 `design-notes.md`, storage-swap decision 4) and doubles the policy surface. **Per-path size enforcement in RLS** would have to read `metadata->>'size'`, which is not reliably populated at insert time. The cost is precise and small: a cover image may now be up to 10 MB server-side instead of 5 MB, with FR-016's 5 MB check still enforced client-side, and the mime allowlist still rejecting a non-image at a cover path. |
| **Principle VI partial: remote source of truth, online-only tool** | Unchanged from Phase 11 — drafts and their audio are remote so the teacher is not tied to one machine, and FR-018 forbids the source recording being copied anywhere durable. | Same as Phase 11: a local-first producer needs sync and merge machinery for a single-user tool. The student-facing offline guarantee is untouched — this phase adds nothing a student device reads. |
| **Content-tagged object names instead of one fixed path per verse** | A fixed path plus `x-upsert` mutates the object a published matn is currently serving, so a reader could receive the new audio for verse 12 and the old for verse 13 — exactly what FR-035 prohibits. Content tags make every change a new object and the row write the single commit point. | **Upload to the fixed path and accept the window**: violates FR-035 outright for published matns. **A staging prefix then copy on commit**: doubles bytes transferred or needs a server-side copy per verse, and still needs the tag scheme for resume. **A monotonic generation counter**: has to be stored somewhere, which is precisely the progress record FR-019b forbids. |
