package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.domain.repository.AppearancePreferencesRepository
import kotlinx.coroutines.flow.Flow

/** Streams the persisted theme-mode preference (FR-006). First emission is the synchronous value. */
@org.koin.core.annotation.Factory
class ObserveThemeModeUseCase(
    private val repo: AppearancePreferencesRepository,
) : FlowUseCase<Unit, ThemeMode> {
    override fun invoke(params: Unit): Flow<ThemeMode> = repo.observeThemeMode()
}