package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.data.audio.ByteArrayByteSource
import com.giraffe.matn.domain.audio.AudioContentTag
import com.giraffe.matn.domain.audio.AudioProbe
import com.giraffe.matn.domain.audio.audioProfile
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.DraftAudio
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.error.AudioAttachError
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Attaches or replaces one verse's recording (`data-model.md` A2/A5, FR-002, FR-005b). Probes
 * before touching the repository, so an unreadable file or a profile mismatch never reaches
 * storage (FR-003).
 */
@OptIn(ExperimentalUuidApi::class)
class AttachVerseAudioUseCase(
    private val repository: CatalogRepository,
    private val audioProbe: AudioProbe,
    private val newId: () -> String = { Uuid.random().toString() },
) : UseCase<AttachVerseAudioUseCase.Params, MatnDraft> {
    data class Params(val draft: MatnDraft, val verseId: String, val bytes: ByteArray)

    override suspend fun invoke(params: Params): Resource<MatnDraft> {
        val probeResult = audioProbe.probe(ByteArrayByteSource(params.bytes))
        val probe = when (probeResult) {
            is Resource.Success -> probeResult.data
            is Resource.Failure -> return Resource.Failure(AudioAttachError.WrongFormat)
        }

        val existingProfile = params.draft.audioProfile()
        if (existingProfile != null && existingProfile != probe.profile) {
            return Resource.Failure(AudioAttachError.ProfileMismatch(existingProfile, probe.profile))
        }

        val previousAudio = params.draft.verses.find { it.id == params.verseId }?.audio
        val tag = AudioContentTag.of(params.bytes)
        val audio = DraftAudio(
            id = previousAudio?.id ?: newId(),
            fileRef = AudioContentTag.objectPath(params.draft.id, params.verseId, tag),
            durationMs = probe.durationMs,
            sizeBytes = params.bytes.size.toLong(),
            sampleRate = probe.profile.sampleRate,
            channels = probe.profile.channels,
        )

        return repository.attachVerseAudio(params.draft, params.verseId, audio, params.bytes)
    }
}
