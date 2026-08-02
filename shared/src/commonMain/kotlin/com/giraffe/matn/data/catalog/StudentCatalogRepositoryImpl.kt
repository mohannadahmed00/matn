package com.giraffe.matn.data.catalog

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.postgrest.MATN_OVERVIEW_COLUMNS
import com.giraffe.matn.data.remote.postgrest.PostgrestClient
import com.giraffe.matn.data.remote.postgrest.PostgrestFilter
import com.giraffe.matn.data.remote.postgrest.toCatalogOverview
import com.giraffe.matn.data.remote.postgrest.toMatnRow
import com.giraffe.matn.db.Catalog_overview
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.catalog.AudioCompleteness
import com.giraffe.matn.domain.catalog.CatalogOverview
import com.giraffe.matn.domain.catalog.CatalogSyncState
import com.giraffe.matn.domain.catalog.StudentCatalogRepository
import com.giraffe.matn.domain.model.StructureKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * PostgREST + SQLDelight implementation of [StudentCatalogRepository]
 * (student-read-contract.md §2).
 *
 * Two properties matter more than anything else here:
 *
 * 1. **A failed sync changes nothing.** The whole remote set is fetched and decoded into memory
 *    first; only a complete success reaches the database, and it does so in one transaction. A
 *    partial or failed response cannot truncate a good catalog (FR-007) — which is why the
 *    reconciliation decision ([CatalogReconciler]) is a separate pure function from applying it.
 *
 * 2. **Reads are anonymous.** No bearer token, `apikey` only (FR-027). `published=eq.true` is on
 *    the query as defence in depth; the actual gate is Phase 11's `matns_read` RLS policy, so an
 *    unpublished matn is unreachable from a student build even with a modified client (FR-004,
 *    research D1).
 */
class StudentCatalogRepositoryImpl(
    private val db: ContentDatabase,
    private val postgrest: PostgrestClient,
    private val nowMillis: () -> Long,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : StudentCatalogRepository {

    override fun observeCatalog(): Flow<List<CatalogOverview>> =
        db.contentQueries.selectAllCatalogOverviews()
            .asFlow()
            .mapToList(io)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeSyncState(): Flow<CatalogSyncState> =
        db.contentQueries.selectSyncState()
            .asFlow()
            .mapToList(io)
            .map { rows ->
                val row = rows.firstOrNull()
                CatalogSyncState(
                    lastSuccessAtMillis = row?.last_success_at_millis,
                    lastAttemptFailed = (row?.last_attempt_failed ?: 0L) != 0L,
                )
            }

    override suspend fun overview(matnId: String): CatalogOverview? = withContext(io) {
        db.contentQueries.selectCatalogOverviewById(matnId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun sync(force: Boolean): Resource<Unit> = withContext(io) {
        if (!force && !isStale()) return@withContext Resource.Success(Unit)

        val response = postgrest.select(
            table = "matns",
            columns = MATN_OVERVIEW_COLUMNS,
            filters = listOf(PostgrestFilter("published", "eq.true")),
            order = "updated_at.desc",
        )

        when (response) {
            is Resource.Failure -> {
                // FR-007: the stored catalog is left completely untouched. Only the sync-state row
                // moves, so the UI can show a retryable notice over a still-usable library.
                markAttemptFailed()
                Resource.Failure(response.error)
            }
            is Resource.Success -> {
                // Decode the WHOLE response before touching the database — a decode failure here
                // must leave the stored catalog alone, exactly like a transport failure (FR-007).
                val remote = response.data.map { it.toMatnRow().toCatalogOverview() }
                applyPlan(remote)
                Resource.Success(Unit)
            }
        }
    }

    // ------------------------------------------------------------------ internals

    /** FR-006: auto-sync only fires when the cached catalog is older than the staleness window. */
    private fun isStale(): Boolean {
        val row = db.contentQueries.selectSyncState().executeAsOneOrNull()
        val last = row?.last_success_at_millis ?: return true
        return nowMillis() - last >= STALENESS_WINDOW_MILLIS
    }

    private fun markAttemptFailed() {
        val row = db.contentQueries.selectSyncState().executeAsOneOrNull()
        db.contentQueries.upsertSyncState(row?.last_success_at_millis, 1L)
    }

    private fun applyPlan(remote: List<CatalogOverview>) {
        db.transaction {
            val local = db.contentQueries.selectAllCatalogOverviews().executeAsList().map { it.toDomain() }
            val downloaded = db.contentQueries.selectAllDownloadedMatns().executeAsList()
                .map { it.matn_id }.toSet()

            val plan = CatalogReconciler.reconcile(remote, local, downloaded)

            plan.upsert.forEach { overview ->
                db.contentQueries.upsertCatalogOverview(
                    matn_id = overview.matnId,
                    title = overview.title,
                    author = overview.author,
                    description = overview.description,
                    cover_image_ref = overview.coverImageRef,
                    structure_kind = overview.structureKind.name,
                    verse_count = overview.verseCount.toLong(),
                    download_size_bytes = overview.downloadSizeBytes,
                    audio_completeness = overview.audioCompleteness.name,
                    revision = overview.revision,
                    withdrawn = 0L,
                )
            }
            plan.delete.forEach { db.contentQueries.deleteCatalogOverviewById(it) }
            plan.markWithdrawn.forEach { db.contentQueries.markCatalogOverviewWithdrawn(it) }

            db.contentQueries.upsertSyncState(nowMillis(), 0L)
        }
    }

    private fun Catalog_overview.toDomain(): CatalogOverview = CatalogOverview(
        matnId = matn_id,
        title = title,
        author = author,
        description = description,
        coverImageRef = cover_image_ref,
        structureKind = StructureKind.fromStorageOrNull(structure_kind) ?: StructureKind.SIMPLE,
        verseCount = verse_count.toInt(),
        downloadSizeBytes = download_size_bytes,
        audioCompleteness = runCatching { AudioCompleteness.valueOf(audio_completeness) }
            .getOrDefault(AudioCompleteness.NONE),
        revision = revision,
        withdrawn = withdrawn != 0L,
    )

    companion object {
        /** FR-006 / Clarification 5. */
        const val STALENESS_WINDOW_MILLIS: Long = 60 * 60 * 1000L
    }
}
