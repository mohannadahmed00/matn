package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.repository.ContentPackRepository

/**
 * T035 (FR-005/FR-006) — best-effort abort of an in-flight install. Pure delegation to
 * [ContentPackRepository.cancel] (content-delivery-contract.md §4).
 */
class CancelInstallUseCase(
    private val repository: ContentPackRepository,
) : UseCase<String, Unit> {
    override suspend fun invoke(params: String): Resource<Unit> {
        repository.cancel(params)
        return Resource.Success(Unit)
    }
}