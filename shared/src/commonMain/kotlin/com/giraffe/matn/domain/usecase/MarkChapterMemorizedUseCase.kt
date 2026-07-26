package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.repository.ProgressRepository

@org.koin.core.annotation.Factory
class MarkChapterMemorizedUseCase(private val repo: ProgressRepository) : UseCase<MarkChapterMemorizedUseCase.Params, Unit> {
    data class Params(val chapterId: String, val memorized: Boolean)

    override suspend fun invoke(params: Params): Resource<Unit> =
        repo.setChapterMemorized(params.chapterId, params.memorized)
}
