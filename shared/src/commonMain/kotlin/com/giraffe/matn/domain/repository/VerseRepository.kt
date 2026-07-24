package com.giraffe.matn.domain.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.Verse
import kotlinx.coroutines.flow.Flow

interface VerseRepository {
    fun observeVerses(matnId: String): Flow<List<Verse>>
    suspend fun getVersesByChapter(chapterId: String): Resource<List<Verse>>
    /** One-shot ordered read of a matn's verses (FR-018 resume path). Defaulted so existing
     *  collaborators that only exercise [observeVerses]/[getVerse] stay source-compatible. */
    suspend fun getVersesByMatn(matnId: String): Resource<List<Verse>> =
        Resource.Success(emptyList())
    suspend fun getVerse(id: String): Resource<Verse?>
}