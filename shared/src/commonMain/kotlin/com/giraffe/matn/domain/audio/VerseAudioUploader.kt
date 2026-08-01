package com.giraffe.matn.domain.audio

import com.giraffe.matn.core.AppError
import kotlinx.coroutines.flow.Flow

/**
 * Uploads a [VerseAudioPlan]'s pending payloads (`data-model.md` §8 step 2). Never deletes —
 * cleanup belongs to the repository, after the commit (`contracts/audio-artifact-contract.md` §4).
 */
interface VerseAudioUploader {
    fun upload(plan: VerseAudioPlan): Flow<UploadProgress>
}

sealed interface UploadProgress {
    data class Verse(val verseId: String, val uploadedBytes: Long, val totalBytes: Long) : UploadProgress
    data object Done : UploadProgress
    data class Failed(val error: AppError) : UploadProgress
}
