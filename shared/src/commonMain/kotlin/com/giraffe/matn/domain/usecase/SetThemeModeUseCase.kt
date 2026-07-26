package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.domain.repository.AppearancePreferencesRepository

/** Persists the theme-mode preference immediately and mirrors the resolved appearance (FR-006). */
@org.koin.core.annotation.Factory
class SetThemeModeUseCase(
    private val repo: AppearancePreferencesRepository,
) : UseCase<ThemeMode, Unit> {
    override suspend fun invoke(params: ThemeMode): Resource<Unit> = repo.setThemeMode(params)
}