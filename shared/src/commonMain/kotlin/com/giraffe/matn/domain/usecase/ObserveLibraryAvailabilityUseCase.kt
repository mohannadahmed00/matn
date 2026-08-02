package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.repository.DownloadedContentRepository
import kotlinx.coroutines.flow.Flow

/**
 * T034 (FR-002) — streams every matn's availability in one emission for the Home/Library grid.
 * Pure delegation to [DownloadedContentRepository.observeLibraryAvailability] (content-delivery-contract.md §4).
 */
@org.koin.core.annotation.Factory
class ObserveLibraryAvailabilityUseCase(
    private val repository: DownloadedContentRepository,
) : FlowUseCase<Unit, Map<String, ContentAvailability>> {
    override fun invoke(params: Unit): Flow<Map<String, ContentAvailability>> =
        repository.observeLibraryAvailability()
}