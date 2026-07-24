package com.giraffe.matn.presentation.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * App-wide theme wrapper. Forces **right-to-left** layout direction at the root so every screen
 * renders RTL regardless of device locale, and applies the canonical Stitch token set
 * (`docs/DESIGN-SOURCE.md`) — Phase 10 ships light only (dark mode is Phase 9):
 * - Color: [MatnLightColors], via `MaterialTheme.colorScheme`.
 * - Typography: [matnTypography], via `MaterialTheme.typography`.
 * - Shape: [MatnShapes] (`lg`/`xl`/`full`) — also mapped onto `MaterialTheme.shapes.medium/large`
 *   so M3 components that default to the ambient shape scale pick up the right radii for free.
 * - Spacing: [MatnSpacing] — a plain object, not composition-local; import it directly where
 *   needed (see `research.md` Decision 4 for why this stays lightweight rather than plumbed
 *   through a `CompositionLocal`).
 *
 * Paints the Material `background` behind content via a root [Surface] so the app (and every
 * `@Preview`) has an opaque backdrop rather than a transparent window.
 */
@Composable
fun MatnTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = MatnLightColors,
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
