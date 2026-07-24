package com.giraffe.matn.playback

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.repository.InMemoryRepetitionSettingsStore
import com.giraffe.matn.domain.audio.AudioEngine
import com.giraffe.matn.domain.audio.AudioEngineEvent
import com.giraffe.matn.domain.audio.WakeLock
import com.giraffe.matn.domain.model.AudioTrack
import com.giraffe.matn.domain.model.LoopRange
import com.giraffe.matn.domain.model.PlaybackCursor
import com.giraffe.matn.domain.model.PlaybackNotice
import com.giraffe.matn.domain.model.PlaybackQueue
import com.giraffe.matn.domain.model.PlaybackSpeed
import com.giraffe.matn.domain.model.PlaybackState
import com.giraffe.matn.domain.model.PlaybackStatus
import com.giraffe.matn.domain.model.PauseReason
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.domain.model.RepetitionSettings
import com.giraffe.matn.domain.repository.RepetitionSettingsStore
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
 *
 * **Repetition (Phase 3, contracts/repetition-contract.md).** The engine playlist never holds the
 * whole drill; it holds a small sliding [window] of the current entry plus [WINDOW_AHEAD] planned
 * ahead ones (research D1/D10). `activeIndex`/`activeVerseId` in [PlaybackState] are always
 * **queue-space** (`cursor.verseIndex`); the engine's own track indices are **window-space** — the
 * two must never be conflated.
 */
class PlaybackController(
    private val engine: AudioEngine,
    private val buildQueue: BuildPlaybackQueueUseCase,
    private val wakeLock: WakeLock,
    private val settingsStore: RepetitionSettingsStore = InMemoryRepetitionSettingsStore(),
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

    /** One materialized playlist entry: the track plus the drill cursor that produced it. */
    private data class WindowEntry(val track: AudioTrack, val cursor: PlaybackCursor)

    /** The engine's current playlist, mirrored 1:1 (engine index == index into [window]). */
    private var window: List<WindowEntry> = emptyList()

    /** Verses whose audio failed this session; skipped by the planner on every later pass. */
    private var failedVerseIds = mutableSetOf<String>()

    // ---------------------------------------------------------------- Play / pause / stop

    /** FR-001 (per-verse play): build the queue starting at [verseId] and play. Phase 4 adds an
     *  optional [startPositionMs] so Continue Learning resumes mid-verse (FR-022/E2). Defaulted so
     *  every existing caller and test stays source-compatible. */
    fun playFromVerse(matnId: String, verseId: String, startPositionMs: Long = 0) {
        startSession(matnId, startVerseId = verseId, startPositionMs = startPositionMs)
    }

    /** FR-001 (global play): build the queue from index 0 (or last-selected; Phase 4) and play. */
    fun playFromStart(matnId: String) {
        startSession(matnId, startVerseId = null, startPositionMs = 0)
    }

    private fun startSession(matnId: String, startVerseId: String?, startPositionMs: Long = 0) {
        // Settings flush rule (T024): capture what was configured while IDLE *before* the reset
        // below wipes state back to defaults, so "configure the drill, then press play" works.
        val pendingSettings = _state.value.settings
        val hadSession = _state.value.hasSession

        cancelJobs()
        queue = null
        window = emptyList()
        failedVerseIds = mutableSetOf()
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

                    val settings = if (pendingSettings != RepetitionSettings() && !hadSession) {
                        pendingSettings
                    } else {
                        settingsStore.get(matnId)
                    }
                    settingsStore.put(matnId, settings)
                    // Phase 4 (FR-020/T035a): recompute the highlighted loop range when a session
                    // starts. Without this a restored loop restricts playback but its verses are
                    // not visibly marked — a latent Phase-3 bug that Phase-4 resume makes universal.
                    _state.value = _state.value.copy(
                        settings = settings,
                        loopRangeVerseIds = loopRangeVerseIds(settings),
                    )

                    val startCursor = PlaybackCursor(q.startIndex, 1, 1)
                    window = buildWindow(startCursor)
                    engine.setQueue(window.map { it.track }, 0)
                    applyCursor(startCursor)
                    engine.setSpeed(_state.value.speed.multiplier)
                    // Phase 4 (FR-022/E2): resume mid-verse. Only seek when a non-zero position is
                    // requested — a 0-position resume from a substituted verse (R4/R5) starts clean,
                    // and the default keeps the existing play-from-start path untouched.
                    if (startPositionMs > 0) engine.seekTo(startPositionMs)
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

    /** FR-004 — stop and clear the session (highlight + position). Settings stay in the store (FR-023). */
    fun stop() {
        cancelJobs()
        engine.stop()
        queue = null
        window = emptyList()
        failedVerseIds.clear()
        _state.value = PlaybackState(status = PlaybackStatus.IDLE, speed = _state.value.speed)
        releaseWakeLock()
    }

    // ---------------------------------------------------------------- Transport (FR-011/FR-012/FR-013)

    /**
     * FR-011/FR-012/FR-021 — advance one **verse**, abandoning any remaining repetitions. Wraps at
     * the range end only inside an A–B loop (Rule A); otherwise the last verse ends the session
     * exactly as Phase 2 did.
     */
    fun next() {
        val s = _state.value
        if (!s.hasSession) return
        val cur = s.cursor ?: return
        val range = activeRange()
        val loopRange = s.settings.loopRange
        val targetVerseIndex = cur.verseIndex + 1
        if (targetVerseIndex > range.last) {
            if (loopRange == null) {
                // last verse → ENDED (unchanged Phase 2 boundary, FR-007)
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
            moveToVerse(PlaybackCursor(range.first, 1, cur.pass))
        } else {
            moveToVerse(PlaybackCursor(targetVerseIndex, 1, cur.pass))
        }
        // User-initiated transport: this call deliberately enters PLAYING even when invoked from
        // PAUSED (the audio starts here, so status + wake lock must follow). Natural gapless
        // `TrackTransition` events go through applyCursor() alone and must NOT force status.
        enterPlaying()
    }

    /**
     * FR-012/FR-021 — previous verse; if positionMs > 2s restarts the current verse regardless of
     * range. At the range start, restarts the current verse unless an A–B loop wraps to the range
     * end (Rule A).
     */
    fun previous() {
        val s = _state.value
        if (!s.hasSession) return
        val cur = s.cursor ?: return
        if (s.positionMs > PREVIOUS_RESTART_THRESHOLD_MS) {
            restartCurrentVerse()
            return
        }
        val range = activeRange()
        val loopRange = s.settings.loopRange
        val targetVerseIndex = cur.verseIndex - 1
        if (targetVerseIndex < range.first) {
            if (loopRange == null) {
                restartCurrentVerse()
                return
            }
            moveToVerse(PlaybackCursor(range.last, 1, cur.pass))
        } else {
            moveToVerse(PlaybackCursor(targetVerseIndex, 1, cur.pass))
        }
        enterPlaying()
    }

    /** Restart the current verse: reset position, replay, enter PLAYING. */
    private fun restartCurrentVerse() {
        engine.seekTo(0)
        _state.value = _state.value.copy(positionMs = 0)
        engine.play()
        enterPlaying()
    }

    /**
     * Move to [target] (repetition already reset to 1 by the caller). Prefers `seekToTrack` into
     * the already-materialized [window] (the common next/previous-by-one-verse case, preserving
     * the Phase 2 engine-call pattern); only rebuilds via `setQueue` when [target]'s verse isn't
     * currently in the window.
     */
    private fun moveToVerse(target: PlaybackCursor) {
        val existingIndex = window.indexOfFirst { it.cursor.verseIndex == target.verseIndex }
        if (existingIndex >= 0) {
            engine.seekToTrack(existingIndex)
            applyCursor(window[existingIndex].cursor)
            refillWindow()
        } else {
            window = buildWindow(target)
            engine.setQueue(window.map { it.track }, 0)
            applyCursor(target)
        }
        engine.play()
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

    // ---------------------------------------------------------------- Repetition intents (US1/US2/US3)

    /**
     * Single funnel for every settings change (T026 / T035 / T036 / T043). Recomputes
     * [PlaybackState.loopRangeVerseIds] and, when a session exists, either relocates an orphaned
     * playhead (FR-025/research D7) or rebuilds only the window tail — the currently playing item
     * is never interrupted (SC-005) except by that user-initiated relocation.
     */
    private fun updateSettings(transform: (RepetitionSettings) -> RepetitionSettings) {
        val updated = transform(_state.value.settings)
        _state.value = _state.value.copy(settings = updated, loopRangeVerseIds = loopRangeVerseIds(updated))
        // No session ⇒ nothing to key the store on yet; startSession() flushes it once a matn is known.
        _state.value.matnId?.let { settingsStore.put(it, updated) }
        if (!_state.value.hasSession) return

        val range = activeRange()
        val cur = _state.value.cursor
        if (cur != null && cur.verseIndex !in range) {
            val target = PlaybackCursor(range.first, 1, 1)
            window = buildWindow(target)
            engine.setQueue(window.map { it.track }, 0)
            applyCursor(target)
            engine.play()
            enterPlaying()
        } else {
            refillWindow()
        }
    }

    /** The verse IDs inside [settings]'s active range, for list highlighting (FR-014). */
    private fun loopRangeVerseIds(settings: RepetitionSettings): Set<String> {
        val q = queue ?: return emptySet()
        val loopRange = settings.loopRange ?: return emptySet()
        val startIdx = q.tracks.indexOfFirst { it.verseId == loopRange.startVerseId }
        val endIdx = q.tracks.indexOfFirst { it.verseId == loopRange.endVerseId }
        if (startIdx < 0 || endIdx < 0) return emptySet()
        return q.tracks.subList(minOf(startIdx, endIdx), maxOf(startIdx, endIdx) + 1)
            .map { it.verseId }
            .toSet()
    }

    /** Set the verse-repeat target ($V_r$). Callable in any state (IDLE/PAUSED/PLAYING). */
    fun setVerseRepeat(count: RepeatCount) = updateSettings { it.copy(verseRepeat = count) }

    /** FR-011 — mark the A boundary; if B is unset, it defaults to the last verse. */
    fun setLoopStart(verseId: String) = updateSettings { current ->
        val endId = current.loopRange?.endVerseId ?: queue?.tracks?.lastOrNull()?.verseId ?: verseId
        normalizedLoop(current, verseId, endId)
    }

    /** FR-011 — mark the B boundary; if A is unset, it defaults to the first verse. */
    fun setLoopEnd(verseId: String) = updateSettings { current ->
        val startId = current.loopRange?.startVerseId ?: queue?.tracks?.firstOrNull()?.verseId ?: verseId
        normalizedLoop(current, startId, verseId)
    }

    /** FR-013 — clear the range; the session continues from the current verse (no restart). */
    fun clearLoop() = updateSettings { it.copy(loopRange = null) }

    /** Set the matn-repeat target ($M_r$, the pass count over the active range). */
    fun setMatnRepeat(count: RepeatCount) = updateSettings { it.copy(matnRepeat = count) }

    /** FR-012 / research D6 — normalize an inverted A/B pair; default $M_r$ to ∞ for a fresh range. */
    private fun normalizedLoop(current: RepetitionSettings, startId: String, endId: String): RepetitionSettings {
        val q = queue
        val startIdx = q?.tracks?.indexOfFirst { it.verseId == startId } ?: -1
        val endIdx = q?.tracks?.indexOfFirst { it.verseId == endId } ?: -1
        val swap = startIdx >= 0 && endIdx >= 0 && startIdx > endIdx
        val range = if (swap) LoopRange(endId, startId) else LoopRange(startId, endId)
        val matnRepeat = if (current.matnRepeat == RepeatCount.ONE) RepeatCount.Unlimited else current.matnRepeat
        return current.copy(loopRange = range, matnRepeat = matnRepeat)
    }

    // ---------------------------------------------------------------- Interruptions (FR-018/FR-019)

    /** Handle an [AudioEngineEvent.InterruptionBegan] per policy D5 / FR-019. */
    private fun onInterruptionBegan(event: AudioEngineEvent.InterruptionBegan) {
        // Phase 4 (FR-022a/T031a): a *non-transient* focus denial may arrive mid-session-start,
        // while status is still LOADING (the engine refuses to play with handleAudioFocus = true
        // and emits InterruptionBegan). The guard below must honour it rather than silently
        // dropping it — otherwise a resume during an active call would report PLAYING while no
        // audio is playing (contractual interruption behaviour, Principle VII). Transient
        // interruptions during LOADING are still ignored — they are expected to end before start
        // completes and the existing auto-resume path owns them.
        val s = _state.value
        if (event.transient) {
            if (s.status != PlaybackStatus.PLAYING) return
        } else if (s.status != PlaybackStatus.PLAYING && s.status != PlaybackStatus.LOADING) {
            return
        }
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
                    is AudioEngineEvent.TrackTransition -> {
                        // Resolve defensively BEFORE trimming: a stale/out-of-range engine index
                        // must be ignored, never crash (never index `window` with `[]` here).
                        val entry = window.getOrNull(event.newIndex) ?: return@collectLatest
                        applyCursor(entry.cursor)
                        refillWindow()
                    }
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

    /**
     * FR-028/FR-029 (research D8) — the failed verse is skipped for the rest of the session (added
     * to [failedVerseIds]), and the planner replans from that point. `End` (nothing left playable)
     * → `ENDED` + `NoPlayableAudio`; otherwise resume at the next planned cursor.
     */
    private fun onTrackError(event: AudioEngineEvent.TrackError) {
        val q = queue ?: return
        val s = _state.value
        val failed = window.getOrNull(event.index)?.cursor ?: s.cursor ?: return
        val failedVerseId = q.tracks.getOrNull(failed.verseIndex)?.verseId
        if (failedVerseId != null) failedVerseIds.add(failedVerseId)

        val range = activeRange()
        when (val step = RepetitionPlanner.next(
            cursor = failed,
            settings = s.settings,
            range = range,
            isPlayable = { idx -> q.tracks[idx].verseId !in failedVerseIds },
        )) {
            is PlanStep.End -> {
                _state.value = s.copy(
                    status = PlaybackStatus.ENDED,
                    activeVerseId = null,
                    activeDisplayNumber = null,
                    notice = PlaybackNotice.NoPlayableAudio,
                )
                releaseWakeLock()
            }
            is PlanStep.Advance -> {
                val nextCursor = step.cursor
                // Same two-path choice as moveToVerse (T028): only `seekToTrack` into an index that
                // still exists in the CURRENT window, then let refillWindow() do the trim/drop/rebuild
                // dance so `window` and the engine's real playlist stay 1:1. Reassigning `window` to a
                // fresh 0-indexed buildWindow(...) here while telling the engine to `seekToTrack` an
                // index from the OLD window (as this used to) breaks that invariant — the next error
                // or transition then resolves against the wrong verse entirely.
                val existingIndex = window.indexOfFirst { it.cursor == nextCursor }
                if (existingIndex >= 0) {
                    engine.seekToTrack(existingIndex)
                    applyCursor(nextCursor)
                    refillWindow()
                } else {
                    window = buildWindow(nextCursor)
                    engine.setQueue(window.map { it.track }, 0)
                    applyCursor(nextCursor)
                }
                engine.play()
                _state.value = _state.value.copy(
                    notice = PlaybackNotice.SkippedMissingVerse(failedVerseId ?: ""),
                )
                // TrackError skip that resumes playback: re-acquire the wake lock along with PLAYING.
                acquireWakeLock()
            }
        }
    }

    /** Map [newCursor] to the active fields (queue-space) and reset position (T024). */
    private fun applyCursor(newCursor: PlaybackCursor) {
        val q = queue ?: return
        val track = q.tracks.getOrNull(newCursor.verseIndex) ?: return
        _state.value = _state.value.copy(
            activeIndex = newCursor.verseIndex,
            activeVerseId = track.verseId,
            activeDisplayNumber = track.displayNumber,
            positionMs = 0,
            durationMs = track.durationMs,
            cursor = newCursor,
        )
    }

    // ---------------------------------------------------------------- Sliding window (US1)

    /**
     * The active queue-index range: the whole matn, or (inside an A–B loop) the normalized
     * start/end verse indices. `IntRange.EMPTY` when there is no queue.
     */
    private fun activeRange(): IntRange {
        val q = queue ?: return IntRange.EMPTY
        val loopRange = _state.value.settings.loopRange ?: return 0..q.tracks.lastIndex
        val startIdx = q.tracks.indexOfFirst { it.verseId == loopRange.startVerseId }
        val endIdx = q.tracks.indexOfFirst { it.verseId == loopRange.endVerseId }
        if (startIdx < 0 || endIdx < 0) return 0..q.tracks.lastIndex
        return minOf(startIdx, endIdx)..maxOf(startIdx, endIdx)
    }

    /** Materialize up to `1 + WINDOW_AHEAD` entries starting at [from] (research D1/D10). */
    private fun buildWindow(from: PlaybackCursor): List<WindowEntry> {
        val q = queue ?: return emptyList()
        val range = activeRange()
        val settings = _state.value.settings
        val entries = mutableListOf(WindowEntry(q.tracks[from.verseIndex], from))
        var cursor = from
        while (entries.size < 1 + WINDOW_AHEAD) {
            when (
                val step = RepetitionPlanner.next(
                    cursor = cursor,
                    settings = settings,
                    range = range,
                    isPlayable = { idx -> q.tracks[idx].verseId !in failedVerseIds },
                )
            ) {
                is PlanStep.Advance -> {
                    cursor = step.cursor
                    entries.add(WindowEntry(q.tracks[cursor.verseIndex], cursor))
                }
                is PlanStep.End -> return entries
            }
        }
        return entries
    }

    /**
     * Drop consumed entries, rebuild the tail from the current cursor, and push only the tail to
     * the engine. **Order matters** (T023): the engine re-bases its indices when consumed entries
     * are dropped, so the controller must trim in lockstep before rebuilding.
     */
    private fun refillWindow() {
        val cur = _state.value.cursor ?: return
        val idx = window.indexOfFirst { it.cursor == cur }
        if (idx > 0) window = window.drop(idx)
        engine.dropConsumed()
        window = buildWindow(cur)
        engine.replaceUpcoming(window.drop(1).map { it.track })
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

        /** Sliding-window lookahead (research D10): current entry + this many planned ahead. */
        private const val WINDOW_AHEAD = 2
    }
}
