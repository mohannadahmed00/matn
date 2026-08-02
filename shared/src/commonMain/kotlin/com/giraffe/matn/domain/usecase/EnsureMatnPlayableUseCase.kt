package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.repository.DownloadedContentRepository
import kotlinx.coroutines.flow.first

/**
 * The single playback gate (FR-021). Returns [DeliveryError.ContentNotDownloaded] when the matn is
 * not `Downloaded`, otherwise success. `PlaybackController.startSession` consults it once per
 * session; the UI consults it before rendering a play affordance, so an actionable download prompt
 * replaces it rather than playback failing silently.
 *
 * Phase 13 removed the starter carve-out — no matn is permanently available any more (FR-040), so
 * this gate applies uniformly to every matn in the catalog.
 */
@org.koin.core.annotation.Factory
class EnsureMatnPlayableUseCase(
    private val repository: DownloadedContentRepository,
) : UseCase<String, Unit> {
    override suspend fun invoke(params: String): Resource<Unit> {
        val availability = repository.observeAvailability(params).first()
        return if (availability is ContentAvailability.Downloaded) {
            Resource.Success(Unit)
        } else {
            Resource.Failure(DeliveryError.ContentNotDownloaded(params))
        }
    }
}