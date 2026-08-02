package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.catalog.CatalogOverview
import com.giraffe.matn.domain.catalog.StudentCatalogRepository
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.MatnSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The browsable library: every synced matn, downloaded or not (FR-005).
 *
 * **Emits [MatnSummary], not a new catalog-card type.** Every field the library grid renders —
 * title, author, cover reference, verse count, download size — already exists on [CatalogOverview],
 * so mapping to the existing projection lets [com.giraffe.matn.presentation.common.MatnCard] render
 * an undownloaded matn with no rendering changes at all. Inventing a parallel card model would
 * duplicate the grid for no behavioural gain (Constitution VIII, "reuse before adding").
 *
 * `totalDurationMs` is `0` for a catalog entry and stays that way until the matn is downloaded: the
 * overview projection carries no verses (FR-003), so the duration is genuinely unknown locally. The
 * card omits the figure rather than printing a fabricated `0:00`.
 *
 * Withdrawn متون are excluded here (FR-009). Their overview is kept so a copy the student already
 * downloaded still renders its title, author and cover offline — but withdrawal means it is no
 * longer offered, so it must not appear in the browsable list.
 */
@org.koin.core.annotation.Factory
class ObserveCatalogUseCase(
    private val repository: StudentCatalogRepository,
) : FlowUseCase<Unit, List<MatnSummary>> {

    override fun invoke(params: Unit): Flow<List<MatnSummary>> =
        repository.observeCatalog().map { overviews ->
            overviews.filterNot { it.withdrawn }.map { it.toSummary() }
        }

    private fun CatalogOverview.toSummary(): MatnSummary = MatnSummary(
        matn = Matn(
            id = matnId,
            title = title,
            author = author,
            description = description,
            coverImageRef = coverImageRef,
            structureKind = structureKind,
        ),
        verseCount = verseCount,
        totalDurationMs = 0L,
        declaredSizeBytes = downloadSizeBytes,
    )
}
