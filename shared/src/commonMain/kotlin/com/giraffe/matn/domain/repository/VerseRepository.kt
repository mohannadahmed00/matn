package com.giraffe.matn.domain.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.Verse
import kotlinx.coroutines.flow.Flow

interface VerseRepository {
    fun observeVerses(matnId: String): Flow<List<Verse>>
    suspend fun getVersesByChapter(chapterId: String): Resource<List<Verse>>
    suspend fun getVerse(id: String): Resource<Verse?>
}