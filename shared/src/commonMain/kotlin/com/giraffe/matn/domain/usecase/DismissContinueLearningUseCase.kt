package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.repository.SessionStateRepository

/** Dismisses the Continue Learning entry by clearing the last-listened pointer and nothing else
 *  (FR-017a). Pure delegation — no logic, no session-row deletion. */
class DismissContinueLearningUseCase(
    private val repository: SessionStateRepository,
) : UseCase<Unit, Unit> {
    override suspend fun invoke(params: Unit): Resource<Unit> =
        repository.clearLastListenedMatnId()
}