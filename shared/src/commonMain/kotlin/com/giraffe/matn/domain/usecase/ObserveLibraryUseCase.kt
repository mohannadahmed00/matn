package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.MatnSummary
import com.giraffe.matn.domain.repository.MatnRepository
import kotlinx.coroutines.flow.Flow

/**
 * Streams the library grid projection — one [MatnSummary] per matn with its derived aggregate
 * totals (FR-002/FR-004). An empty library is a valid success (`emptyList()`), surfaced by the
 * ViewModel as the localized empty state (SC-008), not an error.
 */
class ObserveLibraryUseCase(
    private val repo: MatnRepository,
) : FlowUseCase<Unit, List<MatnSummary>> {
    override fun invoke(params: Unit): Flow<List<MatnSummary>> = repo.observeLibrarySummaries()
}