package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.ContinueLearningEntry
import com.giraffe.matn.domain.repository.SessionStateRepository
import kotlinx.coroutines.flow.Flow

/** Streams the Home-screen Continue Learning offer (contracts §5). Pure delegation — no logic. */
class ObserveContinueLearningUseCase(
    private val repository: SessionStateRepository,
) : FlowUseCase<Unit, ContinueLearningEntry?> {
    override fun invoke(params: Unit): Flow<ContinueLearningEntry?> =
        repository.observeContinueLearning()
}