package com.giraffe.matn.presentation.theme

/**
 * T100 (US5, adaptive-motion-contract.md §B2.1) — the six named transitions this phase animates,
 * each carrying its normal duration. Every call site (`MatnNavHost`, the three sheets,
 * `MatnProgressBar`/`InstallProgressIndicator`, `DailyGoalRing`, `ReadingCarousel`,
 * `OnboardingScreen`) resolves its `tween(...)` duration through [resolvedDurationMs] rather than
 * branching `if (reduceMotion)` ad hoc, so the mapping is one table, not six independent guesses.
 */
enum class MotionTransition(val normalDurationMs: Int) {
    SCREEN_SLIDE(MatnMotion.durationMedium),
    SHEET_SLIDE_UP(MatnMotion.durationMedium),
    PROGRESS_FILL(MatnMotion.durationShort),
    GOAL_RING_SWEEP(MatnMotion.durationShort),
    CAROUSEL_VERSE_SLIDE(MatnMotion.durationMedium),
    ONBOARDING_LOGO_REVEAL(MatnMotion.durationLong),
}

/**
 * Every transition collapses to an immediate (0ms) change under reduce motion. This is what
 * "Fade only" / "Immediate value" / "No reveal; static" have in common (contract §B2.1): the
 * *information* — the value, the active verse, the panel — is never suppressed, only the
 * interpolation stops.
 */
fun MotionTransition.resolvedDurationMs(reduceMotion: Boolean): Int =
    if (reduceMotion) 0 else normalDurationMs
