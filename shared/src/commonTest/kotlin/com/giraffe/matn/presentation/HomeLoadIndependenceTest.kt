package com.giraffe.matn.presentation

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.ContinueLearningEntry
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.MatnSummary
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.presentation.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T043a — the structural guarantee behind SC-005 (Continue Learning adds ≤200 ms to Home load):
 * the grid renders from the library collector alone. Here the continue-learning flow **never
 * emits** (it suspends forever), and the grid must still paint — `isLoading` flips false and
 * `items` populate. If the grid can render while the entry never resolves at all, the entry
 * cannot be adding measurable latency. Paired with the on-device observation in quickstart §B1.
 */
class HomeLoadIndependenceTest {

    @BeforeTest
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `library renders even when the continue-learning flow never emits`() = runTest {
        val neverEmits = object : FlowUseCase<Unit, ContinueLearningEntry?> {
            override fun invoke(params: Unit): Flow<ContinueLearningEntry?> =
                flow { awaitCancellation() }
        }
        val library = object : FlowUseCase<Unit, List<MatnSummary>> {
            override fun invoke(params: Unit): Flow<List<MatnSummary>> =
                flowOf(listOf(summary("m1"), summary("m2")))
        }

        val vm = HomeViewModel(
            observeLibrary = library,
            observeContinueLearning = neverEmits,
        )

        // The library painted without waiting on the entry (SC-005: independent collectors,
        // never combined, never gating isLoading)…
        assertFalse(vm.state.value.isLoading)
        assertEquals(2, vm.state.value.items.size)
        assertTrue(vm.state.value.items.isNotEmpty())
        // …and the unresolved entry simply stays null, which renders nothing (FR-015).
        assertNull(vm.state.value.continueLearning)
    }

    private fun summary(id: String): MatnSummary = MatnSummary(
        matn = Matn(
            id = id,
            title = "متن",
            author = "مؤلف",
            description = "",
            coverImageRef = null,
            structureKind = StructureKind.SIMPLE,
        ),
        verseCount = 4,
        totalDurationMs = 31_300L,
    )
}
