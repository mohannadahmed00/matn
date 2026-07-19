package com.giraffe.matn.presentation.home

import androidx.lifecycle.viewModelScope
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.MatnSummary
import com.giraffe.matn.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Owns the Home library grid state (data-model.md §4.1; Principle II). On init collects
 * [ObserveLibraryUseCase] and maps each emission to [items]; an empty list flips [isEmpty]
 * to true and clears [isLoading] (FR-004/SC-008).
 */
class HomeViewModel(
    observeLibrary: FlowUseCase<Unit, List<MatnSummary>>,
) : BaseViewModel<HomeUiState>(HomeUiState()) {

    init {
        observeLibrary
            .invoke(Unit)
            .onEach { items ->
                setState {
                    it.copy(
                        isLoading = false,
                        items = items,
                        isEmpty = items.isEmpty(),
                        error = null,
                    )
                }
            }
            .launchIn(viewModelScope)
    }
}