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
 * App-wide theme wrapper (Phase 9). Resolves [themeMode] against the platform system-dark signal
 * once here (FR-006 — the single decision point), and applies the canonical Stitch token set
 * (`docs/DESIGN-SOURCE.md`):
 * - Color: [MatnLightColors] / [MatnDarkColors] via `MaterialTheme.colorScheme`, plus the two
 *   non-M3 roles ([MatnSemantics]) via [LocalMatnSemantics].
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
 * ## Layout direction follows the interface language
 *
 * [layoutDirection] defaults to the **ambient** direction — the one the platform already derived
 * from the device locale — rather than forcing `Rtl` as it did through Phase 13. The student app is
 * bilingual: `values/` is Arabic and `values-en/` is English, and the resource loader picks between
 * them by locale. Pinning the layout to RTL while the loader served English strings put the two
 * halves of the interface into permanent disagreement, and that mismatch — not any single layout
 * bug — is what produced the reversed sizes, torn-apart durations, and line-leading full stops
 * across the app.
 *
 * Deferring to the ambient value means an Arabic device gets RTL with Arabic copy and an English
 * device gets LTR with English copy, with no per-screen branching. Callers that genuinely need to
 * pin a direction still can: `:teacherApp` passes `Ltr` for its English interface, and individual
 * `@Preview`s pass `Rtl` to exercise the mirrored layout.
 *
 * Direction-dependent *content* is handled separately, at the string level, by the bidi isolate
 * helpers in [com.giraffe.matn.presentation.common.ltrIsolated] — layout direction alone cannot fix
 * a numeral embedded in an opposite-direction sentence.
 *
 * Paints the Material `background` behind content via a root [Surface] so the app (and every
 * `@Preview`) has an opaque backdrop rather than a transparent window.
 */
@Composable
fun MatnTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    reduceMotion: Boolean = false,
    layoutDirection: LayoutDirection = LocalLayoutDirection.current,
    content: @Composable () -> Unit,
) {
    val systemIsDark = isSystemInDarkTheme()
    val appearance = themeMode.effectiveAppearance(systemIsDark)
    val isDark = appearance == Appearance.DARK
    val colorScheme: ColorScheme = if (isDark) MatnDarkColors else MatnLightColors
    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalReduceMotion provides reduceMotion,
        LocalMatnSemantics provides if (isDark) MatnSemanticsDark else MatnSemanticsLight,
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
