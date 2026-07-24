package com.giraffe.matn.presentation

import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.presentation.player.RepetitionSetupUiState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure derivation tests for [RepetitionSetupUiState.canStart] —
 * specs/010-design-system-adoption/spec.md FR-004 ("the sheet cannot invoke playback with an
 * invalid configuration"). No fakes, no coroutines (Constitution Principle V).
 */
class RepetitionSetupUiStateTest {

    @Test
    fun noVerses_cannotStart_evenWithAbLoopOff() {
        val state = RepetitionSetupUiState(abLoopEnabled = false, totalVerseCount = 0)
        assertFalse(state.canStart)
    }

    @Test
    fun abLoopOff_withVerses_canStart() {
        val state = RepetitionSetupUiState(abLoopEnabled = false, totalVerseCount = 10)
        assertTrue(state.canStart)
    }

    @Test
    fun abLoopOn_validRange_canStart() {
        val state = RepetitionSetupUiState(
            abLoopEnabled = true,
            startVerseIndex = 1,
            endVerseIndex = 4,
            totalVerseCount = 10,
        )
        assertTrue(state.canStart)
    }

    @Test
    fun abLoopOn_singleVerseRange_canStart() {
        val state = RepetitionSetupUiState(
            abLoopEnabled = true,
            startVerseIndex = 2,
            endVerseIndex = 2,
            totalVerseCount = 10,
        )
        assertTrue(state.canStart)
    }

    @Test
    fun abLoopOn_invertedRange_cannotStart() {
        val state = RepetitionSetupUiState(
            abLoopEnabled = true,
            startVerseIndex = 5,
            endVerseIndex = 2,
            totalVerseCount = 10,
        )
        assertFalse(state.canStart)
    }

    @Test
    fun defaultState_reflectsRepeatOnceEachAndAbLoopOff() {
        val state = RepetitionSetupUiState()
        assertFalse(state.abLoopEnabled)
        assertTrue(state.verseRepeatCount == RepeatCount.ONE)
        assertTrue(state.matnRepeatCount == RepeatCount.ONE)
    }
}
