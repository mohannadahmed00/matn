package com.giraffe.matn.domain

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.MatnProgress
import com.giraffe.matn.domain.repository.ProgressRepository
import com.giraffe.matn.domain.usecase.MarkChapterMemorizedUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkChapterMemorizedUseCaseTest {

    private class FakeProgressRepository(
        private val chapterVerses: Map<String, List<String>> = emptyMap(),
    ) : ProgressRepository {
        val memorizedVerseIds = mutableSetOf<String>()
        val creditedVerseIds = mutableListOf<String>()
        var lastSetChapterMemorizedCall: Pair<String, Boolean>? = null

        override suspend fun setVerseMemorized(verseId: String, memorized: Boolean): Resource<Unit> {
            if (memorized) {
                if (memorizedVerseIds.add(verseId)) creditedVerseIds.add(verseId)
            } else {
                memorizedVerseIds.remove(verseId)
            }
            return Resource.Success(Unit)
        }

        override suspend fun setChapterMemorized(chapterId: String, memorized: Boolean): Resource<Unit> {
            lastSetChapterMemorizedCall = chapterId to memorized
            chapterVerses[chapterId].orEmpty().forEach { setVerseMemorized(it, memorized) }
            return Resource.Success(Unit)
        }

        override fun observeMemorizedVerseIds(matnId: String): Flow<Set<String>> = flowOf(memorizedVerseIds.toSet())
        override fun observeMatnProgress(matnId: String): Flow<MatnProgress> = flowOf(MatnProgress(matnId, 0, 0))
        override fun observeLibraryProgress(): Flow<List<MatnProgress>> = flowOf(emptyList())
        override suspend fun recordPractice(verseId: String): Resource<Unit> = Resource.Success(Unit)
        override fun observeTodayPracticeCount(): Flow<Int> = flowOf(0)
    }

    @Test
    fun `forwards chapterId and memorized unchanged`() = runTest {
        val repo = FakeProgressRepository(chapterVerses = mapOf("c1" to listOf("v1", "v2")))
        val useCase = MarkChapterMemorizedUseCase(repo)

        useCase(MarkChapterMemorizedUseCase.Params("c1", true))
        assertEquals("c1" to true, repo.lastSetChapterMemorizedCall)

        useCase(MarkChapterMemorizedUseCase.Params("c1", false))
        assertEquals("c1" to false, repo.lastSetChapterMemorizedCall)
    }

    @Test
    fun `bulk marking a partly memorized chapter credits only newly memorized verses once`() = runTest {
        val repo = FakeProgressRepository(chapterVerses = mapOf("c1" to listOf("v1", "v2", "v3")))
        repo.setVerseMemorized("v1", true) // pre-existing state before the bulk mark
        val useCase = MarkChapterMemorizedUseCase(repo)

        useCase(MarkChapterMemorizedUseCase.Params("c1", true))

        assertEquals(setOf("v1", "v2", "v3"), repo.memorizedVerseIds)
        // v1 was credited by the setup call, not re-credited by the bulk mark.
        assertEquals(listOf("v1", "v2", "v3"), repo.creditedVerseIds)
    }
}
