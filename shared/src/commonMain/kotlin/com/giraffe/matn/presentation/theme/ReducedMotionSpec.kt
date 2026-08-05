package com.giraffe.matn.presentation.theme

/**
 * The named transitions the app animates, each carrying its normal duration and its reduced-motion
 * form. Every call site resolves its `tween(...)` duration through [resolvedDurationMs] rather than
 * branching `if (reduceMotion)` ad hoc, so the mapping stays one table rather than a dozen
 * independent guesses.
 *
 * ## Reduced motion removes *motion*, not *transition*
 *
 * Design System §07: "Reduced motion is a real spec, not a switch that disables everything: state
 * still changes visibly, it just changes by crossfade." What a reduce-motion setting is asking to
 * be spared is translation, parallax, and scale — the things that move across the retina. A 90ms
 * opacity crossfade does not move, and removing it buys the user nothing while making state changes
 * harder to follow.
 *
 * So each transition declares its own [reducedDurationMs]:
 * - **[MatnMotion.fade] (90ms)** where the normal form is a slide or fade and the reduced form is a
 *   crossfade in place — screens, sheets, the verse focus block, the player bar.
 * - **0ms** where the normal form *is* the motion and there is nothing left once it is removed —
 *   an auto-scroll, a progress fill, a ring sweep, a logo reveal.
 * - **[MatnMotion.quick] (140ms)** for the goal-met moment, which keeps its colour crossfade and
 *   check swap after losing its spring.
 *
 * This corrects an earlier implementation that resolved *every* transition to 0ms, which
 * contradicted `adaptive-motion-contract.md` §B2.1 — that table already specified "Fade only,
 * `durationShort`" for screen and sheet transitions rather than an immediate cut.
 */
enum class MotionTransition(
    val normalDurationMs: Int,
    val reducedDurationMs: Int,
) {
    /** Route push/pop. Normal: translateX ±24dp (direction-aware) + alpha. Reduced: crossfade. */
    SCREEN_SLIDE(MatnMotion.page, MatnMotion.fade),

    /** Bottom sheets. Normal: translateY 100%→0 with scrim. Reduced: scrim + content crossfade. */
    SHEET_SLIDE_UP(MatnMotion.sheet, MatnMotion.fade),

    /** Download/install progress. Reduced: the bar snaps to each value — there is no fade to keep. */
    PROGRESS_FILL(MatnMotion.bar, 0),

    /** Daily-goal ring sweep. Reduced: the ring redraws instantly and the number counts in one step. */
    GOAL_RING_SWEEP(MatnMotion.page, 0),

    /** The goal-met bloom — the only spring in the product. Reduced: colour + check swap, no scale. */
    GOAL_MET_BLOOM(MatnMotion.bloom, MatnMotion.quick),

    /** Verse→verse focus change. Normal: translateY ±16dp + alpha. Reduced: alpha only. */
    CAROUSEL_VERSE_SLIDE(MatnMotion.sheet, MatnMotion.fade),

    /** Active-verse highlight: rail width, container colour, text alpha. Reduced: alpha only. */
    ACTIVE_VERSE_HIGHLIGHT(MatnMotion.menu, MatnMotion.fade),

    /** Auto-scroll settle. Reduced: an instant jump — the scroll *is* the motion. */
    AUTO_SCROLL_SETTLE(MatnMotion.bar, 0),

    /** Play/pause icon morph. Reduced: icon crossfade, no press scale. */
    PLAY_PAUSE_MORPH(MatnMotion.quick, MatnMotion.fade),

    /** Player bar expand/collapse. Reduced: height changes with a crossfade, no content stagger. */
    PLAYER_BAR_EXPAND(MatnMotion.sheet, MatnMotion.fade),

    /** "Mark as memorized". Reduced: colour and label swap, check appears already drawn. */
    MARK_MEMORIZED(MatnMotion.menu, MatnMotion.fade),

    /** Bottom-nav tab switch (fade-through). Reduced: instant content swap. */
    TAB_SWITCH(MatnMotion.fade, 0),

    /** Teacher editor list add/remove/reorder. Reduced: instant reflow with a crossfade. */
    EDITOR_LIST_CHANGE(MatnMotion.menu, MatnMotion.fade),

    /** Onboarding logo reveal. Reduced: no reveal, static. */
    ONBOARDING_LOGO_REVEAL(MatnMotion.page, 0),
}

/**
 * The duration to hand a `tween(...)`, given the ambient reduce-motion flag.
 *
 * **Never suppressed**: the *information*. A progress bar still shows its value, the active verse is
 * still indicated, the ring still reads "4/10" — only the interpolation shortens or stops.
 */
fun MotionTransition.resolvedDurationMs(reduceMotion: Boolean): Int =
    if (reduceMotion) reducedDurationMs else normalDurationMs
