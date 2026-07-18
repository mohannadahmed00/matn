package com.giraffe.matn.domain.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.Chapter
import com.giraffe.matn.domain.model.Matn
import kotlinx.coroutines.flow.Flow

interface MatnRepository {
    suspend fun getMatn(id: String): Resource<Matn?>
    fun observeLibrary(): Flow<List<Matn>>
    suspend fun getChapters(matnId: String): Resource<List<Chapter>>
}