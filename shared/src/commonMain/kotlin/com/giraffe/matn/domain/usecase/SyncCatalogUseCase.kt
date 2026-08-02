package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.catalog.StudentCatalogRepository

/**
 * Reconciles the local catalog against what the teacher has published (FR-006, FR-008).
 *
 * @param params `false` — the automatic sync fired when the library opens. Honours the 1-hour
 *   staleness window, so navigating in and out of the library costs nothing.
 *   `true` — the student's explicit refresh, which always syncs.
 *
 * Deliberately thin. The staleness window lives in `StudentCatalogRepositoryImpl.isStale()`, next
 * to the `catalog_sync_state` row it reads; duplicating the comparison here would give the rule two
 * homes that could drift (Principle III).
 *
 * A failure is **not** a reason to clear anything: the repository leaves the last successfully
 * synced catalog completely intact and only flags the attempt, so the UI can show a retryable
 * notice over a still-usable library (FR-007).
 */
@org.koin.core.annotation.Factory
class SyncCatalogUseCase(
    private val repository: StudentCatalogRepository,
) : UseCase<Boolean, Unit> {
    override suspend fun invoke(params: Boolean): Resource<Unit> = repository.sync(force = params)
}
