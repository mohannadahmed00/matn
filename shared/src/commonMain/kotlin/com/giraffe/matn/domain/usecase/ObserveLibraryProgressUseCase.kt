package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.MatnProgress
import com.giraffe.matn.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow

@org.koin.core.annotation.Factory
class ObserveLibraryProgressUseCase(private val repo: ProgressRepository) : FlowUseCase<Unit, List<MatnProgress>> {
    override fun invoke(params: Unit): Flow<List<MatnProgress>> = repo.observeLibraryProgress()
}
