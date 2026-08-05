package com.giraffe.matn.presentation.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Corner-radius scale (Matn Design System §03 — Radii). Screens MUST use these instead of a bare
 * `RoundedCornerShape(<n>.dp)` literal (Constitution Principle VIII).
 *
 * The scale is deliberately shallow. Radius in this product carries one distinction — *how much a
 * surface wants to be picked up* — so it runs from near-square structural chrome (table cells,
 * fields) to fully round affordances (buttons, the ring, the sheet handle), with no decorative
 * steps in between.
 */
object MatnShapes {
    /** 4dp. Chips, text fields, table cells — structure, not affordance. */
    val extraSmall = RoundedCornerShape(4.dp)

    /** 8dp. The default: cards, list rows, banners. */
    val lg = RoundedCornerShape(8.dp)

    /** 12dp. Covers, containers, dialogs — surfaces that sit above the page. */
    val xl = RoundedCornerShape(12.dp)

    /**
     * 20dp on the **top corners only**. Bottom sheets, which meet the screen edge at the bottom and
     * would read as a floating card rather than a drawer if all four corners were rounded.
     */
    val sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 0.dp)

    /** Fully round. Buttons, FAB, the goal ring, the sheet drag handle. */
    val full = RoundedCornerShape(CornerSize(50))
}
