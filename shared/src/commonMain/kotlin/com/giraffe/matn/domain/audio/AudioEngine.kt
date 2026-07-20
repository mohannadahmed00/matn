package com.giraffe.matn.domain.audio

import com.giraffe.matn.domain.model.AudioTrack
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The seam between the shared session logic and the native players (contracts/audio-engine.md /
 * D1). A pure-Kotlin interface in `commonMain`; its concrete implementations
 * (`Media3AudioEngine` on Android, `AvQueuePlayer` on iOS) hold **no business logic** — they
 * translate primitives to the native player and translate native callbacks to
 * [AudioEngineEvent]s. All playback *decisions* live in `PlaybackController` (`commonMain`).
 *
 * Injected via `initMatnKoin(driverFactory, audioEngine, wakeLock)` (mirrors the existing
 * `DatabaseDriverFactory` seam); a `FakeAudioEngine` implements this same interface for
 * `commonTest` (Principle V).
 */
interface AudioEngine {
    /** Immutable snapshot of the native player (polled/pushed to the controller). */
    val playbackInfo: StateFlow<EnginePlaybackInfo>

    /** Discrete one-shot signals (transitions, completion, errors, interruptions). */
    val events: SharedFlow<AudioEngineEvent>

    /** Load the ordered playlist; enables gapless pre-buffering (D2). [startIndex] seeks first. */
    fun setQueue(tracks: List<AudioTrack>, startIndex: Int)

    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun seekToTrack(index: Int)
    fun setSpeed(multiplier: Float)
    fun release()

    /**
     * Replace every item AFTER the currently playing one with [tracks].
     * MUST NOT interrupt, re-prepare, or re-buffer the currently playing item.
     * Called on every window refill and on every live counter/range change.
     */
    fun replaceUpcoming(tracks: List<AudioTrack>)

    /**
     * Drop every item BEFORE the currently playing one, keeping the playlist bounded.
     * MUST NOT interrupt the currently playing item. The engine re-bases its own index;
     * the controller re-bases `window` in lockstep.
     */
    fun dropConsumed()
}

/** A point-in-time snapshot of the native player, surfaced to the controller. */
data class EnginePlaybackInfo(
    val isPlaying: Boolean,
    val currentIndex: Int,
    val positionMs: Long,
    val durationMs: Long,
)

/** Discrete engine signals the controller reduces into [com.giraffe.matn.domain.model.PlaybackState]. */
sealed interface AudioEngineEvent {
    /** Native player gaplessly moved to a new item (FR-005/FR-006). */
    data class TrackTransition(val newIndex: Int) : AudioEngineEvent
    /** The last item in the queue finished (FR-007). */
    data object QueueEnded : AudioEngineEvent
    /** An item failed to load/play — controller skips it with a notice (FR-020). */
    data class TrackError(val index: Int) : AudioEngineEvent
    /** Audio focus/headphones/route change began an interruption (FR-018/FR-019). */
    data class InterruptionBegan(val transient: Boolean) : AudioEngineEvent
    /** A prior interruption ended; [shouldResume] is the OS-reported transient-resume hint. */
    data class InterruptionEnded(val shouldResume: Boolean) : AudioEngineEvent
    /** Queue prepared (LOADING → PLAYING). */
    data object Ready : AudioEngineEvent
}