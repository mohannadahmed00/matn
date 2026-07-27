package com.giraffe.matn.domain.catalog

import com.giraffe.matn.core.Resource
import kotlinx.coroutines.flow.Flow

/** `data-model.md` §9. */
interface CatalogRepository {
    fun observeAuthored(): Flow<List<CatalogEntry>>
    suspend fun load(matnId: String): Resource<MatnDraft>
    suspend fun save(draft: MatnDraft): Resource<MatnDraft>
    suspend fun publish(draft: MatnDraft): Resource<MatnDraft>
    suspend fun unpublish(matnId: String): Resource<MatnDraft>
    suspend fun uploadCover(matnId: String, bytes: ByteArray, ext: String): Resource<String>
}
