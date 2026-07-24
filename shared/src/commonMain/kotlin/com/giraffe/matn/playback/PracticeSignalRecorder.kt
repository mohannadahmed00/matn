package com.giraffe.matn.playback

import com.giraffe.matn.domain.model.PlaybackState
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.domain.repository.ProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Observes [PlaybackController.state] and credits the daily practice goal for a completed verse
 * (practice-signal-contract.md §2; research D2). Pure observer — never calls back into
 * [PlaybackController] and holds no day/clock state of its own; `today()` is owned solely by
 * [ProgressRepository] (research D1/D9). Built like [SessionStateRecorder]: same three-parameter
 * shape, same fire-and-forget `start()`, same swallow-failures rule (W7 precedent).
 */
class PracticeSignalRecorder(
    private val state: StateFlow<PlaybackState>,
    private val repository: ProgressRepository,
    private val scope: CoroutineScope,
) {
    private var lastSeenTick: Long = 0

    fun start() {
        scope.launch {
            state.collect { s ->
                if (s.completionTick == lastSeenTick) return@collect
                lastSeenTick = s.completionTick
                val verseId = s.lastCompletedVerseId ?: return@collect
                if (isRecallMode(s)) {
                    try {
                        repository.recordPractice(verseId)
                    } catch (t: Throwable) {
                        // Never disturb playback on a failed credit (mirrors SessionStateRecorder W7).
                    }
                }
            }
        }
    }

    /** FR-011: Memorization mode ($V_r > 1$ and/or $M_r > 1$) or an A-B loop credits; Normal
     *  continuous playback ($V_r = 1$, $M_r = 1$, no loop) never does. */
    private fun isRecallMode(s: PlaybackState): Boolean =
        s.settings.loopRange != null ||
            s.settings.verseRepeat != RepeatCount.ONE ||
            s.settings.matnRepeat != RepeatCount.ONE
}
