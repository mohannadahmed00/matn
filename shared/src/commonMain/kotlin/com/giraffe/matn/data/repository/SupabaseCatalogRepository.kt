package com.giraffe.matn.data.repository

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.postgrest.MATN_FULL_COLUMNS
import com.giraffe.matn.data.remote.postgrest.MATN_OVERVIEW_COLUMNS
import com.giraffe.matn.data.remote.postgrest.PostgrestClient
import com.giraffe.matn.data.remote.postgrest.PostgrestFilter
import com.giraffe.matn.data.remote.postgrest.toCatalogEntry
import com.giraffe.matn.data.remote.postgrest.toMatnDraft
import com.giraffe.matn.data.remote.postgrest.toMatnRow
import com.giraffe.matn.data.remote.postgrest.toRowJson
import com.giraffe.matn.data.remote.storage.StorageRestClient
import com.giraffe.matn.domain.catalog.CatalogEntry
import com.giraffe.matn.domain.catalog.CatalogLoadException
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.error.RemoteError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject

/** Implements [CatalogRepository] over [PostgrestClient] and [StorageRestClient]. */
class SupabaseCatalogRepository(
    private val postgrest: PostgrestClient,
    private val storageClient: StorageRestClient,
) : CatalogRepository {

    override fun observeAuthored(): Flow<List<CatalogEntry>> = flow {
        when (val result = postgrest.select(TABLE, MATN_OVERVIEW_COLUMNS, order = "updated_at.desc")) {
            is Resource.Success -> emit(result.data.map { it.toMatnRow().toCatalogEntry() })
            is Resource.Failure -> throw CatalogLoadException(result.error)
        }
    }

    override suspend fun load(matnId: String): Resource<MatnDraft> =
        when (val result = postgrest.select(TABLE, MATN_FULL_COLUMNS, idFilter(matnId))) {
            is Resource.Success -> result.data.firstOrNull()
                ?.let { Resource.Success(it.toMatnRow().toMatnDraft()) }
                ?: Resource.Failure(AppError.NotFound)
            is Resource.Failure -> result
        }

    /** Sends [MatnDraft.remoteRevision] as the write precondition and returns the draft with the
     * **new** revision from the response — omitting that makes every subsequent save report a
     * false conflict (the highest-risk mistake in this phase, per `tasks.md` Notes). */
    override suspend fun save(draft: MatnDraft): Resource<MatnDraft> = writeRow(draft)

    /** The same full-row write as [save] with `published` flipped in [draft] by the caller
     * ([com.giraffe.matn.domain.usecase.PublishMatnUseCase]) — publish/unpublish are not separate
     * endpoints, so they inherit the same atomicity and conflict check (`contracts/rest-contract.md` §5.1). */
    override suspend fun publish(draft: MatnDraft): Resource<MatnDraft> = writeRow(draft)

    /** Always permitted; identifiers are preserved so a republish orphans nothing (FR-038). */
    override suspend fun unpublish(matnId: String): Resource<MatnDraft> =
        when (val loaded = load(matnId)) {
            is Resource.Success -> writeRow(loaded.data.copy(publicationState = PublicationState.DRAFT))
            is Resource.Failure -> loaded
        }

    /** Uploads to Storage **before** the row is written (research D3) — an orphaned image from a
     * failed follow-up write is inert and overwritten by the next attempt. */
    override suspend fun uploadCover(matnId: String, bytes: ByteArray, ext: String): Resource<String> {
        val contentType = when (ext.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
        return storageClient.upload("matns/$matnId/cover.$ext", bytes, contentType)
    }

    private suspend fun writeRow(draft: MatnDraft): Resource<MatnDraft> {
        val row = draft.toRowJson()
        val previousRevision = draft.remoteRevision
            ?: return finish(draft, postgrest.insert(TABLE, row))

        val updated = postgrest.update(
            table = TABLE,
            row = row,
            filters = idFilter(draft.id) + PostgrestFilter("revision", "eq.$previousRevision"),
        )
        // A matched-nothing update is the ambiguous case: PostgREST answers "0 rows" both when the
        // revision precondition failed and when row-level security hid the row from this caller.
        if (updated is Resource.Success && updated.data.isEmpty()) {
            return Resource.Failure(diagnoseFailedUpdate(draft.id))
        }
        return finish(draft, updated)
    }

    /** Re-reads the row the update did not touch. Seeing it at all means the caller may read it and
     * the revision simply moved on — a genuine [RemoteError.Conflict]. Not seeing it means the
     * `select` policy hides it too, which is the same refusal a Firestore rules denial produced. */
    private suspend fun diagnoseFailedUpdate(matnId: String): AppError =
        when (val probe = postgrest.select(TABLE, listOf("id", "revision"), idFilter(matnId))) {
            is Resource.Success -> if (probe.data.isEmpty()) RemoteError.Forbidden else RemoteError.Conflict
            is Resource.Failure -> probe.error
        }

    private fun finish(draft: MatnDraft, result: Resource<List<JsonObject>>): Resource<MatnDraft> =
        when (result) {
            is Resource.Success -> result.data.firstOrNull()
                ?.let { Resource.Success(draft.copy(remoteRevision = it.toMatnRow().revision?.toString())) }
                ?: Resource.Failure(RemoteError.Decode)
            is Resource.Failure -> result
        }

    private fun idFilter(matnId: String) = listOf(PostgrestFilter("id", "eq.$matnId"))

    private companion object {
        const val TABLE = "matns"
    }
}
