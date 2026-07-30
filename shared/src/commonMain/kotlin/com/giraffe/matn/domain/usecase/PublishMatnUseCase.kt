package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.ContentIntegrityValidator
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.error.ContentIntegrityError

/** Validates first; refuses on any *blocking* problem (FR-029) — an audio-less matn with only
 * `deferred` problems still publishes (FR-027). */
class PublishMatnUseCase(private val repository: CatalogRepository) : UseCase<MatnDraft, MatnDraft> {
    override suspend fun invoke(params: MatnDraft): Resource<MatnDraft> {
        val report = ContentIntegrityValidator.validate(params)
        if (!report.canPublish) {
            return Resource.Failure(ContentIntegrityError.Aggregate(report.blocking))
        }
        return repository.publish(params.copy(publicationState = PublicationState.PUBLISHED))
    }
}
