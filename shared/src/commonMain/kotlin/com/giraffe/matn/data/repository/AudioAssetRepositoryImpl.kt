package com.giraffe.matn.data.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.mapper.toDomain
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.model.AudioAsset
import com.giraffe.matn.domain.repository.AudioAssetRepository

@org.koin.core.annotation.Single(binds = [AudioAssetRepository::class])
class AudioAssetRepositoryImpl(private val db: ContentDatabase) : AudioAssetRepository {

    override suspend fun getAudioForVerse(
        verseId: String,
        reciterId: String,
    ): Resource<AudioAsset?> =
        storageCall({ "Failed to read audio for verse $verseId" }) {
            db.contentQueries.selectAudioForVerse(verseId, reciterId).executeAsOneOrNull()?.toDomain()
        }

    override suspend fun getAudioForMatn(
        matnId: String,
        reciterId: String,
    ): Resource<List<AudioAsset>> =
        storageCall({ "Failed to read audio for matn $matnId" }) {
            db.contentQueries
                .selectAudioForMatnOrdered(matnId, reciterId)
                .executeAsList()
                .map { it.toDomain() }
        }
}
