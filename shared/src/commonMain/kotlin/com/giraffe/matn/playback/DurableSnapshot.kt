package com.giraffe.matn.playback

import com.giraffe.matn.domain.model.PlaybackState
import com.giraffe.matn.domain.model.RepetitionSettings

/** The subset of [PlaybackState] that is persisted. Everything absent here is deliberately
 *  NOT persisted: status, speed, notice, pauseReason, cursor (FR-005, FR-006). */
data class DurableSnapshot(
    val matnId: String,
    val verseId: String,
    val displayNumber: Int,
    val positionMs: Long,
    val settings: RepetitionSettings,
)

/** Null when there is no resumable session (no matn / no active verse). */
fun PlaybackState.toDurableSnapshot(): DurableSnapshot? {
    val m = matnId ?: return null
    val v = activeVerseId ?: return null
    val n = activeDisplayNumber ?: return null
    return DurableSnapshot(m, v, n, positionMs, settings)
}