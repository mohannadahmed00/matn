package com.giraffe.matn.presentation.search

import androidx.lifecycle.viewModelScope
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.SearchResult
import com.giraffe.matn.presentation.base.BaseViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.milliseconds

private val DEBOUNCE = 250.milliseconds

/**
 * Search-as-you-type (research.md D8; contracts/search-contract.md § ViewModel state machine).
 * [onQueryChange] updates [SearchUiState.query] immediately so the text field never lags, and
 * drives a debounced, `flatMapLatest`-cancelled pipeline into [SearchLibraryUseCase]. A blank raw
 * query short-circuits straight to [SearchPhase.Idle] without ever invoking the use case
 * (FR-008); a non-blank query with an empty result set is [SearchPhase.NoResults] (FR-009).
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val searchLibrary: FlowUseCase<String, List<SearchResult>>,
) : BaseViewModel<SearchUiState>(SearchUiState()) {

    private val queryInput = MutableStateFlow("")

    init {
        queryInput
            .debounce(DEBOUNCE)
            .flatMapLatest { q ->
                if (q.isBlank()) {
                    flowOf(SearchPhase.Idle)
                } else {
                    searchLibrary.invoke(q).map { results ->
                        if (results.isEmpty()) SearchPhase.NoResults else SearchPhase.Results(results)
                    }
                }
            }
            .onEach { phase -> setState { it.copy(phase = phase) } }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(query: String) {
        setState {
            it.copy(
                query = query,
                phase = if (query.isBlank()) SearchPhase.Idle else SearchPhase.Searching,
            )
        }
        queryInput.value = query
    }

    fun onClearQuery() {
        onQueryChange("")
    }
}
