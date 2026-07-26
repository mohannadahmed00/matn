package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.domain.repository.ContentPackRepository
import com.giraffe.matn.playback.PlaybackController

/**
 * T053 (FR-017/FR-021/FR-022/FR-027) — removes a matn's on-demand content after the **ordered
 * effects** in content-delivery-contract.md §4:
 *
 * 1. Starter matn → `Failure(DeliveryError.StarterMatnNotRemovable)`; never a removal target.
 * 2. If this matn is the active playback session, stop playback **before** deleting — the stop
 *    precedes deletion, never races it (FR-021).
 * 3. Delegate to the repository; return the [RemovalOutcome] unchanged so the caller can render
 *    honest copy (Android `Reclaimed` vs iOS `ReleasedPendingSystemReclaim`).
 * 4. Touch no personal data — structurally guaranteed: `content_pack` has no relationship to any
 *    personal-data table (data-model.md §4), so nothing here can reach one.
 */
@org.koin.core.annotation.Factory
class RemoveMatnContentUseCase(
    private val repository: ContentPackRepository,
    private val playbackController: PlaybackController,
) : UseCase<String, RemovalOutcome> {

    override suspend fun invoke(params: String): Resource<RemovalOutcome> {
        if (repository.isStarterMatn(params)) {
            return Resource.Failure(DeliveryError.StarterMatnNotRemovable)
        }
        val session = playbackController.state.value
        if (session.hasSession && session.matnId == params) {
            playbackController.stop()
        }
        return repository.remove(params)
    }
}
