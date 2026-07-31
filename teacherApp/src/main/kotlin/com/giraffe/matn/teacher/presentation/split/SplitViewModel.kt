package com.giraffe.matn.teacher.presentation.split

import androidx.lifecycle.viewModelScope
import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.audio.Mp3FrameIndex
import com.giraffe.matn.domain.audio.AudioProbe
import com.giraffe.matn.domain.audio.AudioSlicer
import com.giraffe.matn.domain.audio.PreviewPlayer
import com.giraffe.matn.domain.audio.PreviewState
import com.giraffe.matn.domain.audio.SourceRecording
import com.giraffe.matn.domain.audio.SplitPlan
import com.giraffe.matn.domain.audio.SplitPlanValidator
import com.giraffe.matn.domain.audio.SplitReport
import com.giraffe.matn.domain.audio.VerseRange
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.usecase.ApplySplitUseCase
import com.giraffe.matn.domain.usecase.LoadSplitSourceUseCase
import com.giraffe.matn.presentation.base.BaseViewModel
import com.giraffe.matn.teacher.platform.JvmFileByteSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The boundary a waveform drag is currently aimed at. */
data class ActiveBoundary(val verseId: String, val isStart: Boolean)

/** `contracts/teacher-ui-contract.md` §2. */
sealed interface SplitUiState {
    data object NoSource : SplitUiState
    data class Loading(val progress: Float) : SplitUiState
    data class Ready(
        val allVerses: List<DraftVerse>,
        val source: SourceRecording,
        val peaks: FloatArray,
        val scopeVerseIds: List<String>,
        val ranges: List<VerseRange>,
        val report: SplitReport,
        val selectedVerseId: String? = null,
        /** The verse whose range is currently being auditioned, if any (FR-014). */
        val auditioningVerseId: String? = null,
        /** Which boundary a waveform drag will move (FR-014's pointer path). `null` means a drag
         * does nothing — the teacher picks a Start or End field first, so a stray drag on the
         * waveform can never silently rewrite a boundary they were not editing. */
        val activeBoundary: ActiveBoundary? = null,
        /** Live position while scrubbing a boundary, for the readout that lets the teacher place it
         * accurately *before* releasing. `null` when not dragging a boundary. */
        val scrubMs: Long? = null,
        /**
         * The transport position on the source's timeline — **always meaningful**, independent of
         * any verse. It advances while audio plays, stays put when playback stops, and is what a
         * free drag on the waveform moves. Deliberately not nullable and not tied to
         * [auditioningVerseId]: a playhead that vanishes when playback ends cannot be used to seek.
         */
        val playheadMs: Long = 0L,
        /** True while the whole recording is playing (as opposed to a verse audition). */
        val isPlayingSource: Boolean = false,
        /** True while the playhead itself is being dragged, which suppresses position updates from
         * the player so playback cannot fight the pointer for control of [playheadMs]. */
        val isSeeking: Boolean = false,
    ) : SplitUiState {
        /**
         * Current position of the armed boundary, or `null` when nothing is armed (or the armed
         * verse has no range yet, so there is no position to point at).
         *
         * Derived rather than stored: it is exactly "look up the armed boundary in `ranges`", and a
         * second copy would be one more thing to keep in step with every edit. Drawn persistently
         * while the field has focus — the marker vanishing the moment the drag ended left the
         * teacher with no on-waveform indication of where the boundary they were editing sits.
         */
        val armedBoundaryMs: Long?
            get() = activeBoundary?.let { armed ->
                ranges.find { it.verseId == armed.verseId }
                    ?.let { range -> if (armed.isStart) range.startMs else range.endMs }
            }
    }
    data class Splitting(val progress: Float) : SplitUiState
    data class Failed(val error: AppError, val previous: Ready?) : SplitUiState
}

/**
 * Owns the transient [SplitPlan] (`data-model.md` §7), re-validates on every edit, runs the split
 * off the UI thread, and exposes upload progress. Replacing the source discards all ranges with a
 * confirmation the caller renders (FR-020). Nothing here is ever persisted — the plan lives only
 * in this ViewModel's state and is gone the moment the screen closes or the split succeeds.
 */
class SplitViewModel(
    private val draft: MatnDraft,
    private val loadSplitSource: LoadSplitSourceUseCase,
    private val applySplit: ApplySplitUseCase,
    private val audioProbe: AudioProbe,
    private val slicer: AudioSlicer,
    private val previewPlayer: PreviewPlayer,
    private val onSplitComplete: (MatnDraft) -> Unit,
) : BaseViewModel<SplitUiState>(SplitUiState.NoSource) {

    private var frameIndex: Mp3FrameIndex? = null
    private var localPath: String? = null

    /** The in-flight audition, so a second press can cancel it rather than stacking another one. */
    private var auditionJob: Job? = null

    /** The in-flight source playback (the transport), separate from an audition. */
    private var sourceJob: Job? = null

    /**
     * Invalidated on every stop/restart. A cancelled playback's `finally` runs **asynchronously** —
     * after `stopSourcePlayback` returns and after its replacement has already set
     * `isPlayingSource = true` — so without this check the dying job clears the flag out from under
     * the job that replaced it: the button showed "play" while audio kept sounding, and every
     * subsequent press repeated the same stomp. Same class of race as `JvmPreviewPlayer`'s playback
     * token, one layer up.
     */
    private var sourcePlaybackToken = 0

    /**
     * Where on the source timeline the clip currently feeding the player begins. The player only
     * knows clip-relative positions (always starting at zero), so this is what turns them back into
     * source positions — the audition sets it to the verse's start, source playback to the start of
     * the chunk being played.
     */
    @Volatile private var playbackOriginMs: Long = 0L

    init {
        // Playhead only. This collector deliberately does **not** touch `auditioningVerseId` or
        // `isPlayingSource`: starting a clip stops the previous one first, which emits `Idle`, and
        // clearing play state on that emission is precisely the desync bug recorded in
        // `design-notes.md`. Ownership of those belongs to the coroutines that await playback.
        previewPlayer.state.collectInto { playerState ->
            val playing = playerState as? PreviewState.Playing ?: return@collectInto
            setState { current ->
                val ready = current as? SplitUiState.Ready ?: return@setState current
                // A drag owns the playhead outright while it lasts, or audio still in the buffer
                // would yank the marker back from under the pointer.
                if (ready.isSeeking) return@setState ready
                val position = (playbackOriginMs + playing.positionMs).coerceIn(0L, ready.source.durationMs)
                if (ready.playheadMs == position) ready else ready.copy(playheadMs = position)
            }
        }
    }

    /** The whole load — frame indexing plus peak decoding — runs in **one**
     * `withContext(Dispatchers.Default)` (`research.md` D7), not a dispatcher hop per read: a long
     * recording's frame index alone is one read per frame, and switching per read would be both
     * wasted overhead and, worse, leaves the UI thread only after every individual read returns. */
    fun onSourcePicked(path: String, sizeBytes: Long) {
        localPath = path
        setState { SplitUiState.Loading(progress = 0f) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.Default) {
                val byteSource = JvmFileByteSource(path)
                val result = loadSplitSource(LoadSplitSourceUseCase.Params(draft, byteSource, path, sizeBytes))
                when (result) {
                    is Resource.Success -> {
                        val peaksResult = audioProbe.peaks(byteSource, PEAK_BUCKETS)
                        val peaks = (peaksResult as? Resource.Success)?.data ?: FloatArray(0)
                        Resource.Success(result.data to peaks)
                    }
                    is Resource.Failure -> result
                }
            }
            when (outcome) {
                is Resource.Success -> {
                    val (loaded, peaks) = outcome.data
                    frameIndex = loaded.index
                    val scope = draft.verses.map { it.id }
                    val plan = SplitPlan(loaded.source, scope, emptyList())
                    setState {
                        SplitUiState.Ready(
                            allVerses = draft.verses,
                            source = loaded.source,
                            peaks = peaks,
                            scopeVerseIds = scope,
                            ranges = emptyList(),
                            report = SplitPlanValidator.validate(plan),
                        )
                    }
                }
                is Resource.Failure -> setState { SplitUiState.Failed(outcome.error, previous = null) }
            }
        }
    }

    /** The chooser refused the pick before any read (wrong extension, or over the 300 MB source
     * ceiling). Surfaced as [SplitUiState.Failed] rather than dropped — a silently ignored pick
     * looks to the teacher exactly like a broken button. */
    fun onSourceRejected(error: AppError) {
        setState { SplitUiState.Failed(error, previous = stateValue as? SplitUiState.Ready) }
    }

    fun onScopeChanged(firstVerseId: String, lastVerseId: String) {
        val ready = stateValue as? SplitUiState.Ready ?: return
        val allIds = ready.allVerses.map { it.id }
        val start = allIds.indexOf(firstVerseId).coerceAtLeast(0)
        val end = allIds.indexOf(lastVerseId).coerceAtLeast(start)
        val newScope = allIds.subList(start, end + 1)
        val newRanges = ready.ranges.filter { it.verseId in newScope }
        updateReady(ready.copy(scopeVerseIds = newScope, ranges = newRanges))
    }

    fun onRangeChanged(verseId: String, startMs: Long, endMs: Long) {
        val ready = stateValue as? SplitUiState.Ready ?: return
        // Editing the range being auditioned invalidates what is playing — keeping it running would
        // have the teacher judging a new boundary against the old clip.
        if (ready.auditioningVerseId == verseId) stopAudition()
        val newRanges = ready.ranges.filterNot { it.verseId == verseId } + VerseRange(verseId, startMs, endMs)
        updateReady((stateValue as? SplitUiState.Ready ?: ready).copy(ranges = newRanges))
    }

    // ---- Transport: play the source recording itself, independent of any verse ----

    fun onTogglePlaySource() {
        val ready = stateValue as? SplitUiState.Ready ?: return
        if (ready.isPlayingSource) stopSourcePlayback() else startSourcePlayback(ready.playheadMs)
    }

    /**
     * Plays the recording from [fromMs] in bounded chunks rather than slicing the remainder in one
     * go: the remainder of a 300 MB source would be exactly the whole-file-in-memory read that
     * `research.md` D8 exists to prevent. Each chunk is re-sliced on demand, so peak memory is one
     * chunk regardless of how long the recording is.
     *
     * The cost is a brief gap at each chunk boundary, since the player opens a line per clip. That
     * is acceptable for a seek-and-listen aid — unlike the *matn preview*, whose gaplessness is a
     * requirement (FR-023) and which keeps one line open for exactly that reason.
     */
    private fun startSourcePlayback(fromMs: Long) {
        val index = frameIndex ?: return
        val path = localPath ?: return
        stopAudition()
        stopSourcePlayback()

        val token = ++sourcePlaybackToken
        setState { (it as? SplitUiState.Ready)?.copy(isPlayingSource = true) ?: it }
        sourceJob = viewModelScope.launch {
            try {
                val duration = (stateValue as? SplitUiState.Ready)?.source?.durationMs ?: return@launch
                var cursor = fromMs.coerceIn(0L, duration)
                while (cursor < duration) {
                    val chunkEnd = minOf(cursor + SOURCE_CHUNK_MS, duration)
                    val sliced = withContext(Dispatchers.Default) {
                        slicer.slice(JvmFileByteSource(path), index, listOf(VerseRange(SOURCE_PLAYBACK_ID, cursor, chunkEnd)))
                    }
                    val bytes = (sliced as? Resource.Success)?.data?.firstOrNull()?.bytes ?: break
                    playbackOriginMs = cursor
                    previewPlayer.playClip(bytes, displayNumber = 0)
                    cursor = chunkEnd
                }
            } finally {
                // Only the playback still registered as current may report that it ended.
                if (sourcePlaybackToken == token) {
                    setState { (it as? SplitUiState.Ready)?.copy(isPlayingSource = false) ?: it }
                }
            }
        }
    }

    private fun stopSourcePlayback() {
        sourcePlaybackToken++
        sourceJob?.cancel()
        sourceJob = null
        previewPlayer.stop()
        setState { (it as? SplitUiState.Ready)?.copy(isPlayingSource = false) ?: it }
    }

    /** Arms a boundary for waveform dragging — the teacher taps a verse's Start or End field, then
     * drags on the waveform to place it (FR-014). */
    fun onBoundarySelected(verseId: String, isStart: Boolean) {
        val ready = stateValue as? SplitUiState.Ready ?: return
        setState { ready.copy(activeBoundary = ActiveBoundary(verseId, isStart), selectedVerseId = verseId) }
    }

    /**
     * Drag in progress over the waveform. Writes the boundary continuously so the waveform redraws
     * under the finger, and publishes [SplitUiState.Ready.scrubMs] for the numeric readout — the
     * teacher needs to *see* the position they are about to commit, not guess it from pixels.
     *
     * A verse with no range yet gets one seeded around the scrub position, so the first drag on an
     * untouched verse creates its range instead of being ignored.
     */
    fun onScrub(positionMs: Long) {
        val ready = stateValue as? SplitUiState.Ready ?: return
        val active = ready.activeBoundary ?: return
        val clamped = positionMs.coerceIn(0L, ready.source.durationMs)
        val existing = ready.ranges.find { it.verseId == active.verseId }

        val updated = when {
            existing == null && active.isStart -> VerseRange(active.verseId, clamped, minOf(clamped + SEED_RANGE_MS, ready.source.durationMs))
            existing == null -> VerseRange(active.verseId, maxOf(clamped - SEED_RANGE_MS, 0L), clamped)
            active.isStart -> existing.copy(startMs = clamped)
            else -> existing.copy(endMs = clamped)
        }

        if (ready.auditioningVerseId == active.verseId) stopAudition()
        val current = stateValue as? SplitUiState.Ready ?: ready
        val newRanges = current.ranges.filterNot { it.verseId == active.verseId } + updated
        updateReady(current.copy(ranges = newRanges, scrubMs = clamped))
    }

    /** Drag released — the boundary is already written; this only drops the live readout. */
    fun onScrubEnd() {
        val ready = stateValue as? SplitUiState.Ready ?: return
        setState { ready.copy(scrubMs = null) }
    }

    /**
     * The teacher has grabbed the transport playhead.
     *
     * Stops playback outright rather than trying to seek underneath it: audio continuing from the
     * old position while the pointer moves elsewhere is confusing, and every attempt to keep them
     * in step invites the state to drift out of sync with what is actually sounding. Pressing play
     * afterwards resumes from wherever the playhead was dropped.
     *
     * Also **disarms any focused boundary**, so there is never ambiguity about what the next drag
     * moves (the caller clears the text field's focus to match).
     */
    fun onSeekStart() {
        val ready = stateValue as? SplitUiState.Ready ?: return
        stopAudition()
        stopSourcePlayback()
        setState { (it as? SplitUiState.Ready ?: ready).copy(activeBoundary = null, isSeeking = true) }
    }

    /**
     * Drags the transport playhead. **Never touches any verse's boundaries** — seeking to listen is
     * a different act from deciding where a verse begins, and conflating them would make it
     * impossible to explore the recording without corrupting the plan.
     */
    fun onSeek(positionMs: Long) {
        val ready = stateValue as? SplitUiState.Ready ?: return
        setState { ready.copy(playheadMs = positionMs.coerceIn(0L, ready.source.durationMs), isSeeking = true) }
    }

    /** Released. Playback stays stopped — [onSeekStart] ended it, and the teacher restarts from the
     * new position with the transport button. */
    fun onSeekEnd() {
        setState { (it as? SplitUiState.Ready)?.copy(isSeeking = false) ?: it }
    }

    fun onVerseSelected(verseId: String) {
        val ready = stateValue as? SplitUiState.Ready ?: return
        setState { ready.copy(selectedVerseId = verseId) }
    }

    /**
     * Auditions one verse's range before committing to it (FR-014). Slices with the **same**
     * [AudioSlicer] the split itself uses, so the teacher hears exactly the bytes that would be
     * uploaded — frame snapping and the bit-reservoir artifact included — rather than an
     * approximation played from arbitrary offsets in the source.
     */
    fun onAuditionRange(verseId: String) {
        val ready = stateValue as? SplitUiState.Ready ?: return
        if (ready.auditioningVerseId == verseId) {
            stopAudition()
            return
        }
        val range = ready.ranges.find { it.verseId == verseId } ?: return
        val index = frameIndex ?: return
        val path = localPath ?: return

        // Tear the previous audition down *before* starting another, so pressing play repeatedly
        // can never leave two clips sounding at once.
        stopAudition()
        stopSourcePlayback()

        setState { ready.copy(auditioningVerseId = verseId, selectedVerseId = verseId) }
        auditionJob = viewModelScope.launch {
            try {
                val displayNumber = ready.allVerses.find { it.id == verseId }?.displayNumber ?: 0
                val sliced = withContext(Dispatchers.Default) {
                    slicer.slice(JvmFileByteSource(path), index, listOf(range))
                }
                // The clip starts at the verse's start, so that is where its positions are measured
                // from on the source timeline.
                playbackOriginMs = range.startMs
                // `playClip` suspends for the whole clip, so reaching the `finally` below means
                // playback genuinely ended — the control returns to "play" on its own, and the
                // state can never claim to be playing something that already stopped.
                if (sliced is Resource.Success) {
                    sliced.data.firstOrNull()?.let { previewPlayer.playClip(it.bytes, displayNumber) }
                }
            } finally {
                clearAuditionOf(verseId)
            }
        }
    }

    /** Leaves [SplitUiState.Ready.playheadMs] where playback reached — the transport position is
     * not owned by the audition, and resetting it would make stopping lose the teacher's place. */
    private fun stopAudition() {
        auditionJob?.cancel()
        auditionJob = null
        previewPlayer.stop()
        setState { (it as? SplitUiState.Ready)?.copy(auditioningVerseId = null) ?: it }
    }

    /** Only clears if [verseId] is still the one on screen — a job cancelled by its successor must
     * not wipe the newer audition's state on its way out. */
    private fun clearAuditionOf(verseId: String) {
        setState { current ->
            val ready = current as? SplitUiState.Ready ?: return@setState current
            if (ready.auditioningVerseId == verseId) ready.copy(auditioningVerseId = null) else ready
        }
    }

    /** FR-020: replacing the source discards every range — ranges measured against one recording
     * are meaningless against another of a different length. */
    fun onReplaceSource() {
        frameIndex = null
        localPath = null
        setState { SplitUiState.NoSource }
    }

    fun onSplitAndUpload() {
        val ready = stateValue as? SplitUiState.Ready ?: return
        val index = frameIndex ?: return
        val path = localPath ?: return
        if (ready.report.blocking.isNotEmpty()) return

        setState { SplitUiState.Splitting(progress = 0f) }
        viewModelScope.launch {
            val plan = SplitPlan(ready.source, ready.scopeVerseIds, ready.ranges)
            val result = withContext(Dispatchers.Default) {
                val byteSource = JvmFileByteSource(path)
                applySplit(ApplySplitUseCase.Params(draft, byteSource, index, plan))
            }
            when (result) {
                is Resource.Success -> onSplitComplete(result.data)
                is Resource.Failure -> setState { SplitUiState.Failed(result.error, previous = ready) }
            }
        }
    }

    private fun updateReady(ready: SplitUiState.Ready) {
        val plan = SplitPlan(ready.source, ready.scopeVerseIds, ready.ranges)
        setState { ready.copy(report = SplitPlanValidator.validate(plan)) }
    }

    private companion object {
        const val PEAK_BUCKETS = 200

        /** Slice length for source playback. Bounded so peak memory is one chunk (~1 MB at
         * 128 kbps) rather than the remainder of the recording. */
        const val SOURCE_CHUNK_MS = 60_000L

        /** Range id used for transport chunks. Never a real verse id, and never persisted — these
         * slices exist only to be fed to the player. */
        const val SOURCE_PLAYBACK_ID = "__source_playback__"

        /** Width of the range created when the first drag lands on a verse that has none yet —
         * long enough to clear `SplitPlanValidator`'s 300 ms minimum without further edits. */
        const val SEED_RANGE_MS = 2_000L
    }
}
