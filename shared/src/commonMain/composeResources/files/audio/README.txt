Phase 2 sample per-verse audio
================================

This folder hosts the short, correctly-trimmed per-verse `.mp3` clips referenced by the
Phase 0 seed `fileRef`s in `shared/src/commonMain/kotlin/com/giraffe/matn/di/MatnKoinStarter.kt`.

Expected files (must match the seed exactly):
  - ajurrumiyya_verse_001.mp3
  - ajurrumiyya_verse_002.mp3
  - ajurrumiyya_verse_003.mp3
  - ajurrumiyya_verse_004.mp3
  - structured_verse_001.mp3
  - structured_verse_002.mp3
  - structured_verse_003.mp3   <-- INTENTIONALLY OMITTED to exercise the FR-020 skip-on-missing
                                   runtime path on device (T002). Its `audio_asset` row is still
                                   seeded; only the file is absent.
  - structured_verse_004.mp3
  - structured_verse_005.mp3

HOW TO PROVIDE THE AUDIO (manual content-prep step):
  These clips are teacher recordings produced one-file-per-verse (FR-022). Drop the real,
  correctly-trimmed MP3s into this directory before running the on-device Phase 2 walkthrough
  (quickstart.md §B). Until real clips are supplied, the bundled placeholder files here let
  the build produce resources and let the app resolve URIs; ExoPlayer/AVQueuePlayer will fail
  to decode placeholders, which itself exercises the `TrackError` → `SkippedMissingVerse` path
  (FR-020) — but for the genuine gapless-flow §B checks real audio is required.

Omitted file (FR-020 skip path): structured_verse_003.mp3