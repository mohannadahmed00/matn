package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.catalog.CatalogEntry
import com.giraffe.matn.domain.catalog.CatalogRepository
import kotlinx.coroutines.flow.Flow

/** FR-035. */
class ListAuthoredMatnsUseCase(private val repository: CatalogRepository) : FlowUseCase<Unit, List<CatalogEntry>> {
    override fun invoke(params: Unit): Flow<List<CatalogEntry>> = repository.observeAuthored()
}
