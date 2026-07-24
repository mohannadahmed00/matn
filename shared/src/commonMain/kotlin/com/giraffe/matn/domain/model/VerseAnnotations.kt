package com.giraffe.matn.domain.model

/** Per-verse bookmark/note indicator flags for the reading carousel (FR-011/FR-016). */
data class VerseAnnotations(
    val verseId: String,
    val isBookmarked: Boolean,
    val hasNote: Boolean,
)
