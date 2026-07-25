package com.giraffe.matn.domain.usecase

import com.giraffe.matn.domain.model.OnboardingStatus
import com.giraffe.matn.domain.repository.OnboardingRepository

/**
 * Synchronous read of the onboarding status, for the navigation start-destination decision
 * (FR-019). Pure domain read via a use case so navigation never reaches past it to a repository
 * (Principle I — contract onboarding-permissions §1.1).
 */
class GetOnboardingStatusNowUseCase(
    private val repo: OnboardingRepository,
) {
    operator fun invoke(): OnboardingStatus = repo.statusNow()
}