package com.giraffe.matn.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * T039: test-only helper implementing the WCAG 2.1 contrast-ratio formulas verbatim from
 * `contracts/accessibility-contract.md` §1.1. Pure arithmetic over `androidx.compose.ui.graphics.Color`
 * — no Compose runtime, no device (research D6).
 *
 * Compose `Color.red/green/blue` are already sRGB channel values in `0f..1f`, which is the unit
 * the WCAG formulas expect before the linearisation step.
 */
object ContrastRatio {

    /** WCAG 2.1 §1.4.3 channel linearisation. */
    private fun channel(c: Float): Double {
        val cv = c.toDouble()
        return if (cv <= 0.03928) cv / 12.92 else ((cv + 0.055) / 1.055).pow(2.4)
    }

    /** Relative luminance of an sRGB colour. */
    fun luminance(color: Color): Double {
        val r = channel(color.red)
        val g = channel(color.green)
        val b = channel(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** The contrast ratio `ratio(a, b) = (max(La, Lb) + 0.05) / (min(La, Lb) + 0.05)`. */
    fun contrastRatio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}