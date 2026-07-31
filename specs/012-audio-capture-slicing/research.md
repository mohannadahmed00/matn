# Research: Phase 12 — Audio Capture & Slicing

Twelve decisions. Each is a choice the spec left open on purpose (it says WHAT, not HOW) or a
consequence of the constitution that had to be worked out before design.

---

## D1 — MP3 support on the JVM

**Decision**: `javazoom:jlayer:1.0.1`, declared by **`:teacherApp` only**, used **for decoding
only**. Cutting, duration, and profile detection use a frame parser written in `commonMain`.

**Rationale**: The phase needs PCM for exactly two things — waveform peaks (FR-013) and preview
playback (FR-022). Everything else people reach for a codec to do turns out not to need one:

| Task | Needs a decoder? | How it is done |
|------|------------------|----------------|
| Duration of a file | No | Sum of frame durations from the frame index |
| Sample rate / channels | No | First frame header |
| Cutting at a boundary | No | Byte copy between frame offsets |
| Waveform peaks | **Yes** | JLayer, streamed |
| Preview playback | **Yes** | JLayer → `SourceDataLine` |

JLayer is a single ~100 KB jar with no transitive dependencies, pure Java, and its `Bitstream` /
`Decoder` pair gives frame-at-a-time PCM, which is what D7's bounded-memory peak extraction needs.

**Alternatives considered**:

- **Bundled ffmpeg** (the roadmap's other named candidate): ~70 MB of platform binaries inside every
  MSI/DMG/DEB, three artifacts to keep updated, plus a subprocess boundary and its error handling
  for what is otherwise in-process computation. Rejected on distribution weight and operational
  surface, not capability.
- **`mp3spi` + `tritonus-share` + JLayer**: makes `AudioSystem.getAudioInputStream` open MP3
  directly, which would let preview reuse `DesktopAudioEngine` verbatim. Rejected for a specific
  reason: SPI registration is **global to the classpath**. `DesktopAudioEngine` lives in
  `shared/jvmMain` and its documented behaviour — "ships no MP3 codec, a track it cannot open emits
  `TrackError`" — is student-desktop behaviour that SC-015 requires to be unchanged. Putting an MP3
  SPI anywhere near `:shared` changes that silently. Three jars for a behaviour change we do not
  want.
- **Writing a decoder**: an MP3 decoder is thousands of lines of DSP with a correctness surface
  worse than everything else in this phase put together.

**Licensing note**: JLayer is LGPL 2.1. Consumed as an unmodified published jar (an ordinary Gradle
dependency), which is the arrangement LGPL is written for. Do not fork it or shade it into a
relinked artifact without revisiting this.

**Environment note**: unlike Phase 11's dependencies, JLayer is **not** currently in the local Gradle
cache (`~/.gradle/caches/modules-2/files-2.1` has no `javazoom` entry). The first build after adding
it needs network access to Maven Central.

---

## D2 — Frame-boundary cutting: how accurate, and what has to be stripped

**Decision**: snap each verse boundary to the nearest frame start. Emit slices consisting of whole
frames only, with **ID3v2 tags and any Xing/Info/VBRI header frame removed**.

**Rationale**: An MPEG-1 Layer III frame carries 1152 samples, so at 44.1 kHz one frame is
**26.12 ms** and at 48 kHz **24 ms**. Snapping to the nearest frame therefore moves a boundary by at
most half a frame — ~13 ms — and never more than 26 ms even rounding the wrong way. SC-004 allows
50 ms, so this is inside tolerance with room to spare, and no re-encoding is needed to hit it
(spec Q1).

Two things must be removed from a slice or the artifact is wrong rather than merely imperfect:

- **A Xing/Info/VBRI header** is a fake first frame declaring the *whole source's* frame count and
  duration. Copied into a slice it makes every player report the source's length for a 6-second
  verse. It is detected by the `Xing`/`Info`/`VBRI` marker at the standard offset inside the first
  frame and dropped.
- **ID3v2 at the head, ID3v1 at the tail** of the source are metadata about the source, not the
  verse. The frame index skips a leading ID3v2 block by its size header; a trailing 128-byte ID3v1
  block is excluded from the last frame's extent.

**The bit-reservoir caveat, stated rather than hidden**: Layer III frames may borrow up to 511 bytes
of "main data" from preceding frames. A slice's first frame can therefore reference data that is no
longer present, producing a brief artifact confined to the first frame or two (≈50 ms) of a slice.
This is the same trade every frame-accurate MP3 splitter makes (mp3splt and friends), it is
inaudible for spoken-word content at the boundary of a verse — where there is a natural pause — and
the alternative is decoding and re-encoding, which spec Q1 rejected. Accepted, documented in the
split contract, and worth revisiting only if a teacher reports an audible click.

**Alternatives considered**: sample-accurate cutting via decode → cut → re-encode (rejected by
Q1: generation loss, an encoder dependency, minutes per matn); gapless-metadata padding tricks
(LAME tags describing encoder delay) — irrelevant here because we are not producing new encodes.

---

## D3 — Where the encoding profile lives

**Decision**: three new fields on the audio object inside the `verses` jsonb — `sizeBytes`,
`sampleRate`, `channels` — mirrored on `SeedAudio` with defaults.

**Rationale**: FR-005b needs "the matn's established profile" available without downloading a file,
and FR-007 needs the audio's size to compute the declared download size. Both are properties of the
stored file, known at upload time, and tiny.

Putting them in the jsonb costs **no migration** — `chapters`/`verses` are opaque documents to
Postgres, and `MatnRow`'s `AudioRow` already round-trips them. Adding matching optional fields to
`SeedAudio` (all with defaults) keeps FR-009's one-to-one correspondence true, and existing bundled
seed JSON continues to parse byte-identically because kotlinx-serialization fills the defaults.

The matn's profile is then simply *the profile of its first audio-bearing verse* — derived, not
declared, so it cannot drift, and a matn with no audio has no profile (which is what makes the
spec's "remove everything, then attach a different profile" edge case resolve cleanly).

**Alternatives considered**: a `profile` column on `public.matns` (a migration, plus a second source
of truth that can disagree with the files); detecting the profile by range-requesting the first
1 KB of an existing verse file on session start (a network round trip on every editor open, and
useless when offline mid-edit).

---

## D4 — Object naming and atomicity

**Decision**: `matns/{matnId}/verses/{verseId}-{tag}.mp3`, where `tag` is a stable 64-bit hash
(FNV-1a, pure Kotlin, `commonMain`) of the exact bytes being stored, rendered as 16 hex characters.

**Rationale**: Phase 11's cover images use one deterministic path per matn plus `x-upsert`, which
mutates the object in place. For audio that is not safe. A published matn is being read while it is
being corrected, and in-place overwrite means a reader can get verse 12's new recording and verse
13's old one — precisely FR-035's prohibition.

Content tagging fixes it without a transaction across Postgres and Storage:

- **New content ⇒ new object name.** Uploading never disturbs what readers are currently served.
- **The row write is the commit.** Before it, readers see the old `fileRef`s; after it, the new ones.
  There is no intermediate state, because the row is written once (FR-019, FR-035).
- **The superseded object is deleted after the commit** (D6), never before.
- **Resume falls out for free** (D5), because the name is a function of the content.
- **`verseId` stays in the path** so two verses with byte-identical audio still get distinct
  objects — FR-006 forbids two verses resolving to one file, and V5 `DuplicateAudioRef` would flag
  it.

FNV-1a is chosen over a cryptographic hash because this is a cache/identity key, not a security
boundary, and because a pure-Kotlin implementation keeps it in `commonMain` with no dependency and
no `expect`/`actual`. Throughput is ~1 GB/s; hashing a 100 MB matn's worth of slices is not
measurable next to uploading them.

**Alternatives considered**: fixed path + upsert (breaks FR-035 for published matns); a staging
prefix copied on commit (doubles transfer or needs a per-verse server-side copy, and still needs
tags for resume); a stored generation counter (that is the progress record FR-019b forbids).

---

## D5 — Resume without a progress record

**Decision**: before uploading, list the matn's audio prefix once (`listWithSizes`) and treat an
expected object that is already present, **at its content-tagged name and with the expected byte
size**, as done.

**Rationale**: FR-019b forbids a separate progress record, and D4 makes one unnecessary. The
expected name is computed from the bytes the operation is about to produce, so:

- Re-running the **same** split produces the same names → already-uploaded slices are skipped
  (FR-019a), and this works after a restart because nothing about it is session state.
- Re-running a **different** split (a different source, or different ranges) produces different
  names → nothing is skipped, and a leftover object from an abandoned attempt cannot be mistaken for
  current work (FR-019c). The size check is a cheap second guard against a truncated upload.
- Per-verse attaching needs no listing at all: the matn's own `fileRef` already records what
  succeeded, because each attach commits on its own.

One honest limitation, recorded rather than papered over: after a restart the markers are gone
(FR-018), so the teacher must re-mark before re-running. If they reproduce the same ranges, resume
skips the already-uploaded slices; if they mark differently, the work re-uploads, which is correct.
The restart guarantee is therefore real but conditional on the plan being reproduced — the spec's
"same source recording with the same ranges" (FR-019c) is exactly this condition, and the content
tag is how it is tested without storing anything.

---

## D6 — Deleting superseded objects

**Decision**: delete after the row write returns success; treat a failed delete as a logged
non-event.

**Rationale**: This is the spec's Q2 answer expressed as an ordering rule. Deleting first would let
a failed write leave a live matn pointing at nothing. Deleting after leaves, in the worst case, an
object nobody references — storage cost, not corruption (FR-033a/b). No sweeper is built this phase;
`design-notes` records the debt.

The delete set is computed as part of the same commit plan: `objects referenced before` minus
`objects referenced after`. That single rule covers replacement, removal, verse deletion, and a
re-split — none of them need their own cleanup path (Principle III).

---

## D7 — Waveform peaks for a 4-hour recording

**Decision**: one streaming decode pass; accumulate min/max per bucket; discard PCM as it is
consumed. Bucket count is a function of the drawn width, not of the file length.

**Rationale**: A 4-hour 44.1 kHz stereo decode is ~2.5 GB of PCM — unholdable. JLayer decodes frame
by frame, so peaks can be folded in as frames arrive with a bounded working set (one frame of
samples plus the peaks array). At ~2000 buckets the peaks array is 16 KB regardless of whether the
recording is 3 minutes or 4 hours.

Zooming recomputes from a cached **coarse** peak array where possible and re-decodes only the
visible window when the zoom exceeds the coarse resolution. The decode runs off the UI thread on
`Dispatchers.Default`, publishing progress, so FR-041's frame budget is unaffected by a long load.

**Alternatives considered**: deriving amplitude from frame side-info or bitrate without decoding
(fast but wrong — CBR files would render as a flat bar, which is worse than no waveform); decoding
the whole file to a temp PCM file (turns a memory problem into a 2.5 GB disk problem).

---

## D8 — Slicing a large source without loading it

**Decision**: build a frame index (offsets + cumulative durations) in one pass, then produce each
slice by reading only its byte range from the source file, one slice at a time.

**Rationale**: The frame index for 4 hours at 26.12 ms/frame is ~550 000 entries; as a `LongArray`
of offsets that is ~4.4 MB, which is fine, and it makes "ms → byte offset" an array lookup. Slices
are then plain byte-range copies, so peak memory is one slice (≤10 MB), not one source (≤300 MB).

The index and the ms→frame arithmetic are pure functions over a `ByteSource`-style abstraction in
`commonMain`; the JVM side supplies a `RandomAccessFile`-backed implementation. That is what lets
`commonTest` exercise the arithmetic against **synthesized** frame headers — valid MPEG-1 Layer III
headers with silent payloads, constructed byte-by-byte in the test — with no fixture file and no
decoder.

---

## D9 — Gapless preview

**Decision**: a teacher-side `PreviewPlayer` that opens **one** `SourceDataLine` and writes
successive verses' decoded PCM into it, rather than one line per verse.

**Rationale**: FR-023 wants no audible gap, and SC-008 makes it measurable. Opening and draining a
line per verse gives an audible seam at every boundary. One continuously-open line has no seam at
all, because the boundary is just the next `write()`.

This is only legal because of FR-005: every verse file in a matn shares a sample rate and channel
layout, so one line configured once accepts all of them. The spec's uniformity rule and the
gaplessness requirement turn out to be the same requirement viewed from two ends — which is a good
sign the Q1 answer was right.

"Which verse is sounding" (FR-024) is derived from bytes written versus each verse's byte length, so
the indicator tracks the line rather than a timer.

**Why not reuse `DesktopAudioEngine`**: it cannot open MP3 by design (D1), it is student-facing code
SC-015 requires to stay unchanged, and its per-track line handling is exactly the seam this decision
avoids. Preview also needs to report a *missing* verse (FR-025), which is an authoring concept the
student engine has no business knowing.

Preview reads from a **local cache** of downloaded verse files keyed by object name. Because
content-tagged names are immutable (D4), a cached file is never stale and the cache needs no
invalidation logic.

---

## D10 — Bucket limits

**Decision**: one migration — add `audio/mpeg` to `allowed_mime_types` and raise
`file_size_limit` from 5 MB to 10 MB.

**Rationale**: FR-004b requires the server to be at least as permissive as the tool, or an accepted
file fails on upload. The tool's per-verse ceiling is 10 MB. The **300 MB continuous ceiling never
needs a server-side counterpart**, because the source recording is never uploaded (FR-018) — a nice
consequence of the slicing model: the largest object the bucket will ever see is one verse.

The cost is stated in the plan's Complexity Tracking: a cover image may now be up to 10 MB
server-side rather than 5 MB, since a bucket has a single limit. FR-016's 5 MB check remains
client-side, and the mime allowlist still prevents an audio file from being stored at a cover path
or vice versa.

---

## D11 — Waveform direction under a right-to-left interface

**Decision**: the waveform timeline runs **left-to-right in both interface languages**. The numeric
start/end fields, labels, and surrounding chrome mirror with the UI as everything else does.

**Rationale**: The spec's edge case asks for "the direction the teacher expects for a timeline",
consistent between waveform and fields. Audio timelines are left-to-right in every editor a teacher
has used, including Arabic-localized ones; mirroring the *waveform* would also mirror the meaning of
dragging a marker "forward". Mirroring the chrome while keeping the timeline LTR is the arrangement
that keeps both the tool's language rules (FR-039) and the teacher's muscle memory intact.

This is a deviation from a naive reading of FR-039's mirroring rule and is recorded in this
feature's design notes, as Phase 11 recorded its own chrome deviations.

---

## D12 — Flipping V4/V5 to blocking without touching student behaviour

**Decision**: move `MissingAudio` and `DuplicateAudioRef` from `ValidationReport.deferred` to
`ValidationReport.blocking` inside `ContentIntegrityValidator`, and change nothing in
`ContentSeedLoaderImpl`.

**Rationale**: The risk is that this validator is shared with the student app's ingestion path, so a
classification change could alter what the student app rejects — an SC-015 violation. It cannot,
because of how Phase 11 wired it:

```kotlin
return (report.blocking + report.deferred).filterNot {
    it is ContentIntegrityError.EmptyMatn || it is ContentIntegrityError.DocumentTooLarge
}
```

The loader **flattens both lists**, so moving an error between them is invisible to it. Student
ingestion keeps treating a missing audio reference exactly as it does today. The blocking/deferred
split is, as that code's comment already says, the teacher tool's policy — and this phase is where
the policy changes.

Consequences on the teacher side, which are the point: `ValidateMatnUseCase` reports the errors as
blocking, `PublishMatnUseCase` refuses (FR-028), and `SaveDraftUseCase` refuses for a matn that is
currently published and would become incomplete (FR-030).

**Verification**: the existing `ContentIntegrityValidatorTest`, `ContentSeedLoader` tests, and
`ProgressRepositoryTest` (the pre-existing student test that Phase 11's V8/V9 filter was added to
protect) must pass **unchanged**. If any of them needs editing, this decision is wrong and the flip
needs a caller-supplied policy parameter instead.
