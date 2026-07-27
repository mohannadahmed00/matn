package com.giraffe.matn.data.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.firestore.FirestoreRestClient
import com.giraffe.matn.data.remote.firestore.catalogEntryFromFields
import com.giraffe.matn.data.remote.firestore.matnDraftFromFields
import com.giraffe.matn.data.remote.firestore.toFirestoreFields
import com.giraffe.matn.data.remote.storage.StorageRestClient
import com.giraffe.matn.domain.catalog.CatalogEntry
import com.giraffe.matn.domain.catalog.CatalogLoadException
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Implements [CatalogRepository] over [FirestoreRestClient] and [StorageRestClient]. */
class FirestoreCatalogRepository(
    private val firestoreClient: FirestoreRestClient,
    private val storageClient: StorageRestClient,
) : CatalogRepository {

    override fun observeAuthored(): Flow<List<CatalogEntry>> = flow {
        when (val result = firestoreClient.listDocuments("matns", OVERVIEW_MASK)) {
            is Resource.Success -> emit(result.data.documents.map { catalogEntryFromFields(it.name.substringAfterLast('/'), it.fields) })
            is Resource.Failure -> throw CatalogLoadException(result.error)
        }
    }

    override suspend fun load(matnId: String): Resource<MatnDraft> =
        when (val result = firestoreClient.getDocument("matns/$matnId")) {
            is Resource.Success -> Resource.Success(matnDraftFromFields(matnId, result.data.fields, result.data.updateTime))
            is Resource.Failure -> result
        }

    /** Sends [MatnDraft.remoteUpdateTime] as the write precondition and returns the draft with the
     * **new** `updateTime` from the response — omitting that makes every subsequent save report a
     * false conflict (the highest-risk mistake in this phase, per `tasks.md` Notes). */
    override suspend fun save(draft: MatnDraft): Resource<MatnDraft> = writeDocument(draft)

    /** The same full-document write as [save] with `published` flipped in [draft] by the caller
     * ([com.giraffe.matn.domain.usecase.PublishMatnUseCase]) — publish/unpublish are not separate
     * endpoints, so they inherit the same atomicity and conflict check (`contracts/rest-contract.md` §5.1). */
    override suspend fun publish(draft: MatnDraft): Resource<MatnDraft> = writeDocument(draft)

    private suspend fun writeDocument(draft: MatnDraft): Resource<MatnDraft> =
        when (
            val result = firestoreClient.patchDocument(
                path = "matns/${draft.id}",
                fields = draft.toFirestoreFields(),
                updateTimePrecondition = draft.remoteUpdateTime,
                requireNotExists = draft.remoteUpdateTime == null,
            )
        ) {
            is Resource.Success -> Resource.Success(draft.copy(remoteUpdateTime = result.data.updateTime))
            is Resource.Failure -> result
        }

    /** Always permitted; identifiers are preserved so a republish orphans nothing (FR-038). */
    override suspend fun unpublish(matnId: String): Resource<MatnDraft> =
        when (val loaded = load(matnId)) {
            is Resource.Success -> writeDocument(loaded.data.copy(publicationState = PublicationState.DRAFT))
            is Resource.Failure -> loaded
        }

    /** Uploads to Storage **before** the document is patched (research D3) — an orphaned image from
     * a failed follow-up document write is inert and overwritten by the next attempt. */
    override suspend fun uploadCover(matnId: String, bytes: ByteArray, ext: String): Resource<String> {
        val contentType = when (ext.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
        return storageClient.upload("matns/$matnId/cover.$ext", bytes, contentType)
    }

    private companion object {
        val OVERVIEW_MASK = listOf(
            "id", "title", "author", "description", "coverImageRef",
            "published", "audioCompleteness", "verseCount", "declaredSizeBytes", "updatedAt",
        )
    }
}
