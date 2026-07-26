package com.giraffe.matn.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.giraffe.matn.core.Resource
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.repository.DailyGoalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val KEY_DAILY_GOAL = "daily_goal"
private const val DEFAULT_DAILY_GOAL = 10

/**
 * Implements [DailyGoalRepository] over the additive `app_setting` key/value table (data-model
 * §1.3), mirroring [ReadingPreferencesRepositoryImpl]'s style almost line for line. An absent or
 * unparseable stored value — or one below 1 — resolves to [DEFAULT_DAILY_GOAL]; the preference
 * never crashes the UI (FR-009/Q3).
 */
@org.koin.core.annotation.Single(binds = [DailyGoalRepository::class])
class DailyGoalRepositoryImpl(
    private val db: ContentDatabase,
) : DailyGoalRepository {

    override fun observeGoal(): Flow<Int> =
        db.contentQueries
            .selectSetting(KEY_DAILY_GOAL)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { value -> value?.toIntOrNull()?.takeIf { v -> v >= 1 } ?: DEFAULT_DAILY_GOAL }

    override suspend fun setGoal(target: Int): Resource<Unit> =
        storageCall({ "Failed to persist daily goal" }) {
            db.contentQueries.upsertSetting(KEY_DAILY_GOAL, target.coerceAtLeast(1).toString())
        }
}
