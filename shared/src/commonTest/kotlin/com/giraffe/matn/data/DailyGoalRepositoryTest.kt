package com.giraffe.matn.data

import com.giraffe.matn.data.repository.DailyGoalRepositoryImpl
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.newTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DailyGoalRepositoryTest {

    @Test
    fun `default is 10 when unset`() = runTest {
        val repo = DailyGoalRepositoryImpl(newTestDatabase())
        assertEquals(10, repo.observeGoal().first())
    }

    @Test
    fun `setGoal then observe emits the new value`() = runTest {
        val repo = DailyGoalRepositoryImpl(newTestDatabase())
        repo.setGoal(25)
        assertEquals(25, repo.observeGoal().first())
    }

    @Test
    fun `setGoal with zero or negative is stored as 1`() = runTest {
        val repo = DailyGoalRepositoryImpl(newTestDatabase())
        repo.setGoal(0)
        assertEquals(1, repo.observeGoal().first())

        repo.setGoal(-3)
        assertEquals(1, repo.observeGoal().first())
    }

    @Test
    fun `garbage stored value falls back to 10`() = runTest {
        val db: ContentDatabase = newTestDatabase()
        db.contentQueries.upsertSetting("daily_goal", "abc")
        val repo = DailyGoalRepositoryImpl(db)
        assertEquals(10, repo.observeGoal().first())
    }

    @Test
    fun `fresh repository over the same DB reads the persisted goal`() = runTest {
        val db: ContentDatabase = newTestDatabase()
        DailyGoalRepositoryImpl(db).setGoal(15)
        val freshRepo = DailyGoalRepositoryImpl(db)
        assertEquals(15, freshRepo.observeGoal().first())
    }
}
