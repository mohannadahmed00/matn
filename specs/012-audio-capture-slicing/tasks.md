---

description: "Task list for Phase 12 — Audio Capture & Slicing"
---

# Tasks: Phase 12 — Audio Capture & Slicing

**Input**: Design documents from `specs/012-audio-capture-slicing/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`

**Tests**: REQUIRED. Constitution Principle V is NON-NEGOTIABLE — new domain/data behaviour lands
with tests in the same change.

**Organization**: grouped by user story so each is independently implementable and testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel — different files, no dependency on an incomplete task
- **[Story]**: `[US1]`…`[US5]`, mapping to the user stories in `spec.md`
- Every task names its exact file path

---

## GROUND RULES — read before starting any task

Not style advice. Breaking any one of these produces a build failure or a blocking review failure.

1. **Base package is `com.giraffe.matn`.** The directory path always mirrors the package.
2. **Never invent an API.** If a signature is not written in this file or in `contracts/`, open the
   named file and copy the real one. Read these before starting:
   - `shared/src/commonMain/kotlin/com/giraffe/matn/core/Resource.kt` — `Resource<T>`, `AppError`
   - `shared/src/commonMain/kotlin/com/giraffe/matn/core/usecase/UseCase.kt`
   - `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/MatnDraft.kt`
   - `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/CatalogRepository.kt`
   - `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/storage/StorageRestClient.kt`
   - `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/postgrest/MatnRow.kt`
   - `shared/src/commonMain/kotlin/com/giraffe/matn/data/repository/SupabaseCatalogRepository.kt`
   - `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/di/TeacherModule.kt`
   - `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/common/VerseRow.kt`
3. **Dependencies are fixed.** The only new library in this phase is `javazoom:jlayer:1.0.1`, and it
   goes in **`:teacherApp` only**. Adding any other third-party library — including any MP3
   encoder, `mp3spi`, `tritonus`, or an ffmpeg wrapper — is out of scope for every task here.
4. **`:shared` gets no audio-decoding dependency.** If you find yourself needing JLayer inside
   `shared/`, the design has been misread: frame parsing is pure byte math (T010–T015) and needs no
   library. Decoding happens only in `teacherApp/.../platform/`.
5. **Tokens, never literals.** In `:teacherApp`: no raw hex colour, no bare `.dp`/`.sp`. Use
   `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, `MatnShapes.*`, `MatnSpacing.*`.
6. **No user-visible string literals in composables.** Every label goes through `TeacherStrings`.
   Add the key to the interface **and** to both `ArabicStrings` and `EnglishStrings` in the same
   task — the interface makes a missing translation a compile error. The string tasks are T052
   (US1), T068 (US2), T077 (US3), T087 (US4).
7. **MVVM split is mandatory.** Every screen is a stateless `XxxContent(state, onIntent…)` plus a
   thin `XxxScreen()` holder that only collects the `StateFlow`. No rendering logic in the holder,
   no use-case or repository call from a composable.
8. **Every state-rendering composable gets a `@Preview`** driven by hand-built sample state — no
   ViewModel, no Koin, no network, no decoder. Screen-level composables preview each key state **in
   both languages** (`PreviewScaffold(TeacherLanguage.ARABIC/ENGLISH)`).
9. **No Koin annotations on classes added to `:shared`.** `:shared`'s `ContentModule` declares
   `@ComponentScan("com.giraffe.matn")`; an annotated teacher-side class there makes every student
   app's graph demand a `SupabaseConfig` and fail the build with `KOIN-D001`. Register new shared
   classes with an explicit provider function in `TeacherModule` instead.
10. **Do not change student-app behaviour.** `:androidApp`, `:iosApp`, `:desktopApp` must build and
    behave identically. The only shipped student file this phase touches is
    `shared/.../data/seed/SeedContent.kt` (T016), and only by adding fields **with defaults**.
11. **The per-verse audio lock is absolute.** Never persist, upload, or reference the continuous
    source recording. Never store a start/end offset in `MatnRow`, `SeedMatn`, or any file. If a
    task seems to require it, stop and re-read `contracts/split-contract.md` §6.
12. **Commit ordering is a contract, not a preference.** Upload → write the row once → delete stale
    objects. Never delete before the row write. See `contracts/audio-artifact-contract.md` §4.
13. **Time values are `Long` milliseconds** everywhere. Byte sizes are `Long`. Sample rates and
    channel counts are `Int`.
14. **Run the build after each task**: `./gradlew :shared:jvmTest :teacherApp:test`. A task is not
    done until it compiles and its tests pass.

### Reference: MPEG audio frame header (needed by T010–T014)

A frame header is 4 bytes, big-endian. Bit 31 is the most significant bit of byte 0.

| Bits | Field | Values |
|------|-------|--------|
| 31–21 | sync | all 1s (`0xFF` then top 3 bits of byte 1 set) |
| 20–19 | version | `00`=MPEG2.5, `01`=reserved, `10`=MPEG2, `11`=MPEG1 |
| 18–17 | layer | `01`=Layer III, `10`=Layer II, `11`=Layer I, `00`=reserved |
| 16 | protection | `0` ⇒ a 16-bit CRC follows the header |
| 15–12 | bitrate index | see table |
| 11–10 | sample-rate index | see table |
| 9 | padding | adds 1 byte to the frame |
| 7–6 | channel mode | `00`=stereo, `01`=joint, `10`=dual, `11`=mono |

Sample rates (Hz) by index 0,1,2 (3 = reserved/invalid):

| Version | 0 | 1 | 2 |
|---------|---|---|---|
| MPEG1 | 44100 | 48000 | 32000 |
| MPEG2 | 22050 | 24000 | 16000 |
| MPEG2.5 | 11025 | 12000 | 8000 |

Layer III bitrates (kbps) by index 1..14 (0 = free, 15 = invalid):

- MPEG1: 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320
- MPEG2/2.5: 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160

Derived values for Layer III:

```text
samplesPerFrame = 1152 (MPEG1) | 576 (MPEG2, MPEG2.5)
frameLengthBytes = (samplesPerFrame / 8) * bitrateBitsPerSecond / sampleRate + padding
                 = 144 * bitrate / sampleRate + padding      (MPEG1)
                 = 72  * bitrate / sampleRate + padding      (MPEG2 / MPEG2.5)
frameDurationMs  = samplesPerFrame * 1000 / sampleRate        (26.12 ms @ MPEG1 44.1 kHz)
channels         = if (channelMode == 0b11) 1 else 2
```

Tag blocks to skip:

- **ID3v2** at the start: bytes `49 44 33` (`"ID3"`), then version(2), flags(1), size(4). The size
  bytes are *syncsafe* — 7 bits each: `size = (b0<<21) | (b1<<14) | (b2<<7) | b3`. Total block =
  `10 + size`, plus another 10 if bit 4 of flags is set (footer).
- **ID3v1** at the end: last 128 bytes begin with `54 41 47` (`"TAG"`).
- **Xing/Info** inside the first frame, at `frameStart + 4 + sideInfoSize`, where `sideInfoSize` is
  32 (MPEG1 stereo), 17 (MPEG1 mono), 17 (MPEG2/2.5 stereo), 9 (MPEG2/2.5 mono). Marker is `"Xing"`
  or `"Info"`.
- **VBRI** inside the first frame at `frameStart + 36`. Marker is `"VBRI"`.

### Reference: content tag (needed by T017)

```text
FNV-1a 64-bit:
  h = 0xcbf29ce484222325uL
  for each byte b: h = (h xor b.toULong()) * 0x100000001b3uL
  tag = h.toString(16).padStart(16, '0')
```

---

## Phase 1: Setup

**Purpose**: dependency, migration, and test fixtures that everything else needs.

- [X] T001 Add `jlayer = "1.0.1"` to `[versions]` and `jlayer = { module = "javazoom:jlayer", version.ref = "jlayer" }` to `[libraries]` in `gradle/libs.versions.toml`
- [X] T002 Add `implementation(libs.jlayer)` to the `dependencies` block in `teacherApp/build.gradle.kts` — do **not** add it to `shared/build.gradle.kts` (Ground Rule 4)
- [X] T003 Create `supabase/migrations/20260801000000_matn_content_audio_limits.sql` setting `file_size_limit = 10 * 1024 * 1024` and `allowed_mime_types = array['image/png','image/jpeg','image/webp','audio/mpeg']` on the `matn-content` bucket, per `contracts/storage-contract.md` §2
- [X] T004 [P] Create `shared/src/commonTest/kotlin/com/giraffe/matn/audio/Mp3Fixtures.kt` with a helper that **synthesizes** MP3 bytes: `fun mp3(frames: Int, sampleRate: Int = 44100, bitrateKbps: Int = 128, mono: Boolean = true, id3v2Bytes: Int = 0, xing: Boolean = false): ByteArray` — builds real 4-byte headers per the table above and fills each frame body with zeros
- [X] T005 [P] Add `teacherApp/src/test/resources/tiny.mp3` — a real MP3 of about one second, consumed only by the JLayer decode test (T042)

**Checkpoint**: `./gradlew build` succeeds and the fixtures compile.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the shared, pure, testable core. Every user story depends on this phase.

**⚠️ No user story work may begin until this phase is complete.**

### Pure MP3 format layer (`:shared`, no decoder)

- [X] T010 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/audio/Mp3Header.kt` — `data class Mp3Header(val sampleRate: Int, val channels: Int, val frameLengthBytes: Int, val samplesPerFrame: Int)` plus `fun parseHeader(bytes: ByteArray, offset: Int): Mp3Header?` returning `null` when the 4 bytes are not a valid Layer III header (use the tables in Ground Rules)
- [X] T011 [P] Create `shared/src/commonTest/kotlin/com/giraffe/matn/audio/Mp3HeaderTest.kt` covering: MPEG1 44.1 kHz mono 128 kbps gives `frameLengthBytes == 417`, padding adds 1, MPEG2 gives `samplesPerFrame == 576`, a non-sync word returns `null`, and a reserved sample-rate index returns `null`
- [X] T012 Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/audio/ByteSource.kt` — `interface ByteSource { val size: Long; suspend fun read(offset: Long, length: Int): ByteArray }` and an in-memory `ByteArrayByteSource(bytes: ByteArray)` implementation for tests
- [X] T013 Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/audio/Mp3FrameIndex.kt` — `class Mp3FrameIndex(val offsets: LongArray, val frameDurationMs: Double, val header: Mp3Header)` with `suspend fun build(source: ByteSource): Mp3FrameIndex?`; skips a leading ID3v2 block, excludes a trailing ID3v1 block, records one offset per frame, and exposes `val durationMs: Long`, `fun frameAtMs(ms: Long): Int`, `fun byteRange(startFrame: Int, endFrameExclusive: Int): LongRange`
- [X] T014 Create `shared/src/commonTest/kotlin/com/giraffe/matn/audio/Mp3FrameIndexTest.kt` using `Mp3Fixtures`: frame count matches, `durationMs` is `frames * 26.12` rounded, a 2 KB ID3v2 block is skipped, `frameAtMs` snaps to the nearest frame, and `byteRange` returns whole frames only
- [X] T015 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/audio/Mp3TagStripper.kt` — `fun isXingOrVbriFrame(bytes: ByteArray, frameOffset: Int, header: Mp3Header): Boolean` per the offsets in Ground Rules, used to drop a Xing/Info/VBRI header frame from a slice (`contracts/split-contract.md` §4)

### Persisted shape

- [X] T016 Add `sizeBytes: Long = 0L`, `sampleRate: Int = 0`, `channels: Int = 0` to `SeedAudio` in `shared/src/commonMain/kotlin/com/giraffe/matn/data/seed/SeedContent.kt` — **defaults are mandatory** so existing bundled JSON parses unchanged (Ground Rule 10); run `./gradlew :shared:jvmTest :shared:testAndroidHostTest` and confirm every existing seed/loader test passes **without edits**
- [X] T017 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/audio/AudioContentTag.kt` — `object AudioContentTag { fun of(bytes: ByteArray): String; fun objectPath(matnId: String, verseId: String, tag: String): String }` implementing FNV-1a-64 per Ground Rules and the path grammar `matns/{matnId}/verses/{verseId}-{tag}.mp3`
- [X] T018 [P] Create `shared/src/commonTest/kotlin/com/giraffe/matn/audio/AudioContentTagTest.kt`: same bytes give the same tag, one flipped byte gives a different tag, the tag is always 16 lowercase hex chars, and `objectPath` matches the grammar
- [X] T019 [P] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/audio/AudioProfile.kt` — `data class AudioProfile(val sampleRate: Int, val channels: Int)` plus `fun MatnDraft.audioProfile(): AudioProfile?` returning the profile of the first verse with audio, or `null`
- [X] T020 Extend `DraftAudio` in `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/MatnDraft.kt` with `sizeBytes: Long`, `sampleRate: Int`, `channels: Int`, and change `MatnDraft.declaredSizeBytes` to add `verses.sumOf { it.audio?.sizeBytes ?: 0L }` (`data-model.md` §3)
- [X] T021 Add the same three fields to `AudioRow` in `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/postgrest/MatnRow.kt` (defaults `0L`/`0`/`0`) and update the `AudioRow ↔ DraftAudio` mapping in the same file — no SQL migration, these live in the `verses` jsonb
- [X] T022 [P] Create `shared/src/commonTest/kotlin/com/giraffe/matn/audio/AudioProfileTest.kt`: an empty matn has `null` profile, the first audio-bearing verse establishes it, a second verse with a different profile is detectable as a mismatch, and `declaredSizeBytes` grows by the audio's `sizeBytes`
- [X] T023 Update `shared/src/commonTest/kotlin/com/giraffe/matn/remote/MatnRowTest.kt` with a round-trip case for a verse **with** audio carrying all six audio fields

### Storage surface

- [X] T024 Add three methods to `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/storage/StorageRestClient.kt` per `contracts/storage-contract.md` §1: `suspend fun download(objectPath: String): Resource<ByteArray>` (`GET /object/{bucket}/{path}`), `suspend fun delete(objectPath: String): Resource<Unit>` (`DELETE …`), `suspend fun listWithSizes(prefix: String): Resource<Map<String, Long>>` (same paginated `POST /object/list/{bucket}` loop `totalUsageBytes` already uses)
- [X] T025 Refactor `totalUsageBytes` and `listWithSizes` in `shared/src/commonMain/kotlin/com/giraffe/matn/data/remote/storage/StorageRestClient.kt` onto one private paginated listing helper — do not copy the offset loop twice (Principle III)
- [X] T026 [P] Create `shared/src/jvmTest/kotlin/com/giraffe/matn/remote/StorageRestClientAudioTest.kt` using Ktor `MockEngine`: `download` returns bytes, a 404 maps to a failure, `delete` issues `DELETE` with the bearer token, `listWithSizes` pages through two pages and merges them, and a 413 maps to `RemoteError.Quota`

### Commit plan and uploader

- [X] T027 Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/audio/VerseAudioPlan.kt` — `data class PendingUpload(val verseId: String, val objectPath: String, val bytes: ByteArray, val audio: DraftAudio)`, `data class VerseAudioPlan(val uploads: List<PendingUpload>, val skips: List<String>, val deletes: List<String>)`, and `fun buildPlan(before: MatnDraft, after: MatnDraft, payloads: List<PendingUpload>, existingObjects: Map<String, Long>): VerseAudioPlan` where `deletes` = object paths referenced in `before` but not in `after`, and a payload is a skip when `existingObjects[path] == bytes.size.toLong()` (`contracts/audio-artifact-contract.md` §5)
- [X] T028 [P] Create `shared/src/commonTest/kotlin/com/giraffe/matn/audio/VerseAudioPlanTest.kt`: an unchanged verse produces no upload, a replaced verse produces one upload and one delete, a removed verse produces only a delete, an already-present object of the right size is skipped, and one of the wrong size is **not** skipped
- [X] T029 Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/audio/VerseAudioUploader.kt` — `interface VerseAudioUploader { fun upload(plan: VerseAudioPlan): Flow<UploadProgress> }` with `sealed interface UploadProgress { data class Verse(val verseId: String, val uploadedBytes: Long, val totalBytes: Long) ; data object Done ; data class Failed(val error: AppError) }`
- [X] T030 Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/audio/DefaultVerseAudioUploader.kt` implementing T029 over `StorageRestClient`: uploads sequentially in verse order, emits per-verse progress, stops on the first failure, and is cancellable — it **must not** delete anything (deletion belongs to the repository, after the commit)
- [X] T031 [P] Create `shared/src/commonTest/kotlin/com/giraffe/matn/audio/DefaultVerseAudioUploaderTest.kt` with a fake `StorageRestClient`: progress is emitted per verse, skips are not uploaded, a mid-batch failure stops the flow and reports the error, and cancellation stops further uploads

### Repository writes

- [X] T032 Add to `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/CatalogRepository.kt`: `suspend fun attachVerseAudio(draft: MatnDraft, verseId: String, audio: DraftAudio, bytes: ByteArray): Resource<MatnDraft>`, `suspend fun removeVerseAudio(draft: MatnDraft, verseId: String): Resource<MatnDraft>`, `suspend fun applySplit(draft: MatnDraft, updates: Map<String, DraftAudio>, payloads: List<PendingUpload>): Resource<MatnDraft>`
- [X] T033 Implement the three methods in `shared/src/commonMain/kotlin/com/giraffe/matn/data/repository/SupabaseCatalogRepository.kt` following the **exact** order in `contracts/audio-artifact-contract.md` §4: (1) `listWithSizes("matns/{id}/verses/")`, (2) upload via `VerseAudioUploader`, (3) `writeRow(draft)` once with the existing `revision` precondition, (4) `delete` each stale object — a delete failure must be swallowed, never turned into a `Resource.Failure`
- [X] T034 [P] Create `shared/src/jvmTest/kotlin/com/giraffe/matn/repository/SupabaseCatalogRepositoryAudioTest.kt` with fake clients asserting the **call order** (list → upload → write → delete), that a failed write performs no delete, and that a failed delete still returns `Resource.Success`

### Wiring

- [X] T035 Add provider functions to `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/di/TeacherModule.kt` for `VerseAudioUploader` and the audio use cases as they are created — no Koin annotations on any `:shared` class (Ground Rule 9)

**Checkpoint**: `./gradlew :shared:jvmTest :shared:testAndroidHostTest :teacherApp:test` passes, and the student apps still build: `./gradlew :desktopApp:build`.

---

## Phase 3: User Story 1 — Attach a recording to a single verse (Priority: P1) 🎯 MVP

**Goal**: the teacher attaches, plays, replaces, and removes one verse's recording; it survives a
restart.

**Independent Test**: attach recordings to 3 of 5 verses, quit, reopen — all three are still there
with their durations, and the matn reports partial audio.

### Design source (blocks the UI tasks)

- [X] T039 [US1] Fetch the *Upload Matn (Per-Verse)* screen (`4f1bee3d7518487b986c7c63cb3c07ff`) through the `stitch` MCP server and record the **audio column's** regions and states in `specs/012-audio-capture-slicing/design-notes.md` — Phase 11 implemented this screen's text regions and deliberately left the audio column to Phase 12 (`specs/011-teacher-authoring-upload/design-notes.md` § *What this phase does NOT build*). **Blocks T048 and T051.** Inventing this layout is a blocking review failure (Principle VIII)

### Platform edges (JVM, JLayer)

- [X] T040 [P] [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/audio/AudioProbe.kt` — `interface AudioProbe { suspend fun probe(source: ByteSource): Resource<ProbeResult> ; suspend fun peaks(source: ByteSource, buckets: Int): Resource<FloatArray> }` with `data class ProbeResult(val durationMs: Long, val profile: AudioProfile, val frameCount: Int)`
- [X] T041 [US1] Create `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/platform/JLayerAudioProbe.kt` implementing T040: `probe` uses `Mp3FrameIndex` only (no decoding); `peaks` decodes with JLayer's `Bitstream`/`Decoder` frame by frame, folding min/max into the bucket array and **discarding each frame's PCM immediately** (`research.md` D7)
- [X] T042 [US1] Create `teacherApp/src/test/kotlin/com/giraffe/matn/teacher/JLayerAudioProbeTest.kt` against `tiny.mp3`: duration is within 50 ms of the real length, profile matches the file, and `peaks(buckets = 100)` returns 100 finite values in `-1f..1f`
- [X] T043 [P] [US1] Create `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/platform/JvmFileByteSource.kt` — a `ByteSource` over `RandomAccessFile` that never reads the whole file into memory
- [X] T044 [US1] Extend `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/platform/JvmFileChooser.kt` with `fun pickAudio(): PickedFile?` filtered to `.mp3`, returning the path and size **without copying the file**; reject over 10 MB for a per-verse pick with the limit named (FR-004, FR-004a). The extension filter is the *first* gate only — a file merely named `.mp3` still has to survive header parsing in T045

### Use cases

- [X] T045 [P] [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/AttachVerseAudioUseCase.kt` — probes the bytes, rejects a profile mismatch against `draft.audioProfile()` with an error naming both profiles (FR-005b), builds `DraftAudio` (new UUID, `fileRef` from `AudioContentTag`, measured duration, size, profile), and calls `CatalogRepository.attachVerseAudio`. Content that yields no valid first frame (`Mp3FrameIndex.build` returns `null`) is rejected with `audioWrongFormat` **before** any upload — this is the spec's "cannot read this file" case, distinct from the chooser's extension filter (FR-003, FR-004a)
- [X] T046 [P] [US1] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/RemoveVerseAudioUseCase.kt` — refuses when the draft is `PUBLISHED` (FR-030), otherwise calls `CatalogRepository.removeVerseAudio`
- [X] T047 [US1] Create `shared/src/commonTest/kotlin/com/giraffe/matn/usecase/AttachVerseAudioUseCaseTest.kt`: first attach establishes the profile, a mismatched profile is rejected and the verse keeps its previous audio, a replacement keeps `DraftAudio.id` stable (`data-model.md` A5), unparseable bytes are rejected as `audioWrongFormat` with no upload attempted (FR-003), and remove-on-published is refused

### UI

- [X] T048 [US1] Add the audio-slot states to `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/common/VerseRow.kt` — new parameters `audioState: VerseAudioUiState`, `onAttach: () -> Unit`, `onPlay: () -> Unit`, `onRemove: () -> Unit`; render Empty / Uploading(progress) / Loaded(duration) / Failed(message) per `contracts/teacher-ui-contract.md` §1; **hold no state in the row**
- [X] T049 [P] [US1] Create `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/common/VerseAudioUiState.kt` — the sealed state the row renders
- [X] T050 [US1] Extend `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/editor/EditorViewModel.kt` with attach/remove intents and a `Map<String, VerseAudioUiState>` in its state, keyed by verse id — progress belongs here, never in the row (Ground Rule 7)
- [X] T051 [US1] Add `@Preview`s for all four `VerseRow` audio states in both languages at the bottom of `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/common/VerseRow.kt`
- [X] T052 [US1] Add the US1 string keys to `TeacherStrings`, `ArabicStrings`, and `EnglishStrings` in `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/strings/`: `addRecording`, `replaceRecording`, `removeRecording`, `playRecording`, `uploadingAudio`, `audioProfileMismatch` (takes both profiles), `audioTooLarge`, `audioWrongFormat`, `audioUnreadable`, `storageFull` — the last maps from `RemoteError.Quota` so an exhausted bucket reads as a storage problem, not a generic failure (FR-040, `contracts/storage-contract.md` §1)
- [X] T053 [US1] Extend `teacherApp/src/test/kotlin/com/giraffe/matn/teacher/EditorViewModelTest.kt`: attaching sets `Uploading` then `Loaded`, a failure sets `Failed` and leaves the verse's previous audio, removing returns the row to `Empty`, and the completeness badge state follows

**Checkpoint**: US1 is fully usable — a teacher can record one verse at a time and the artifact in
storage is one object per verse.

---

## Phase 4: User Story 2 — Split one continuous recording (Priority: P1)

**Goal**: upload one recording, mark verse boundaries on a waveform, save, and get per-verse files.

**Independent Test**: split a recording covering 10 verses; 10 objects exist with the expected
durations; neither the source nor any marker is stored anywhere.

### Domain

- [X] T054 [P] [US2] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/audio/SplitPlan.kt` — `data class VerseRange(val verseId: String, val startMs: Long, val endMs: Long)`, `data class SourceRecording(val localPath: String, val sizeBytes: Long, val durationMs: Long, val profile: AudioProfile, val frameCount: Int)`, `data class SplitPlan(val source: SourceRecording, val scopeVerseIds: List<String>, val ranges: List<VerseRange>)` — **none of these are serializable and none are ever persisted** (Ground Rule 11)
- [X] T055 [US2] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/audio/SplitPlanValidator.kt` implementing rules R1–R7 from `contracts/split-contract.md` §2, returning `SplitReport(blocking: List<SplitProblem>, warnings: List<SplitProblem>)`; R7 (uncovered stretch ≥ 1 s) is the only warning
- [X] T056 [US2] Create `shared/src/commonTest/kotlin/com/giraffe/matn/audio/SplitPlanValidatorTest.kt` — one case per rule R1–R7, plus a valid plan producing no blocking problems, plus a plan whose only problem is R7 being splittable
- [X] T057 [P] [US2] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/audio/AudioSlicer.kt` — `interface AudioSlicer { suspend fun slice(source: ByteSource, index: Mp3FrameIndex, ranges: List<VerseRange>): Resource<List<VerseSlice>> }` with `data class VerseSlice(val verseId: String, val bytes: ByteArray, val durationMs: Long)`
- [X] T058 [US2] Create `shared/src/commonMain/kotlin/com/giraffe/matn/data/audio/FrameAccurateSlicer.kt` implementing T057: for each range, snap to frame boundaries via `Mp3FrameIndex`, read **only that byte range** from the `ByteSource` (never the whole file), drop a Xing/Info/VBRI first frame via `Mp3TagStripper`, and report the measured duration
- [X] T059 [US2] Create `shared/src/commonTest/kotlin/com/giraffe/matn/audio/FrameAccurateSlicerTest.kt` using `Mp3Fixtures`: each slice contains whole frames only, boundaries land within one frame duration of the request, a Xing header frame is excluded, slices concatenated cover the requested spans, and no read exceeds the range's length. **Add the byte-identity case (SC-004a)**: a slice's bytes equal the corresponding byte range of the source exactly — no re-compression, no rewritten header — asserted with `assertContentEquals` against the range `Mp3FrameIndex.byteRange` reports

### Use cases

- [X] T060 [P] [US2] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/LoadSplitSourceUseCase.kt` — probes the picked file, rejects over 300 MB or 4 hours with the limit named (FR-004), rejects a profile that mismatches the matn's, and returns `SourceRecording` plus the frame index
- [X] T061 [US2] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/ApplySplitUseCase.kt` — validates the plan (refusing if any blocking problem), slices, builds `PendingUpload`s with content-tagged paths, and calls `CatalogRepository.applySplit`; on success the plan is discarded (FR-018)
- [X] T062 [US2] Create `shared/src/commonTest/kotlin/com/giraffe/matn/usecase/ApplySplitUseCaseTest.kt`: a plan with a blocking problem never reaches the repository, a valid plan produces one payload per in-scope verse, verses outside the scope are untouched (FR-013a), and a re-run with identical ranges produces identical object paths (the resume property). Two more cases: a **materially changed range produces a different object path**, so a leftover object from an abandoned split is never skipped as finished work (FR-019c); and a split-produced `DraftAudio` is shape-identical to an attach-produced one — same fields populated, same path grammar (FR-021)

### UI

- [X] T063 [US2] Fetch the *Upload Matn (Timestamp Map)* screen through the `stitch` MCP server and append its regions and any deviation to `specs/012-audio-capture-slicing/design-notes.md` (the file T039 created) — **blocks T064–T069** (Principle VIII, `plan.md` post-design note 2). Its *architecture* stays rejected; only the layout is adopted (`contracts/split-contract.md` §6)
- [X] T064 [US2] Create `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/split/WaveformCanvas.kt` — a stateless `Canvas` composable of `(peaks: FloatArray, ranges: List<VerseRange>, durationMs: Long, selectedVerseId: String?)`; the timeline runs **left-to-right in both languages** (`research.md` D11), so wrap it in `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)`
- [X] T065 [US2] Create `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/split/MarkerLayer.kt` — drag handling that moves a boundary against the cached peaks with **no re-decode during the gesture** (FR-041)
- [X] T066 [US2] Create `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/split/SplitScreen.kt` — stateless `SplitContent(state, onIntent…)` plus a thin `SplitScreen()` holder, with the five states from `contracts/teacher-ui-contract.md` §2 and the scope picker, range fields, and problem list
- [X] T067 [US2] Create `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/split/SplitViewModel.kt` — owns the plan, re-validates on every edit, runs the split off the UI thread, and exposes upload progress; replacing the source discards all ranges with a confirmation (FR-020)
- [X] T068 [US2] Add the split string keys to all three files in `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/strings/` (`TeacherStrings.kt`, `ArabicStrings.kt`, `EnglishStrings.kt`): `splitFromRecording`, `pickRecording`, `replaceSourceRecording`, `scopeFirstVerse`, `scopeLastVerse`, `rangeStart`, `rangeEnd`, `splitAndUpload`, `sourceTooLong`, `sourceTooLarge`, plus one message per rule R1–R7 — **do not re-add `replaceRecording`**, which T052 already declared; a second declaration of the same property is a compile error
- [X] T069 [US2] Add `@Preview`s for `SplitContent` (no-source / ready-valid / ready-with-problems / splitting) and `WaveformCanvas` (with ranges, with an overlap), each in both languages
- [X] T070 [US2] Create `teacherApp/src/test/kotlin/com/giraffe/matn/teacher/SplitViewModelTest.kt` on `StandardTestDispatcher`: marker drag updates the range and re-validates, an overlap disables the split action, changing the scope drops out-of-scope ranges, and replacing the source clears every range

**Checkpoint**: both authoring paths produce byte-identical artifact shapes.

---

## Phase 5: User Story 3 — Preview the matn as a student hears it (Priority: P2)

**Goal**: gapless sequential playback of the stored verse files, from any verse, with the sounding
verse shown.

**Independent Test**: play a fully recorded matn end to end with no audible gap; remove one verse's
audio and confirm that verse is named rather than silence.

- [X] T071 [P] [US3] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/audio/PreviewPlayer.kt` — `interface PreviewPlayer { val state: StateFlow<PreviewState>; suspend fun play(verses: List<PreviewVerse>, startIndex: Int); fun pause(); fun resume(); fun stop() }` with `data class PreviewVerse(val verseId: String, val displayNumber: Int, val fileRef: String?)`
- [X] T072 [US3] Create `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/platform/PreviewCache.kt` — downloads a verse object once via `StorageRestClient.download` into the app-data dir, keyed by the object name; because names are content-tagged the cache never needs invalidation (`research.md` D9)
- [X] T073 [US3] Create `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/platform/JvmPreviewPlayer.kt` implementing T071 — opens **one** `SourceDataLine` for the matn's profile and writes successive verses' decoded PCM into it; do **not** open a line per verse (that is the seam this design exists to avoid), and derive the sounding verse from bytes written
- [X] T074 [US3] Create `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/PreviewMatnAudioUseCase.kt` — builds the `PreviewVerse` list in display order and reports a verse with `fileRef == null` as `PreviewState.MissingAudio(displayNumber)` instead of skipping silently (FR-025)
- [X] T075 [US3] Create `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/preview/PreviewBar.kt` — stateless transport rendering the five states from `contracts/teacher-ui-contract.md` §3, with `@Preview`s for idle / playing / missing-audio in both languages
- [X] T076 [US3] Wire preview into `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/editor/EditorViewModel.kt`: preview stops cleanly on any verse edit or new attachment (FR-026)
- [X] T077 [US3] Add preview string keys to all three files in `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/strings/`: `previewMatn`, `previewPlaying`, `previewPaused`, `previewMissingAudio`, `previewBuffering`
- [X] T078 [US3] Create `teacherApp/src/test/kotlin/com/giraffe/matn/teacher/PreviewStateTest.kt`: a missing verse produces `MissingAudio` naming its display number, an edit during playback produces `Idle`, and starting from verse N begins at N

**Checkpoint**: the teacher can hear the matn before publishing it.

---

## Phase 6: User Story 4 — Publish with audio complete (Priority: P2)

**Goal**: missing and duplicated recordings block publication; completeness and size are accurate.

**Independent Test**: a matn missing one recording is refused with that verse named; attaching it
allows publishing, and the stored matn records complete audio.

- [X] T079 [US4] Move `MissingAudio` and `DuplicateAudioRef` from `deferred` to `blocking` in `shared/src/commonMain/kotlin/com/giraffe/matn/domain/catalog/ContentIntegrityValidator.kt` — change nothing in `ContentSeedLoaderImpl` (`contracts/validation-contract.md` §2)
- [X] T080 [US4] Run `./gradlew :shared:jvmTest :shared:testAndroidHostTest` and confirm `ContentIntegrityValidatorTest`, the `ContentSeedLoader` tests, and `ProgressRepositoryTest` pass **without edits** — if any needs editing, stop: the flip must instead become a caller-supplied policy parameter (`contracts/validation-contract.md` §2, proof obligation)
- [X] T081 [US4] Update `shared/src/commonTest/kotlin/com/giraffe/matn/catalog/ContentIntegrityValidatorTest.kt` with new cases asserting V4/V5 are now in `blocking` and that `deferred` is empty
- [X] T082 [US4] Update `shared/src/commonMain/kotlin/com/giraffe/matn/domain/usecase/SaveDraftUseCase.kt` to refuse a save that would leave a **published** matn with any verse lacking audio (FR-030)
- [X] T083 [P] [US4] Update `shared/src/commonTest/kotlin/com/giraffe/matn/usecase/PublishMatnUseCaseTest.kt` and the save-use-case test: publishing with a silent verse is refused and names it; publishing a complete matn succeeds with `audioCompleteness == COMPLETE`
- [X] T084 [US4] Add a `recordedCount`/`totalCount` parameter to `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/common/AudioCompletenessBadge.kt` so `PARTIAL` reads "12 of 109 recorded" (FR-031) — extend the existing component, do not add a second badge (Principle VIII reuse rule)
- [X] T085 [US4] Update `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/publish/ValidationPanel.kt` to render V4/V5 with error styling rather than note styling, each row jumping to its verse
- [X] T086 [US4] Show audio completeness alongside verse count in `teacherApp/src/main/kotlin/com/giraffe/matn/teacher/presentation/publish/PublishConfirmDialog.kt`
- [X] T087 [US4] Add/adjust the string keys for the partial-count badge in the three string files; the V4/V5 message keys already exist from Phase 11 — reuse them

**Checkpoint**: no matn can be published silent.

---

## Phase 7: User Story 5 — Correct a recording after publishing (Priority: P3)

**Goal**: replacing one verse's audio on a published matn goes live without disturbing any other
verse, and no reader ever sees a broken reference.

**Independent Test**: replace one verse's recording on a published matn; it stays published, only
that verse's `fileRef` changed, and every other object is untouched.

- [X] T088 [US5] Verify and, if needed, fix that `attachVerseAudio`/`applySplit` in `SupabaseCatalogRepository` keep `publicationState` unchanged for a published matn (FR-034) and always carry the `revision` precondition (FR-037)
- [X] T089 [P] [US5] Add cases to `shared/src/jvmTest/kotlin/com/giraffe/matn/repository/SupabaseCatalogRepositoryAudioTest.kt`: replacing one verse's audio on a published matn leaves other verses' `fileRef`s byte-identical, a stale `revision` yields `RemoteError.Conflict` and performs no delete, deleting a verse with audio adds exactly that object to `deletes`, and an **unpublish → republish cycle preserves every `DraftAudio.id` and `fileRef`** so nothing that referenced them is orphaned (FR-036)
- [X] T090 [US5] Confirm re-splitting a scope updates only in-scope verses — add the case to `shared/src/commonTest/kotlin/com/giraffe/matn/usecase/ApplySplitUseCaseTest.kt` if T062 did not already cover it; verses outside the scope keep their existing `fileRef`
- [X] T091 [US5] Add a case to `shared/src/commonTest/kotlin/com/giraffe/matn/audio/AudioProfileTest.kt`: removing all audio clears the matn's profile, so the next attachment may establish a new one (spec edge case)
- [X] T091a [P] [US5] Create `shared/src/commonTest/kotlin/com/giraffe/matn/audio/VerseAudioOrderingTest.kt` — attach audio to verses 1 and 5, move verse 5 to position 2 via `VerseOrdering`, and assert each `fileRef` travelled with its own verse and display numbers renumbered 1..n (FR-032). This is true today by construction; the test is what stops a later reorder refactor from silently breaking it

**Checkpoint**: all five stories work independently.

---

## Phase 8: Polish & Cross-Cutting

- [X] T092 [P] Add the audio rows A1–A7 from `contracts/storage-contract.md` §4 to the RLS matrix test (`shared/src/jvmTest/.../RlsPolicyTest.kt`), including the assertion that `(storage.foldername(name))[2]` still resolves to the matn id at the deeper `verses/` path
- [X] T093 [P] Update `docs/ROADMAP.md`: mark Phase 12 complete and correct the "known new dependency — an MP3 decoder (JLayer/mp3spi vs. a bundled ffmpeg)" line to what actually shipped — JLayer, decode-only, `:teacherApp`-only
- [X] T094 [P] Close the constitution's deferred TODO (b) in `.specify/memory/constitution.md`'s Sync Impact Report — the Phase 12 decoder justification is discharged by `plan.md`'s Complexity Tracking
- [X] T095 [P] Record post-implementation notes in `specs/012-audio-capture-slicing/design-notes.md`: the Stitch fetch result, the LTR-timeline deviation, the bit-reservoir artifact, and anything found during the live pass
- [X] T096 Run the full `specs/012-audio-capture-slicing/quickstart.md` §3 walkthrough against the real Supabase project and record what could not be verified
- [X] T097 Confirm no student-app regression: `./gradlew :desktopApp:build :androidApp:assembleDebug` and check that `shared/build.gradle.kts` contains **no** `jlayer` reference (SC-015)

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 (Setup)**: no dependencies.
- **Phase 2 (Foundational)**: needs Phase 1. **Blocks every user story.**
- **Phase 3 (US1)**: needs Phase 2. This is the MVP. Starts with T039, the design fetch its UI tasks
  depend on.
- **Phase 4 (US2)**: needs Phase 2, and reuses US1's `AudioProbe`/uploader — start it after US1 if
  working alone.
- **Phase 5 (US3)**: needs Phase 2; useful only once US1 or US2 has produced audio.
- **Phase 6 (US4)**: needs Phase 2; independent of US1–US3 in code, but only demonstrable once audio
  exists.
- **Phase 7 (US5)**: needs Phase 2 and the repository work in T033.
- **Phase 8 (Polish)**: last.

### Hard ordering inside Phase 2

```text
T010 → T013 → T058 (header → index → slicer)
T016 must land and be verified (T080-style: existing tests unedited) before anything else touches shared/data/seed
T027 → T030 → T033 (plan → uploader → repository)
T024 → T033 (storage methods before the repository uses them)
```

### Blocking single tasks

- **T039 (Stitch fetch, Per-Verse audio column) blocks T048 and T051.** Do not write the verse row's
  audio slot before it.
- **T063 (Stitch fetch, Timestamp Map) blocks T064–T069.** Do not write split UI before it.
- **T079 (validator flip) blocks T080–T083.**
- **T016 blocks T020, T021.**

Both fetch tasks exist because Principle VIII treats an invented layout for a screen that *has* a
design as a blocking review failure — and this phase touches two designed screens, not one.

### Parallel opportunities

- T004, T005 in Phase 1.
- T010/T011, T015, T017/T018, T019 in Phase 2 — all different files.
- T026, T028, T031, T034 (test files) once their subjects exist.
- T040, T043, T045, T046 in Phase 3 (all after T039 only if they touch UI — T040–T046 do not, so
  they may run alongside the fetch).
- T054, T057 in Phase 4.
- T091a alongside T089 in Phase 7.
- All of Phase 8 except T096/T097.

---

## Parallel Example: Phase 2 foundational

```bash
# Different files, no shared state — safe together:
Task: "T010 Mp3Header in shared/src/commonMain/kotlin/com/giraffe/matn/data/audio/Mp3Header.kt"
Task: "T015 Mp3TagStripper in shared/src/commonMain/kotlin/com/giraffe/matn/data/audio/Mp3TagStripper.kt"
Task: "T017 AudioContentTag in shared/src/commonMain/kotlin/com/giraffe/matn/domain/audio/AudioContentTag.kt"
Task: "T019 AudioProfile in shared/src/commonMain/kotlin/com/giraffe/matn/domain/audio/AudioProfile.kt"
```

---

## Implementation Strategy

### MVP (User Story 1 only)

1. Phase 1 Setup.
2. Phase 2 Foundational — the largest phase, and the one everything rests on.
3. Phase 3 US1.
4. **Stop and validate**: `quickstart.md` §3.1, and inspect the bucket — one object per attached
   verse, correctly named.

At this point a teacher can record a matn verse by verse and publish it once US4 lands. The split
path is a large convenience on top, not a prerequisite.

### Incremental delivery

1. Setup + Foundational → nothing user-visible, everything testable.
2. US1 → attach audio per verse → demo.
3. US2 → split from one recording → demo (the real workflow).
4. US3 → preview → demo.
5. US4 → publish gate → the phase's terminal action.
6. US5 → corrections → completeness.

### Self-check before opening the PR

- [X] `./gradlew :shared:jvmTest :shared:testAndroidHostTest :teacherApp:test` green.
- [X] `./gradlew :desktopApp:build` green, and `shared/build.gradle.kts` has no `jlayer`.
- [X] No `.dp`/`.sp`/hex literal added in `:teacherApp`; no bare string in a composable.
- [X] Every new state-rendering composable has previews in both languages.
- [X] No file anywhere persists a start/end offset, a marker list, or the source recording.
- [X] Every audio write follows list → upload → write row → delete.
