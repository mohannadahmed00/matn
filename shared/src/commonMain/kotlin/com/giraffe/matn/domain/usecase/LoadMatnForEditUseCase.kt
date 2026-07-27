package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.MatnDraft

class LoadMatnForEditUseCase(private val repository: CatalogRepository) : UseCase<String, MatnDraft> {
    override suspend fun invoke(params: String): Resource<MatnDraft> = repository.load(params)
}
