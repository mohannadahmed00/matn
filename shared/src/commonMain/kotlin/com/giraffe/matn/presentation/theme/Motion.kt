package com.giraffe.matn.presentation.theme

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.compositionLocalOf

/**
 * One shared motion vocabulary (FR-032, contract adaptive-motion §B1). Screens never name a
 * duration or easing of their own — they reach for these tokens. The only file allowed to name
 * durations is this one (rule 2).
 */
object MatnMotion {
    /** Quick value changes (progress, toggles). 150ms targets SC-013's 100ms input-delay ceiling. */
    const val durationShort: Int = 150

    /** Screen and sheet transitions. */
    const val durationMedium: Int = 250

    /** Long reveal animations (onboarding panels). */
    const val durationLong: Int = 400

    /** Standard enter/grow easing. */
    val easingStandard = FastOutSlowInEasing

    /** Emphasized easing for hero transitions. */
    val easingEmphasized = FastOutSlowInEasing

    /** Exit/shrink easing. */
    val easingExit = FastOutLinearInEasing
}

/**
 * Resolved reduce-motion flag (research D7). Provided by [MatnTheme] from the
 * [com.giraffe.matn.domain.preferences.MotionPreferences] seam; defaults to `false` so the 88
 * existing `@Preview`s keep working untouched (research D12).
 *
 * Plain [compositionLocalOf]: this can change mid-session if the OS-level "reduce motion"
 * accessibility setting changes, and `staticCompositionLocalOf` would force a full recomposition
 * of the whole provided subtree on that change instead of just the readers of this value.
 */
val LocalReduceMotion = compositionLocalOf { false }