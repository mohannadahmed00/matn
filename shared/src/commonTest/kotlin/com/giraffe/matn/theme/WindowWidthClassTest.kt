package com.giraffe.matn.theme

import com.giraffe.matn.presentation.theme.WindowWidthClass
import com.giraffe.matn.presentation.theme.widthClassFor
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T033: every breakpoint boundary, both sides. The lower bound is exclusive at each transition
 * (COMPACT covers `< 600.dp`, MEDIUM covers `600 ..< 840.dp`, EXPANDED covers `>= 840.dp`).
 */
class WindowWidthClassTest {

    @Test
    fun `below the COMPACT or MEDIUM lower bounds`() {
        assertEquals(WindowWidthClass.COMPACT, widthClassFor(319.dp))
        assertEquals(WindowWidthClass.COMPACT, widthClassFor(320.dp))
    }

    @Test
    fun `just under MEDIUM`() {
        assertEquals(WindowWidthClass.COMPACT, widthClassFor(599.dp))
    }

    @Test
    fun `MEDIUM boundaries`() {
        assertEquals(WindowWidthClass.MEDIUM, widthClassFor(600.dp))
        assertEquals(WindowWidthClass.MEDIUM, widthClassFor(839.dp))
    }

    @Test
    fun `EXPANDED boundaries`() {
        assertEquals(WindowWidthClass.EXPANDED, widthClassFor(840.dp))
        assertEquals(WindowWidthClass.EXPANDED, widthClassFor(1280.dp))
    }
}