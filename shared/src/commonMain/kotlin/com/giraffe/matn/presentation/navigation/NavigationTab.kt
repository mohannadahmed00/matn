package com.giraffe.matn.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.giraffe.matn.presentation.common.GoalsGlyph
import com.giraffe.matn.presentation.common.LibraryGlyph
import com.giraffe.matn.presentation.common.NotesGlyph
import com.giraffe.matn.presentation.common.SettingsGlyph
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.nav_goals
import matn.shared.generated.resources.nav_library
import matn.shared.generated.resources.nav_notes
import matn.shared.generated.resources.nav_settings
import org.jetbrains.compose.resources.StringResource

/**
 * The four persistent bottom-nav destinations (specs/010-design-system-adoption User Story 3;
 * `docs/PRODUCT-SPEC.md` § Navigation & App Shell). [LIBRARY] and [NOTES] (Phase 6) have real
 * screens; [GOALS]/[SETTINGS] route to the shared [ComingSoonScreen] until Phases 7-8 land.
 */
enum class NavigationTab(val route: String, val labelRes: StringResource) {
    LIBRARY(Routes.HOME, Res.string.nav_library),
    GOALS(Routes.GOALS, Res.string.nav_goals),
    NOTES(Routes.NOTES, Res.string.nav_notes),
    SETTINGS(Routes.SETTINGS, Res.string.nav_settings),
}

/** The hand-drawn glyph for [tab], per [com.giraffe.matn.presentation.common.PlaybackGlyphs]'s convention. */
@Composable
fun NavTabIcon(tab: NavigationTab, color: Color) {
    when (tab) {
        NavigationTab.LIBRARY -> LibraryGlyph(color = color)
        NavigationTab.GOALS -> GoalsGlyph(color = color)
        NavigationTab.NOTES -> NotesGlyph(color = color)
        NavigationTab.SETTINGS -> SettingsGlyph(color = color)
    }
}
