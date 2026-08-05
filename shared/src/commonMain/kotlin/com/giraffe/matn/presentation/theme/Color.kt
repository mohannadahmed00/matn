package com.giraffe.matn.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Canonical Material 3 color roles, from the **Matn Design System** (Claude Design project
 * `9902f252-a0d0-46c9-9e44-d7a7d7a8a0b2`, §03 Tokens) — which supersedes the earlier Stitch token
 * table recorded in `docs/DESIGN-SOURCE.md` § Design tokens.
 *
 * Both schemes are gated by `ColorContrastTest` (24 pairs × 2 schemes = 48 assertions). That test
 * computes WCAG 2.1 ratios rather than asserting literal hexes, so these values may be tuned — but
 * only if the gate still passes.
 *
 * ## What changed from the Stitch set, and why
 *
 * - **`primaryContainer` was inverted.** The Stitch pair put a *dark* container (`#5A6D5D`) under a
 *   *light* on-color (`#D9EEDA`) in a light scheme — backwards for the role, and it forced every
 *   container-tinted surface to read as a filled button. Corrected to `#D7E8D8` / `#0B1F12`.
 *   `#5A6D5D` survives as [PrimaryFixedDim], which is what the reader's active-verse rail uses.
 * - **`onSecondaryContainer` was near-illegible** at `#706145` on `#F2DDBA` (~2.4:1, below the 4.5
 *   floor the contrast gate applies to every on-pair). Corrected to `#241A04`.
 * - **`tertiary` became semantic rather than a third neutral.** The Stitch value (`#51504B`) was a
 *   grey indistinguishable from the surface ramp. Tertiary is now the *in-progress* color — the
 *   download arc and nothing else — so "something is happening" never borrows primary or error.
 * - **Dark surfaces gained a green cast** (`#131513` rather than `#121414`), matching the light
 *   scheme's warm paper rather than reading as neutral charcoal beside it.
 */
val Primary = Color(0xFF425546)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFD7E8D8)
val OnPrimaryContainer = Color(0xFF0B1F12)
val InversePrimary = Color(0xFFB7CCB9)

/**
 * The Stitch-era `primaryContainer` value, retained under its correct M3 role. This is the reader's
 * active-verse rail: a 4dp bar that must read as *chrome marking the line*, not as a filled
 * container competing with the Arabic text beside it.
 */
val PrimaryFixedDim = Color(0xFF5A6D5D)
val Secondary = Color(0xFF6B5C41)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFF2DDBA)
val OnSecondaryContainer = Color(0xFF241A04)
val Tertiary = Color(0xFF3E5C63)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFCDE7EC)
val OnTertiaryContainer = Color(0xFF05262B)
val Background = Color(0xFFFCF9F8)
val OnBackground = Color(0xFF1B1C1C)
val Surface = Color(0xFFFCF9F8)
val OnSurface = Color(0xFF1B1C1C)
val SurfaceVariant = Color(0xFFE4E2E1)
val OnSurfaceVariant = Color(0xFF434843)
val SurfaceTint = Color(0xFF506353)
val InverseSurface = Color(0xFF303030)
val InverseOnSurface = Color(0xFFF3F0EF)
val Outline = Color(0xFF737872)
val OutlineVariant = Color(0xFFC3C8C1)
val Error = Color(0xFFBA1A1A)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF410002)
val SurfaceDim = Color(0xFFDEDBDA)
val SurfaceBright = Color(0xFFFCF9F8)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF8F5F3)
val SurfaceContainer = Color(0xFFF0EDED)
val SurfaceContainerHigh = Color(0xFFEAE7E7)
val SurfaceContainerHighest = Color(0xFFE4E1E1)

/** The light color scheme, mapping the design-system token set onto M3 roles. */
val MatnLightColors: ColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    inversePrimary = InversePrimary,
    primaryFixedDim = PrimaryFixedDim,
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
 * The dark scheme (design system §03, dark role table). Tone-mapped from the light roles rather
 * than inverted: the surface ramp keeps the light scheme's warm-green cast so the two themes read
 * as one product rather than two.
 *
 * ⚠️ **[DarkSurface] is mirrored in two places the Kotlin layer cannot reach** —
 * `androidApp/src/main/res/values-night/themes.xml` for the Android launch theme and
 * `iosApp/iosApp/Assets.xcassets/LaunchBackground.colorset` for the iOS launch storyboard. The
 * platform launch window is selected before any Kotlin runs, so this duplication is unavoidable.
 * Change all three together, or the app flashes a different background on cold start than the one
 * it settles into.
 */
val DarkPrimary = Color(0xFFA8C6AB)
val DarkOnPrimary = Color(0xFF17331D)
val DarkPrimaryContainer = Color(0xFF2E4433)
val DarkOnPrimaryContainer = Color(0xFFC4E2C6)
val DarkInversePrimary = Color(0xFF425546)
val DarkPrimaryFixedDim = Color(0xFFC4E2C6)
val DarkSecondary = Color(0xFFDCC49A)
val DarkOnSecondary = Color(0xFF3B2E13)
val DarkSecondaryContainer = Color(0xFF53422A)
val DarkOnSecondaryContainer = Color(0xFFF2DDBA)
val DarkTertiary = Color(0xFFA6CBD2)
val DarkOnTertiary = Color(0xFF0A353B)
val DarkTertiaryContainer = Color(0xFF274B52)
val DarkOnTertiaryContainer = Color(0xFFC2E8EF)
// Surface (#131513) is mirrored in two extra places — see the ⚠️ above.
val DarkBackground = Color(0xFF131513)
val DarkOnBackground = Color(0xFFE3E2DD)
val DarkSurface = Color(0xFF131513)
val DarkOnSurface = Color(0xFFE3E2DD)
val DarkSurfaceVariant = Color(0xFF424841)
val DarkOnSurfaceVariant = Color(0xFFC1C7BE)
val DarkSurfaceTint = Color(0xFFA8C6AB)
val DarkInverseSurface = Color(0xFFE3E2DD)
val DarkInverseOnSurface = Color(0xFF2F312E)
val DarkOutline = Color(0xFF8B928A)
val DarkOutlineVariant = Color(0xFF424841)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkSurfaceDim = Color(0xFF131513)
val DarkSurfaceBright = Color(0xFF393B39)
val DarkSurfaceContainerLowest = Color(0xFF0D0F0D)
val DarkSurfaceContainerLow = Color(0xFF1A1C1A)
val DarkSurfaceContainer = Color(0xFF1E211E)
val DarkSurfaceContainerHigh = Color(0xFF292C29)
val DarkSurfaceContainerHighest = Color(0xFF343834)

/** The dark color scheme, mapping the derived dark roles onto the same M3 roles as the light one. */
val MatnDarkColors: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = DarkInversePrimary,
    primaryFixedDim = DarkPrimaryFixedDim,
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

/**
 * The two states M3 has no slot for (design system §03, semantic tables).
 *
 * **Success** carries *completed and verified*: a finished download, a validated matn, a verse the
 * student has marked memorized. It is deliberately not `primary` — primary means "the thing you can
 * act on", and a completed download is precisely the thing you no longer act on.
 *
 * **Warning** carries *non-blocking problem*: a validation notice the teacher may publish past.
 * Keeping it out of `error` is what makes `error` mean "this stops here" everywhere it appears.
 *
 * Read via [LocalMatnSemantics] rather than importing the instances directly, so a composable
 * picks up the right pair for the ambient theme without branching on appearance itself.
 */
data class MatnSemantics(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

val MatnSemanticsLight = MatnSemantics(
    success = Color(0xFF2E6B44),
    successContainer = Color(0xFFCDEBD5),
    onSuccessContainer = Color(0xFF062012),
    warning = Color(0xFF8A5A00),
    warningContainer = Color(0xFFFFE0AE),
    onWarningContainer = Color(0xFF2A1800),
)

val MatnSemanticsDark = MatnSemantics(
    success = Color(0xFF86D6A0),
    successContainer = Color(0xFF14432A),
    onSuccessContainer = Color(0xFFA6EDBB),
    warning = Color(0xFFE9C16A),
    warningContainer = Color(0xFF4A3410),
    onWarningContainer = Color(0xFFFFDFA6),
)

/**
 * The ambient semantic pair, provided by [MatnTheme] alongside the M3 scheme. Static because it
 * changes only when the whole theme changes, at which point the subtree recomposes anyway.
 */
val LocalMatnSemantics = staticCompositionLocalOf { MatnSemanticsLight }
