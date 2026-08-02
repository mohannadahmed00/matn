package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.domain.repository.DownloadedContentRepository

/**
 * T054 (FR-029) — removes every installed on-demand matn's content. Pure delegation to
 * [DownloadedContentRepository.removeAll], which already spares the starter.
 */
@org.koin.core.annotation.Factory
class RemoveAllContentUseCase(
    private val repository: DownloadedContentRepository,
) : UseCase<Unit, List<RemovalOutcome>> {
    override suspend fun invoke(params: Unit): Resource<List<RemovalOutcome>> =
        repository.removeAll()
}
