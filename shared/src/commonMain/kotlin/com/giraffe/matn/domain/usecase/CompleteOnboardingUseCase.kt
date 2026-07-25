package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.repository.OnboardingRepository

/**
 * Marks onboarding completed (or skipped — same state, FR-018). Persists immediately (Principle VI).
 */
@org.koin.core.annotation.Factory
class CompleteOnboardingUseCase(
    private val repo: OnboardingRepository,
) : UseCase<Unit, Unit> {
    override suspend fun invoke(params: Unit): Resource<Unit> = repo.markCompleted()
}