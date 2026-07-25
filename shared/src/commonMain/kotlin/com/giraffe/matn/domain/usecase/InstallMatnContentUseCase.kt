package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.delivery.DeviceStorage
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.DeliveryFailure
import com.giraffe.matn.domain.repository.ContentPackRepository
import kotlinx.coroutines.flow.first

/**
 * T036 (FR-004, FR-007, FR-015) — installs a matn's on-demand content after passing the **ordered
 * preconditions** in content-delivery-contract.md §4:
 *
 * 1. Starter matn → never an install target. Failure([DeliveryError.StarterMatnNotRemovable]).
 * 2. Already `Installed`/`Installing` → success no-op (duplicate-tap edge case).
 * 3. `freeSpaceBytes() < bestKnownSize(matnId)` → Failure(DeliveryFailed(InsufficientStorage(required, available))).
 * 4. Otherwise delegate to the repository/engine, whose failure (e.g. no connectivity) passes
 *    through unchanged — there is no separate network-state API in this contract (FR-015).
 */
class InstallMatnContentUseCase(
    private val repository: ContentPackRepository,
    private val storage: DeviceStorage,
) : UseCase<String, Unit> {

    override suspend fun invoke(params: String): Resource<Unit> {
        // (1) Starter matn — refuses outright. The repository knows the catalog flag directly.
        if (repository.isStarterMatn(params)) {
            return Resource.Failure(DeliveryError.StarterMatnNotRemovable)
        }

        // (2) Already Installed or Installing → success no-op.
        val current = repository.observeAvailability(params).first()
        if (current is ContentAvailability.Installed || current is ContentAvailability.Installing) {
            return Resource.Success(Unit)
        }

        // (3) Insufficient storage. Best-known size preferred over declared (research D4).
        val required = repository.bestKnownSize(params)
        val available = storage.freeSpaceBytes()
        if (required > 0L && available < required) {
            return Resource.Failure(
                DeliveryError.DeliveryFailed(
                    DeliveryFailure.InsufficientStorage(requiredBytes = required, availableBytes = available),
                ),
            )
        }

        // (4) Delegate. Connectivity loss surfaces here as whatever DeliveryFailure the engine
        // reports (e.g. NoConnectivity), passed through unchanged.
        return when (val outcome = repository.install(params)) {
            is Resource.Success -> Resource.Success(Unit)
            is Resource.Failure -> Resource.Failure(outcome.error)
        }
    }
}