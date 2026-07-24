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
}
