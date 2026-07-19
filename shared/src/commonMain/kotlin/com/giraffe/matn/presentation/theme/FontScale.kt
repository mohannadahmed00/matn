package com.giraffe.matn.presentation.theme

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.giraffe.matn.domain.model.ReadingFontSize

/**
 * Maps a [ReadingFontSize] to the concrete verse text size in `sp` (FR-016/SC-007).
 *
 * Defaults (SMALL=18, MEDIUM=22, LARGE=26, XLARGE=30) are tuned during implementation; T037/T043
 * confirms no clipping/overlap at SMALL and XLARGE.
 */
fun ReadingFontSize.toSp(): TextUnit = when (this) {
    ReadingFontSize.SMALL -> 18.sp
    ReadingFontSize.MEDIUM -> 22.sp
    ReadingFontSize.LARGE -> 26.sp
    ReadingFontSize.XLARGE -> 30.sp
}