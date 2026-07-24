package com.giraffe.matn.presentation.goals

import androidx.lifecycle.viewModelScope
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.DailyProgress
import com.giraffe.matn.domain.model.MatnProgress
import com.giraffe.matn.domain.model.MatnSummary
import com.giraffe.matn.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Owns the Goals dashboard state (contracts/goals-ui-contract.md §1; Principle II). Two
 * independent collectors, mirroring [com.giraffe.matn.presentation.home.HomeViewModel]: the
 * library-progress emission is the one that clears [GoalsUiState.isLoading] and sets
 * [GoalsUiState.isEmpty]; the daily-progress emission fills [GoalsUiState.dailyProgress]
 * independently. No Compose or platform imports (Constitution II).
 */
class GoalsViewModel(
    observeDailyProgress: FlowUseCase<Unit, DailyProgress>,
    observeLibraryProgress: FlowUseCase<Unit, List<MatnProgress>>,
    private val setDailyGoal: UseCase<Int, Unit>,
    /** Supplies matn titles for the per-matn rows (FR-016) — [MatnProgress] itself carries no
     *  title (data-model.md §2.1). Same use case [com.giraffe.matn.presentation.home.HomeViewModel]
     *  already reads; merged here by matn id. */
    observeLibrary: FlowUseCase<Unit, List<MatnSummary>>,
) : BaseViewModel<GoalsUiState>(GoalsUiState()) {

    init {
        observeDailyProgress.invoke(Unit)
            .onEach { progress -> setState { it.copy(dailyProgress = progress) } }
            .launchIn(viewModelScope)

        observeLibraryProgress.invoke(Unit)
            .onEach { list ->
                setState { it.copy(isLoading = false, matnProgress = list, isEmpty = list.isEmpty()) }
            }
            .launchIn(viewModelScope)

        observeLibrary.invoke(Unit)
            .onEach { summaries ->
                val titles = summaries.associate { it.matn.id to it.matn.title }
                setState { it.copy(matnTitles = titles) }
            }
            .launchIn(viewModelScope)
    }

    /** US3: persist a new daily goal; the observed flow re-emits and rescales the ring. */
    fun onGoalChanged(target: Int) {
        viewModelScope.launch { setDailyGoal.invoke(target) }
    }
}
