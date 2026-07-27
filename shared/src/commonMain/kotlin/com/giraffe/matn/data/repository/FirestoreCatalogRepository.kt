package com.giraffe.matn.data.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.firestore.FirestoreRestClient
import com.giraffe.matn.data.remote.firestore.matnDraftFromFields
import com.giraffe.matn.data.remote.firestore.toFirestoreFields
import com.giraffe.matn.data.remote.storage.StorageRestClient
import com.giraffe.matn.domain.catalog.CatalogEntry
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.MatnDraft
import kotlinx.coroutines.flow.Flow

/** Implements [CatalogRepository] over [FirestoreRestClient] and [StorageRestClient]. */
class FirestoreCatalogRepository(
    private val firestoreClient: FirestoreRestClient,
    private val storageClient: StorageRestClient,
) : CatalogRepository {

    override fun observeAuthored(): Flow<List<CatalogEntry>> = TODO("T083 (US5)")

    override suspend fun load(matnId: String): Resource<MatnDraft> =
        when (val result = firestoreClient.getDocument("matns/$matnId")) {
            is Resource.Success -> Resource.Success(matnDraftFromFields(matnId, result.data.fields, result.data.updateTime))
            is Resource.Failure -> result
        }

    /** Sends [MatnDraft.remoteUpdateTime] as the write precondition and returns the draft with the
     * **new** `updateTime` from the response — omitting that makes every subsequent save report a
     * false conflict (the highest-risk mistake in this phase, per `tasks.md` Notes). */
    override suspend fun save(draft: MatnDraft): Resource<MatnDraft> =
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

    override suspend fun publish(draft: MatnDraft): Resource<MatnDraft> = TODO("T072 (US4)")

    override suspend fun unpublish(matnId: String): Resource<MatnDraft> = TODO("T083 (US5)")

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
}
