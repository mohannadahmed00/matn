package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.OnboardingStatus
import com.giraffe.matn.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.Flow

/** Streams the onboarding completion flag (FR-017/FR-019). */
@org.koin.core.annotation.Factory
class ObserveOnboardingStatusUseCase(
    private val repo: OnboardingRepository,
) : FlowUseCase<Unit, OnboardingStatus> {
    override fun invoke(params: Unit): Flow<OnboardingStatus> = repo.observeStatus()
}