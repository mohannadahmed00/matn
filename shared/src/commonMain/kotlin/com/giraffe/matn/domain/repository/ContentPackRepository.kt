package com.giraffe.matn.domain.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.domain.model.StorageUsage
import kotlinx.coroutines.flow.Flow

/**
 * The catalog/availability boundary above the [com.giraffe.matn.domain.delivery.ContentDeliveryEngine]
 * (content-delivery-contract.md §3). `ContentPackRepositoryImpl` owns the `matnId ↔ packId`
 * translation so no layer above ever sees a pack id. Availability is computed per read from the
 * engine plus the `content_pack` row and is **never persisted** (research D5).
 */
interface ContentPackRepository {

    /** Per-matn availability. Emits the current truth on every platform state change; never caches. */
    fun observeAvailability(matnId: String): Flow<ContentAvailability>

    /** Every matn's availability in one emission; drives the Home/Library grid. */
    fun observeLibraryAvailability(): Flow<Map<String, ContentAvailability>>

    /** Aggregate storage usage for the Settings tab; entries ordered by `bytes` DESC. */
    fun observeStorageUsage(): Flow<StorageUsage>

    /** The catalog's authored declared size for [matnId] (data-model §1.1). */
    suspend fun declaredSize(matnId: String): Long

    /** Live platform size when available, else [declaredSize] (research D4). */
    suspend fun bestKnownSize(matnId: String): Long

    /**
     * True iff [matnId] is the catalog's starter (the bundled, non-removable matn). Used by the
     * audio resolver to route the starter's `fileRef` to Compose resources.
     */
    suspend fun isStarterMatn(matnId: String): Boolean

    /**
     * The delivered pack's filesystem root for [matnId], or null when not installed. Used by the
     * audio resolver to build `file://` URIs for on-demand content. Does NOT expose the pack id.
     */
    suspend fun packRootFor(matnId: String): String?

    /**
     * Starts or rejoins a transfer for [matnId]. Idempotent per packId: a duplicate request for a
     * pack already `Installing`/`Installed` is a no-op. Serialised per `packId` so a remove racing
     * an in-flight install of the same matn is determinist (removal wins).
     */
    suspend fun install(matnId: String): Resource<Unit>

    /** Best-effort cancel; the install resolves to `NotInstalled(Cancelled)`. */
    suspend fun cancel(matnId: String)

    /**
     * Removes [matnId]'s content; returns the platform's [RemovalOutcome] unchanged so the caller
     * can render honest copy (Android `Reclaimed` vs iOS `ReleasedPendingSystemReclaim`).
     */
    suspend fun remove(matnId: String): Resource<RemovalOutcome>

    /**
     * Removes every installed on-demand matn's content, **sparing the starter** (FR-029). Returns
     * the per-matn outcomes in order.
     */
    suspend fun removeAll(): Resource<List<RemovalOutcome>>
}