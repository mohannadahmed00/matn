# Implementation Plan: Phase 2 — Core Audio Playback

**Branch**: `003-core-audio-playback` | **Date**: 2026-07-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-core-audio-playback/spec.md`

## Summary

Phase 2 turns the Phase 1 static reading surface into a guided **read-along recitation**. A single
app-scoped **`PlaybackController`** (in `commonMain`) drives one playback session over an injected
**`AudioEngine`** interface: it builds the matn's ordered per-verse queue (from the Phase 0
`verse` + `audio_asset` model), starts playback from a chosen verse, advances **gaplessly** verse
to verse until the matn ends then stops (plain continuous — no repetition, that is Phase 3),
exposes play / pause / resume / stop, next / previous, in-verse scrub, and a discrete
0.5×–1.5× speed, and **skips a missing/unreadable verse with a brief notice** (FR-020). The
reading screen highlights the active verse and auto-scrolls to keep it in view.

Technical approach: the **session state machine, queue building, boundary/skip/resume-policy
logic** all live once in `commonMain` (Principle IV) behind an `AudioEngine` **interface**
(Principle I), so they are unit-testable against a **`FakeAudioEngine`** in `commonTest` with no
device or real audio (Principle V). The genuinely platform-specific parts are thin `expect`/`actual`
edges with **no business logic** (Principle IV): the audio engine itself (**Media3 ExoPlayer** on
Android — gapless, `MediaSession`, audio-focus; **AVQueuePlayer** on iOS — gapless queue,
`AVAudioSession`), background playback (Android `MediaSessionService` foreground service; iOS
`audio` background mode + Now-Playing/remote commands), the **screen wake lock**, and OS
**interruption** signals. The engine is obtained through a platform-provided **`AudioEngineFactory`**
injected exactly like the existing `DatabaseDriverFactory` seam. `PlaybackController` is a Koin
`single` that the reading screen's `MatnDetailsViewModel` and a new `PlayerBarViewModel` both read
(one `StateFlow<PlaybackState>`) and forward intents to — no per-screen playback logic (Principle
III). The only new dependency is **Media3** (androidMain); iOS uses AVFoundation. No schema change —
one additive ordered-audio query. Sample per-verse audio is bundled under `composeResources/files/`
so the phase is validated end to end, offline. Session state is intentionally **in-memory only**;
its shape is designed to be exactly what Phase 4 will persist.

## Technical Context

**Language/Version**: Kotlin 2.4.10 (Kotlin Multiplatform), JVM target 11 for Android.

**Primary Dependencies**: Existing — Compose Multiplatform 1.11.1 (UI/Material 3), AndroidX
Lifecycle ViewModel/runtime 2.11.0-beta01, CMP Navigation 2.9.2, Koin 4.0.0,
kotlinx-coroutines/Flow 1.9.0, SQLDelight 2.0.2. **New**: **AndroidX Media3 1.4.x** on
`androidMain` only — `media3-exoplayer` (gapless engine + audio focus), `media3-session`
(`MediaSession` + `MediaSessionService` for background + notification/lock-screen controls),
`media3-common`. iOS uses first-party **AVFoundation** (`AVQueuePlayer`, `AVAudioSession`) +
**MediaPlayer** (`MPNowPlayingInfoCenter`, `MPRemoteCommandCenter`) — no new dependency. Each new
dependency is justified in [research.md](./research.md).

**Storage**: Reuses the Phase 0 SQLDelight `ContentDatabase` read-only. Adds **one additive query**
`selectAudioForMatnOrdered` (join `audio_asset` → `verse` for `display_number` order) plus an
`AudioAssetRepository.getAudioForMatn(...)` method. **No schema change** to
`matn`/`chapter`/`verse`/`audio_asset`/`app_setting`. **No new persisted entity** — the playback
session is in-memory (persistence is Phase 4, FR-023).

**Testing**: `kotlin.test` + `kotlinx-coroutines-test` + `koin-test` in `commonTest`. The
`PlaybackController` state machine (advance-on-completion, skip-on-error, stop-at-end, next/previous
index math + boundaries, transient-vs-non-transient resume, speed-persists-across-transitions,
start-from-verse) is proven against a **`FakeAudioEngine`** that emits scripted events — no device,
emulator, or real audio files. Gapless flow, real audio-focus, wake lock, background service, and
notification controls are **platform behaviours validated on-device** via
[quickstart.md](./quickstart.md).

**Target Platform**: Android (minSdk 24, compileSdk 36) + iOS (iosArm64, iosSimulatorArm64), single
shared source in `commonMain`; default (light) theme + phone layout only (dark mode / tablet remain
Phase 8, FR-023).

**Project Type**: Mobile — Kotlin Multiplatform shared library (`:shared`) consumed by
`:androidApp` and `iosApp`. This phase adds a `playback` presentation/domain slice inside
`:shared`, an `expect`/`actual` audio engine + wake-lock edge, Media3 on `androidMain`, a
foreground `MediaSessionService`, and platform manifest/Info.plist entries.

**Performance Goals**: Verse-to-verse transitions are gapless — no perceptible gap/click in 100% of
auto-advances (SC-002); the active-verse highlight + auto-scroll track audio within **1 s** of a
verse starting (SC-003); transport controls (play/pause/resume/stop/next/previous) take visible
effect within **300 ms** (SC-004); speed changes apply immediately without a restart (SC-005).

**Constraints**: Fully offline, zero network (FR-021/SC-010); gapless across separate per-verse
files (FR-006/SC-002 — the locked one-file-per-verse model, FR-022); plain single-pass playback,
**no repetition** (FR-007/FR-023); interruptions pause gracefully, transient auto-resume /
non-transient manual (FR-018/FR-019/SC-009); screen stays awake only while playing (FR-017/SC-008);
background playback continues with media-notification controls (FR-015/FR-016/SC-007); missing audio
skips with a notice, never crashes (FR-020); session state is in-memory only this phase (FR-023).

**Scale/Scope**: One `PlaybackController` + `AudioEngine` interface (+ `FakeAudioEngine` for tests) +
2 platform `actual` engines, 1 `AudioEngineFactory` (expect/actual), a wake-lock edge (expect/actual),
1 Android `MediaSessionService`, 1 new `PlayerBarViewModel` + stateless player-bar composables,
highlight/auto-scroll wiring into the existing reading screen, 1 queue-building use case, 1 additive
repository method + SQL query, and bundled sample per-verse audio. Content scale: matn up to ~500
verses (queue of ~500 tracks); a handful of seeded متون.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design — still PASS.*

| Principle | Applies to Phase 2? | How this plan complies |
|-----------|---------------------|------------------------|
| **I. Clean Architecture & Layer Boundaries** | Yes | Playback logic depends on an `AudioEngine` **interface** and `AudioAssetRepository`/`VerseRepository` interfaces — never on ExoPlayer/AVQueuePlayer types. Queue building is a use case; `PlaybackController` orchestrates over interfaces. ViewModels reach it only through the controller + use cases, never data-layer classes. Dependency direction presentation → domain ← data(+platform engine) upheld. |
| **II. MVVM Presentation (NON-NEGOTIABLE)** | Yes (**core**) | The player bar and highlight render one immutable `StateFlow<PlaybackState>` (exposed by the controller, surfaced through `PlayerBarViewModel` and the existing `MatnDetailsViewModel`); composables emit intents only. ViewModels/controller reference no Compose/`Context`/platform-UI type. Stateless `PlayerBarContent` + a thin stateful holder, with **`@Preview`s for playing / paused / loading / error / hidden** states (blocking review item). |
| **III. DRY via Base Abstractions** | Yes | Reuses `BaseViewModel`, `UseCase`/`FlowUseCase`, `Resource`/`AppError`. The **single** `PlaybackController` is the one source of session state — the reading screen and player bar both consume it, so no playback logic is duplicated per screen. Position/speed/active-verse derivation lives in exactly one reducer. |
| **IV. Shared-First Multiplatform** | Yes | The session state machine, queue building, advance/boundary/skip/resume-policy, and speed handling live once in `commonMain`. `expect`/`actual` is reserved for the audio engine, `AudioEngineFactory`, wake lock, and interruption signals — the platform `actual`s wrap ExoPlayer/AVQueuePlayer primitives and **emit events only, holding no business logic**. Zero logic duplicated across `androidMain`/`iosMain`. |
| **V. Test-First & Testable Design (NON-NEGOTIABLE)** | Yes | `AudioEngine` is an interface injected into `PlaybackController`; a `FakeAudioEngine` drives `commonTest` unit tests for every decision the spec names (advance, skip-on-error, stop-at-end, next/previous boundaries, transient/non-transient resume, speed persistence, start-from-verse). No device/emulator/audio needed. Tests land with the code. Platform-only behaviour (gapless, focus, wake lock, service) is device-validated (quickstart). |
| **VI. Offline-First & Future-Proof Data** | Yes (**forward-designed**) | All audio comes from bundled per-verse files; zero network (FR-021). Phase 2 keeps session state **in-memory only** (FR-023) but the `PlaybackState` deliberately carries exactly what Phase 4 must persist — `matnId`, active `verseId`, millisecond `positionMs`, and `speed` (the constitution's "Continue Learning" fields) — so Phase 4 adds persistence at a ready seam without reshaping it. Reuses stable-UUID Phase 0 entities. A scoped deferral per the roadmap (Phase 4 depends on 1–3), not a violation. |
| **VII. Experience Fidelity: Audio, RTL & Accessibility** | Yes (**the audio slice — core of this principle**) | Delivers the contractual audio guarantees: **gapless** verse-to-verse via deliberate pre-buffering (ExoPlayer gapless playlist / AVQueuePlayer pre-enqueue) (FR-006); **interruption handling** — pause on focus loss, transient auto-resume, never silently die (FR-018/FR-019); pitch-preserved speed (FR-014); active-verse highlight + auto-scroll on the native-RTL reading surface (FR-008/FR-009). Dark mode / tablet-adaptive layouts remain Phase 8 (FR-023) — scoped deferral. |

**Additional constraints**
- **Stack** matches the constitution: KMP + Compose Multiplatform (Material 3), MVVM, Koin DI, base
  package `com.giraffe.matn`. Audio is accessed only through the domain `AudioEngine` interface; the
  concrete engines (ExoPlayer / AVQueuePlayer) live in the platform layer, exactly as the
  constitution's Technology Constraints require.
- **Per-verse audio model is honoured**: the engine is fed an ordered list of one-file-per-verse
  tracks; gapless is achieved by pre-buffering the next file, never by seeking within a shared file
  (FR-022).
- **DI**: `contentModule()` gains the `PlaybackController` (`single`), the queue use case, and the
  `PlayerBarViewModel` factory; the platform shell provides `AudioEngineFactory` in DI just as it
  already provides `DatabaseDriverFactory` — no manual singletons across layer boundaries.
- **New dependency** (Media3, androidMain only) is justified in [research.md](./research.md) against
  the simpler rejected alternative (raw `MediaPlayer`/`AudioTrack`), per the constitution.

**Result**: ✅ PASS — no violations. Complexity Tracking table below is empty.

## Project Structure

### Documentation (this feature)

```text
specs/003-core-audio-playback/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output — audio engine, gapless, background, focus decisions
├── data-model.md        # Phase 1 output — PlaybackState/Track models, additive audio query
├── quickstart.md        # Phase 1 output — automated (commonTest) + on-device validation guide
├── contracts/           # Phase 1 output
│   ├── audio-engine.md   # AudioEngine expect interface, events, platform actual obligations
│   └── playback-contract.md # PlaybackController state/intents, ViewModel + platform integration
├── checklists/          # (pre-existing — requirements.md)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

Existing KMP layout (`:shared` consumed by `:androidApp` + `iosApp`). This phase adds a `playback`
slice under the existing `com.giraffe.matn` base package, an audio-engine `expect`/`actual` edge, a
wake-lock edge, Media3 on `androidMain`, a foreground `MediaSessionService`, and platform manifest /
Info.plist entries. Phase 0/1 domain, data, and reading UI are reused; the reading screen gains
highlight + auto-scroll and hosts the player bar.

```text
shared/
├── build.gradle.kts                              # + media3 deps on androidMain; (no common dep)
├── src/
│   ├── commonMain/
│   │   ├── composeResources/
│   │   │   └── files/audio/                       # bundled sample per-verse .mp3 (fileRef → uri)
│   │   ├── sqldelight/com/giraffe/matn/db/
│   │   │   └── Content.sq                         # + selectAudioForMatnOrdered (additive query)
│   │   └── kotlin/com/giraffe/matn/
│   │       ├── domain/
│   │       │   ├── model/                          # + PlaybackState, PlaybackStatus, PlaybackSpeed,
│   │       │   │                                   #   AudioTrack, PlaybackQueue
│   │       │   ├── audio/                          # AudioEngine (interface), AudioEngineEvent,
│   │       │   │                                   #   WakeLock + NoOpWakeLock, AudioSourceResolver
│   │       │   ├── repository/                     # AudioAssetRepository += getAudioForMatn
│   │       │   └── usecase/                        # BuildPlaybackQueueUseCase
│   │       ├── data/
│   │       │   ├── repository/                     # AudioAssetRepositoryImpl += getAudioForMatn
│   │       │   └── audio/                          # AudioSourceResolverImpl (Res.getUri("files/audio/.."))
│   │       ├── playback/                           # NEW app-scoped session orchestrator
│   │       │   └── PlaybackController.kt            # StateFlow<PlaybackState>; queue+engine driver
│   │       ├── presentation/
│   │       │   ├── player/                          # PlayerBarViewModel, PlayerBarUiState,
│   │       │   │                                   #   PlayerBar (stateless content) + previews
│   │       │   └── details/                         # MatnDetailsViewModel/Screen += activeVerseId
│   │       │                                       #   highlight + auto-scroll; per-verse play intent
│   │       └── di/
│   │           └── ContentModule.kt                # + controller, queue use case, PlayerBarViewModel
│   ├── androidMain/kotlin/com/giraffe/matn/
│   │   ├── audio/                                  # Media3AudioEngine (impl), MatnMediaSessionService
│   │   │                                           #   (foreground), AndroidWakeLock (FLAG_KEEP_SCREEN_ON)
│   │   └── AndroidManifest.xml                     # + service + FOREGROUND_SERVICE(_MEDIA_PLAYBACK),
│   │                                               #   POST_NOTIFICATIONS, WAKE_LOCK
│   ├── iosMain/kotlin/com/giraffe/matn/
│   │   ├── audio/                                  # AvQueueAudioEngine (impl), IosWakeLock
│   │   │                                           #   (isIdleTimerDisabled)
│   └── commonTest/kotlin/com/giraffe/matn/
│       ├── playback/                               # PlaybackControllerTest + FakeAudioEngine
│       └── data/                                   # getAudioForMatn ordered-query test
```

`:androidApp` — `MainActivity` constructs `Media3AudioEngine(applicationContext)` (and later
`AndroidWakeLock(this)`) and passes them into `initMatnKoin(...)` alongside the existing
`DatabaseDriverFactory` (same injection seam). `iosApp` — `Info.plist` gains
`UIBackgroundModes: [audio]`; `MainViewController` constructs `AvQueueAudioEngine()` (and later
`IosWakeLock()`) and passes them into `initMatnKoin(...)`. Thin entry points otherwise unchanged.

**Structure Decision**: Kotlin Multiplatform shared library. All playback **logic** (session state
machine, queue building, transport decisions, speed, boundaries, skip-on-error, resume policy) lives
in `shared/src/commonMain` under `playback/`, `domain/{model,audio,usecase}`, and the additive data
query. Only the audio engine, its factory, the wake lock, and the Android foreground service are
`expect`/`actual` platform code holding **no business logic**. No new Gradle module; Media3 is added
to `androidMain` only.

> **Platform-edge delivery (supersedes the `expect`/`actual` sketch above and in `contracts/`).**
> The audio-engine and wake-lock edges are delivered as plain `commonMain` **interfaces**
> (`AudioEngine`, `WakeLock`) whose concrete implementations live in `androidMain`/`iosMain` as
> ordinary classes and are handed to the shared Koin graph through an extended
> `initMatnKoin(driverFactory, audioEngine, wakeLock)` — exactly the existing `DatabaseDriverFactory`
> injection seam. This is simpler than `expect`/`actual` (no compile-coupling; every phase stays
> green) and satisfies Principle IV identically (the platform classes hold no business logic).
> `tasks.md` is the authority on this approach; wherever this plan or `contracts/` say
> `expect class AudioEngineFactory` / `expect class WakeLock`, read "platform-provided interface
> implementation injected via `initMatnKoin`".

## Complexity Tracking

> No Constitution Check violations — this table is intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
