package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.domain.repository.ReadingPreferencesRepository
import kotlinx.coroutines.flow.Flow

/** Streams the global reading font-size preference (FR-016/FR-017). */
class GetFontSizeUseCase(
    private val repo: ReadingPreferencesRepository,
) : FlowUseCase<Unit, ReadingFontSize> {
    override fun invoke(params: Unit): Flow<ReadingFontSize> = repo.observeFontSize()
}