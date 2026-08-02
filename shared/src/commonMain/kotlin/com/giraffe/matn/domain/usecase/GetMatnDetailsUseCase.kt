package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.MatnDetails
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.domain.catalog.StudentCatalogRepository
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
 * second round trip. [catalog] is optional/defaulted so existing call sites and tests keep
 * compiling.
 */
@org.koin.core.annotation.Factory
class GetMatnDetailsUseCase(
    private val matnRepo: MatnRepository,
    private val catalog: StudentCatalogRepository? = null,
) : UseCase<String, MatnDetails> {

    override suspend fun invoke(params: String): Resource<MatnDetails> {
        when (val matnResult = matnRepo.getMatn(params)) {
            is Resource.Failure -> return matnResult
            is Resource.Success -> {
                val matn = matnResult.data
                    // Phase 13 (US1 scenario 7): no local `matn` row means the matn is simply not
                    // downloaded, which is now the *normal* state for most of the catalog — not a
                    // missing-matn error. Fall back to the synced overview so the details screen
                    // renders cover/title/author/description plus a download action, with no verse
                    // text. Only a matn absent from the catalog too is genuinely NotFound.
                    ?: return catalog?.overview(params)?.let { overview ->
                        Resource.Success(
                            MatnDetails(
                                matn = Matn(
                                    id = overview.matnId,
                                    title = overview.title,
                                    author = overview.author,
                                    description = overview.description,
                                    coverImageRef = overview.coverImageRef,
                                    structureKind = overview.structureKind,
                                ),
                                // No chapters until it is downloaded, so no table of contents.
                                chapters = emptyList(),
                                showTableOfContents = false,
                                declaredSizeBytes = overview.downloadSizeBytes,
                            ),
                        )
                    } ?: Resource.Failure(AppError.NotFound)
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
                                // Phase 13: the size comes from the synced catalog overview, which
                                // is present whether or not the matn is downloaded (FR-002).
                                declaredSizeBytes = catalog?.overview(params)?.downloadSizeBytes ?: 0L,
                            ),
                        )
                    }
                }
            }
        }
    }
}