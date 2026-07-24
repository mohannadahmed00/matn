package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.repository.ProgressRepository

class ToggleVerseMemorizedUseCase(private val repo: ProgressRepository) : UseCase<ToggleVerseMemorizedUseCase.Params, Unit> {
    data class Params(val verseId: String, val memorized: Boolean)

    override suspend fun invoke(params: Params): Resource<Unit> =
        repo.setVerseMemorized(params.verseId, params.memorized)
}
