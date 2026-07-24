package com.giraffe.matn.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.mapper.toDomain
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.model.Verse
import com.giraffe.matn.domain.repository.VerseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VerseRepositoryImpl(private val db: ContentDatabase) : VerseRepository {

    override fun observeVerses(matnId: String): Flow<List<Verse>> =
        db.contentQueries
            .selectVersesByMatn(matnId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getVersesByChapter(chapterId: String): Resource<List<Verse>> =
        storageCall({ "Failed to read verses for chapter $chapterId" }) {
            db.contentQueries.selectVersesByChapter(chapterId).executeAsList().map { it.toDomain() }
        }

    override suspend fun getVersesByMatn(matnId: String): Resource<List<Verse>> =
        storageCall({ "Failed to read verses for matn $matnId" }) {
            db.contentQueries.selectVersesByMatn(matnId).executeAsList().map { it.toDomain() }
        }

    override suspend fun getVerse(id: String): Resource<Verse?> =
        storageCall({ "Failed to read verse $id" }) {
            db.contentQueries.selectVerseById(id).executeAsOneOrNull()?.toDomain()
        }
}
