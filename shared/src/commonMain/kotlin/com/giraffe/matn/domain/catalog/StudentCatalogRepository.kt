package com.giraffe.matn.domain.catalog

import com.giraffe.matn.core.Resource
import kotlinx.coroutines.flow.Flow

/**
 * The student's view of what the teacher has published (data-model §3.1).
 *
 * Named `StudentCatalogRepository`, **not** `CatalogRepository`: that name is already taken in this
 * same package by the teacher-side authoring interface from Phase 11. The two are genuinely
 * different — the teacher's writes drafts and publishes; this one only ever reads, anonymously
 * (FR-027).
 */
interface StudentCatalogRepository {

    /**
     * The browsable library — every synced matn, downloaded or not (FR-005). Emits from local
     * storage, so it renders in full with no connectivity after the first successful sync.
     */
    fun observeCatalog(): Flow<List<CatalogOverview>>

    /** Drives FR-044's three-way empty/offline/nothing-downloaded distinction. */
    fun observeSyncState(): Flow<CatalogSyncState>

    /**
     * Reconciles the local catalog against the source.
     *
     * @param force `false` honours the 1-hour staleness window and returns early without a request
     *   when the cached catalog is fresh (FR-006); `true` is the student's explicit refresh and
     *   always syncs.
     *
     * A failure leaves the last successfully synced catalog **completely intact** and reports the
     * failure as retryable — a partial sync never replaces a good catalog with a truncated one
     * (FR-007).
     */
    suspend fun sync(force: Boolean): Resource<Unit>

    /** One overview, offline-safe. Backs the details screen for an undownloaded matn (FR-002). */
    suspend fun overview(matnId: String): CatalogOverview?
}
