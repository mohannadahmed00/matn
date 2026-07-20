package com.giraffe.matn.playback

import com.giraffe.matn.domain.model.PlaybackCursor
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.domain.model.RepetitionSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Table tests for [RepetitionPlanner] — rows P1–P12 of
 * [contracts/repetition-contract.md](../../../../../../../specs/004-repetition-engine/contracts/repetition-contract.md)
 * §3. Pure `kotlin.test` assertions, no fakes, no coroutines (Principle V).
 */
class RepetitionPlannerTest {

    private val fullRange = 0..4

    @Test
    fun p1_Vr3_rep1_advances_same_verse() {
        val settings = RepetitionSettings(verseRepeat = RepeatCount.of(3))
        val step = RepetitionPlanner.next(PlaybackCursor(2, repetition = 1, pass = 1), settings, fullRange)
        val advance = assertIs<PlanStep.Advance>(step)
        assertEquals(PlaybackCursor(2, 2, 1), advance.cursor)
    }

    @Test
    fun p2_Vr3_rep3_advances_next_verse() {
        val settings = RepetitionSettings(verseRepeat = RepeatCount.of(3))
        val step = RepetitionPlanner.next(PlaybackCursor(2, repetition = 3, pass = 1), settings, fullRange)
        val advance = assertIs<PlanStep.Advance>(step)
        assertEquals(PlaybackCursor(3, 1, 1), advance.cursor)
    }

    @Test
    fun p3_Vr1_lastVerse_Mr1_pass1_ends() {
        val settings = RepetitionSettings(verseRepeat = RepeatCount.ONE, matnRepeat = RepeatCount.ONE)
        val step = RepetitionPlanner.next(PlaybackCursor(fullRange.last, 1, 1), settings, fullRange)
        assertIs<PlanStep.End>(step)
    }

    @Test
    fun p4_Vr1_lastVerse_Mr3_pass1_advances_next_pass() {
        val settings = RepetitionSettings(verseRepeat = RepeatCount.ONE, matnRepeat = RepeatCount.of(3))
        val step = RepetitionPlanner.next(PlaybackCursor(fullRange.last, 1, 1), settings, fullRange)
        val advance = assertIs<PlanStep.Advance>(step)
        assertEquals(PlaybackCursor(fullRange.first, 1, 2), advance.cursor)
    }

    @Test
    fun p5_Vr1_lastVerse_Mr3_pass3_ends() {
        val settings = RepetitionSettings(verseRepeat = RepeatCount.ONE, matnRepeat = RepeatCount.of(3))
        val step = RepetitionPlanner.next(PlaybackCursor(fullRange.last, 1, 3), settings, fullRange)
        assertIs<PlanStep.End>(step)
    }

    @Test
    fun p6_Vr_unlimited_advances_same_verse() {
        val settings = RepetitionSettings(verseRepeat = RepeatCount.Unlimited)
        val step = RepetitionPlanner.next(PlaybackCursor(1, repetition = 97, pass = 1), settings, fullRange)
        val advance = assertIs<PlanStep.Advance>(step)
        assertEquals(PlaybackCursor(1, 98, 1), advance.cursor)
    }

    @Test
    fun p7_Mr_unlimited_lastVerse_advances_next_pass() {
        val settings = RepetitionSettings(verseRepeat = RepeatCount.ONE, matnRepeat = RepeatCount.Unlimited)
        val step = RepetitionPlanner.next(PlaybackCursor(fullRange.last, 1, 1), settings, fullRange)
        val advance = assertIs<PlanStep.Advance>(step)
        assertEquals(PlaybackCursor(fullRange.first, 1, 2), advance.cursor)
    }

    @Test
    fun p8_both_unlimited_never_reaches_range_end() {
        val settings = RepetitionSettings(verseRepeat = RepeatCount.Unlimited, matnRepeat = RepeatCount.Unlimited)
        val step = RepetitionPlanner.next(PlaybackCursor(fullRange.last, repetition = 5, pass = 1), settings, fullRange)
        val advance = assertIs<PlanStep.Advance>(step)
        assertEquals(PlaybackCursor(fullRange.last, 6, 1), advance.cursor)
    }

    @Test
    fun p9_lowering_Vr_below_completed_repetitions_advances() {
        val settings = RepetitionSettings(verseRepeat = RepeatCount.of(2))
        val step = RepetitionPlanner.next(PlaybackCursor(1, repetition = 5, pass = 1), settings, fullRange)
        val advance = assertIs<PlanStep.Advance>(step)
        assertEquals(PlaybackCursor(2, 1, 1), advance.cursor)
    }

    @Test
    fun p10_single_verse_range_Mr_unlimited_repeats_pass() {
        val range = 2..2
        val settings = RepetitionSettings(verseRepeat = RepeatCount.ONE, matnRepeat = RepeatCount.Unlimited)
        val step = RepetitionPlanner.next(PlaybackCursor(2, 1, 1), settings, range)
        val advance = assertIs<PlanStep.Advance>(step)
        assertEquals(PlaybackCursor(2, 1, 2), advance.cursor)
    }

    @Test
    fun p11_isPlayable_false_skips_to_next_playable_verse() {
        val settings = RepetitionSettings(verseRepeat = RepeatCount.ONE)
        val step = RepetitionPlanner.next(
            cursor = PlaybackCursor(0, 1, 1),
            settings = settings,
            range = fullRange,
            isPlayable = { idx -> idx != 1 },
        )
        val advance = assertIs<PlanStep.Advance>(step)
        assertEquals(PlaybackCursor(2, 1, 1), advance.cursor)
    }

    @Test
    fun p12_isPlayable_false_for_every_verse_ends() {
        val settings = RepetitionSettings(verseRepeat = RepeatCount.ONE, matnRepeat = RepeatCount.of(3))
        val step = RepetitionPlanner.next(
            cursor = PlaybackCursor(fullRange.last, 1, 1),
            settings = settings,
            range = fullRange,
            isPlayable = { false },
        )
        assertIs<PlanStep.End>(step)
    }
}
