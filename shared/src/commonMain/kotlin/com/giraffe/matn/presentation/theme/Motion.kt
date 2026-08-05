package com.giraffe.matn.presentation.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.compositionLocalOf

/**
 * One shared motion vocabulary (Matn Design System §07 — *Four curves, nine moments*). Screens never
 * name a duration or easing of their own; they reach for these tokens. This is the only file
 * allowed to name durations.
 *
 * The design's governing rule: **nothing bounces and nothing overshoots** except the single
 * goal-met bloom. A student may watch the verse transition fire several thousand times in one
 * sitting, so it has to be invisible by the hundredth — which rules out anything playful on the
 * paths that repeat.
 */
object MatnMotion {

    // ---- Duration ladder (§07) -------------------------------------------------------------

    /** 90ms — the reduced-motion crossfade, and the shortest state change worth interpolating. */
    const val fade: Int = 90

    /** 140ms — icon morphs, press feedback, small in-place value changes. */
    const val quick: Int = 140

    /** 180ms — the active-verse highlight, menus, "mark memorized". */
    const val menu: Int = 180

    /** 220ms — sheets and the verse-to-verse focus transition entering. */
    const val sheet: Int = 220

    /** 250ms — the player bar, auto-scroll settle, download progress updates. */
    const val bar: Int = 250

    /** 400ms — route pushes and the goal-ring sweep. */
    const val page: Int = 400

    /** 620ms — the goal-met bloom. The one long animation, and the one spring, in the product. */
    const val bloom: Int = 620

    // ---- Easings (§07) ---------------------------------------------------------------------

    /** Default for anything changing state in place: highlights, fades, progress, colour. */
    val easingStandard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Things entering — sheets, menus, dialogs, snackbars, the goal-ring fill. */
    val easingDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Things leaving. Exits are always shorter than their matching entry. */
    val easingAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Indeterminate loops (shimmer, spinners) and reduced-motion crossfades only. */
    val easingLinear: Easing = LinearEasing

    // ---- Legacy names ----------------------------------------------------------------------
    // The pre-design-system vocabulary, kept so the ~15 existing call sites keep compiling and
    // pick up the new curves for free. Prefer the ladder above in new code.

    /** @see quick */
    const val durationShort: Int = quick

    /** @see bar */
    const val durationMedium: Int = bar

    /** @see page */
    const val durationLong: Int = page

    /** @see easingDecelerate */
    val easingEmphasized: Easing = easingDecelerate

    /** @see easingAccelerate */
    val easingExit: Easing = easingAccelerate
}

/**
 * Resolved reduce-motion flag (research D7). Provided by [MatnTheme] from the
 * [com.giraffe.matn.domain.preferences.MotionPreferences] seam; defaults to `false` so the existing
 * `@Preview`s keep working untouched (research D12).
 *
 * Plain [compositionLocalOf]: this can change mid-session if the OS-level "reduce motion"
 * accessibility setting changes, and `staticCompositionLocalOf` would force a full recomposition
 * of the whole provided subtree on that change instead of just the readers of this value.
 */
val LocalReduceMotion = compositionLocalOf { false }
