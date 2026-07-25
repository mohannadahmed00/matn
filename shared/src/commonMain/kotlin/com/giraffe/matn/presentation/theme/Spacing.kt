package com.giraffe.matn.presentation.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale from the Stitch design set (`docs/DESIGN-SOURCE.md` § Design tokens). Screens
 * MUST use these (or a small multiple of [unit]) instead of a bare `.dp` literal for layout
 * padding/gaps/sizes (Constitution Principle VIII). Icon intrinsic sizes tied to platform icon
 * guidelines are exempt — this governs layout spacing, not icon size.
 */
object MatnSpacing {
    /** Stitch `unit`: 8px — the base increment; most gaps are a small multiple of this. */
    val unit: Dp = 8.dp

    /** Stitch `gutter`: 24px — spacing between grid items / major sections. */
    val gutter: Dp = 24.dp

    /** Stitch `margin-mobile`: 20px — horizontal screen edge inset on phones. */
    val marginMobile: Dp = 20.dp

    /** Stitch `margin-desktop`: 64px — horizontal screen edge inset on wide/tablet layouts. */
    val marginDesktop: Dp = 64.dp

    /** Per-class horizontal screen edge inset (FR-027). COMPACT uses the phone margin; MEDIUM/EXPANDED the desktop one. */
    fun horizontalMargin(w: WindowWidthClass): Dp =
        if (w == WindowWidthClass.COMPACT) marginMobile else marginDesktop

    /** The bounded measure for verse and text-heavy content; content centres when wider (FR-028). */
    val readingMaxWidth: Dp = 640.dp

    /**
     * The bound for control bars, sheets, and dialogs (FR-029). Deliberately narrower than
     * [readingMaxWidth]: a comfortable *reading* measure is too wide for a control bar, which
     * pushes its controls uncomfortably far apart. Invariant asserted by `MatnSpacingTest`.
     */
    val surfaceMaxWidth: Dp = 480.dp

    /** Library-grid column count per width class (FR-027; SC-011 requires EXPANDED >= 2 × COMPACT). */
    fun libraryColumns(w: WindowWidthClass): Int = when (w) {
        WindowWidthClass.COMPACT -> 2
        WindowWidthClass.MEDIUM -> 3
        WindowWidthClass.EXPANDED -> 4
    }
}
