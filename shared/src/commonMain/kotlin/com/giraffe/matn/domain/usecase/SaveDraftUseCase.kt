package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.ContentIntegrityValidator
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.error.ContentIntegrityError

/** A draft is allowed to be half-recorded at any completeness (`validation-contract.md` §3) — but
 * a **published** matn may never be saved back down to incomplete audio (FR-030): that would let
 * a live matn go silent for a verse without ever going through the publish gate. */
class SaveDraftUseCase(private val repository: CatalogRepository) : UseCase<MatnDraft, MatnDraft> {
    override suspend fun invoke(params: MatnDraft): Resource<MatnDraft> {
        if (params.publicationState == PublicationState.PUBLISHED) {
            val report = ContentIntegrityValidator.validate(params)
            val missingAudio = report.blocking.filterIsInstance<ContentIntegrityError.MissingAudio>()
            if (missingAudio.isNotEmpty()) {
                return Resource.Failure(ContentIntegrityError.Aggregate(missingAudio))
            }
        }
        return repository.save(params)
    }
}
