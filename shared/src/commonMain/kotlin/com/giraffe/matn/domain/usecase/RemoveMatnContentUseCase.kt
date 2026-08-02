package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.domain.repository.DownloadedContentRepository
import com.giraffe.matn.playback.PlaybackController

/**
 * Removes a matn's downloaded content (FR-028) after the **ordered effects** in
 * delivery-contract.md §3:
 *
 * 1. If this matn is the active playback session, stop playback **before** deleting — the stop
 *    precedes deletion, never races it.
 * 2. Delegate to the repository, which deletes `downloads/{matnId}/` and then the content rows in
 *    one transaction, and returns the bytes actually reclaimed.
 *
 * **No matn is exempt** (FR-031). Phase 8's starter guard is gone with the starter itself (FR-040).
 *
 * Personal data survives by construction: since `5.sqm` dropped the `ON DELETE CASCADE` from
 * `verse`/`matn` to the five personal-data tables (research D5), deleting content rows cannot reach
 * a bookmark, note, memorized mark, practice record or saved session. `RemovalPreservesUserDataTest`
 * is the guard (SC-010).
 */
@org.koin.core.annotation.Factory
class RemoveMatnContentUseCase(
    private val repository: DownloadedContentRepository,
    private val playbackController: PlaybackController,
) : UseCase<String, RemovalOutcome> {

    override suspend fun invoke(params: String): Resource<RemovalOutcome> {
        val session = playbackController.state.value
        if (session.hasSession && session.matnId == params) {
            playbackController.stop()
        }
        return repository.remove(params)
    }
}
