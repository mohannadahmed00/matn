package com.giraffe.matn.presentation.search

import com.giraffe.matn.domain.model.SearchResult

/**
 * The search screen's 4-state machine (contracts/search-contract.md § ViewModel state machine).
 * [SearchPhase.Idle] is keyed on the raw [query] being blank, not on an empty result set — a
 * cleared field is Idle, a non-blank miss is [SearchPhase.NoResults] (FR-008/FR-009).
 */
data class SearchUiState(
    val query: String = "",
    val phase: SearchPhase = SearchPhase.Idle,
)

sealed interface SearchPhase {
    data object Idle : SearchPhase
    data object Searching : SearchPhase
    data class Results(val results: List<SearchResult>) : SearchPhase
    data object NoResults : SearchPhase
}
