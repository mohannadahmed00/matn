package com.giraffe.matn.presentation.goals

import com.giraffe.matn.domain.model.DailyProgress
import com.giraffe.matn.domain.model.MatnProgress

/**
 * Immutable UI-state for the Goals tab (Phase 7, contracts/goals-ui-contract.md §1). [isLoading]
 * clears once the library-progress emission arrives (mirrors Home's load-independence collector
 * split). [isEmpty] means the library itself has no متون — not that nothing is memorized yet.
 */
data class GoalsUiState(
    val isLoading: Boolean = true,
    val dailyProgress: DailyProgress? = null,
    val matnProgress: List<MatnProgress> = emptyList(),
    val isEmpty: Boolean = false,
    /** matnId -> title, so each [matnProgress] row can render the matn's name (FR-016) alongside
     *  its bar — [MatnProgress] itself deliberately carries no title (data-model.md §2.1). */
    val matnTitles: Map<String, String> = emptyMap(),
)
