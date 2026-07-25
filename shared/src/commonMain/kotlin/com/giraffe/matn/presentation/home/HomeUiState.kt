package com.giraffe.matn.presentation.home

import com.giraffe.matn.core.AppError
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.ContinueLearningEntry
import com.giraffe.matn.domain.model.MatnSummary

/**
 * Immutable UI state for the Home / library screen (data-model.md §4.1; Principle II).
 *
 *  * [isLoading] is true until the first emission of [ObserveLibraryUseCase] arrives.
 *  * [items] holds one [MatnSummary] per matn (cover/title/author/count/duration) for the grid.
 *  * [isEmpty] is true only when the store has zero متون → render the localized empty state
 *    (FR-004/SC-008), not a blank/error screen.
 *  * [continueLearning] is the Phase-4 Home offer. `null` renders **nothing at all** — no
 *    placeholder, no reserved space (FR-015). It stays null until its own collector resolves and
 *    MUST NOT gate the grid (SC-005/T043a).
 *  * [dailyGoal] (specs/010-design-system-adoption) is the top-bar progress ring's state. It
 *    stays at its default (placeholder) value until specs/007 wires up real goal tracking.
 *  * [progressByMatn] (Phase 7, FR-006) is the per-matn memorized fraction for the library card
 *    affordance, keyed by matn id. Collected independently — like [continueLearning] — so it
 *    never gates [isLoading]; a matn absent from the map simply renders no progress affordance.
 *  * [availability] (Phase 8, FR-002) is the per-matn content-delivery state, keyed by matn id.
 *    Collected independently — like [progressByMatn] — so it never gates [isLoading]; a matn
 *    absent from the map renders no availability badge yet (storage-ui-contract.md §5:
 *    re-collection on resume, never a cached snapshot).
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val items: List<MatnSummary> = emptyList(),
    val isEmpty: Boolean = false,
    val error: AppError? = null,
    val continueLearning: ContinueLearningEntry? = null,
    val dailyGoal: DailyGoalUiState = DailyGoalUiState(),
    val progressByMatn: Map<String, Float> = emptyMap(),
    val availability: Map<String, ContentAvailability> = emptyMap(),
)