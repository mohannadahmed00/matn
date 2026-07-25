package com.giraffe.matn.presentation.onboarding

/**
 * T069 (US3, onboarding-permissions-contract.md §2) — which of the three panels is showing, plus
 * [isCompleted] once `markCompleted()` has succeeded (skip or finish, FR-018) — the stateful
 * holder reacts to this by navigating to Home.
 */
data class OnboardingUiState(val panelIndex: Int = 0, val isCompleted: Boolean = false) {
    val isLastPanel: Boolean get() = panelIndex == 2
}
