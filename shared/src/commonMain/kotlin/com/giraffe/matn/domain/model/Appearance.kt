package com.giraffe.matn.domain.model

/**
 * The resolved appearance after combining [ThemeMode] with the platform's system-dark signal
 * (data-model §2.2). Distinct from [ThemeMode] on purpose: `SYSTEM` is a *preference*, never an
 * appearance — conflating them is how "pinned theme still follows the device" bugs happen
 * (FR-006's failure mode).
 */
enum class Appearance {
    LIGHT,
    DARK,
}

/**
 * The single decision point that resolves a [ThemeMode] against the platform's system-dark flag
 * into a concrete [Appearance] (research D1, contract theming §3). Pure and unit-tested by
 * [com.giraffe.matn.theme.ThemeModeTest].
 */
fun ThemeMode.effectiveAppearance(systemIsDark: Boolean): Appearance = when (this) {
    ThemeMode.LIGHT -> Appearance.LIGHT
    ThemeMode.DARK -> Appearance.DARK
    ThemeMode.SYSTEM -> if (systemIsDark) Appearance.DARK else Appearance.LIGHT
}