package com.giraffe.matn.presentation.onboarding

import com.giraffe.matn.domain.usecase.CompleteOnboardingUseCase
import com.giraffe.matn.presentation.base.BaseViewModel

/**
 * T070 (US3, onboarding-permissions-contract.md §2.2) — panel navigation plus completion.
 * [onSkip] and [onFinish] both call [completeOnboarding]: skipping and finishing produce
 * identical persisted state (FR-018).
 */
class OnboardingViewModel(
    private val completeOnboarding: CompleteOnboardingUseCase,
) : BaseViewModel<OnboardingUiState>(OnboardingUiState()) {

    fun onNext() {
        val current = stateValue
        if (current.isLastPanel) {
            onFinish()
        } else {
            setState { it.copy(panelIndex = it.panelIndex + 1) }
        }
    }

    fun onSkip() = complete()

    /**
     * Rewinds to the first panel for a re-open from Settings (contract §2.3). Needed because
     * onboarding is now an overlay above Library rather than a route: the ViewModel outlives a
     * dismissal, so without this the re-opened overlay would still be holding `isCompleted = true`
     * and would close itself on its first frame. Re-opening never clears the *persisted* completed
     * flag — this is presentation state only.
     */
    fun onReopened() {
        setState { OnboardingUiState() }
    }

    fun onFinish() = complete()

    private fun complete() {
        runUseCase(
            useCase = completeOnboarding,
            params = Unit,
            onSuccess = { setState { it.copy(isCompleted = true) } },
            onError = { /* best-effort — a retry is just tapping Skip/Continue again */ },
        )
    }
}
