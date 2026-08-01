package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.data.audio.ByteSource
import com.giraffe.matn.data.audio.Mp3FrameIndex
import com.giraffe.matn.domain.audio.AudioProbe
import com.giraffe.matn.domain.audio.SourceRecording
import com.giraffe.matn.domain.audio.audioProfile
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.error.AudioAttachError

/** Loads and validates the continuous recording a split session starts from (FR-004, FR-005b).
 * The source itself is never persisted or uploaded (FR-018) — only its measured properties and,
 * from the caller, its frame index for later slicing. */
class LoadSplitSourceUseCase(
    private val audioProbe: AudioProbe,
) : UseCase<LoadSplitSourceUseCase.Params, LoadSplitSourceUseCase.Result> {

    data class Params(val draft: MatnDraft, val source: ByteSource, val localPath: String, val sizeBytes: Long)
    data class Result(val source: SourceRecording, val index: Mp3FrameIndex)

    override suspend fun invoke(params: Params): Resource<Result> {
        if (params.sizeBytes > MAX_SOURCE_BYTES) {
            return Resource.Failure(AudioAttachError.SourceTooLarge)
        }

        val probeResult = audioProbe.probe(params.source)
        val probe = when (probeResult) {
            is Resource.Success -> probeResult.data
            is Resource.Failure -> return Resource.Failure(AudioAttachError.WrongFormat)
        }

        if (probe.durationMs > MAX_SOURCE_DURATION_MS) {
            return Resource.Failure(AudioAttachError.SourceTooLong)
        }

        val existingProfile = params.draft.audioProfile()
        if (existingProfile != null && existingProfile != probe.profile) {
            return Resource.Failure(AudioAttachError.ProfileMismatch(existingProfile, probe.profile))
        }

        val index = Mp3FrameIndex.build(params.source) ?: return Resource.Failure(AudioAttachError.WrongFormat)

        val recording = SourceRecording(
            localPath = params.localPath,
            sizeBytes = params.sizeBytes,
            durationMs = probe.durationMs,
            profile = probe.profile,
            frameCount = probe.frameCount,
        )
        return Resource.Success(Result(recording, index))
    }

    private companion object {
        const val MAX_SOURCE_BYTES = 300L * 1024 * 1024
        const val MAX_SOURCE_DURATION_MS = 4L * 60 * 60 * 1000
    }
}
