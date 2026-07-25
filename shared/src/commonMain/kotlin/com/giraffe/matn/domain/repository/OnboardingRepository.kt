package com.giraffe.matn.domain.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.OnboardingStatus
import kotlinx.coroutines.flow.Flow

/**
 * Phase 9 onboarding completion flag (FR-017/FR-018/FR-019, contract onboarding-permissions §1.1).
 * Persisted in the existing `app_setting` table under `onboarding_completed` (data-model §1).
 * Skipping and finishing both write `COMPLETED` — FR-018 requires skipping to leave nothing unset.
 */
interface OnboardingRepository {
    /** Live status; an absent or unrecognized stored value resolves to [OnboardingStatus.NOT_COMPLETED]. */
    fun observeStatus(): Flow<OnboardingStatus>

    /** Synchronous read for the navigation start-destination decision (FR-019). */
    fun statusNow(): OnboardingStatus

    /** Marks onboarding complete (or skipped — same state). Persists immediately (Principle VI). */
    suspend fun markCompleted(): Resource<Unit>
}