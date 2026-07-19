package com.giraffe.matn.playback

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.audio.AudioEngine
import com.giraffe.matn.domain.audio.AudioEngineEvent
import com.giraffe.matn.domain.audio.WakeLock
import com.giraffe.matn.domain.model.PlaybackNotice
import com.giraffe.matn.domain.model.PlaybackQueue
import com.giraffe.matn.domain.model.PlaybackSpeed
import com.giraffe.matn.domain.model.PlaybackState
import com.giraffe.matn.domain.model.PlaybackStatus
import com.giraffe.matn.domain.model.PauseReason
import com.giraffe.matn.domain.usecase.BuildPlaybackQueueUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * App-scoped session orchestrator (D8 / contracts/playback-contract.md). Owns the single
 * [PlaybackState] state machine (data-model.md §6): builds the queue via [BuildPlaybackQueueUseCase],
 * drives [AudioEngine] primitives, and reduces engine `events`/`playbackInfo` into state. All
 * playback *decisions* live here in `commonMain` (Principle IV); platform engines hold no logic.
 *
 * Koin `single`; both [com.giraffe.matn.presentation.details.MatnDetailsViewModel] and
 * [com.giraffe.matn.presentation.player.PlayerBarViewModel] read its state and forward intents.
 *
 * Wake-lock gating (FR-017): acquire on entering `PLAYING`, release on `PAUSED`/`ENDED`/`IDLE`.
 */
class PlaybackController(
    private val engine: AudioEngine,
    private val buildQueue: BuildPlaybackQueueUseCase,
    private val wakeLock: WakeLock,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** The queue built for the current session; null when no session. */
    private var queue: PlaybackQueue? = null

    /** Job collecting engine events; cancelled on [stop]/[release]. */
    private var eventsJob: Job? = null

    /** Job polling `playbackInfo` into `positionMs` (throttled ~8 Hz). */
    private var infoJob: Job? = null

    // ---------------------------------------------------------------- Play / pause / stop

    /** FR-001 (per-verse play): build the queue starting at [verseId] and play. */
    fun playFromVerse(matnId: String, verseId: String) {
        startSession(matnId, startVerseId = verseId)
    }

    /** FR-001 (global play): build the queue from index 0 (or last-selected; Phase 4) and play. */
    fun playFromStart(matnId: String) {
        startSession(matnId, startVerseId = null)
    }

    private fun startSession(matnId: String, startVerseId: String?) {
        cancelJobs()
        queue = null
        _state.value = PlaybackState(
            status = PlaybackStatus.LOADING,
            matnId = matnId,
            speed = _state.value.speed, // retain across sessions
        )
        scope.launch {
            val params = BuildPlaybackQueueUseCase.Params(matnId, startVerseId)
            when (val result = buildQueue.invoke(params)) {
                is Resource.Success -> {
                    val q = result.data
                    queue = q
                    // Subscribe the event/info collectors BEFORE priming the engine so a `Ready`
                    // (or an early `TrackError`) emitted before our collector subscribes is never
                    // dropped — `engine.events` is a replay=0 SharedFlow.
                    startEventCollection()
                    startInfoPolling()
                    engine.setQueue(q.tracks, q.startIndex)
                    applyActiveIndex(q.startIndex)
                    engine.setSpeed(_state.value.speed.multiplier)
                    engine.play()
                }
                is Resource.Failure -> {
                    val notice = if (result.error == AppError.NotFound) {
                        PlaybackNotice.NoPlayableAudio
                    } else null
                    _state.value = PlaybackState(status = PlaybackStatus.IDLE, notice = notice)
                }
            }
        }
    }

    /** FR-002 — pause with [PauseReason.USER]. */
    fun pause(reason: PauseReason = PauseReason.USER) {
        if (_state.value.status != PlaybackStatus.PLAYING) return
        engine.pause()
        update(status = PlaybackStatus.PAUSED, pauseReason = reason)
        releaseWakeLock()
    }

    /** FR-002/FR-003 — resume from a paused state. No-op for a USER pause is the caller's choice. */
    fun resume() {
        val s = _state.value
        if (s.status != PlaybackStatus.PAUSED) return
        engine.play()
        update(status = PlaybackStatus.PLAYING, pauseReason = null)
        acquireWakeLock()
    }

    /** FR-004 — stop and clear the session (highlight + position). */
    fun stop() {
        cancelJobs()
        engine.stop()
        queue = null
        _state.value = PlaybackState(status = PlaybackStatus.IDLE, speed = _state.value.speed)
        releaseWakeLock()
    }

    // ---------------------------------------------------------------- Transport (FR-011/FR-012/FR-013)

    /** FR-011/FR-012 — advance one verse. At the last verse go ENDED (no loop, FR-007). */
    fun next() {
        val q = queue ?: return
        val s = _state.value
        if (!s.hasSession) return
        val nextIndex = s.activeIndex + 1
        if (nextIndex > q.tracks.lastIndex) {
            // last verse → ENDED
            engine.stop()
            _state.value = s.copy(
                status = PlaybackStatus.ENDED,
                activeVerseId = null,
                activeDisplayNumber = null,
                positionMs = 0,
                notice = PlaybackNotice.ReachedEnd,
            )
            releaseWakeLock()
            return
        }
        engine.seekToTrack(nextIndex)
        applyActiveIndex(nextIndex)
        engine.play()
        // User-initiated transport: this call deliberately enters PLAYING even when invoked from
        // PAUSED (the audio starts here, so status + wake lock must follow). Natural gapless
        // `TrackTransition` events go through applyActiveIndex() alone and must NOT force status.
        enterPlaying()
    }

    /** FR-012 — previous verse; if positionMs > 2s or already at index 0, restart current. */
    fun previous() {
        val q = queue ?: return
        val s = _state.value
        if (!s.hasSession) return
        if (s.positionMs > PREVIOUS_RESTART_THRESHOLD_MS || s.activeIndex <= 0) {
            // Restart the current verse: reset our position, ask the engine to jump to 0, replay,
            // and enter PLAYING (otherwise a restart issued while PAUSED is silent and the scrub
            // bar shows a stale positionMs).
            engine.seekTo(0)
            _state.value = _state.value.copy(positionMs = 0)
            engine.play()
            enterPlaying()
            return
        }
        val prevIndex = (s.activeIndex - 1).coerceAtLeast(0)
        engine.seekToTrack(prevIndex)
        applyActiveIndex(prevIndex)
        engine.play()
        enterPlaying()
    }

    /** FR-013 — scrub within the active verse. */
    fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs.coerceAtLeast(0))
    }

    // ---------------------------------------------------------------- Speed (FR-014)

    /** FR-014 — set speed (pitch preserved by the engine); persisted across transitions. */
    fun setSpeed(speed: PlaybackSpeed) {
        engine.setSpeed(speed.multiplier)
        _state.value = _state.value.copy(speed = speed)
    }

    // ---------------------------------------------------------------- Interruptions (FR-018/FR-019)

    /** Handle an [AudioEngineEvent.InterruptionBegan] per policy D5 / FR-019. */
    private fun onInterruptionBegan(event: AudioEngineEvent.InterruptionBegan) {
        if (_state.value.status != PlaybackStatus.PLAYING) return
        val reason = if (event.transient) {
            PauseReason.TRANSIENT_INTERRUPTION
        } else {
            PauseReason.NON_TRANSIENT_INTERRUPTION
        }
        engine.pause()
        update(status = PlaybackStatus.PAUSED, pauseReason = reason)
        releaseWakeLock()
    }

    /** Handle an [AudioEngineEvent.InterruptionEnded]; auto-resume only transient interruptions. */
    private fun onInterruptionEnded(event: AudioEngineEvent.InterruptionEnded) {
        val s = _state.value
        if (s.status != PlaybackStatus.PAUSED) return
        if (s.pauseReason == PauseReason.TRANSIENT_INTERRUPTION && event.shouldResume) {
            resume()
        }
    }

    // ---------------------------------------------------------------- Notice

    /** Clear the one-shot [PlaybackState.notice] after the UI has shown it. */
    fun consumeNotice() {
        if (_state.value.notice != null) {
            _state.value = _state.value.copy(notice = null)
        }
    }

    // ---------------------------------------------------------------- Engine event reduce

    private fun startEventCollection() {
        eventsJob?.cancel()
        eventsJob = scope.launch {
            engine.events.collectLatest { event ->
                when (event) {
                    is AudioEngineEvent.Ready -> {
                        update(status = PlaybackStatus.PLAYING)
                        acquireWakeLock()
                    }
                    is AudioEngineEvent.TrackTransition -> applyActiveIndex(event.newIndex)
                    is AudioEngineEvent.QueueEnded -> {
                        val s = _state.value
                        _state.value = s.copy(
                            status = PlaybackStatus.ENDED,
                            activeVerseId = null,
                            activeDisplayNumber = null,
                            positionMs = 0,
                            notice = PlaybackNotice.ReachedEnd,
                        )
                        releaseWakeLock()
                    }
                    is AudioEngineEvent.TrackError -> onTrackError(event)
                    is AudioEngineEvent.InterruptionBegan -> onInterruptionBegan(event)
                    is AudioEngineEvent.InterruptionEnded -> onInterruptionEnded(event)
                }
            }
        }
    }

    /** FR-020 — skip the failed track one step forward; emit `SkippedMissingVerse`. */
    private fun onTrackError(event: AudioEngineEvent.TrackError) {
        val q = queue ?: return
        val s = _state.value
        val failedVerseId = q.tracks.getOrNull(event.index)?.verseId
        val nextIndex = event.index + 1
        if (nextIndex > q.tracks.lastIndex) {
            _state.value = s.copy(
                status = PlaybackStatus.ENDED,
                activeVerseId = null,
                activeDisplayNumber = null,
                notice = PlaybackNotice.NoPlayableAudio,
            )
            releaseWakeLock()
            return
        }
        engine.seekToTrack(nextIndex)
        engine.play()
        val verse = q.tracks[nextIndex]
        _state.value = s.copy(
            status = PlaybackStatus.PLAYING,
            activeIndex = nextIndex,
            activeVerseId = verse.verseId,
            activeDisplayNumber = verse.displayNumber,
            notice = PlaybackNotice.SkippedMissingVerse(failedVerseId ?: ""),
        )
        // TrackError skip that resumes playback: re-acquire the wake lock along with PLAYING.
        acquireWakeLock()
    }

    /** Map [index] of the current queue to `activeIndex`/`activeVerseId` and reset position. */
    private fun applyActiveIndex(index: Int) {
        val q = queue ?: return
        val track = q.tracks.getOrNull(index) ?: return
        _state.value = _state.value.copy(
            activeIndex = index,
            activeVerseId = track.verseId,
            activeDisplayNumber = track.displayNumber,
            positionMs = 0,
            durationMs = track.durationMs,
        )
    }

    private fun startInfoPolling() {
        infoJob?.cancel()
        infoJob = scope.launch {
            // Periodic poll (not `collectLatest`): the engine's `playbackInfo` StateFlow only
            // emits on native callbacks (track transitions / state changes); Media3 does NOT
            // emit position progress during steady playback, so we must read the snapshot on a
            // timer to keep `positionMs` advancing.
            while (true) {
                if (_state.value.status == PlaybackStatus.PLAYING) {
                    _state.value = _state.value.copy(positionMs = engine.playbackInfo.value.positionMs)
                }
                delay(POSITION_TICK_INTERVAL_MS)
            }
        }
    }

    private fun cancelJobs() {
        eventsJob?.cancel(); eventsJob = null
        infoJob?.cancel(); infoJob = null
    }

    /** Enter PLAYING (status + wake lock). Used by user-initiated transport (next/previous). */
    private fun enterPlaying() {
        update(status = PlaybackStatus.PLAYING, pauseReason = null)
        acquireWakeLock()
    }

    private fun update(
        status: PlaybackStatus,
        pauseReason: PauseReason? = _state.value.pauseReason,
    ) {
        _state.value = _state.value.copy(status = status, pauseReason = pauseReason)
    }

    private fun acquireWakeLock() {
        wakeLock.acquire()
    }

    private fun releaseWakeLock() {
        wakeLock.release()
    }

    /** Release native resources on app teardown. */
    fun release() {
        cancelJobs()
        engine.release()
        releaseWakeLock()
    }

    companion object {
        /** ~2 s; restarting the current verse inside this window jumps to the previous verse. */
        private const val PREVIOUS_RESTART_THRESHOLD_MS = 2_000L

        /** Throttled position ticker cadence (~8 Hz) for the scrub bar + highlight. */
        private const val POSITION_TICK_INTERVAL_MS = 125L
    }
}