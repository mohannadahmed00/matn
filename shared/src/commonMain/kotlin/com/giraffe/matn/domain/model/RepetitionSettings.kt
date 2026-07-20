package com.giraffe.matn.domain.model

/**
 * One matn's configured drill (data-model.md §1.3). Defaults are $V_r$ = $M_r$ = 1 with no
 * range — i.e. **exactly Phase 2 behavior** (FR-004, SC-010). [mode] is a computed property with
 * no backing field (FR-008 / research D5), so the displayed mode cannot contradict the
 * configuration.
 */
data class RepetitionSettings(
    val verseRepeat: RepeatCount = RepeatCount.ONE,
    val matnRepeat: RepeatCount = RepeatCount.ONE,
    val loopRange: LoopRange? = null,
) {
    val mode: PlaybackMode get() = when {
        loopRange != null -> PlaybackMode.A_B_LOOP
        verseRepeat != RepeatCount.ONE || matnRepeat != RepeatCount.ONE -> PlaybackMode.MEMORIZATION
        else -> PlaybackMode.NORMAL
    }
}
