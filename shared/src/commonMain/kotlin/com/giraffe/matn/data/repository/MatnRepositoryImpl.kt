package com.giraffe.matn.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.mapper.toDomain
import com.giraffe.matn.data.mapper.toDomainOrNull
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.model.Chapter
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.repository.MatnRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MatnRepositoryImpl(private val db: ContentDatabase) : MatnRepository {

    override suspend fun getMatn(id: String): Resource<Matn?> =
        storageCall({ "Failed to read matn $id" }) {
            db.contentQueries.selectMatnById(id).executeAsOneOrNull()?.toDomain()
        }

    override fun observeLibrary(): Flow<List<Matn>> =
        db.contentQueries
            .selectAllMatn()
            .asFlow()
            .mapToList(Dispatchers.Default)
            // Drop (rather than throw on) any row with an unrecognized structure_kind so a
            // single corrupt/legacy row can't tear down the whole library stream.
            .map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

    override suspend fun getChapters(matnId: String): Resource<List<Chapter>> =
        storageCall({ "Failed to read chapters for $matnId" }) {
            db.contentQueries.selectChaptersByMatn(matnId).executeAsList().map { it.toDomain() }
        }
}
