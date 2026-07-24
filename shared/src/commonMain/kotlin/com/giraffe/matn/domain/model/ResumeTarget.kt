package com.giraffe.matn.domain.model

/** Resolved outcome of "what should tapping Continue Learning do?" (data-model.md §2.2). */
sealed interface ResumeTarget {
    data class Resolved(
        val matnId: String,
        val verseId: String,
        val positionMs: Long,
        val settings: RepetitionSettings,
        val substituted: Boolean,
    ) : ResumeTarget
    data object None : ResumeTarget
}