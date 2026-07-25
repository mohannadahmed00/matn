package com.giraffe.matn.domain.delivery

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.DeliveryProgress
import com.giraffe.matn.domain.model.RemovalOutcome
import kotlinx.coroutines.flow.Flow

/**
 * The seam between the shared delivery logic and the platform's on-demand content delivery
 * (content-delivery-contract.md §1, research D6). A pure-Kotlin interface in `commonMain`; its
 * concrete implementations (`PlayAssetDeliveryEngine` on Android, `OnDemandResourcesEngine` on iOS)
 * hold **no business logic** — they translate domain primitives to the platform API and platform
 * state callbacks to domain types. All delivery *decisions* (free-space checks, connectivity
 * policy, ordering, formatting) live in the use cases (Principle IV).
 *
 * Injected via `initMatnKoin(driverFactory, audioEngine, wakeLock, deliveryEngine, deviceStorage)`
 * alongside the existing `AudioEngine`/`WakeLock` seams. A `FakeContentDeliveryEngine` implements
 * this same interface for `commonTest` (Principle V).
 */
interface ContentDeliveryEngine {

    /** Live size from the platform, or null when unavailable (offline, or iOS — research D4). */
    suspend fun querySize(packId: String): Long?

    /** Starts or rejoins a transfer. Idempotent per packId (duplicate taps are a no-op). */
    suspend fun install(packId: String): Resource<Unit>

    /** Progress for one pack. Emits on every platform state change; completes never. */
    fun observe(packId: String): Flow<DeliveryProgress>

    /** Best-effort abort; partial bytes are released by the platform. */
    suspend fun cancel(packId: String)

    /** Android: deletes and reports reclaimed bytes. iOS: releases the tag (research D2). */
    suspend fun remove(packId: String): Resource<RemovalOutcome>

    /** Filesystem root of an installed pack, or null when not present. */
    suspend fun locate(packId: String): String?

    /** True when the pack's content is present and complete right now. */
    suspend fun isInstalled(packId: String): Boolean
}