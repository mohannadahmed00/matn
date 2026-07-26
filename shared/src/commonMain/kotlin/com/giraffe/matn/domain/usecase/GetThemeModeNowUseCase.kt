package com.giraffe.matn.domain.usecase

import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.domain.repository.AppearancePreferencesRepository

/**
 * Synchronous read of the stored theme mode, for the first composed frame (FR-005, research D12).
 * Exists so the presentation layer never reaches past a use case to a repository (Principle I —
 * contract theming §2). Not `suspend` and returns no [com.giraffe.matn.core.Resource]: the
 * underlying SQLDelight query is synchronous and falls back to [ThemeMode.SYSTEM] rather than
 * failing.
 */
@org.koin.core.annotation.Factory
class GetThemeModeNowUseCase(
    private val repo: AppearancePreferencesRepository,
) {
    operator fun invoke(): ThemeMode = repo.themeModeNow()
}