package com.giraffe.matn.domain.model

/**
 * Position within the drill (data-model.md §1.5). [verseIndex] is an index into the queue's
 * `tracks` (queue-space); [repetition] and [pass] are 1-based.
 */
data class PlaybackCursor(
    val verseIndex: Int,
    val repetition: Int = 1,
    val pass: Int = 1,
)
