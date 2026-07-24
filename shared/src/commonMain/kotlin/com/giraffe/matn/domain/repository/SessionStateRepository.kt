package com.giraffe.matn.domain.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.ContinueLearningEntry
import com.giraffe.matn.domain.model.SavedMatnSession
import kotlinx.coroutines.flow.Flow

/** Persistence boundary for the Continue Learning feature (contracts §1). The single source of
 *  truth for the saved per-matn session and the "last listened" pointer that drives the Home card.
 *  Implementations MUST NOT throw — unreadable/undecodable rows degrade to `null` (P1, FR-029),
 *  failed writes surface as [Resource.Failure] (P1, FR-011), and dismiss clears ONLY the pointer
 *  leaving every `matn_session` row intact (P2, FR-017a). */
interface SessionStateRepository {
    /** Null when no row exists. MUST NOT throw on unreadable/undecodable rows — returns null. */
    suspend fun getSession(matnId: String): SavedMatnSession?

    /** Upsert. Failures surface as [Resource.Failure] ([com.giraffe.matn.core.AppError.Storage]), never a thrown exception. */
    suspend fun putSession(session: SavedMatnSession): Resource<Unit>

    /** Null when unset or when the referenced matn no longer exists. */
    suspend fun getLastListenedMatnId(): String?

    suspend fun setLastListenedMatnId(matnId: String): Resource<Unit>

    /** Dismiss. Clears ONLY the pointer; every `matn_session` row is left intact (FR-017a). */
    suspend fun clearLastListenedMatnId(): Resource<Unit>

    /** Cold flow for the Home card; re-emits when the pointer or its session row changes. */
    fun observeContinueLearning(): Flow<ContinueLearningEntry?>
}