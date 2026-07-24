package com.giraffe.matn.presentation

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.AnnotatedVerseRef
import com.giraffe.matn.domain.model.SearchResult
import com.giraffe.matn.presentation.search.SearchPhase
import com.giraffe.matn.presentation.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun verseMatch(id: String) = SearchResult.VerseMatch(
        AnnotatedVerseRef(matnId = "m1", matnTitle = "matn", verseId = id, verseNumber = 1, verseText = "text"),
    )

    @Test
    fun `Idle then Searching then Results happy path`() = runTest(testDispatcher) {
        val vm = SearchViewModel(FakeSearch { flowOf(listOf(verseMatch("v1"))) })
        assertEquals(SearchPhase.Idle, vm.state.value.phase)

        vm.onQueryChange("verse")
        assertEquals(SearchPhase.Searching, vm.state.value.phase)

        advanceTimeBy(300)
        val results = assertIs<SearchPhase.Results>(vm.state.value.phase)
        assertEquals(1, results.results.size)
    }

    @Test
    fun `non-blank query with no matches becomes NoResults`() = runTest(testDispatcher) {
        val vm = SearchViewModel(FakeSearch { flowOf(emptyList()) })
        vm.onQueryChange("gibberish")
        advanceTimeBy(300)
        assertEquals(SearchPhase.NoResults, vm.state.value.phase)
    }

    @Test
    fun `rapid keystrokes collapse into a single search invocation`() = runTest(testDispatcher) {
        var invocations = 0
        val vm = SearchViewModel(
            FakeSearch {
                invocations++
                flowOf(listOf(verseMatch("v1")))
            },
        )
        vm.onQueryChange("a")
        vm.onQueryChange("al")
        vm.onQueryChange("ala")
        advanceTimeBy(300)
        assertEquals(1, invocations)
    }

    @Test
    fun `clearing the query returns Idle immediately without waiting for debounce`() = runTest(testDispatcher) {
        val vm = SearchViewModel(FakeSearch { flowOf(listOf(verseMatch("v1"))) })
        vm.onQueryChange("something")
        assertEquals(SearchPhase.Searching, vm.state.value.phase)
        vm.onClearQuery()
        assertEquals(SearchPhase.Idle, vm.state.value.phase)
        assertEquals("", vm.state.value.query)
    }

    @Test
    fun `a stale emission from a superseded query never lands`() = runTest(testDispatcher) {
        // "slow" never completes until explicitly resumed after the test asserts; flatMapLatest
        // must cancel it once "fast" supersedes it.
        val vm = SearchViewModel(
            FakeSearch { query ->
                if (query == "slow") {
                    flow {
                        kotlinx.coroutines.delay(10_000)
                        emit(listOf(verseMatch("stale")))
                    }
                } else {
                    flowOf(listOf(verseMatch("fresh")))
                }
            },
        )
        vm.onQueryChange("slow")
        advanceTimeBy(300) // debounce elapses, slow's flow starts its long delay
        vm.onQueryChange("fast")
        advanceTimeBy(15_000) // long enough for slow's flow to have emitted, if it weren't cancelled

        val results = assertIs<SearchPhase.Results>(vm.state.value.phase)
        assertTrue(results.results.all { it is SearchResult.VerseMatch && it.ref.verseId == "fresh" })
    }

    private class FakeSearch(
        private val block: (String) -> Flow<List<SearchResult>>,
    ) : FlowUseCase<String, List<SearchResult>> {
        override fun invoke(params: String): Flow<List<SearchResult>> = block(params)
    }
}
