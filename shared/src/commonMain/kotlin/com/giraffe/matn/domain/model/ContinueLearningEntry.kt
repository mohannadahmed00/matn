package com.giraffe.matn.domain.model

/** Derived Home-screen offer — never stored (FR-015). */
data class ContinueLearningEntry(
    val matnId: String,
    val matnTitle: String,
    val verseDisplayNumber: Int,
    val verseId: String,
)