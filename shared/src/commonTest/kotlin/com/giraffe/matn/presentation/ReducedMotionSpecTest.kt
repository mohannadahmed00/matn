package com.giraffe.matn.presentation

import com.giraffe.matn.presentation.theme.MotionTransition
import com.giraffe.matn.presentation.theme.resolvedDurationMs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T100 (US5, adaptive-motion-contract.md §B2.1) — a pure mapping test, no rendering. Every one of
 * the six named transitions resolves to its reduced (0ms, immediate) form when the flag is set,
 * and to its documented normal duration otherwise.
 */
class ReducedMotionSpecTest {

    @Test
    fun `every transition is immediate under reduce motion`() {
        for (transition in MotionTransition.entries) {
            assertEquals(
                0,
                transition.resolvedDurationMs(reduceMotion = true),
                "$transition must resolve to an immediate (0ms) change under reduce motion",
            )
        }
    }

    @Test
    fun `every transition uses its documented normal duration otherwise`() {
        for (transition in MotionTransition.entries) {
            assertEquals(
                transition.normalDurationMs,
                transition.resolvedDurationMs(reduceMotion = false),
            )
            assertTrue(transition.normalDurationMs > 0, "$transition's normal duration must be a real animation, not already 0")
        }
    }
}
