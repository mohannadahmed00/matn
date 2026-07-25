package com.giraffe.matn.delivery

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.delivery.ContentDeliveryEngine
import com.giraffe.matn.domain.model.DeliveryPhase
import com.giraffe.matn.domain.model.DeliveryProgress
import com.giraffe.matn.domain.model.RemovalOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Desktop adapter implementing [ContentDeliveryEngine]. There is no on-demand delivery system on
 * desktop (content-delivery-contract.md §1) — packs ship directly on disk with the build — so every
 * pack reports as already installed and `install`/`remove` are immediate no-ops. Mirrors the shape
 * of [com.giraffe.matn.delivery.OnDemandResourcesEngine]'s always-available branches.
 */
class DesktopContentDeliveryEngine : ContentDeliveryEngine {

    override suspend fun querySize(packId: String): Long? = null

    override suspend fun install(packId: String): Resource<Unit> = Resource.Success(Unit)

    override fun observe(packId: String): Flow<DeliveryProgress> =
        MutableStateFlow(DeliveryProgress(0, 0, DeliveryPhase.PENDING))

    override suspend fun cancel(packId: String) {}

    override suspend fun remove(packId: String): Resource<RemovalOutcome> =
        Resource.Success(RemovalOutcome.Reclaimed(0L))

    override suspend fun locate(packId: String): String? = null

    override suspend fun isInstalled(packId: String): Boolean = true
}
