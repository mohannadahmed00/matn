package com.giraffe.matn.domain.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.domain.model.StorageUsage
import kotlinx.coroutines.flow.Flow

/**
 * The availability boundary above [com.giraffe.matn.domain.delivery.ContentDeliveryEngine]
 * (delivery-contract.md §3), and the owner of the download queue.
 *
 * Renamed from `ContentPackRepository` in Phase 13. The role survives untouched; the name did not,
 * because this phase deletes the `content_pack` table and the `pack_id` concept outright — the
 * storage prefix `matns/{matnId}/` is derivable from the matn id, so there is no `matnId ↔ packId`
 * translation left to own (research D6).
 *
 * Availability is computed per read from what is on disk and is **never persisted** (FR-022).
 *
 * Concurrency rules this implementation must hold (delivery-contract.md §3):
 * - duplicate `download` while queued or downloading — no-op
 * - duplicate `remove` — no-op
 * - `remove` racing an in-flight `download` of the same matn — **removal wins**
 * - `remove` on a queued matn — dropped from the queue, nothing transferred (FR-032)
 */
interface DownloadedContentRepository {

    /** Per-matn availability. Re-derives on every state change; never caches. */
    fun observeAvailability(matnId: String): Flow<ContentAvailability>

    /** Every catalogued matn's availability in one emission; drives the library grid. */
    fun observeLibraryAvailability(): Flow<Map<String, ContentAvailability>>

    /** Aggregate storage usage for the Settings tab; entries ordered by `bytes` DESC. */
    fun observeStorageUsage(): Flow<StorageUsage>

    /**
     * Filesystem root of a downloaded matn, or null when not present. Used by the audio resolver to
     * build `file://` URIs.
     */
    suspend fun contentRootFor(matnId: String): String?

    /**
     * Accepts a download request. Exactly one transfer runs at a time: further requests are
     * accepted, reported as [ContentAvailability.Queued] and started in request order
     * (FR-015, Clarification 4).
     */
    suspend fun download(matnId: String): Resource<Unit>

    /** Cancels an in-flight transfer, or drops a queued matn before it starts. */
    suspend fun cancel(matnId: String)

    /** Removes [matnId]'s content and reports the bytes reclaimed. */
    suspend fun remove(matnId: String): Resource<RemovalOutcome>

    /**
     * Removes every downloaded matn. **Spares nothing** (FR-031) — Phase 8's starter exemption is
     * gone with the starter itself.
     */
    suspend fun removeAll(): Resource<List<RemovalOutcome>>
}
