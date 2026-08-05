package com.giraffe.matn.presentation

import com.giraffe.matn.presentation.theme.MatnMotion
import com.giraffe.matn.presentation.theme.MotionTransition
import com.giraffe.matn.presentation.theme.resolvedDurationMs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A pure mapping test over the [MotionTransition] table, no rendering.
 *
 * The gate is no longer "everything resolves to 0" — that was stricter than
 * `adaptive-motion-contract.md` §B2.1, whose table specifies *"Fade only"* rather than an immediate
 * cut for screen and sheet transitions. Reduced motion removes translation, parallax and scale; a
 * short opacity crossfade is not motion, and dropping it makes state changes harder to follow for
 * no accessibility gain.
 *
 * What is still guaranteed: the reduced form is never *longer* than the normal one, and it is
 * either instant or short enough that nothing perceptibly travels.
 */
class ReducedMotionSpecTest {

    @Test
    fun `reduce motion never lengthens a transition`() {
        for (transition in MotionTransition.entries) {
            val reduced = transition.resolvedDurationMs(reduceMotion = true)
            assertTrue(
                reduced <= transition.normalDurationMs,
                "$transition resolves to ${reduced}ms under reduce motion, longer than its normal " +
                    "${transition.normalDurationMs}ms",
            )
        }
    }

    @Test
    fun `every reduced form is instant or a crossfade at most`() {
        for (transition in MotionTransition.entries) {
            val reduced = transition.resolvedDurationMs(reduceMotion = true)
            assertTrue(
                reduced <= MatnMotion.quick,
                "$transition resolves to ${reduced}ms under reduce motion — above the " +
                    "${MatnMotion.quick}ms ceiling, which means something is still travelling",
            )
        }
    }

    @Test
    fun `the transitions that lose their whole point are fully instant`() {
        val mustBeInstant = listOf(
            MotionTransition.PROGRESS_FILL,
            MotionTransition.GOAL_RING_SWEEP,
            MotionTransition.AUTO_SCROLL_SETTLE,
            MotionTransition.TAB_SWITCH,
            MotionTransition.ONBOARDING_LOGO_REVEAL,
        )
        for (transition in mustBeInstant) {
            assertEquals(
                0,
                transition.resolvedDurationMs(reduceMotion = true),
                "$transition's normal form *is* the motion — there is no crossfade left to keep",
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
            assertTrue(
                transition.normalDurationMs > 0,
                "$transition's normal duration must be a real animation, not already 0",
            )
        }
    }
}
