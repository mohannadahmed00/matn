package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.audio.AudioSourceResolver
import com.giraffe.matn.domain.model.AudioTrack
import com.giraffe.matn.domain.model.PlaybackQueue
import com.giraffe.matn.domain.repository.AudioAssetRepository
import com.giraffe.matn.domain.repository.VerseRepository
import kotlinx.coroutines.flow.first

/**
 * Builds the ordered per-verse [PlaybackQueue] for one matn / reciter (data-model.md §4.2).
 *
 * Behavior:
 *  - reads the ordered verses (`verseRepository.observeVerses(matnId).first()`) and the ordered
 *    audio (`audioRepository.getAudioForMatn(...)`);
 *  - builds one [AudioTrack] per verse **in `displayNumber` order** (FR-005/FR-006);
 *  - a verse with no audio row is a **defensive** case only (Phase 0 V4 validation guarantees
 *    every verse has audio for validly-seeded content) and is silently omitted from the queue —
 *    no user notice (nothing playable to reach); the runtime FR-020 skip+notice path is handled
 *    by `PlaybackController` on `AudioEngineEvent.TrackError`, not here;
 *  - resolves each `fileRef` → `uri` via [AudioSourceResolver];
 *  - computes `startIndex` from `startVerseId` (null → 0);
 *  - returns `Resource.Failure(AppError.NotFound)` if the matn has zero playable tracks.
 *
 * No coroutine launched inside (Principle III); returns `Resource` per the Phase 0/1 convention.
 */
open class BuildPlaybackQueueUseCase(
    private val verseRepository: VerseRepository,
    private val audioRepository: AudioAssetRepository,
    private val audioSourceResolver: AudioSourceResolver,
) : UseCase<BuildPlaybackQueueUseCase.Params, PlaybackQueue> {

    data class Params(
        val matnId: String,
        val startVerseId: String? = null,
        val reciterId: String = AudioAssetRepository.DEFAULT_RECITER,
    )

    open suspend override fun invoke(params: Params): Resource<PlaybackQueue> {
        val verses = verseRepository.observeVerses(params.matnId).first()
        val audioResult = audioRepository.getAudioForMatn(params.matnId, params.reciterId)
        val audioByVerse = when (audioResult) {
            is Resource.Success -> audioResult.data.associateBy { it.verseId }
            is Resource.Failure -> return audioResult
        }

        val tracks = mutableListOf<AudioTrack>()
        for (verse in verses) {
            val asset = audioByVerse[verse.id] ?: continue
            val uri = audioSourceResolver.resolve(params.matnId, asset.fileRef)
            tracks.add(
                AudioTrack(
                    verseId = verse.id,
                    displayNumber = verse.displayNumber,
                    uri = uri,
                    durationMs = asset.durationMs.takeIf { it > 0 } ?: verse.durationMs,
                ),
            )
        }

        if (tracks.isEmpty()) return Resource.Failure(AppError.NotFound)

        val startIndex = params.startVerseId?.let { id ->
            tracks.indexOfFirst { it.verseId == id }.coerceAtLeast(0)
        } ?: 0

        return Resource.Success(
            PlaybackQueue(
                matnId = params.matnId,
                tracks = tracks,
                startIndex = startIndex.coerceIn(0, tracks.lastIndex),
            ),
        )
    }
}