package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.data.audio.ByteSource
import com.giraffe.matn.data.audio.Mp3FrameIndex
import com.giraffe.matn.domain.audio.AudioContentTag
import com.giraffe.matn.domain.audio.AudioSlicer
import com.giraffe.matn.domain.audio.PendingUpload
import com.giraffe.matn.domain.audio.SplitPlan
import com.giraffe.matn.domain.audio.SplitPlanValidator
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.DraftAudio
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.error.SplitBlockedError
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Slices the source at its plan's ranges and commits the result in one write (FR-018, FR-019,
 * `split-contract.md` §5). Refuses before slicing when any blocking rule stands (FR-015/FR-016) —
 * a blocked split never reaches the repository. The plan itself is discarded by the caller on
 * success (FR-018) — this use case never stores it.
 */
@OptIn(ExperimentalUuidApi::class)
class ApplySplitUseCase(
    private val repository: CatalogRepository,
    private val slicer: AudioSlicer,
    private val newId: () -> String = { Uuid.random().toString() },
) : UseCase<ApplySplitUseCase.Params, MatnDraft> {

    data class Params(val draft: MatnDraft, val source: ByteSource, val index: Mp3FrameIndex, val plan: SplitPlan)

    override suspend fun invoke(params: Params): Resource<MatnDraft> {
        val report = SplitPlanValidator.validate(params.plan)
        if (report.blocking.isNotEmpty()) return Resource.Failure(SplitBlockedError(report.blocking))

        val sliceResult = slicer.slice(params.source, params.index, params.plan.ranges)
        val slices = when (sliceResult) {
            is Resource.Success -> sliceResult.data
            is Resource.Failure -> return sliceResult
        }

        val updates = mutableMapOf<String, DraftAudio>()
        val payloads = mutableListOf<PendingUpload>()
        slices.forEach { slice ->
            val previousAudio = params.draft.verses.find { it.id == slice.verseId }?.audio
            val tag = AudioContentTag.of(slice.bytes)
            val fileRef = AudioContentTag.objectPath(params.draft.id, slice.verseId, tag)
            val audio = DraftAudio(
                id = previousAudio?.id ?: newId(),
                fileRef = fileRef,
                durationMs = slice.durationMs,
                sizeBytes = slice.bytes.size.toLong(),
                sampleRate = params.plan.source.profile.sampleRate,
                channels = params.plan.source.profile.channels,
            )
            updates[slice.verseId] = audio
            payloads.add(PendingUpload(slice.verseId, fileRef, slice.bytes, audio))
        }

        return repository.applySplit(params.draft, updates, payloads)
    }
}
