# Phase 2 Quickstart: Validating Core Audio Playback

How to prove core audio playback works end to end. Two validation surfaces: **device-free unit
tests** for the session state machine / queue logic (Principle V, primary gate) against a
`FakeAudioEngine`, and a **manual on-device walkthrough** for the platform-only guarantees —
gapless flow, background playback, wake lock, real interruptions — that cannot be asserted
headlessly.

See [contracts/audio-engine.md](./contracts/audio-engine.md),
[contracts/playback-contract.md](./contracts/playback-contract.md), and
[data-model.md](./data-model.md) for the exact types and rules referenced below.

## Prerequisites

- Phase 0 + Phase 1 complete: SQLDelight content store, repositories, the reading screen, and at
  least one **simple** and one **structured** matn seeded.
- **Sample per-verse audio bundled** under `shared/src/commonMain/composeResources/files/audio/`
  matching the seed `fileRef`s (e.g. `ajurrumiyya_verse_001.mp3` … `structured_verse_005.mp3`) —
  short, correctly-trimmed clips so gapless flow is audible (D7). Include a matn/verse with a
  **deliberately missing** file to exercise the skip path (FR-020).
- Toolchain from Phase 0/1 (Kotlin 2.4.10 KMP, Compose Multiplatform, Koin, SQLDelight) + **Media3**
  on `androidMain`.

## A. Automated validation (`commonTest`, no device)

```
./gradlew :shared:allTests            # or :shared:testDebugUnitTest for the Android host
```

Drive `PlaybackController` with `FakeAudioEngine` (emit scripted `AudioEngineEvent`s) and a
`FakeWakeLock`/faked repositories — no device, emulator, audio, or network:

| Scenario | Assert | Requirement |
|----------|--------|-------------|
| Start from a verse | `playFromVerse` builds a queue with the right `startIndex`; `state.activeVerseId` = that verse; status `PLAYING` | FR-001 |
| Gapless advance | on `TrackTransition(n)` → `activeVerseId`/`activeIndex` move to verse n; no stop/replay call to the engine | FR-005/FR-006 |
| End of matn | `QueueEnded` → status `ENDED`, highlight cleared, `notice = ReachedEnd`, **no** loop/restart | FR-007 |
| Pause / resume | `pause()` → `PAUSED(USER)`, position held; `resume()` → `PLAYING` from same position | FR-002/FR-003 |
| Stop | `stop()` → `IDLE`, `activeVerseId == null`, position 0 | FR-004 |
| Next / previous | `next()` advances one; `previous()` after >2s (or at index 0) restarts current, else steps back | FR-011/FR-012 |
| Next at last / previous at first | `next()` at last → `ENDED`; `previous()` at index 0 → restart, never negative index | FR-012 |
| Scrub | `seekTo(pos)` sets `positionMs`; reaching end triggers the same advance | FR-013 |
| Speed persists | `setSpeed(X0_75)` then a `TrackTransition` → `state.speed == X0_75`, re-applied to engine | FR-014 |
| Missing/unreadable audio | `TrackError(i)` → skip to i+1 + `notice = SkippedMissingVerse`; none left → `ENDED` + `NoPlayableAudio` | FR-020 |
| Transient interruption | `InterruptionBegan(transient=true)` → `PAUSED(TRANSIENT)`; `InterruptionEnded(resume=true)` → `PLAYING` | FR-019 |
| Non-transient interruption | `InterruptionBegan(transient=false)` → `PAUSED(NON_TRANSIENT)`; end → stays paused | FR-019 |
| User pause not auto-resumed | after `pause()`, an `InterruptionEnded(resume=true)` does **not** resume | FR-019 (edge) |
| Wake lock | `FakeWakeLock.acquire` on entering `PLAYING`, `release` on `PAUSED`/`ENDED`/`IDLE` | FR-017 |
| Queue ordering | `BuildPlaybackQueueUseCase` returns tracks in ascending `displayNumber`; records missing-audio gaps | FR-005/FR-020 |

All of the above land in the same change as the code (Principle V).

## B. Manual on-device walkthrough (Android + iOS)

```
./gradlew :androidApp:installDebug     # Android device/emulator
# iOS: open iosApp in Xcode and run on a device/simulator
```

Platform-only guarantees — verify on **both** platforms:

| # | Steps | Expected | Requirement |
|---|-------|----------|-------------|
| 1 | Open a matn, tap a verse's play | Recitation starts from that verse; verse highlighted; list auto-scrolls to it | FR-001/FR-008/FR-009, SC-001/SC-003 |
| 2 | Let it play across several verses | Transitions are **gapless** — no click/silence between files; highlight follows | FR-006, SC-002 |
| 3 | Reach the last verse | Playback stops at the end; no loop | FR-007 |
| 4 | Pause, then resume | Resumes from the same position; highlight retained | FR-002/FR-003, SC-004 |
| 5 | Tap next / previous rapidly | Settles on the correct verse and plays; no stacked audio | FR-011/FR-012 |
| 6 | Scrub within a verse | Audio seeks to the point; scrubbing to the end advances gaplessly | FR-013, SC-006 |
| 7 | Cycle speed 0.5×→1.5× | Speed changes immediately, pitch stays natural, persists across verses | FR-014, SC-005 |
| 8 | Background the app / lock screen while playing | Audio continues; media notification / lock-screen shows play-pause + next/previous and controls it | FR-015/FR-016, SC-007 |
| 9 | While playing in foreground | Screen stays awake; after pause/stop, normal timeout returns | FR-017, SC-008 |
| 10 | Trigger a phone call (or play another app's audio), then end it | Playback pauses; transient → auto-resumes, call/other-app → stays paused for manual resume | FR-018/FR-019, SC-009 |
| 11 | Unplug/disconnect Bluetooth or headphones while playing | Playback pauses (no speaker blast); resumes manually | FR-018, SC-009 |
| 12 | Play a matn containing the deliberately missing audio file | Playback skips that verse with a brief notice and continues; never crashes/stalls | FR-020 |
| 13 | Enable airplane mode, repeat 1–3 | Everything plays from bundled files; zero network | FR-021, SC-010 |
| 14 | Inspect the player bar previews in the IDE | `PlayerBarContent` renders playing / paused / loading / hidden states without a ViewModel | Principle II |

## Definition of done (Phase 2)

- [ ] All `commonTest` scenarios in §A pass headlessly and ship with the code (Principle V).
- [ ] The §B walkthrough passes on **both** Android and iOS — gapless, background+notification, wake
      lock, and interruption/resume all behave per the table.
- [ ] Constitution Check re-confirmed PASS; no new dependency beyond justified Media3 (androidMain).
- [ ] Session state (`matnId`/`activeVerseId`/`positionMs`/`speed`) exists in `PlaybackState` and is
      **not** yet persisted (that is Phase 4) — the seam is present, unused.
