package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.MatnProgress
import com.giraffe.matn.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow

class ObserveMatnProgressUseCase(private val repo: ProgressRepository) : FlowUseCase<String, MatnProgress> {
    override fun invoke(params: String): Flow<MatnProgress> = repo.observeMatnProgress(params)
}
