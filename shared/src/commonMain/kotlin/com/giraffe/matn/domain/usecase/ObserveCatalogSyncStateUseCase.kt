package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.catalog.CatalogSyncState
import com.giraffe.matn.domain.catalog.StudentCatalogRepository
import kotlinx.coroutines.flow.Flow

/**
 * Streams whether the catalog has ever synced and whether the last attempt failed.
 *
 * This is what lets the library tell FR-044's three cases apart — "no connectivity", "reachable but
 * empty", and "you have not downloaded anything yet". Without it a never-synced library and a
 * teacher who has published nothing look identical on screen, which is exactly the confusion
 * FR-044 exists to prevent.
 */
@org.koin.core.annotation.Factory
class ObserveCatalogSyncStateUseCase(
    private val repository: StudentCatalogRepository,
) : FlowUseCase<Unit, CatalogSyncState> {
    override fun invoke(params: Unit): Flow<CatalogSyncState> = repository.observeSyncState()
}
