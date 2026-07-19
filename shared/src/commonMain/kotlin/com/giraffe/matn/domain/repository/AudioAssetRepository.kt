package com.giraffe.matn.domain.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.AudioAsset

interface AudioAssetRepository {
    suspend fun getAudioForVerse(
        verseId: String,
        reciterId: String = DEFAULT_RECITER,
    ): Resource<AudioAsset?>

    /**
     * Phase 2 (FR-005/FR-006): a matn's per-verse audio in matn-global `display_number` order,
     * for one reciter (default reciter). Reuses the additive `selectAudioForMatnOrdered` query.
     */
    suspend fun getAudioForMatn(
        matnId: String,
        reciterId: String = DEFAULT_RECITER,
    ): Resource<List<AudioAsset>>

    companion object {
        const val DEFAULT_RECITER = "reciter-default-v1"
    }
}