package com.giraffe.matn.domain

import com.giraffe.matn.domain.model.BookmarkEntry
import com.giraffe.matn.domain.repository.BookmarkRepository
import com.giraffe.matn.domain.usecase.ObserveVerseAnnotationsUseCase
import com.giraffe.matn.core.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObserveVerseAnnotationsUseCaseTest {

    private class FakeBookmarkRepository(private val ids: Set<String>) : BookmarkRepository {
        override suspend fun toggle(verseId: String): Resource<Boolean> = Resource.Success(true)
        override fun observeAll(): Flow<List<BookmarkEntry>> = flowOf(emptyList())
        override fun observeBookmarkedVerseIds(matnId: String): Flow<Set<String>> = flowOf(ids)
    }

    @Test
    fun `map contains an entry per bookmarked verse with hasNote false`() = runTest {
        val useCase = ObserveVerseAnnotationsUseCase(FakeBookmarkRepository(setOf("v1", "v2")))
        val map = useCase.invoke("m1").first()
        assertEquals(setOf("v1", "v2"), map.keys)
        assertTrue(map.values.all { it.isBookmarked && !it.hasNote })
    }

    @Test
    fun `no bookmarks yields an empty map`() = runTest {
        val useCase = ObserveVerseAnnotationsUseCase(FakeBookmarkRepository(emptySet()))
        assertEquals(emptyMap(), useCase.invoke("m1").first())
    }
}
