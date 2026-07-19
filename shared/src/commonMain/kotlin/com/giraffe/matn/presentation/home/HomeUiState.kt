package com.giraffe.matn.presentation.home

import com.giraffe.matn.core.AppError
import com.giraffe.matn.domain.model.MatnSummary

/**
 * Immutable UI state for the Home / library screen (data-model.md §4.1; Principle II).
 *
 *  * [isLoading] is true until the first emission of [ObserveLibraryUseCase] arrives.
 *  * [items] holds one [MatnSummary] per matn (cover/title/author/count/duration) for the grid.
 *  * [isEmpty] is true only when the store has zero متون → render the localized empty state
 *    (FR-004/SC-008), not a blank/error screen.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val items: List<MatnSummary> = emptyList(),
    val isEmpty: Boolean = false,
    val error: AppError? = null,
)