package com.giraffe.matn

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giraffe.matn.di.MatnKoinHolder
import com.giraffe.matn.domain.appearance.AppearanceMirror
import com.giraffe.matn.domain.preferences.MotionPreferences
import com.giraffe.matn.domain.usecase.GetThemeModeNowUseCase
import com.giraffe.matn.domain.usecase.ObserveThemeModeUseCase
import com.giraffe.matn.presentation.AppViewModel
import com.giraffe.matn.presentation.navigation.MatnNavHost
import com.giraffe.matn.presentation.theme.LocalWindowWidthClass
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.presentation.theme.widthClassFor

/**
 * App root composable. Hosts the navigation graph inside [MatnTheme], which forces right-to-left
 * layout and resolves the appearance from the student's [com.giraffe.matn.domain.model.ThemeMode]
 * against the platform system-dark signal (FR-006, research D12). The `@Composable fun App()`
 * signature is preserved so `MainActivity` (Android) and `MainViewController` (iOS) keep their
 * thin entry points unchanged.
 *
 * T042 — builds [AppViewModel] via `viewModel { }` from [MatnKoinHolder.koin] (the same pattern
 * `MatnNavHost.kt` uses per destination) and threads the resolved `themeMode` / `reduceMotion`
 * into [MatnTheme]. T083 will additionally wrap this in `BoxWithConstraints` to publish
 * `LocalWindowWidthClass`.
 */
@Composable
@Preview
fun App() {
    val s  = isSystemInDarkTheme()
    val koin = MatnKoinHolder.koin
    val viewModel: AppViewModel = viewModel {
        AppViewModel(
            getThemeModeNow = koin.get<GetThemeModeNowUseCase>(),
            observeThemeMode = koin.get<ObserveThemeModeUseCase>(),
            motionPreferences = koin.get<MotionPreferences>(),
            appearanceMirror = koin.get<AppearanceMirror>(),
            systemIsDark = { s },
        )
    }
    val state by viewModel.state.collectAsState()
    MatnTheme(themeMode = state.themeMode, reduceMotion = state.reduceMotion) {
        // T083 (US4, research D8): available-width window class, recomputed on every resize so
        // rotation and multi-window transitions are ordinary recompositions (FR-026).
        BoxWithConstraints {
            CompositionLocalProvider(LocalWindowWidthClass provides widthClassFor(maxWidth)) {
                MatnNavHost()
            }
        }
    }
}