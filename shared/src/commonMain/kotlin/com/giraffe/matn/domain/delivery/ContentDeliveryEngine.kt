package com.giraffe.matn.domain.delivery

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.DeliveryProgress
import com.giraffe.matn.domain.model.RemovalOutcome
import kotlinx.coroutines.flow.Flow

/**
 * The seam between the shared delivery logic and however content actually arrives
 * (delivery-contract.md §2). This interface is the one piece of Phase 8's delivery model that
 * survives Phase 13 unchanged in *role* — it is the worked example in Constitution Principle I's
 * own rationale: the data layer swaps its content source without touching domain or UI code.
 *
 * What changed underneath it: three platform implementations (`PlayAssetDeliveryEngine`,
 * `OnDemandResourcesEngine`, `DesktopContentDeliveryEngine`) collapsed into one
 * `RemoteContentDeliveryEngine` in `commonMain` (FR-041, research D3). Over HTTP nothing here is
 * platform-specific, and Principle IV puts shared logic in `commonMain`. Every parameter is now a
 * `matnId`: `packId` is gone with the pack concept (research D6).
 *
 * `querySize` is also gone — the catalog overview always carries the size, so there is no second
 * source to reconcile and no download ever waits on a lookup (spec Assumptions).
 *
 * The engine holds **no policy**. Free-space checks (FR-019), connectivity checks (FR-020), the
 * staleness window (FR-006) and queue ordering (FR-015) all live above it.
 *
 * A `FakeContentDeliveryEngine` implements this same interface for `commonTest` (Principle V).
 */
interface ContentDeliveryEngine {

    /**
     * Fetches the matn's verse text and per-verse recitations and commits them, following the
     * nine-step sequence in delivery-contract.md §4: stage into `downloads/.tmp-{matnId}/`, move
     * into `downloads/{matnId}/` only on full success, then insert rows in one transaction.
     *
     * Any failure deletes the staging directory and writes nothing (FR-017, SC-006).
     */
    suspend fun download(matnId: String): Resource<Unit>

    /** Progress for one matn. Emits as bytes land; completes never. */
    fun observe(matnId: String): Flow<DeliveryProgress>

    /** Aborts an in-flight transfer and deletes its staging directory. */
    suspend fun cancel(matnId: String)

    /** Deletes `downloads/{matnId}/` and reports the bytes reclaimed. */
    suspend fun remove(matnId: String): Resource<RemovalOutcome>

    /** Filesystem root of a downloaded matn, or null when not present. */
    suspend fun contentRootFor(matnId: String): String?

    /**
     * True when the matn's content is present right now — determined by asking the filesystem, not
     * by reading a stored flag (FR-022). This is what makes FR-045 (files deleted behind the app's
     * back) a free property rather than a reconciliation job.
     */
    suspend fun isDownloaded(matnId: String): Boolean
}
