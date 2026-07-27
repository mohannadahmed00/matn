package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.MatnDraft

/** FR-038. */
class UnpublishMatnUseCase(private val repository: CatalogRepository) : UseCase<String, MatnDraft> {
    override suspend fun invoke(params: String): Resource<MatnDraft> = repository.unpublish(params)
}
