package com.giraffe.matn.domain.model

/**
 * What the player bar renders (data-model.md §1.6 / FR-020): a projection of the cursor plus
 * targets. `Unlimited` targets render as **∞**, not a number.
 */
data class RepetitionProgress(
    val repetition: Int,
    val verseRepeatTarget: RepeatCount,
    val pass: Int,
    val matnRepeatTarget: RepeatCount,
)
