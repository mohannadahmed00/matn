package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.repository.ContentPackRepository
import kotlinx.coroutines.flow.first

/**
 * T038 (FR-011/FR-012) — the single playback gate (research D9). Returns
 * [DeliveryError.ContentNotInstalled] when the matn is not `Installed`, otherwise success.
 * `PlaybackController.startSession` consults it once per session; the UI consults it before
 * rendering a play affordance so the install prompt replaces it (FR-011). The starter matn is
 * always `Installed`, so this gate always passes for it (FR-014).
 */
@org.koin.core.annotation.Factory
class EnsureMatnPlayableUseCase(
    private val repository: ContentPackRepository,
) : UseCase<String, Unit> {
    override suspend fun invoke(params: String): Resource<Unit> {
        val availability = repository.observeAvailability(params).first()
        return if (availability is ContentAvailability.Installed) {
            Resource.Success(Unit)
        } else {
            Resource.Failure(DeliveryError.ContentNotInstalled(params))
        }
    }
}