package com.giraffe.matn.presentation.home

/**
 * UI-state for the Home screen's daily-goal progress ring
 * (specs/010-design-system-adoption/data-model.md § DailyGoalUiState). [isPlaceholder] is always
 * `true` until specs/007 (Progress & Daily Goals) implements real tracking — FR-010: show a
 * clearly-placeholder state rather than a fabricated number. No repository/use case backs this
 * yet; [HomeViewModel] supplies a constant placeholder.
 */
data class DailyGoalUiState(
    val isPlaceholder: Boolean = true,
    val progressFraction: Float = 0f,
)
