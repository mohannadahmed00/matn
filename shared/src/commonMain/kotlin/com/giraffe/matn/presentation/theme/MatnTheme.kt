package com.giraffe.matn.presentation.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * App-wide theme wrapper. Forces **right-to-left** layout direction at the root
 * (FR-010/SC-005) so every screen renders RTL regardless of device locale (Decision 7),
 * applies the manuscript light color scheme ([MatnLightColors]) and Amiri-led typography
 * ([matnTypography]) — Phase 1 ships light only (FR-020 defers dark mode) — and paints the
 * Material `background` behind content via a root [Surface] so the app (and every `@Preview`)
 * has an opaque parchment backdrop rather than a transparent window.
 */
@Composable
fun MatnTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = MatnLightColors,
            typography = matnTypography(),
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
