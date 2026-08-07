package com.giraffe.matn.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.giraffe.matn.presentation.common.LibraryGlyph
import com.giraffe.matn.presentation.common.SavedGlyph
import com.giraffe.matn.presentation.common.SettingsGlyph
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.nav_library
import matn.shared.generated.resources.nav_saved
import matn.shared.generated.resources.nav_settings
import org.jetbrains.compose.resources.StringResource

/**
 * The three persistent bottom-nav destinations (Matn Design System §02 — *Navigation
 * consolidation*; `docs/PRODUCT-SPEC.md` § Navigation & App Shell).
 *
 * Down from four. **Goals** left the bar because its ring was already rendered on Library, so the
 * tab was a second copy of the control the student reaches for — it is now a sheet opened from that
 * ring. **Notes** became [SAVED], which absorbed bookmarks, notes and memorized verses: all three
 * are "verses I have touched", so they are one route filtered in place rather than three surfaces.
 */
enum class NavigationTab(val route: String, val labelRes: StringResource) {
    LIBRARY(Routes.HOME, Res.string.nav_library),
    SAVED(Routes.SAVED, Res.string.nav_saved),
    SETTINGS(Routes.SETTINGS, Res.string.nav_settings),
}

/** The hand-drawn glyph for [tab], per [com.giraffe.matn.presentation.common.PlaybackGlyphs]'s convention. */
@Composable
fun NavTabIcon(tab: NavigationTab, color: Color) {
    when (tab) {
        NavigationTab.LIBRARY -> LibraryGlyph(color = color)
        NavigationTab.SAVED -> SavedGlyph(color = color)
        NavigationTab.SETTINGS -> SettingsGlyph(color = color)
    }
}
