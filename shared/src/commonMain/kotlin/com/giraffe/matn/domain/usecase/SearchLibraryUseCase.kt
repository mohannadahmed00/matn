package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.SearchResult
import com.giraffe.matn.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow

@org.koin.core.annotation.Factory
class SearchLibraryUseCase(private val repo: SearchRepository) : FlowUseCase<String, List<SearchResult>> {
    override fun invoke(params: String): Flow<List<SearchResult>> = repo.search(params)
}
