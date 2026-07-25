package com.giraffe.matn.presentation

import androidx.lifecycle.viewModelScope
import com.giraffe.matn.domain.appearance.AppearanceMirror
import com.giraffe.matn.domain.model.Appearance
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.domain.model.effectiveAppearance
import com.giraffe.matn.domain.preferences.MotionPreferences
import com.giraffe.matn.domain.usecase.GetThemeModeNowUseCase
import com.giraffe.matn.domain.usecase.ObserveThemeModeUseCase
import com.giraffe.matn.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * T041 — the app-root view model. Owns the two cross-screen render-layer signals the root
 * [com.giraffe.matn.presentation.theme.MatnTheme] resolves from:
 *  * the resolved [ThemeMode] (so the right color scheme is applied before the first frame);
 *  * the reduce-motion flag, from [MotionPreferences].
 *
 * **First-frame correctness (research D12)**: the *initial* state uses [GetThemeModeNowUseCase]'s
 * synchronous read so the very first composed frame already carries the student's choice, before
 * the [ObserveThemeModeUseCase] flow's first emission arrives. This is what makes FR-005 ("no
 * incorrect theme at any point during launch") actually true.
 *
 * Principle I: this view model injects **use cases** and the [AppearanceMirror] — it never reaches
 * past a use case to [com.giraffe.matn.domain.repository.AppearancePreferencesRepository]. The
 * mirror is written here because it needs the system-dark signal, which lives in the presentation
 * layer (`isSystemInDarkTheme()` on Android, the CMP `LocalSystemTheme` on iOS) and cannot be
 * resolved in the domain layer.
 */
class AppViewModel(
    getThemeModeNow: GetThemeModeNowUseCase,
    observeThemeMode: ObserveThemeModeUseCase,
    private val motionPreferences: MotionPreferences,
    private val appearanceMirror: AppearanceMirror,
    private val systemIsDark: () -> Boolean,
) : BaseViewModel<AppUiState>(AppUiState(themeMode = getThemeModeNow())) {

    init {
        // Live updates: the resolved appearance is mirrored on every emission so the next cold
        // start's launch window already matches. `combine` re-runs on either change, so a system
        // appearance toggle while the app is foregrounded also re-mirrors (FR-003, research D9).
        combine(
            observeThemeMode.invoke(Unit),
            motionPreferences.observeReduceMotion(),
        ) { mode, reduceMotion -> mode to reduceMotion }
            .onEach { (mode, reduceMotion) ->
                val resolved = mode.effectiveAppearance(systemIsDark())
                appearanceMirror.write(resolved)
                setState { it.copy(themeMode = mode, reduceMotion = reduceMotion) }
            }
            .launchIn(viewModelScope)
    }
}

/** The two render-layer signals the root [com.giraffe.matn.presentation.theme.MatnTheme] reads. */
data class AppUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reduceMotion: Boolean = false,
)