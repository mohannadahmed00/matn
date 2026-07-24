package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow

class ObserveVerseMemorizationUseCase(private val repo: ProgressRepository) : FlowUseCase<String, Set<String>> {
    override fun invoke(params: String): Flow<Set<String>> = repo.observeMemorizedVerseIds(params)
}
