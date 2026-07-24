package com.giraffe.matn.domain.repository

import com.giraffe.matn.core.Resource
import kotlinx.coroutines.flow.Flow

interface DailyGoalRepository {
    /** Reactive read of the daily goal. Absent or unparseable resolves to the default **10** (FR-009). */
    fun observeGoal(): Flow<Int>

    /** Persists `max(1, target)` as the goal (FR-009 positive; FR-014 lower bound). */
    suspend fun setGoal(target: Int): Resource<Unit>
}
