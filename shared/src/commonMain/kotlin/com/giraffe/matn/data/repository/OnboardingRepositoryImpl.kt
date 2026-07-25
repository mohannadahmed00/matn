package com.giraffe.matn.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.giraffe.matn.core.Resource
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.model.OnboardingStatus
import com.giraffe.matn.domain.repository.OnboardingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
private const val VALUE_TRUE = "true"
private const val VALUE_FALSE = "false"

/**
 * Implements [OnboardingRepository] over the additive `app_setting` key/value table (data-model
 * §1). Stored values are `"true"` / `"false"`; an absent row resolves to
 * [OnboardingStatus.NOT_COMPLETED] (the default — never throws).
 */
class OnboardingRepositoryImpl(
    private val db: ContentDatabase,
) : OnboardingRepository {

    override fun observeStatus(): Flow<OnboardingStatus> =
        db.contentQueries
            .selectSetting(KEY_ONBOARDING_COMPLETED)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { value -> parseStatus(value) }

    override fun statusNow(): OnboardingStatus =
        parseStatus(db.contentQueries.selectSetting(KEY_ONBOARDING_COMPLETED).executeAsOneOrNull())

    override suspend fun markCompleted(): Resource<Unit> =
        storageCall({ "Failed to persist onboarding completion" }) {
            db.contentQueries.upsertSetting(KEY_ONBOARDING_COMPLETED, VALUE_TRUE)
        }

    private fun parseStatus(value: String?): OnboardingStatus =
        if (value == VALUE_TRUE) OnboardingStatus.COMPLETED else OnboardingStatus.NOT_COMPLETED
}