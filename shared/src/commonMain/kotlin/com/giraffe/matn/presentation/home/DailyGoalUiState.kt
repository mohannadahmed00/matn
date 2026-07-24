package com.giraffe.matn.presentation.home

/**
 * UI-state for the Home screen's daily-goal progress ring (Phase 7, data-model.md §2.2/§5). Backed
 * by [com.giraffe.matn.domain.usecase.ObserveDailyProgressUseCase] via [HomeViewModel] — real
 * practiced/goal tracking, no placeholder.
 */
data class DailyGoalUiState(
    val practiced: Int = 0,
    val goal: Int = 10,
    val fraction: Float = 0f,
    val isComplete: Boolean = false,
)
