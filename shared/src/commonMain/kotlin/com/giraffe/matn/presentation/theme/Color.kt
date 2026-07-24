package com.giraffe.matn.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Canonical Material 3 color roles, pulled verbatim from the Stitch design set (project
 * `5201142409061412050`) and recorded in `docs/DESIGN-SOURCE.md` § Design tokens. Phase 10 ships
 * the light scheme only — dark tokens don't exist in the Stitch set yet and remain Phase 9's job.
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
