package com.giraffe.matn.data.audio

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.storage.StorageRestClient
import com.giraffe.matn.domain.audio.UploadProgress
import com.giraffe.matn.domain.audio.VerseAudioPlan
import com.giraffe.matn.domain.audio.VerseAudioUploader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Uploads sequentially, in verse order, over [StorageRestClient] (`data-model.md` §8 step 2). */
class DefaultVerseAudioUploader(
    private val storageClient: StorageRestClient,
) : VerseAudioUploader {

    override fun upload(plan: VerseAudioPlan): Flow<UploadProgress> = flow {
        for (pending in plan.uploads) {
            val totalBytes = pending.bytes.size.toLong()
            emit(UploadProgress.Verse(pending.verseId, uploadedBytes = 0L, totalBytes = totalBytes))
            when (val result = storageClient.upload(pending.objectPath, pending.bytes, "audio/mpeg")) {
                is Resource.Success -> emit(UploadProgress.Verse(pending.verseId, uploadedBytes = totalBytes, totalBytes = totalBytes))
                is Resource.Failure -> {
                    emit(UploadProgress.Failed(result.error))
                    return@flow
                }
            }
        }
        emit(UploadProgress.Done)
    }
}
