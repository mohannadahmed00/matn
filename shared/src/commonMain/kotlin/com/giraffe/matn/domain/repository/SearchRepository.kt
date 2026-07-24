package com.giraffe.matn.domain.repository

import com.giraffe.matn.domain.model.SearchResult
import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    /**
     * Emits the deterministic result set for [query] (search-contract.md § Semantics) and
     * re-emits when library content changes. Blank (whitespace-only) queries emit an empty
     * list — the Idle-vs-NoResults distinction is the ViewModel's job (FR-008/FR-009), keyed on
     * the *raw* query, not this result list.
     */
    fun search(query: String): Flow<List<SearchResult>>
}
