package com.giraffe.matn.domain.catalog

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.audio.PendingUpload
import kotlinx.coroutines.flow.Flow

/** `data-model.md` §9. */
interface CatalogRepository {
    fun observeAuthored(): Flow<List<CatalogEntry>>
    suspend fun load(matnId: String): Resource<MatnDraft>
    suspend fun save(draft: MatnDraft): Resource<MatnDraft>
    suspend fun publish(draft: MatnDraft): Resource<MatnDraft>
    suspend fun unpublish(matnId: String): Resource<MatnDraft>
    suspend fun uploadCover(matnId: String, bytes: ByteArray, ext: String): Resource<String>

    /** Attaches or replaces one verse's recording, following the commit ordering in
     * `contracts/audio-artifact-contract.md` §4. [audio] is the [DraftAudio] the caller has already
     * built from [bytes] (id, fileRef, measured duration/size/profile); this is the single write. */
    suspend fun attachVerseAudio(draft: MatnDraft, verseId: String, audio: DraftAudio, bytes: ByteArray): Resource<MatnDraft>

    /** Clears one verse's recording. Refused by the caller (not here) when the draft is published. */
    suspend fun removeVerseAudio(draft: MatnDraft, verseId: String): Resource<MatnDraft>

    /** Commits a split's per-verse payloads in one row write, per the same ordering. [updates] maps
     * verse id to its new [DraftAudio]; [payloads] are the bytes for verses that were not skipped. */
    suspend fun applySplit(draft: MatnDraft, updates: Map<String, DraftAudio>, payloads: List<PendingUpload>): Resource<MatnDraft>
}
