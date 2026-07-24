package com.giraffe.matn.domain

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.Bookmark
import com.giraffe.matn.domain.model.BookmarkEntry
import com.giraffe.matn.domain.model.Note
import com.giraffe.matn.domain.model.NoteEntry
import com.giraffe.matn.domain.repository.BookmarkRepository
import com.giraffe.matn.domain.repository.NoteRepository
import com.giraffe.matn.domain.usecase.ObserveVerseAnnotationsUseCase
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

    private class FakeNoteRepository(private val ids: Set<String>) : NoteRepository {
        override suspend fun save(verseId: String, text: String): Resource<Note> =
            Resource.Success(Note("n", verseId, text, 0L))
        override suspend fun delete(verseId: String): Resource<Unit> = Resource.Success(Unit)
        override suspend fun get(verseId: String): Resource<Note?> = Resource.Success(null)
        override fun observeAll(): Flow<List<NoteEntry>> = flowOf(emptyList())
        override fun observeNotedVerseIds(matnId: String): Flow<Set<String>> = flowOf(ids)
    }

    @Test
    fun `bookmark only`() = runTest {
        val useCase = ObserveVerseAnnotationsUseCase(FakeBookmarkRepository(setOf("v1")), FakeNoteRepository(emptySet()))
        val map = useCase.invoke("m1").first()
        assertEquals(setOf("v1"), map.keys)
        assertTrue(map.getValue("v1").isBookmarked)
        assertTrue(!map.getValue("v1").hasNote)
    }

    @Test
    fun `note only`() = runTest {
        val useCase = ObserveVerseAnnotationsUseCase(FakeBookmarkRepository(emptySet()), FakeNoteRepository(setOf("v1")))
        val map = useCase.invoke("m1").first()
        assertEquals(setOf("v1"), map.keys)
        assertTrue(!map.getValue("v1").isBookmarked)
        assertTrue(map.getValue("v1").hasNote)
    }

    @Test
    fun `both bookmark and note on the same verse`() = runTest {
        val useCase = ObserveVerseAnnotationsUseCase(FakeBookmarkRepository(setOf("v1")), FakeNoteRepository(setOf("v1")))
        val map = useCase.invoke("m1").first()
        assertEquals(setOf("v1"), map.keys)
        assertTrue(map.getValue("v1").isBookmarked)
        assertTrue(map.getValue("v1").hasNote)
    }

    @Test
    fun `neither yields an empty map`() = runTest {
        val useCase = ObserveVerseAnnotationsUseCase(FakeBookmarkRepository(emptySet()), FakeNoteRepository(emptySet()))
        assertEquals(emptyMap(), useCase.invoke("m1").first())
    }
}
