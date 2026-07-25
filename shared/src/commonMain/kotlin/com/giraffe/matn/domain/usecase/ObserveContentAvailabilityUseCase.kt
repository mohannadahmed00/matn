package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.repository.ContentPackRepository
import kotlinx.coroutines.flow.Flow

/**
 * T033 (FR-002) — streams per-matn content availability. Pure delegation to
 * [ContentPackRepository.observeAvailability] — no logic (content-delivery-contract.md §4).
 * Mirrors the shape of [ObserveContinueLearningUseCase].
 */
class ObserveContentAvailabilityUseCase(
    private val repository: ContentPackRepository,
) : FlowUseCase<String, ContentAvailability> {
    override fun invoke(params: String): Flow<ContentAvailability> =
        repository.observeAvailability(params)
}