package com.giraffe.matn.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Matn's "المخطوط" (manuscript) palette — a deliberate visual identity grounded in the craft of
 * classical Arabic manuscripts rather than stock Material defaults: a parchment page, iron-gall
 * **ink**, a **scholarly green**, and a restrained **illumination gold** (the vocabulary of
 * منمنمات / manuscript illumination). Phase 1 ships the light scheme only (dark mode is Phase 8).
 */
val Parchment = Color(0xFFF7F2E8)        // page background
val ParchmentDim = Color(0xFFEDE4D2)     // raised fields (cover, cards)
val InkBlack = Color(0xFF23201B)         // primary reading ink
val MutedInk = Color(0xFF6C6152)         // secondary text (author, meta)
val ScholarGreen = Color(0xFF14594A)     // primary — interactive, accents on Arabic titles
val ScholarGreenDeep = Color(0xFF0A3128) // on-container green
val ScholarGreenSoft = Color(0xFFCADFD6) // primary container
val IlluminationGold = Color(0xFFB0842A) // secondary — verse rosette, ornament, dividers
val GoldDeep = Color(0xFF4A3608)         // on-secondary-container
val GoldSoft = Color(0xFFEBDCBB)         // secondary container
val Hairline = Color(0xFFE2D8C4)         // subtle rules between verses
val OutlineInk = Color(0xFFB8AB92)       // stronger outlines
val RustError = Color(0xFF8C3B2B)        // muted, period-appropriate error red

/** The single light color scheme for Phase 1, mapping the manuscript palette onto M3 roles. */
val MatnLightColors: ColorScheme = lightColorScheme(
    primary = ScholarGreen,
    onPrimary = Parchment,
    primaryContainer = ScholarGreenSoft,
    onPrimaryContainer = ScholarGreenDeep,
    secondary = IlluminationGold,
    onSecondary = InkBlack,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = GoldDeep,
    background = Parchment,
    onBackground = InkBlack,
    surface = Parchment,
    onSurface = InkBlack,
    surfaceVariant = ParchmentDim,
    onSurfaceVariant = MutedInk,
    outline = OutlineInk,
    outlineVariant = Hairline,
    error = RustError,
    onError = Parchment,
)
