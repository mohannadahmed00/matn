package com.giraffe.matn.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Canonical Material 3 color roles, pulled verbatim from the Stitch design set (project
 * `5201142409061412050`) and recorded in `docs/DESIGN-SOURCE.md` § Design tokens. Phase 10 ships
 * the light scheme; Phase 9 (US1) adds the derived dark scheme here (`MatnDarkColors`).
 */
val Primary = Color(0xFF425546)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFF5A6D5D)
val OnPrimaryContainer = Color(0xFFD9EEDA)
val InversePrimary = Color(0xFFB7CCB9)
val Secondary = Color(0xFF6B5C41)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFF2DDBA)
val OnSecondaryContainer = Color(0xFF706145)
val Tertiary = Color(0xFF51504B)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFF696863)
val OnTertiaryContainer = Color(0xFFEBE8E1)
val Background = Color(0xFFFCF9F8)
val OnBackground = Color(0xFF1B1C1C)
val Surface = Color(0xFFFCF9F8)
val OnSurface = Color(0xFF1B1C1C)
val SurfaceVariant = Color(0xFFE4E2E1)
val OnSurfaceVariant = Color(0xFF434843)
val SurfaceTint = Color(0xFF506353)
val InverseSurface = Color(0xFF303030)
val InverseOnSurface = Color(0xFFF3F0F0)
val Outline = Color(0xFF737872)
val OutlineVariant = Color(0xFFC3C8C1)
val Error = Color(0xFFBA1A1A)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF93000A)
val SurfaceDim = Color(0xFFDCD9D9)
val SurfaceBright = Color(0xFFFCF9F8)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF6F3F2)
val SurfaceContainer = Color(0xFFF0EDED)
val SurfaceContainerHigh = Color(0xFFEAE7E7)
val SurfaceContainerHighest = Color(0xFFE4E2E1)

/** The single light color scheme for Phase 10, mapping the Stitch token set onto M3 roles. */
val MatnLightColors: ColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    inversePrimary = InversePrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceTint = SurfaceTint,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    surfaceDim = SurfaceDim,
    surfaceBright = SurfaceBright,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
)

/**
 * Phase 9 (US1 / T037) — the dark scheme, derived from the light roles by the tone-mapping rule
 * in research D3 and *pre-checked* against the contract's 24-pair contrast table. These exact
 * values are what makes `ColorContrastTest` pass; do not substitute your own.
 *
 * ⚠️ **Surface / DarkSurface are mirrored in two places the Kotlin layer cannot reach** —
 * `androidApp/src/main/res/values{,-night}/themes.xml` (T047) for the Android launch theme and
 * `iosApp/iosApp/Assets.xcassets` `LaunchBackground` (T050) for the iOS launch storyboard. The
 * platform launch window is selected before any Kotlin runs, so this duplication is unavoidable;
 * the three-way agreement is re-checked by T105 — change all three together.
 */
val DarkPrimary = Color(0xFFB6CCBA)
val DarkOnPrimary = Color(0xFF233427)
val DarkPrimaryContainer = Color(0xFF384B3C)
val DarkOnPrimaryContainer = Color(0xFFD2E8D6)
val DarkInversePrimary = Color(0xFF506354)
val DarkSecondary = Color(0xFFD7C4A5)
val DarkOnSecondary = Color(0xFF3B2F16)
val DarkSecondaryContainer = Color(0xFF53452B)
val DarkOnSecondaryContainer = Color(0xFFF3E0C1)
val DarkTertiary = Color(0xFFC8C6C1)
val DarkOnTertiary = Color(0xFF31302C)
val DarkTertiaryContainer = Color(0xFF484742)
val DarkOnTertiaryContainer = Color(0xFFE4E2DC)
// Surface (#121414) is mirrored in two extra places — see the ⚠️ above.
val DarkBackground = Color(0xFF121414)
val DarkOnBackground = Color(0xFFE1E3E3)
val DarkSurface = Color(0xFF121414)
val DarkOnSurface = Color(0xFFE1E3E3)
val DarkSurfaceVariant = Color(0xFF434843)
val DarkOnSurfaceVariant = Color(0xFFC2C8C2)
val DarkSurfaceTint = Color(0xFFB6CCBA)
val DarkInverseSurface = Color(0xFFE1E3E3)
val DarkInverseOnSurface = Color(0xFF2F3131)
val DarkOutline = Color(0xFF8C928C)
val DarkOutlineVariant = Color(0xFF434843)
val DarkError = Color(0xFFFCB5A7)
val DarkOnError = Color(0xFF660806)
val DarkErrorContainer = Color(0xFF900B0F)
val DarkOnErrorContainer = Color(0xFFF9DCD6)
val DarkSurfaceDim = Color(0xFF121414)
val DarkSurfaceBright = Color(0xFF383939)
val DarkSurfaceContainerLowest = Color(0xFF0D0E0E)
val DarkSurfaceContainerLow = Color(0xFF1B1C1C)
val DarkSurfaceContainer = Color(0xFF1F2020)
val DarkSurfaceContainerHigh = Color(0xFF292A2A)
val DarkSurfaceContainerHighest = Color(0xFF343535)

/** The dark color scheme for Phase 9, mapping the derived dark roles onto the same 34 M3 roles. */
val MatnDarkColors: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = DarkInversePrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkSurfaceTint,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
)
