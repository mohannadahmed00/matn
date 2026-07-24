package com.giraffe.matn.presentation.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Corner-radius scale from the Stitch design set (`docs/DESIGN-SOURCE.md` § Design tokens:
 * `borderRadius.lg/xl/full`). Screens MUST use these instead of a bare `RoundedCornerShape(<n>.dp)`
 * literal (Constitution Principle VIII).
 */
object MatnShapes {
    /** Stitch `lg`: 0.5rem. Cards, buttons, chips. */
    val lg = RoundedCornerShape(8.dp)

    /** Stitch `xl`: 0.75rem. Larger surfaces — hero cards, bottom sheets, the active-verse card. */
    val xl = RoundedCornerShape(12.dp)

    /** Stitch `full`: 9999px. Pills — transport buttons, tags, the resume CTA. */
    val full = RoundedCornerShape(CornerSize(50))
}
