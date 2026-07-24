package com.giraffe.matn.presentation.player

import com.giraffe.matn.domain.model.RepeatCount

/**
 * UI-state for the consolidated repetition-setup bottom sheet
 * (specs/010-design-system-adoption/data-model.md § RepetitionSetupUiState, User Story 2). Purely
 * presentational — on "start" this is translated 1:1 into calls against the existing
 * `PlaybackController` intents (`setLoopStart`/`setLoopEnd`/`clearLoop`/`setVerseRepeat`/
 * `setMatnRepeat`/`playFromStart`), per
 * specs/010-design-system-adoption/contracts/repetition-setup-contract.md. [RepeatCount] itself
 * is reused unchanged from specs/004-repetition-engine — this phase does not redefine it.
 *
 * [startVerseIndex]/[endVerseIndex] are 0-based indices into the matn's verse list (not verse
 * IDs) so the sheet can step through them with simple +/- controls; the stateful holder resolves
 * the chosen indices to verse IDs when calling `onSetLoopStart`/`onSetLoopEnd`.
 */
data class RepetitionSetupUiState(
    val abLoopEnabled: Boolean = false,
    val startVerseIndex: Int = 0,
    val endVerseIndex: Int = 0,
    val verseRepeatCount: RepeatCount = RepeatCount.ONE,
    val matnRepeatCount: RepeatCount = RepeatCount.ONE,
    val totalVerseCount: Int = 0,
) {
    /**
     * False when there's nothing to repeat yet, or when A–B mode is on but the range is inverted
     * (spec FR: "the sheet cannot invoke playback with an invalid configuration"). A start index
     * past the end index is the only invalid shape this state can represent — equal indices (a
     * single-verse loop) are valid.
     */
    val canStart: Boolean
        get() = totalVerseCount > 0 && (!abLoopEnabled || startVerseIndex <= endVerseIndex)
}
