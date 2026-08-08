package com.giraffe.matn.presentation

import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.presentation.theme.lineHeightSp
import com.giraffe.matn.presentation.theme.toSp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Arabic verse scale (Matn Design System §03). Stop 5 landed with the Settings frame, so the
 * invariants that used to hold across four stops are pinned here rather than left to a reviewer.
 */
class ReadingFontScaleTest {

    @Test
    fun every_stop_has_a_size_and_a_line_height() {
        for (size in ReadingFontSize.entries) {
            assertTrue(size.toSp().value > 0f, "$size has no size")
            assertTrue(size.lineHeightSp().value > 0f, "$size has no line height")
        }
    }

    @Test
    fun the_scale_increases_monotonically() {
        val sizes = ReadingFontSize.entries.map { it.toSp().value }
        assertEquals(sizes.sorted(), sizes)
        val lineHeights = ReadingFontSize.entries.map { it.lineHeightSp().value }
        assertEquals(lineHeights.sorted(), lineHeights)
    }

    /**
     * Line height is measured per stop, not derived by ratio, and the ratio has to *widen* as the
     * text grows or diacritics on one line start colliding with the line above. Asserting the
     * widening is what stops someone "simplifying" the table back into a flat multiplier.
     */
    @Test
    fun the_line_height_ratio_widens_with_size() {
        val ratios = ReadingFontSize.entries.map { it.lineHeightSp().value / it.toSp().value }
        for (i in 1 until ratios.size) {
            assertTrue(
                ratios[i] >= ratios[i - 1],
                "ratio narrowed at ${ReadingFontSize.entries[i]}: ${ratios[i]} < ${ratios[i - 1]}",
            )
        }
        // Every stop clears the flat 1.6x the scale replaced.
        assertTrue(ratios.all { it > 1.6f })
    }

    /** Stop 5 is the design's "Accessible": 38sp on an 87sp line. */
    @Test
    fun the_accessible_stop_matches_the_design() {
        assertEquals(38f, ReadingFontSize.ACCESSIBLE.toSp().value)
        assertEquals(87f, ReadingFontSize.ACCESSIBLE.lineHeightSp().value)
        assertEquals(ReadingFontSize.ACCESSIBLE, ReadingFontSize.entries.last())
    }

    /** The default must never be the largest stop — Accessible trades breadth for legibility. */
    @Test
    fun the_default_is_not_the_accessible_stop() {
        assertEquals(ReadingFontSize.MEDIUM, ReadingFontSize.DEFAULT)
    }

    // ------------------------------------------------------------------ Reader neighbour stepping

    @Test
    fun one_stop_down_steps_the_real_scale() {
        assertEquals(ReadingFontSize.XLARGE, ReadingFontSize.ACCESSIBLE.oneStopDown())
        assertEquals(ReadingFontSize.LARGE, ReadingFontSize.XLARGE.oneStopDown())
        assertEquals(ReadingFontSize.SMALL, ReadingFontSize.MEDIUM.oneStopDown())
    }

    /** Clamping, not wrapping: a student who chose SMALL has already said what they can read. */
    @Test
    fun one_stop_down_clamps_at_the_bottom() {
        assertEquals(ReadingFontSize.SMALL, ReadingFontSize.SMALL.oneStopDown())
    }

    /** A persisted value from a build that predates stop 5 must still resolve. */
    @Test
    fun unknown_persisted_values_fall_back_to_the_default() {
        assertEquals(ReadingFontSize.ACCESSIBLE, ReadingFontSize.fromStorageOrDefault("ACCESSIBLE"))
        assertEquals(ReadingFontSize.DEFAULT, ReadingFontSize.fromStorageOrDefault("HUGE"))
        assertEquals(ReadingFontSize.DEFAULT, ReadingFontSize.fromStorageOrDefault(null))
    }
}
