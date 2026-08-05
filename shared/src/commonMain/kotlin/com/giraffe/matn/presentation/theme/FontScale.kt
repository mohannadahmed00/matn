package com.giraffe.matn.presentation.theme

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.giraffe.matn.domain.model.ReadingFontSize

/**
 * The Arabic verse scale (Matn Design System §03 — Arabic type). Five stops were specified; the four
 * here are stops 1–4, matching the persisted [ReadingFontSize] values. Stop 5 ("Accessible",
 * 38sp/87sp, one verse per screen) needs a new enum constant and a settings-slider step, so it is
 * deliberately not smuggled in here.
 *
 * ## Line height is per-stop, never a multiplier
 *
 * Each stop carries its own measured line height rather than deriving one by ratio. Fully
 * diacriticized Arabic stacks تَشْكِيل above and below the baseline, and the clearance that stack
 * needs does not scale linearly with point size — the ratio has to *widen* as the text grows
 * (2.05× at 20sp up to 2.30× at 32sp) or ascenders on one line start colliding with descenders on
 * the one above.
 *
 * This replaces a flat `× 1.6` multiplier, which was too tight for diacritics at every stop.
 */
fun ReadingFontSize.toSp(): TextUnit = when (this) {
    ReadingFontSize.SMALL -> 20.sp
    ReadingFontSize.MEDIUM -> 24.sp
    ReadingFontSize.LARGE -> 28.sp
    ReadingFontSize.XLARGE -> 32.sp
}

/**
 * The measured line height for this stop. Always pair it with [toSp] — using one without the other
 * is what produces either crowded diacritics or a wastefully airy column.
 */
fun ReadingFontSize.lineHeightSp(): TextUnit = when (this) {
    ReadingFontSize.SMALL -> 41.sp
    ReadingFontSize.MEDIUM -> 53.sp
    ReadingFontSize.LARGE -> 62.sp
    ReadingFontSize.XLARGE -> 72.sp
}
