package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.MatnDetails
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.domain.repository.ContentPackRepository
import com.giraffe.matn.domain.repository.MatnRepository

/**
 * Loads a matn's details (header source + chapters) and decides whether to show its table of
 * contents (FR-013/FR-015):
 *  * `showTableOfContents = (structureKind == STRUCTURED && chapters.isNotEmpty())`
 *
 * A missing matn yields `Resource.Failure(AppError.NotFound)`. Any storage failure is
 * propagated. Does **not** load the verse list — that's [ObserveVersesUseCase]'s job; the
 * details header derives its totals from the observed verses in the ViewModel to avoid a second
 * query (research.md Decision 6 / data-model.md §3.1).
 *
 * Phase 8 (FR-002/FR-003/FR-027): also folds in the catalog's declared size + starter flag, so
 * the details header's [com.giraffe.matn.presentation.common.ContentAvailabilityBadge] and
 * [com.giraffe.matn.presentation.common.ContentActionButton] have what they need without a
 * second round trip. [contentPackRepo] is optional/defaulted so existing call sites and tests
 * keep compiling.
 */
class GetMatnDetailsUseCase(
    private val matnRepo: MatnRepository,
    private val contentPackRepo: ContentPackRepository? = null,
) : UseCase<String, MatnDetails> {

    override suspend fun invoke(params: String): Resource<MatnDetails> {
        when (val matnResult = matnRepo.getMatn(params)) {
            is Resource.Failure -> return matnResult
            is Resource.Success -> {
                val matn = matnResult.data
                    ?: return Resource.Failure(AppError.NotFound)
                return when (val chaptersResult = matnRepo.getChapters(params)) {
                    is Resource.Failure -> chaptersResult
                    is Resource.Success -> {
                        val chapters = chaptersResult.data
                        Resource.Success(
                            MatnDetails(
                                matn = matn,
                                chapters = chapters,
                                showTableOfContents =
                                    matn.structureKind == StructureKind.STRUCTURED &&
                                        chapters.isNotEmpty(),
                                declaredSizeBytes = contentPackRepo?.declaredSize(params) ?: 0L,
                                isStarter = contentPackRepo?.isStarterMatn(params) ?: false,
                            ),
                        )
                    }
                }
            }
        }
    }
}