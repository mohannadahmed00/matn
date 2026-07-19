package com.giraffe.matn.domain.model

/**
 * Discrete pitch-preserved playback speeds (FR-014 / data-model.md §2.1).
 *
 * Only the five clarified steps are representable; `DEFAULT` is `X1`. `next()` cycles to the
 * following step for the speed button, wrapping `X1_5` back to `X0_5`. Applied pitch-preserved
 * on both platforms (D6).
 */
enum class PlaybackSpeed(val multiplier: Float) {
    X0_5(0.5f),
    X0_75(0.75f),
    X1(1.0f),
    X1_25(1.25f),
    X1_5(1.5f), ;

    fun next(): PlaybackSpeed {
        val values = entries
        val i = values.indexOf(this)
        return values[(i + 1) % values.size]
    }

    companion object {
        val DEFAULT = X1
    }
}