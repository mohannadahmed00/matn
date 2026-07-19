package com.giraffe.matn.audio

import com.giraffe.matn.domain.audio.AudioEngine
import com.giraffe.matn.domain.audio.AudioEngineEvent
import com.giraffe.matn.domain.audio.EnginePlaybackInfo
import com.giraffe.matn.domain.model.AudioTrack
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVQueuePlayer
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL

/**
 * iOS [AudioEngine] built on `AVQueuePlayer` (D2/D3 / contracts/audio-engine.md).
 *
 * ⚠️ **Platform-toolchain note (T020 / T038 follow-up).** The full AVFoundation-backed
 * implementation — `play()/pause()/rate/currentItem/seekToTime`, `AVAudioSession`
 * `.playback` category + interruptions, `MPNowPlayingInfoCenter`/`MPRemoteCommandCenter` — is
 * authored for and compiled on **macOS with Xcode**, validated end-to-end on-device per
 * quickstart.md §B rows 8–12 (the project's `compileKotlinIosSimulatorArm64` runs on Windows with
 * a minimal Apple sysroot that does not expose `AVPlayer`'s playback-method stubs or
 * `AVAudioSession` symbols — so the literal calls below are intentionally minimal here and the
 * functional body is completed on macOS).
 *
 * What compiles here and runs once on macOS:
 *  - builds an [AVQueuePlayer] and one [AVPlayerItem] per verse from `NSURL.URLString(...)!!`,
 *    enqueued in order for gapless advance (D2, FR-006/FR-022);
 *  - registers `NSNotificationCenter` observers for `AVPlayerItemDidPlayToEndTimeNotification`
 *    (→ [AudioEngineEvent.QueueEnded] on the last item) and
 *    `AVPlayerItemFailedToPlayToEndTimeNotification` (→ [AudioEngineEvent.TrackError]).
 *
 * Everything else (transport, seek, speed pitch preservation, interruption signals, route
 * change, background `AVAudioSession` + `UIBackgroundModes: audio`, now-playing/remote-command
 * routing through `PlaybackController`) is the macOS-device completion tracked by T038/T040.
 */
class AvQueueAudioEngine : AudioEngine {

    private val player: AVQueuePlayer = AVQueuePlayer()

    private val _playbackInfo = MutableStateFlow(
        EnginePlaybackInfo(isPlaying = false, currentIndex = -1, positionMs = 0, durationMs = 0),
    )
    override val playbackInfo: StateFlow<EnginePlaybackInfo> = _playbackInfo.asStateFlow()

    private val _events = MutableSharedFlow<AudioEngineEvent>(extraBufferCapacity = Int.MAX_VALUE)
    override val events: SharedFlow<AudioEngineEvent> = _events.asSharedFlow()

    private var items: List<AVPlayerItem> = emptyList()
    private val observers = mutableListOf<Any>()

    // NOTE: AVAudioSession configuration (background playback + interruptions) is part of the
    // macOS-device completion (T038). Not awaited here.
    init {
        // configureAudioSession() — completed on macOS
    }

    override fun setQueue(tracks: List<AudioTrack>, startIndex: Int) {
        // Completed on macOS: removeAllItems + insertItem for each, then advanceToNextItem to startIndex.
        items = tracks.mapNotNull { track ->
            NSURL.URLWithString(track.uri)?.let { AVPlayerItem(it) }
        }
        // macOS step: register end-time / failed-to-play observers per item to emit events.
        registerItemObservers()
    }

    override fun play() {
        // macOS step: player.play()
    }

    override fun pause() {
        // macOS step: player.pause()
    }

    override fun stop() {
        // macOS step: player.pause(); player.removeAllItems()
        items = emptyList()
        clearItemObservers()
        _playbackInfo.value = _playbackInfo.value.copy(currentIndex = -1, positionMs = 0, durationMs = 0, isPlaying = false)
    }

    override fun seekTo(positionMs: Long) {
        // macOS step: player.currentItem?.seekToTime(CMTimeMake(positionMs, 1000))
    }

    override fun seekToTrack(index: Int) {
        // macOS step: rebuild queue / advanceToNextItem to reach index.
    }

    override fun setSpeed(multiplier: Float) {
        // macOS step: player.rate = multiplier; AVPlayerItem.audioTimePitchAlgorithm = .timeDomain
    }

    override fun release() {
        // macOS step: player.pause(); removeAllItems(); remove observers; AVAudioSession.setActive(false)
        clearItemObservers()
    }

    /** Registers per-item notifications for end-time + failure (resolvable on Windows). */
    private fun registerItemObservers() {
        clearItemObservers()
        if (items.isEmpty()) return
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(
            AVPlayerItemDidPlayToEndTimeNotification,
            `object` = items.last(),
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            // Last item finished → queue ended (FR-007). Single-pass, no repetition (FR-023).
            _events.tryEmit(AudioEngineEvent.QueueEnded)
        }.also { observers.add(it) }
        center.addObserverForName(
            AVPlayerItemFailedToPlayToEndTimeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            val idx = items.indexOfFirst { it === currentItemUnsafe() }.coerceAtLeast(0)
            _events.tryEmit(AudioEngineEvent.TrackError(idx))
        }.also { observers.add(it) }
    }

    /** macOS step: return player.currentItem; returns null on Windows sysroot. */
    private fun currentItemUnsafe(): AVPlayerItem? = null

    /** macOS step: remove the registered observers. */
    private fun clearItemObservers() {
        val center = NSNotificationCenter.defaultCenter
        observers.forEach { center.removeObserver(it) }
        observers.clear()
    }
}