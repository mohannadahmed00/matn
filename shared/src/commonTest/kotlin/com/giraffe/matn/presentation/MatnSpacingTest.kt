package com.giraffe.matn.presentation

import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.WindowWidthClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T034: the width-aware spacing rules (data-model §3.4, contract adaptive-motion §A2).
 *  - `horizontalMargin`/`libraryColumns` per class.
 *  - SC-011: `libraryColumns(EXPANDED) >= 2 × libraryColumns(COMPACT)`.
 *  - The invariant behind FR-029: a control surface (`surfaceMaxWidth`) must be narrower than a
 *    reading measure (`readingMaxWidth`) — a control bar stretched to a reading measure pushes
 *    its controls uncomfortably far apart.
 */
class MatnSpacingTest {

    @Test
    fun `horizontalMargin uses mobile margin on COMPACT and desktop margin on wider classes`() {
        assertEquals(MatnSpacing.marginMobile, MatnSpacing.horizontalMargin(WindowWidthClass.COMPACT))
        assertEquals(MatnSpacing.marginDesktop, MatnSpacing.horizontalMargin(WindowWidthClass.MEDIUM))
        assertEquals(MatnSpacing.marginDesktop, MatnSpacing.horizontalMargin(WindowWidthClass.EXPANDED))
    }

    @Test
    fun `libraryColumns steps 2 3 4 across the three classes`() {
        assertEquals(2, MatnSpacing.libraryColumns(WindowWidthClass.COMPACT))
        assertEquals(3, MatnSpacing.libraryColumns(WindowWidthClass.MEDIUM))
        assertEquals(4, MatnSpacing.libraryColumns(WindowWidthClass.EXPANDED))
    }

    @Test
    fun `SC-011 - EXPANDED column count is at least double COMPACT`() {
        assertTrue(
            MatnSpacing.libraryColumns(WindowWidthClass.EXPANDED) >= 2 * MatnSpacing.libraryColumns(WindowWidthClass.COMPACT),
            "EXPANDED columns must be at least 2× COMPACT (SC-011)",
        )
    }

    @Test
    fun `surfaceMaxWidth is strictly narrower than readingMaxWidth - FR-029 invariant`() {
        assertTrue(
            MatnSpacing.surfaceMaxWidth < MatnSpacing.readingMaxWidth,
            "A control bar must not stretch to a reading measure (FR-029)",
        )
    }
}