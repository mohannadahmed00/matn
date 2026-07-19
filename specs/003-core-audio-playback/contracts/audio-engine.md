# Contract: AudioEngine (domain interface + platform `actual` obligations) — Phase 2

The single seam between the shared session logic and the native players. Lives in
`commonMain/domain/audio` (Principle I). Platform `actual`s (Media3 / AVQueuePlayer) implement the
*primitives* and emit *events* only — **no business logic** (Principle IV). `PlaybackController`
(commonMain) makes every decision; a `FakeAudioEngine` implements this same interface for
`commonTest` (Principle V).

## Interface (commonMain)

```
interface AudioEngine {
    // one immutable snapshot of the native player, polled/pushed to the controller
    val playbackInfo: StateFlow<EnginePlaybackInfo>
    // discrete one-shot signals (transitions, completion, errors, interruptions)
    val events: SharedFlow<AudioEngineEvent>

    fun setQueue(tracks: List<AudioTrack>, startIndex: Int)   // load playlist; enables gapless (D2)
    fun play()
    fun pause()
    fun stop()                       // release playlist, back to idle
    fun seekTo(positionMs: Long)     // within the current item (FR-013)
    fun seekToTrack(index: Int)      // next/previous jump (FR-011)
    fun setSpeed(multiplier: Float)  // pitch-preserved (D6); retained across items
    fun release()                    // tear down native player + session (onCleared/deinit)
}

data class EnginePlaybackInfo(
    val isPlaying: Boolean,
    val currentIndex: Int,
    val positionMs: Long,
    val durationMs: Long,
)

sealed interface AudioEngineEvent {
    data class TrackTransition(val newIndex: Int) : AudioEngineEvent   // active verse changed (gapless)
    data object QueueEnded : AudioEngineEvent                          // last item finished (FR-007)
    data class TrackError(val index: Int) : AudioEngineEvent           // item failed to load/play (FR-020)
    data class InterruptionBegan(val transient: Boolean) : AudioEngineEvent  // (FR-018/019)
    data class InterruptionEnded(val shouldResume: Boolean) : AudioEngineEvent
    data object Ready : AudioEngineEvent                               // queue prepared (LOADING→PLAYING)
}
```

### Semantics the controller relies on
- `setQueue` hands the engine the **whole ordered list** so it can pre-buffer the next item —
  gapless is the engine's job, not the controller's (D2). `startIndex` seeks before first play.
- `TrackTransition(newIndex)` fires **when the native player gaplessly moves to a new item**; the
  controller maps `newIndex → activeVerseId` (highlight/auto-scroll, FR-008/FR-009).
- `TrackError(index)` fires when an item can't be read; the controller advances one step and emits a
  `SkippedMissingVerse` notice (FR-020). The engine does **not** decide to skip.
- Interruption events carry the transient/non-transient bit the OS reports; the controller applies
  the resume policy (D5). The engine never auto-resumes on its own.
- `playbackInfo.positionMs` updates at a modest cadence (native listener / periodic); the controller
  throttles it (~4–10 Hz) for the scrub bar + highlight, not per-frame.

## Platform engine provisioning (interface impl injected via `initMatnKoin`)

> **Final approach (per `tasks.md`) — supersedes the `expect class AudioEngineFactory` sketch.**
> `AudioEngine` is a plain `commonMain` interface. Its concrete implementations are ordinary
> platform classes (`Media3AudioEngine` in `androidMain`, `AvQueueAudioEngine` in `iosMain`) that
> the platform shell constructs and passes into an extended
> `initMatnKoin(driverFactory, audioEngine, wakeLock)`, which registers `single<AudioEngine> { audioEngine }`.
> This mirrors the existing `DatabaseDriverFactory` seam, avoids `expect`/`actual` compile-coupling,
> and holds no business logic in the platform classes (Principle IV). No `AudioEngineFactory` type
> is created.

- **Android impl** (`Media3AudioEngine`): constructed with the application `Context` (provided by
  `MainActivity`, exactly like `DatabaseDriverFactory(this)`); builds the Media3 `ExoPlayer`
  (+ `MediaSession`, wired to `MatnMediaSessionService`).
- **iOS impl** (`AvQueueAudioEngine`): no-arg; builds the `AVQueuePlayer`-based engine and configures
  `AVAudioSession`.

## Android `actual` obligations (Media3) — no business logic

- Build `ExoPlayer` with `setAudioAttributes(AudioAttributes.Builder()
  .setUsage(USAGE_MEDIA).setContentType(CONTENT_TYPE_SPEECH).build(), handleAudioFocus = true)` →
  Media3 pauses on transient focus loss and on becoming-noisy; map its focus/`onIsPlayingChanged`
  and `AUDIOFOCUS_LOSS` callbacks to `InterruptionBegan/Ended` (D5).
- `setMediaItems(tracks.map { MediaItem.fromUri(it.uri) })` + `prepare()`; gapless is automatic (D2).
  Map `Player.Listener.onMediaItemTransition` → `TrackTransition`,
  `onPlaybackStateChanged(STATE_ENDED)` on the last item → `QueueEnded`,
  `onPlayerError`/item load failure → `TrackError(currentIndex)`.
- `setPlaybackParameters(PlaybackParameters(speed, 1.0f))` for pitch-preserved speed (D6).
- Host in `MatnMediaSessionService` (foreground while playing) so background playback + the
  media/lock-screen notification with play/pause/next/previous are provided by the `MediaSession`
  (D4, FR-015/FR-016). Transport buttons feed the same `Player` → the same controller.

## iOS `actual` obligations (AVFoundation/MediaPlayer) — no business logic

- `AVAudioSession.sharedInstance().setCategory(.playback)` + `setActive(true)`; `Info.plist`
  `UIBackgroundModes: [audio]` keeps playback alive backgrounded (D4).
- Enqueue all items in an `AVQueuePlayer` for gapless advance (D2); observe `currentItem` (KVO) →
  `TrackTransition`, `AVPlayerItemDidPlayToEndTime` on the last item → `QueueEnded`,
  item `.status == .failed` / `AVPlayerItemFailedToPlayToEndTime` → `TrackError`.
- `player.rate = speed` with `AVPlayerItem.audioTimePitchAlgorithm = .timeDomain` (pitch preserved,
  D6).
- Observe `AVAudioSession` interruption notifications → `InterruptionBegan/Ended(shouldResume)` and
  route-change (old device unavailable) → `InterruptionBegan(transient = false)` (D5).
- Publish `MPNowPlayingInfoCenter.default().nowPlayingInfo` and wire `MPRemoteCommandCenter`
  play/pause/next/previous to the engine (lock-screen/control-center transport, FR-016).

## `FakeAudioEngine` (commonTest) obligation

- Pure in-memory implementation: records `setQueue/play/pause/seekTo/seekToTrack/setSpeed` calls and
  lets tests **emit scripted `AudioEngineEvent`s** (e.g. `TrackTransition`, `QueueEnded`,
  `TrackError`, `InterruptionBegan/Ended`) to drive `PlaybackController` through every transition in
  data-model.md §6 with no device or audio. This is the primary Principle V vehicle for the phase.
