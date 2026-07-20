package com.giraffe.matn.playback

import com.giraffe.matn.domain.model.PlaybackCursor
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.domain.model.RepetitionSettings

/** The result of asking the planner what comes after a cursor (data-model.md §4). */
sealed interface PlanStep {
    data class Advance(val cursor: PlaybackCursor) : PlanStep
    data object End : PlanStep
}

/**
 * Pure, coroutine-free advance rules for the repetition drill (research D3 / data-model.md §4).
 * Touches no coroutines, no engine, no clock, and no `var` — the memorization matrix ($V_r$,
 * $M_r$, ∞, A–B loops) is the product's core correctness surface (Constitution Principle V) and
 * is provable here in isolation, with no fakes.
 */
object RepetitionPlanner {
    fun next(
        cursor: PlaybackCursor,
        settings: RepetitionSettings,
        range: IntRange,
        isPlayable: (Int) -> Boolean = { true },
    ): PlanStep {
        val verseRepeat = settings.verseRepeat
        if (verseRepeat.isUnlimited) {
            return PlanStep.Advance(cursor.copy(repetition = cursor.repetition + 1))
        }
        if (cursor.repetition + 1 <= (verseRepeat as RepeatCount.Finite).value) {
            return PlanStep.Advance(cursor.copy(repetition = cursor.repetition + 1))
        }

        val nextVerseIndex = ((cursor.verseIndex + 1)..range.last).firstOrNull { isPlayable(it) }
        if (nextVerseIndex != null) {
            return PlanStep.Advance(PlaybackCursor(nextVerseIndex, 1, cursor.pass))
        }

        val firstPlayableInRange = range.firstOrNull { isPlayable(it) }
            ?: return PlanStep.End

        val matnRepeat = settings.matnRepeat
        if (matnRepeat.isUnlimited) {
            return PlanStep.Advance(PlaybackCursor(firstPlayableInRange, 1, cursor.pass + 1))
        }
        if (cursor.pass + 1 <= (matnRepeat as RepeatCount.Finite).value) {
            return PlanStep.Advance(PlaybackCursor(firstPlayableInRange, 1, cursor.pass + 1))
        }

        return PlanStep.End
    }
}
