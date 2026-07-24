package com.giraffe.matn.domain

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.MatnProgress
import com.giraffe.matn.domain.repository.DailyGoalRepository
import com.giraffe.matn.domain.repository.ProgressRepository
import com.giraffe.matn.domain.usecase.ObserveDailyProgressUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObserveDailyProgressUseCaseTest {

    private class FakeProgressRepository(private val countFlow: Flow<Int>) : ProgressRepository {
        override suspend fun setVerseMemorized(verseId: String, memorized: Boolean): Resource<Unit> =
            Resource.Success(Unit)
        override suspend fun setChapterMemorized(chapterId: String, memorized: Boolean): Resource<Unit> =
            Resource.Success(Unit)
        override fun observeMemorizedVerseIds(matnId: String) = throw NotImplementedError()
        override fun observeMatnProgress(matnId: String): Flow<MatnProgress> = throw NotImplementedError()
        override fun observeLibraryProgress(): Flow<List<MatnProgress>> = throw NotImplementedError()
        override suspend fun recordPractice(verseId: String): Resource<Unit> = Resource.Success(Unit)
        override fun observeTodayPracticeCount(): Flow<Int> = countFlow
    }

    private class FakeGoalRepository(private val goalFlow: Flow<Int>) : DailyGoalRepository {
        override fun observeGoal(): Flow<Int> = goalFlow
        override suspend fun setGoal(target: Int): Resource<Unit> = Resource.Success(Unit)
    }

    @Test
    fun `zero out of goal is empty and incomplete`() = runTest {
        val useCase = ObserveDailyProgressUseCase(
            FakeProgressRepository(MutableStateFlow(0)),
            FakeGoalRepository(MutableStateFlow(10)),
        )
        val progress = useCase.invoke(Unit).first()
        assertEquals(0f, progress.fraction)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `five out of ten is half`() = runTest {
        val useCase = ObserveDailyProgressUseCase(
            FakeProgressRepository(MutableStateFlow(5)),
            FakeGoalRepository(MutableStateFlow(10)),
        )
        val progress = useCase.invoke(Unit).first()
        assertEquals(0.5f, progress.fraction)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `ten out of ten is complete`() = runTest {
        val useCase = ObserveDailyProgressUseCase(
            FakeProgressRepository(MutableStateFlow(10)),
            FakeGoalRepository(MutableStateFlow(10)),
        )
        val progress = useCase.invoke(Unit).first()
        assertEquals(1f, progress.fraction)
        assertTrue(progress.isComplete)
    }

    @Test
    fun `twelve out of ten clamps fraction to one and is complete`() = runTest {
        val useCase = ObserveDailyProgressUseCase(
            FakeProgressRepository(MutableStateFlow(12)),
            FakeGoalRepository(MutableStateFlow(10)),
        )
        val progress = useCase.invoke(Unit).first()
        assertEquals(1f, progress.fraction)
        assertTrue(progress.isComplete)
    }

    @Test
    fun `changing the goal re-emits a rescaled DailyProgress`() = runTest {
        val countState = MutableStateFlow(5)
        val goalState = MutableStateFlow(10)
        val useCase = ObserveDailyProgressUseCase(FakeProgressRepository(countState), FakeGoalRepository(goalState))

        assertEquals(0.5f, useCase.invoke(Unit).first().fraction)

        goalState.value = 20
        assertEquals(0.25f, useCase.invoke(Unit).first().fraction)
    }
}
