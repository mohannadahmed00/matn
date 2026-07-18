package com.giraffe.matn.domain.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.AudioAsset

interface AudioAssetRepository {
    suspend fun getAudioForVerse(
        verseId: String,
        reciterId: String = DEFAULT_RECITER,
    ): Resource<AudioAsset?>

    companion object {
        const val DEFAULT_RECITER = "reciter-default-v1"
    }
}