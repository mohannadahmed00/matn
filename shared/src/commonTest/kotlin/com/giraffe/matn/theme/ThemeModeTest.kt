package com.giraffe.matn.theme

import com.giraffe.matn.domain.model.Appearance
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.domain.model.effectiveAppearance
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T032: the single mode→appearance decision point (data-model §2.1, contract theming §3).
 * Asserts `effectiveAppearance` for all 3 × 2 combinations and `fromStorageOrDefault` on the
 * null / empty / unrecognized / valid cases that previously produced bugs.
 */
class ThemeModeTest {

    @Test
    fun `SYSTEM resolves to system appearance in both directions`() {
        assertEquals(Appearance.LIGHT, ThemeMode.SYSTEM.effectiveAppearance(systemIsDark = false))
        assertEquals(Appearance.DARK, ThemeMode.SYSTEM.effectiveAppearance(systemIsDark = true))
    }

    @Test
    fun `LIGHT is always light regardless of system`() {
        assertEquals(Appearance.LIGHT, ThemeMode.LIGHT.effectiveAppearance(systemIsDark = false))
        assertEquals(Appearance.LIGHT, ThemeMode.LIGHT.effectiveAppearance(systemIsDark = true))
    }

    @Test
    fun `DARK is always dark regardless of system`() {
        assertEquals(Appearance.DARK, ThemeMode.DARK.effectiveAppearance(systemIsDark = false))
        assertEquals(Appearance.DARK, ThemeMode.DARK.effectiveAppearance(systemIsDark = true))
    }

    @Test
    fun `fromStorageOrDefault returns SYSTEM for null`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageOrDefault(null))
    }

    @Test
    fun `fromStorageOrDefault returns SYSTEM for empty string`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageOrDefault(""))
    }

    @Test
    fun `fromStorageOrDefault returns SYSTEM for unrecognized nonsense`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageOrDefault("NONSENSE"))
    }

    @Test
    fun `fromStorageOrDefault returns DARK when DARK is stored`() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorageOrDefault("DARK"))
    }
}