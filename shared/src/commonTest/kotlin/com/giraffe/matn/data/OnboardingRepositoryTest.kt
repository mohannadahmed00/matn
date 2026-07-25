package com.giraffe.matn.data

import com.giraffe.matn.data.repository.OnboardingRepositoryImpl
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.model.OnboardingStatus
import com.giraffe.matn.newTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T035: `OnboardingRepository` over the existing `app_setting` table (no migration).
 *  - default when absent (`NOT_COMPLETED`);
 *  - round-trip persistence after `markCompleted()`;
 *  - synchronous `statusNow()` agrees with the flow's first emission.
 */
class OnboardingRepositoryTest {

    @Test
    fun `default is NOT_COMPLETED when unset`() = runTest {
        val repo = OnboardingRepositoryImpl(newTestDatabase())
        assertEquals(OnboardingStatus.NOT_COMPLETED, repo.statusNow())
        assertEquals(OnboardingStatus.NOT_COMPLETED, repo.observeStatus().first())
    }

    @Test
    fun `markCompleted persists COMPLETED`() = runTest {
        val db: ContentDatabase = newTestDatabase()
        val repo = OnboardingRepositoryImpl(db)
        repo.markCompleted()
        assertEquals(OnboardingStatus.COMPLETED, repo.statusNow())
        assertEquals(OnboardingStatus.COMPLETED, repo.observeStatus().first())
    }

    @Test
    fun `fresh repository over the same DB reads the persisted status`() = runTest {
        val db: ContentDatabase = newTestDatabase()
        OnboardingRepositoryImpl(db).markCompleted()
        val freshRepo = OnboardingRepositoryImpl(db)
        assertEquals(OnboardingStatus.COMPLETED, freshRepo.statusNow())
        assertEquals(OnboardingStatus.COMPLETED, freshRepo.observeStatus().first())
    }

    @Test
    fun `statusNow agrees with the flow's first emission in every stored state`() = runTest {
        // Absent → NOT_COMPLETED
        val dbNoValue: ContentDatabase = newTestDatabase()
        val repoNoValue = OnboardingRepositoryImpl(dbNoValue)
        assertEquals(repoNoValue.statusNow(), repoNoValue.observeStatus().first())
        // Stored true → COMPLETED
        val dbDone: ContentDatabase = newTestDatabase()
        OnboardingRepositoryImpl(dbDone).markCompleted()
        val repoDone = OnboardingRepositoryImpl(dbDone)
        assertEquals(repoDone.statusNow(), repoDone.observeStatus().first())
    }
}