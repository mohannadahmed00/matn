# Phase 0 Research — Core Audio Playback

Technical decisions for Phase 2. Each resolves an unknown in the plan's Technical Context. Format:
**Decision / Rationale / Alternatives considered**. These feed the Constitution Check (all PASS)
and the Phase 1 design artifacts.

---

## D1. Audio engine boundary — a `commonMain` interface, platform `actual` engines

**Decision**: Define `AudioEngine` as a pure-Kotlin **interface** in
`commonMain/domain/audio`, exposing only playback *primitives* and an event/`StateFlow` stream:
`setQueue(tracks, startIndex)`, `play()`, `pause()`, `stop()`, `seekTo(positionMs)`,
`seekToTrack(index)`, `setSpeed(speed)`, `release()`, and observable `playbackInfo:
StateFlow<EnginePlaybackInfo>` + `events: Flow<AudioEngineEvent>`. The platform `actual` engines
(Media3 on Android, AVQueuePlayer on iOS) implement it and contain **no business logic** — they
translate primitives to the native player and translate native callbacks to
`AudioEngineEvent`s. All *decisions* (which verse is next, when to stop, when to skip, resume
policy) live in `PlaybackController` (`commonMain`).

**Rationale**: Satisfies Principle I (domain depends on an interface), Principle IV (logic in
`commonMain`, platform edge thin), and Principle V (the controller is unit-testable against a
`FakeAudioEngine`). It mirrors the existing `DatabaseDriverFactory` / `AudioAssetRepository` seams
already in the codebase, so it fits the team's established pattern.

**Alternatives considered**:
- *`expect class AudioEngine` with `actual` bodies* — rejected: an `expect class` can't be faked in
  `commonTest`, breaking Principle V. An interface + `expect` **factory** keeps tests device-free.
- *Put advance/skip logic in each platform engine* — rejected: duplicates business logic across
  `androidMain`/`iosMain` (Principle IV violation) and makes it untestable in isolation.

---

## D2. Gapless playback — delegate to a native *playlist*, not manual file chaining

**Decision**: Achieve gapless verse-to-verse flow by handing the native engine the **whole ordered
queue** of per-verse tracks up front and letting it pre-buffer the next item:
- **Android (Media3 ExoPlayer)**: `setMediaItems(list)` + `prepare()`; ExoPlayer pre-buffers the
  next item and transitions gaplessly. The controller listens to
  `Player.Listener.onMediaItemTransition` to learn which verse became active.
- **iOS (AVQueuePlayer)**: enqueue all `AVPlayerItem`s; `AVQueuePlayer` advances without a gap.
  Observe `AVPlayerItemDidPlayToEndTime` / `currentItem` (KVO) to learn the active verse.

The controller updates `activeVerseId` from the engine's transition events; it does **not** stop and
start a new player per verse (which would guarantee an audible gap).

**Rationale**: Constitution Principle VII and PRODUCT-SPEC require "deliberate pre-buffering of the
next verse". Both native engines implement gapless queue playback natively; reproducing it by hand
(start next `MediaPlayer` on completion) reliably produces the click/gap the spec forbids
(SC-002). The per-verse micro-file model (FR-022) maps 1:1 to playlist items.

**Alternatives considered**:
- *One `MediaPlayer`/`AVPlayer` per verse, swapped on completion* — rejected: audible gap between
  files; no native pre-buffer; fails SC-002.
- *Concatenate files into one stream and seek* — rejected: violates the locked one-file-per-verse
  model (FR-022) and the constitution's Technology Constraints.

---

## D3. Media3 as the Android engine (only new dependency)

**Decision**: Add **AndroidX Media3 1.4.x** (`media3-exoplayer`, `media3-session`, `media3-common`)
to `androidMain` only. iOS uses first-party AVFoundation/MediaPlayer (no dependency).

**Rationale**: Media3 delivers, in one maintained library, the three hardest Phase 2 requirements
that raw platform APIs do not: (a) **gapless** playlist playback (D2), (b) **automatic audio-focus
handling** via `setAudioAttributes(..., handleAudioFocus = true)` — pause on transient loss,
duck/stop appropriately, plus `ACTION_AUDIO_BECOMING_NOISY` handling for headphone unplug (FR-018),
and (c) `MediaSession` + `MediaSessionService` giving the **background foreground-service +
media-notification/lock-screen controls** (FR-015/FR-016) largely for free. It is Google's current,
recommended media stack (supersedes ExoPlayer2 and `MediaPlayer`). Justified against a simpler
alternative per the constitution.

**Alternatives considered**:
- *`android.media.MediaPlayer` + manual `AudioManager` focus + hand-built Notification/MediaSession*
  — rejected: no gapless; must hand-roll focus, becoming-noisy, notification, and lock-screen
  transport — far more platform code and exactly the logic Media3 already hardened.
- *A KMP audio library* (e.g. a third-party multiplatform player) — rejected: adds a heavier
  cross-platform dependency, hides the native gapless/session controls we need, and offers weaker
  control than talking to ExoPlayer/AVQueuePlayer directly behind our own interface.

---

## D4. Background playback, notification & lock-screen controls

**Decision**:
- **Android**: run playback in a Media3 **`MediaSessionService`** (`MatnMediaSessionService`)
  declared in the `:shared` `androidMain` manifest and started as a foreground service while
  playing. Media3 renders the media-style notification (play/pause, next/previous) and mirrors it to
  the lock screen from the `MediaSession`. Permissions: `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (Android 14+), `POST_NOTIFICATIONS` (Android 13+, requested at
  runtime), `WAKE_LOCK`.
- **iOS**: set `AVAudioSession` category `.playback` and activate it; declare
  `UIBackgroundModes: [audio]` in `Info.plist`; publish `MPNowPlayingInfoCenter` metadata and wire
  `MPRemoteCommandCenter` (play/pause/next/previous) to the engine. AVQueuePlayer keeps playing when
  backgrounded under the `.playback` category.

The notification/remote controls issue the **same intents** into the one `PlaybackController`, so
foreground UI and background controls share a single session (FR-016).

**Rationale**: These are the OS-sanctioned mechanisms for continuing audio off-screen with system
transport controls (FR-015/FR-016/SC-007). They are inherently platform-specific → the thin `actual`
edge; no business logic lives here.

**Alternatives considered**:
- *Plain background service without a MediaSession* — rejected: Android requires a media-type
  foreground service for ongoing audio, and without a `MediaSession` there are no lock-screen/system
  controls (fails FR-016).
- *Stop audio when backgrounded* — rejected: fails FR-015/SC-007 outright.

---

## D5. Interruption & resume policy (FR-018/FR-019)

**Decision**: Map OS interruption signals to the clarified policy — **transient loss auto-resumes;
non-transient loss stays paused for manual resume**:
- **Android**: Media3's built-in focus handling pauses on `AUDIOFOCUS_LOSS_TRANSIENT` and resumes on
  gain (transient → auto-resume). On `AUDIOFOCUS_LOSS` (another app takes over) and on
  `ACTION_AUDIO_BECOMING_NOISY` (output disconnect) it pauses without auto-resume (non-transient →
  manual). The engine surfaces these as `AudioEngineEvent.InterruptionBegan(transient: Boolean)` /
  `InterruptionEnded(shouldResume: Boolean)`.
- **iOS**: `AVAudioSession` interruption notifications — `.began` → pause; `.ended` with the
  `.shouldResume` option flag → resume (transient); route-change with
  `AVAudioSessionRouteChangeReasonOldDeviceUnavailable` (unplug) → pause without resume.

`PlaybackController` records *why* it paused (`PauseReason.User` vs `.TransientInterruption` vs
`.NonTransientInterruption`) so it never auto-resumes a **user-initiated** pause (edge case) and
resumes only transient ones.

**Rationale**: Directly implements the clarified requirement and the constitution's "pause and allow
resume, never silently die". Keeping the transient/non-transient decision in the controller (fed by
a boolean the engine reports) keeps it unit-testable (Principle V) while the OS detail stays at the
edge.

**Alternatives considered**:
- *Always auto-resume* / *always manual* — rejected: contradicts the clarification (Session
  2026-07-19).
- *Let each platform engine decide resume* — rejected: policy is business logic; belongs in the
  shared controller, not duplicated in `actual`s.

---

## D6. Playback speed — discrete steps, pitch preserved

**Decision**: Model speed as a `PlaybackSpeed` enum of the clarified discrete steps
`0.5× / 0.75× / 1.0× / 1.25× / 1.5×` (default `1.0×`), held in `PlaybackState` and persisted for the
session only. Apply with pitch preserved: Android `ExoPlayer.setPlaybackParameters(PlaybackParameters(speed, 1.0f))`
(Media3 uses Sonic time-stretch, pitch unchanged); iOS `AVPlayer.rate = speed` with
`AVPlayerItem.audioTimePitchAlgorithm = .timeDomain` (or `.spectral`) to keep the recitation
natural. The selected step carries across verse transitions because the controller re-applies it to
the engine (and the native engine retains playback parameters across playlist items).

**Rationale**: Discrete steps match the clarification and the Phase 1 precedent (font size is
discrete named steps). Pitch preservation keeps the recited Arabic intelligible (FR-014). Immediate
application without a restart satisfies SC-005.

**Alternatives considered**:
- *Continuous slider* — rejected by clarification (harder to label/test).
- *Speed without pitch correction* — rejected: chipmunk/slowed pitch makes recitation unusable for
  memorization.

---

## D7. Resolving a per-verse `fileRef` to a playable source

**Decision**: Bundle sample per-verse audio under
`shared/src/commonMain/composeResources/files/audio/<fileRef>` (matching the `fileRef`s already in
the Phase 0 seed, e.g. `ajurrumiyya_verse_001.mp3`). Resolve at queue-build time with an
`AudioSourceResolver` (`commonMain`) that calls Compose Resources
`Res.getUri("files/audio/$fileRef")`, yielding a platform URI (`file:///android_asset/...` on
Android — playable via ExoPlayer's `AssetDataSource`; a bundle `file://` URL on iOS — playable by
`AVPlayerItem`). The resolver is an interface so it is faked in tests.

**Rationale**: Compose Multiplatform resources are the single cross-platform bundling mechanism
already in use (Amiri fonts, drawables live there), so audio needs no per-platform copy step or new
Gradle wiring, honouring Principle IV. Bundling real (short) sample files makes the phase
**independently testable end to end** and lets the on-device quickstart verify gapless flow — the
one thing `commonTest` cannot assert. PRODUCT-SPEC names correctly-trimmed per-verse files as a
standing content-prep dependency; Phase 2 bundles a small validated set to exercise the engine.

**Alternatives considered**:
- *Per-platform raw resources* (`res/raw` on Android, bundle resources on iOS) — rejected:
  duplicated asset management and two resolution paths; `Res.getUri` unifies both.
- *Ship no audio, mock the engine only* — rejected: the gapless/background/focus guarantees (the
  heart of the phase) can only be validated with real files playing on a device.
- *Stream/download audio* — rejected: out of scope (offline-first, FR-021; downloads are Phase 7).

---

## D8. Session ownership — one app-scoped `PlaybackController`, not a per-screen ViewModel

**Decision**: `PlaybackController` is a Koin **`single`** in `commonMain/playback`, holding the one
`AudioEngine`, the current `PlaybackQueue`, and the authoritative `StateFlow<PlaybackState>`. The
reading screen's `MatnDetailsViewModel` and a new `PlayerBarViewModel` both **read** its state and
**forward intents** (playFromVerse, pause, resume, stop, next, previous, seek, setSpeed). The
notification/remote controls also call the controller. It exposes `positionMs` via a throttled
ticker (~4–10 Hz) for the scrub bar and highlight.

**Rationale**: The session must outlive any single screen (it keeps playing when the user backgrounds
the app or navigates) and must be driven from the media notification — so it cannot be per-screen
`ViewModel` state. A single app-scoped source (Principle III, no duplication) with ViewModels as thin
readers keeps MVVM intact (Principle II — composables still render one `StateFlow` and emit intents)
while giving one place to unit-test the state machine (Principle V). Its state fields
(`matnId`/`verseId`/`positionMs`/`speed`) are precisely Phase 4's "Continue Learning" persistence
target (Principle VI, forward-designed).

**Alternatives considered**:
- *Playback state inside `MatnDetailsViewModel`* — rejected: dies when the screen leaves the back
  stack; can't back a background service or notification.
- *A global singleton object* — rejected: not injectable/fakeable, breaks Principle V and the DI
  rule against service locators across layers.

---

## D9. Active-verse highlight & auto-scroll on the existing reading screen

**Decision**: Extend `MatnDetailsUiState` with a nullable `activeVerseId` (and an
`isPlaying`/status hint) fed from `PlaybackController.state`. `MatnDetailsContent` highlights the row
whose `id == activeVerseId` and, on change, `animateScrollToItem` to keep it comfortably in view
(reusing the existing `LazyListState` + header/TOC index offset logic already in
`MatnDetailsScreen`). Each verse row gains a **play** affordance that issues `playFromVerse(verseId)`
(FR-001); a global play control lives in the player bar / header.

**Rationale**: Reuses the Phase 1 reading surface and its scroll machinery (Principle III) rather
than a new screen; highlighting is pure state → composable (Principle II) and previewable. Keeps
auto-scroll within 1 s of a verse becoming active (SC-003).

**Alternatives considered**:
- *A separate playback screen* — rejected: the spec requires read-along on the existing reading
  surface; a second surface duplicates the verse list.
- *Scroll on every position tick* — rejected: only scroll on `activeVerseId` change to avoid fighting
  the user's manual scroll and to stay smooth.

---

## D10. Screen wake lock

**Decision**: A tiny `expect`/`actual` wake-lock edge toggled by the controller's play/pause state:
Android sets/clears `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` on the current window while
playing in the foreground; iOS sets `UIApplication.shared.isIdleTimerDisabled = true` while playing,
`false` when paused/stopped. Released on pause, stop, and when playback ends (FR-017/SC-008).

**Rationale**: Keeping the screen awake for hands-free recitation is a named requirement; both
mechanisms are one-line platform calls with no logic, so the edge stays thin (Principle IV). Gating
strictly on "actively playing in foreground" restores normal timeout otherwise (SC-008).

**Alternatives considered**:
- *A partial `WAKE_LOCK` PowerManager lock* — rejected: that keeps the CPU awake for background
  audio (already handled by the foreground service), not the **screen**; the requirement is the
  screen staying on while reading.

---

## Summary of decisions → requirements

| Decision | Key requirements satisfied |
|----------|----------------------------|
| D1 engine interface + fake | FR-001..FR-013 testability (Principle V) |
| D2 native playlist gapless | FR-005/FR-006/FR-022, SC-002 |
| D3 Media3 | FR-006/FR-015/FR-016/FR-018 (Android) |
| D4 service + notification / iOS background | FR-015/FR-016, SC-007 |
| D5 interruption/resume policy | FR-018/FR-019, SC-009 |
| D6 discrete pitch-preserved speed | FR-014, SC-005 |
| D7 fileRef → bundled Res.getUri | FR-021/FR-022, SC-010, end-to-end validation |
| D8 app-scoped controller | FR-002/FR-010, Principle III/VI |
| D9 highlight + auto-scroll | FR-008/FR-009, SC-003 |
| D10 wake lock | FR-017, SC-008 |
| D5/D9 skip-on-error path | FR-020 |

All Technical Context unknowns resolved — no `NEEDS CLARIFICATION` remains. Proceed to Phase 1
design.
