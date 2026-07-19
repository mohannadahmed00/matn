# Contract: PlaybackController, ViewModels & Platform Integration — Phase 2

The shared session orchestrator and the MVVM surface over it. `PlaybackController` owns the session
and the state machine (data-model.md §6); ViewModels are thin readers/forwarders (Principle II);
platform shells provide the engine factory and the wake-lock/service edges. All logic is in
`commonMain`; only the noted edges are `expect`/`actual` (Principle IV).

## PlaybackController (commonMain/playback — Koin `single`, D8)

```
class PlaybackController(
    private val engine: AudioEngine,                 // platform impl injected via initMatnKoin
    private val buildQueue: BuildPlaybackQueueUseCase,
    private val wakeLock: WakeLock,                   // expect/actual edge (D10)
    private val scope: CoroutineScope,               // app-scoped (SupervisorJob + Main)
) {
    val state: StateFlow<PlaybackState>              // single source of truth (Principle III)

    fun playFromVerse(matnId: String, verseId: String)   // FR-001 (per-verse play)
    fun playFromStart(matnId: String)                    // FR-001 (global play → index 0 / last-selected)
    fun pause()                                          // PauseReason.USER (FR-002)
    fun resume()                                         // FR-002/FR-003
    fun stop()                                           // → IDLE, clear highlight (FR-004)
    fun next()                                           // FR-011/FR-012
    fun previous()                                       // FR-012 (restart-current vs prev)
    fun seekTo(positionMs: Long)                         // within active verse (FR-013)
    fun setSpeed(speed: PlaybackSpeed)                   // FR-014
    fun consumeNotice()                                  // clear one-shot PlaybackState.notice
}
```

### Responsibilities (the tested state machine)
- Build the `PlaybackQueue` via `buildQueue`, call `engine.setQueue(...)`, reduce engine
  `events`/`playbackInfo` into `PlaybackState` per data-model.md §6.
- **Advance** on `TrackTransition` (update `activeVerseId`/`activeIndex`); **stop** on `QueueEnded`
  (`ENDED`, clear highlight, `notice = ReachedEnd`) — no repetition (FR-007).
- **Skip** on `TrackError`: `seekToTrack(index+1)`, `notice = SkippedMissingVerse`; if none remain →
  `ENDED`, `notice = NoPlayableAudio` (FR-020).
- **Resume policy** on interruption events (D5): pause with the right `PauseReason`; auto-`resume()`
  only when a **transient** interruption ends; never auto-resume a `USER` pause.
- **Boundaries**: `next()` at last index → `ENDED`; `previous()` restarts the current verse when
  `positionMs > ~2s` or already at index 0, else steps back (FR-012).
- **Speed** retained across transitions (re-applied to the engine) (FR-014).
- **Wake lock**: acquire when entering `PLAYING`, release on `PAUSED`/`ENDED`/`IDLE` (FR-017).
- Owns a throttled position ticker feeding `positionMs` (scrub + highlight cadence), not per-frame.

## Wake-lock edge (plain interface injected via `initMatnKoin` — D10)

> **Final approach (per `tasks.md`) — supersedes the `expect class WakeLock` sketch.**

```
// commonMain — plain interface + no-op default
interface WakeLock {
    fun acquire()   // Android: FLAG_KEEP_SCREEN_ON ; iOS: isIdleTimerDisabled = true
    fun release()   // restore normal screen timeout (SC-008)
}
object NoOpWakeLock : WakeLock { /* default binding until US3 supplies the real ones */ }
```
No business logic — pure platform call. Platform impls (`AndroidWakeLock`, `IosWakeLock`) are
ordinary classes in `androidMain`/`iosMain`, passed into `initMatnKoin(driverFactory, audioEngine, wakeLock)`
and bound as `single<WakeLock> { wakeLock }`. US1/US2 bind `NoOpWakeLock`; US3 swaps the binding to
the platform implementation (binding change only — no `PlaybackController` ctor/signature churn).

## ViewModels (Principle II — read state, emit intents)

### `PlayerBarViewModel : BaseViewModel<PlayerBarUiState>` (new)
- Maps `PlaybackController.state` → `PlayerBarUiState` (data-model.md §5.2): `visible = hasSession`,
  status, active verse number, position/duration, speed, `canNext`/`canPrevious`.
- Intents → controller: `onPlayPause`, `onStop`, `onNext`, `onPrevious`, `onSeek(pos)`,
  `onSpeedSelected(speed)` (FR-002/FR-010/FR-011/FR-013/FR-014).

### `MatnDetailsViewModel` (extended — D9)
- Additionally collects `PlaybackController.state` → sets `activeVerseId` + `isPlaying` in
  `MatnDetailsUiState` (FR-008).
- New intents: `onVersePlayClicked(verseId)` → `controller.playFromVerse(matnId, verseId)` (FR-001);
  `onGlobalPlayClicked()` → `controller.playFromStart(matnId)`.
- Existing Phase 1 responsibilities (header, verses, TOC, font size) unchanged.

## Screen behaviour contracts

- **Reading screen** (`MatnDetailsContent`, extended): highlights the row where
  `id == activeVerseId`; on `activeVerseId` change, `animateScrollToItem` to keep it in view using
  the existing header/TOC index offset (FR-008/FR-009, SC-003). Each verse row shows a **play**
  affordance (FR-001). The **player bar** is hosted at the bottom of this screen, visible only while
  a session exists (FR-010). No network (FR-021).
- **Player bar** (`PlayerBarContent`, stateless): play/pause, stop, previous, next, a scrub bar bound
  to `positionMs`/`durationMs` within the active verse (FR-013), and a speed selector cycling the
  five `PlaybackSpeed` steps (FR-014). Pure function of `PlayerBarUiState`.
- **`@Preview` coverage (Principle II, blocking)**: `PlayerBarContent` previews at least
  **playing**, **paused**, **loading**, and **hidden/ended** states with hand-built state — no
  ViewModel/DI. Reading-screen preview gains a variant with a non-null `activeVerseId` (highlighted
  row).

## DI wiring (`ContentModule.kt`, additive)

```
// in initMatnKoin(driverFactory, audioEngine, wakeLock) startup module — platform-provided:
single<AudioEngine> { audioEngine }                            // Media3AudioEngine / AvQueueAudioEngine
single<WakeLock> { wakeLock }                                  // AndroidWakeLock / IosWakeLock (US3; NoOpWakeLock before)
// in contentModule() — shared:
factory { BuildPlaybackQueueUseCase(get(), get(), get()) }     // verse repo, audio repo, resolver
single<AudioSourceResolver> { AudioSourceResolverImpl() }
single { PlaybackController(get(), get(), get(), scope) }      // app-scoped session
factory { PlayerBarViewModel(get()) }                          // reads the controller
single<AudioAssetRepository> { AudioAssetRepositoryImpl(get()) } // now also getAudioForMatn
```
- The platform shell constructs the `AudioEngine` and `WakeLock` implementations and passes them
  into `initMatnKoin(...)` — the same injection seam already used for `DatabaseDriverFactory` (no
  service locator across layers, no `expect`/`actual`).

## Platform manifest / config obligations

- **Android** (`:shared` `androidMain` `AndroidManifest.xml`, merged into `:androidApp`): declare
  `MatnMediaSessionService` (`android:foregroundServiceType="mediaPlayback"`, `exported="false"`,
  the `MediaSessionService` intent filter); permissions `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK`. Request
  `POST_NOTIFICATIONS` at runtime on Android 13+.
- **iOS** (`iosApp/Info.plist`): add `UIBackgroundModes` array containing `audio`.

## Test contract (`commonTest`, Principle V)

`PlaybackControllerTest` + `FakeAudioEngine` assert every edge in data-model.md §6:
start-from-verse sets the right `startIndex`; `TrackTransition` advances the highlight; `QueueEnded`
→ `ENDED` (no loop, FR-007); `TrackError` skips one + notice, none-left → `NoPlayableAudio`
(FR-020); `next` boundary → `ENDED`, `previous` at index 0 / after 2s restarts current (FR-012);
transient interruption auto-resumes, non-transient & user do not (FR-019); `setSpeed` persists
across a subsequent `TrackTransition` (FR-014); `stop` clears session + highlight (FR-004); wake
lock acquired on `PLAYING` and released on `PAUSED`/`ENDED` (asserted via a `FakeWakeLock`).
`BuildPlaybackQueueUseCaseTest` asserts display-number ordering, `startIndex` resolution, and
missing-audio gap recording.
