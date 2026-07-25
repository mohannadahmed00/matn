package com.giraffe.matn.theme

import androidx.compose.material3.ColorScheme
import com.giraffe.matn.presentation.theme.MatnDarkColors
import com.giraffe.matn.presentation.theme.MatnLightColors
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * T040: the contrast gate — every one of the 24 pairs in `contracts/accessibility-contract.md`
 * §1.2 asserted against **both** [MatnLightColors] and [MatnDarkColors] (48 assertions). Uses
 * [ContrastRatio] for the WCAG 2.1 arithmetic.
 *
 * A failure message names the pair, the scheme, and the measured ratio. No
 * `outlineVariant`-on-`surface` assertion is present — see contract §1.2a for why that decorative
 * role is deliberately not gated.
 */
class ColorContrastTest {

    private data class Pair(
        val label: String,
        val minRatio: Double,
        val fg: (ColorScheme) -> androidx.compose.ui.graphics.Color,
        val bg: (ColorScheme) -> androidx.compose.ui.graphics.Color,
    )

    private val pairs: List<Pair> = listOf(
        Pair("onSurface/surface", 4.5, { it.onSurface }, { it.surface }),
        Pair("onSurface/surfaceContainerLowest", 4.5, { it.onSurface }, { it.surfaceContainerLowest }),
        Pair("onSurface/surfaceContainerLow", 4.5, { it.onSurface }, { it.surfaceContainerLow }),
        Pair("onSurface/surfaceContainer", 4.5, { it.onSurface }, { it.surfaceContainer }),
        Pair("onSurface/surfaceContainerHigh", 4.5, { it.onSurface }, { it.surfaceContainerHigh }),
        Pair("onSurface/surfaceContainerHighest", 4.5, { it.onSurface }, { it.surfaceContainerHighest }),
        Pair("onSurfaceVariant/surface", 4.5, { it.onSurfaceVariant }, { it.surface }),
        Pair("onSurfaceVariant/surfaceContainerLow", 4.5, { it.onSurfaceVariant }, { it.surfaceContainerLow }),
        Pair("onBackground/background", 4.5, { it.onBackground }, { it.background }),
        Pair("onPrimary/primary", 4.5, { it.onPrimary }, { it.primary }),
        Pair("onPrimaryContainer/primaryContainer", 4.5, { it.onPrimaryContainer }, { it.primaryContainer }),
        Pair("onSecondary/secondary", 4.5, { it.onSecondary }, { it.secondary }),
        Pair("onSecondaryContainer/secondaryContainer", 4.5, { it.onSecondaryContainer }, { it.secondaryContainer }),
        Pair("onTertiary/tertiary", 4.5, { it.onTertiary }, { it.tertiary }),
        Pair("onTertiaryContainer/tertiaryContainer", 4.5, { it.onTertiaryContainer }, { it.tertiaryContainer }),
        Pair("onError/error", 4.5, { it.onError }, { it.error }),
        Pair("onErrorContainer/errorContainer", 4.5, { it.onErrorContainer }, { it.errorContainer }),
        Pair("inverseOnSurface/inverseSurface", 4.5, { it.inverseOnSurface }, { it.inverseSurface }),
        Pair("primary/surface", 4.5, { it.primary }, { it.surface }),
        Pair("error/surface", 4.5, { it.error }, { it.surface }),
        Pair("outline/surface", 3.0, { it.outline }, { it.surface }),
        Pair("primary/surfaceContainerLow", 3.0, { it.primary }, { it.surfaceContainerLow }),
        Pair("primary/outlineVariant", 3.0, { it.primary }, { it.outlineVariant }),
        Pair("outline/surfaceContainerHigh", 3.0, { it.outline }, { it.surfaceContainerHigh }),
    )

    @Test
    fun `light scheme meets the contrast table`() {
        assertScheme(MatnLightColors, "light")
    }

    @Test
    fun `dark scheme meets the contrast table`() {
        assertScheme(MatnDarkColors, "dark")
    }

    private fun assertScheme(scheme: ColorScheme, schemeName: String) {
        for (p in pairs) {
            val measured = ContrastRatio.contrastRatio(p.fg(scheme), p.bg(scheme))
            assertTrue(
                measured >= p.minRatio,
                "${p.label} on $schemeName measured $measured : 1, below the required ${p.minRatio} : 1",
            )
        }
    }
}