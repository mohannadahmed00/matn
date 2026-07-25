package com.giraffe.matn.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.giraffe.matn.domain.model.Appearance
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.domain.model.effectiveAppearance

/**
 * App-wide theme wrapper (Phase 9). Forces **right-to-left** layout direction at the root so every
 * screen renders RTL regardless of device locale, resolves [themeMode] against the platform
 * system-dark signal once here (FR-006 — the single decision point), and applies the canonical
 * Stitch token set (`docs/DESIGN-SOURCE.md`):
 * - Color: [MatnLightColors] / [MatnDarkColors] via `MaterialTheme.colorScheme`.
 * - Typography: [matnTypography], via `MaterialTheme.typography`.
 * - Shape: [MatnShapes] (`lg`/`xl`/`full`) — also mapped onto `MaterialTheme.shapes.medium/large`
 *   so M3 components that default to the ambient shape scale pick up the right radii for free.
 * - Spacing: [MatnSpacing] — a plain object, not composition-local; import it directly where
 *   needed (see `research.md` Decision 4 for why this stays lightweight rather than plumbed
 *   through a `CompositionLocal`).
 * - Motion: [LocalReduceMotion] — provided here from [reduceMotion]; defaults to `false`.
 *
 * [themeMode] and [reduceMotion] are **both defaulted** so the 88 existing `@Preview`s keep
 * working untouched (research D12, rule 3).
 *
 * Paints the Material `background` behind content via a root [Surface] so the app (and every
 * `@Preview`) has an opaque backdrop rather than a transparent window.
 */
@Composable
fun MatnTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemIsDark = isSystemInDarkTheme()
    val appearance = themeMode.effectiveAppearance(systemIsDark)
    val colorScheme: ColorScheme = if (appearance == Appearance.DARK) MatnDarkColors else MatnLightColors
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = matnTypography(),
            shapes = Shapes(medium = MatnShapes.lg, large = MatnShapes.xl),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                content()
            }
        }
    }
}

/** T051: every colour role as a labelled swatch, for eyeballing a scheme end to end. */
@Composable
private fun ColorSwatchSheet() {
    val scheme = MaterialTheme.colorScheme
    val roles = listOf(
        "primary" to scheme.primary, "onPrimary" to scheme.onPrimary,
        "primaryContainer" to scheme.primaryContainer, "onPrimaryContainer" to scheme.onPrimaryContainer,
        "inversePrimary" to scheme.inversePrimary,
        "secondary" to scheme.secondary, "onSecondary" to scheme.onSecondary,
        "secondaryContainer" to scheme.secondaryContainer, "onSecondaryContainer" to scheme.onSecondaryContainer,
        "tertiary" to scheme.tertiary, "onTertiary" to scheme.onTertiary,
        "tertiaryContainer" to scheme.tertiaryContainer, "onTertiaryContainer" to scheme.onTertiaryContainer,
        "background" to scheme.background, "onBackground" to scheme.onBackground,
        "surface" to scheme.surface, "onSurface" to scheme.onSurface,
        "surfaceVariant" to scheme.surfaceVariant, "onSurfaceVariant" to scheme.onSurfaceVariant,
        "surfaceTint" to scheme.surfaceTint,
        "inverseSurface" to scheme.inverseSurface, "inverseOnSurface" to scheme.inverseOnSurface,
        "outline" to scheme.outline, "outlineVariant" to scheme.outlineVariant,
        "error" to scheme.error, "onError" to scheme.onError,
        "errorContainer" to scheme.errorContainer, "onErrorContainer" to scheme.onErrorContainer,
        "surfaceDim" to scheme.surfaceDim, "surfaceBright" to scheme.surfaceBright,
        "surfaceContainerLowest" to scheme.surfaceContainerLowest,
        "surfaceContainerLow" to scheme.surfaceContainerLow,
        "surfaceContainer" to scheme.surfaceContainer,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh,
        "surfaceContainerHighest" to scheme.surfaceContainerHighest,
    )
    LazyColumn(modifier = Modifier.fillMaxSize().background(scheme.background)) {
        items(items = roles, key = { it.first }) { (name, color) ->
            ColorSwatchRow(name = name, color = color)
        }
    }
}

@Composable
private fun ColorSwatchRow(name: String, color: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.fillMaxWidth().background(color).padding(12.dp)) {
            Text(text = name)
        }
    }
}

@Preview
@Composable
private fun ColorSwatchSheetLightPreview() {
    MatnTheme(themeMode = ThemeMode.LIGHT) { ColorSwatchSheet() }
}

@Preview
@Composable
private fun ColorSwatchSheetDarkPreview() {
    MatnTheme(themeMode = ThemeMode.DARK) { ColorSwatchSheet() }
}
