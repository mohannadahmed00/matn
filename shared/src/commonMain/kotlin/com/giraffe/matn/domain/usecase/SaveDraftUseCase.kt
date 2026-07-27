package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.MatnDraft

class SaveDraftUseCase(private val repository: CatalogRepository) : UseCase<MatnDraft, MatnDraft> {
    override suspend fun invoke(params: MatnDraft): Resource<MatnDraft> = repository.save(params)
}
