package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.domain.repository.ReadingPreferencesRepository

/** Persists the global reading font-size preference immediately (FR-016/FR-017). */
@org.koin.core.annotation.Factory
class SetFontSizeUseCase(
    private val repo: ReadingPreferencesRepository,
) : UseCase<ReadingFontSize, Unit> {
    override suspend fun invoke(params: ReadingFontSize): Resource<Unit> =
        repo.setFontSize(params)
}