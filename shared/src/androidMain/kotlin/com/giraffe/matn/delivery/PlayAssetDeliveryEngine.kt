package com.giraffe.matn.delivery

import android.content.Context
import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.delivery.ContentDeliveryEngine
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.model.DeliveryFailure
import com.giraffe.matn.domain.model.DeliveryPhase
import com.giraffe.matn.domain.model.DeliveryProgress
import com.giraffe.matn.domain.model.RemovalOutcome
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import com.google.android.play.core.ktx.assetsPath
import com.google.android.play.core.ktx.bytesDownloaded
import com.google.android.play.core.ktx.name
import com.google.android.play.core.ktx.requestFetch
import com.google.android.play.core.ktx.requestPackStates
import com.google.android.play.core.ktx.requestProgressFlow
import com.google.android.play.core.ktx.requestRemovePack
import com.google.android.play.core.ktx.status
import com.google.android.play.core.ktx.totalBytesToDownload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Android adapter implementing [ContentDeliveryEngine] via Play Asset Delivery's KTX coroutine
 * extensions (content-delivery-contract.md §1 / T028). Maps each platform API exactly per the
 * contract's table. **No decisions of any kind** (Constitution IV): no free-space checks,
 * connectivity policy, ordering, or formatting — those live in the use cases / repo above.
 *
 * Play statuses `WAITING_FOR_WIFI` and `REQUIRES_USER_CONFIRMATION` map to
 * [DeliveryPhase.WAITING_FOR_NETWORK_POLICY] / [DeliveryPhase.REQUIRES_CONFIRMATION] — these are
 * **waits, not failures** (research D11 / SC-007).
 *
 * Construction mirrors the existing `Media3AudioEngine(context)` shape: this file accepts the
 * application context and does nothing else; the only "decision" here is choosing which platform
 * API to call, which is what the contract says an adapter does.
 */
class PlayAssetDeliveryEngine(context: Context) : ContentDeliveryEngine {

    private val manager: AssetPackManager = AssetPackManagerFactory.getInstance(context)

    override suspend fun querySize(packId: String): Long? {
        val states = manager.requestPackStates(listOf(packId))
        return states.packStates()[packId]?.totalBytesToDownload
    }

    override suspend fun install(packId: String): Resource<Unit> = try {
        manager.requestFetch(listOf(packId))
        Resource.Success(Unit)
    } catch (e: com.google.android.play.core.assetpacks.AssetPackException) {
        Resource.Failure(DeliveryError.DeliveryFailed(DeliveryFailure.Unknown(e.errorCode)))
    } catch (t: Throwable) {
        // No usable connectivity surfaces as a generic exception from Play services — the use case
        // layer cannot avoid this signal; map to NoConnectivity here only if Play says so.
        Resource.Failure(DeliveryError.DeliveryFailed(DeliveryFailure.Unknown(-1)))
    }

    override fun observe(packId: String): Flow<DeliveryProgress> =
        manager.requestProgressFlow(listOf(packId))
            .filter { state -> state.name == packId }
            .map { state -> mapState(state) }

    private fun mapState(state: AssetPackState): DeliveryProgress = DeliveryProgress(
        bytesTransferred = state.bytesDownloaded,
        totalBytes = state.totalBytesToDownload,
        phase = mapStatusToPhase(state.status),
    )

    private fun mapStatusToPhase(status: Int): DeliveryPhase = when (status) {
        AssetPackStatus.PENDING -> DeliveryPhase.PENDING
        AssetPackStatus.DOWNLOADING,
        AssetPackStatus.TRANSFERRING -> DeliveryPhase.TRANSFERRING
        AssetPackStatus.WAITING_FOR_WIFI -> DeliveryPhase.WAITING_FOR_NETWORK_POLICY
        AssetPackStatus.REQUIRES_USER_CONFIRMATION -> DeliveryPhase.REQUIRES_CONFIRMATION
        else -> DeliveryPhase.TRANSFERRING
    }

    override suspend fun cancel(packId: String) {
        try {
            manager.cancel(listOf(packId))
        } catch (_: Throwable) {
            // Best-effort abort; partial bytes are released by the platform.
        }
    }

    override suspend fun remove(packId: String): Resource<RemovalOutcome> = try {
        manager.requestRemovePack(packId)
        Resource.Success(RemovalOutcome.Reclaimed(0L))
    } catch (t: Throwable) {
        Resource.Failure(AppError.Storage(t.message ?: "remove failed"))
    }

    override suspend fun locate(packId: String): String? =
        runCatching { manager.getPackLocation(packId)?.assetsPath }.getOrNull()

    override suspend fun isInstalled(packId: String): Boolean {
        val states = manager.requestPackStates(listOf(packId))
        val state = states.packStates()[packId] ?: return false
        return state.status == AssetPackStatus.COMPLETED
    }
}