package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState

/** Clears one verse's recording. Refused outright on a published matn (FR-030) — a published matn
 * may never go silent, so this is stopped before it reaches the repository, not just at save time. */
class RemoveVerseAudioUseCase(
    private val repository: CatalogRepository,
) : UseCase<RemoveVerseAudioUseCase.Params, MatnDraft> {
    data class Params(val draft: MatnDraft, val verseId: String)

    override suspend fun invoke(params: Params): Resource<MatnDraft> {
        if (params.draft.publicationState == PublicationState.PUBLISHED) {
            return Resource.Failure(AppError.Storage("cannot remove a recording from a published matn"))
        }
        return repository.removeVerseAudio(params.draft, params.verseId)
    }
}
