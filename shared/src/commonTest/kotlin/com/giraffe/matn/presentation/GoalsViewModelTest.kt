package com.giraffe.matn.presentation

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.DailyProgress
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.MatnProgress
import com.giraffe.matn.domain.model.MatnSummary
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.presentation.goals.GoalsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertTrue

class GoalsViewModelTest {

    @BeforeTest
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    private fun summary(id: String, title: String): MatnSummary = MatnSummary(
        matn = Matn(id, title, "مؤلف", "", null, StructureKind.SIMPLE),
        verseCount = 4,
        totalDurationMs = 1000,
    )

    private fun newViewModel(
        dailyProgressFlow: Flow<DailyProgress> = flowOf(DailyProgress(0, 10)),
        libraryProgressFlow: Flow<List<MatnProgress>> = flowOf(emptyList()),
        libraryFlow: Flow<List<MatnSummary>> = flowOf(emptyList()),
        setGoal: UseCase<Int, Unit> = object : UseCase<Int, Unit> {
            override suspend fun invoke(params: Int): Resource<Unit> = Resource.Success(Unit)
        },
    ): GoalsViewModel = GoalsViewModel(
        observeDailyProgress = object : FlowUseCase<Unit, DailyProgress> {
            override fun invoke(params: Unit): Flow<DailyProgress> = dailyProgressFlow
        },
        observeLibraryProgress = object : FlowUseCase<Unit, List<MatnProgress>> {
            override fun invoke(params: Unit): Flow<List<MatnProgress>> = libraryProgressFlow
        },
        setDailyGoal = setGoal,
        observeLibrary = object : FlowUseCase<Unit, List<MatnSummary>> {
            override fun invoke(params: Unit): Flow<List<MatnSummary>> = libraryFlow
        },
    )

    @Test
    fun `both emissions populate loading dailyProgress and matnProgress`() = runTest {
        val vm = newViewModel(
            dailyProgressFlow = flowOf(DailyProgress(4, 10)),
            libraryProgressFlow = flowOf(listOf(MatnProgress("m1", 2, 4))),
            libraryFlow = flowOf(listOf(summary("m1", "الأجرومية"))),
        )
        assertFalse(vm.state.value.isLoading)
        assertEquals(DailyProgress(4, 10), vm.state.value.dailyProgress)
        assertEquals(listOf(MatnProgress("m1", 2, 4)), vm.state.value.matnProgress)
        assertEquals("الأجرومية", vm.state.value.matnTitles["m1"])
    }

    @Test
    fun `empty library-progress list sets isEmpty true`() = runTest {
        val vm = newViewModel(libraryProgressFlow = flowOf(emptyList()))
        assertTrue(vm.state.value.isEmpty)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `onGoalChanged invokes SetDailyGoalUseCase with the new value`() = runTest {
        var persisted: Int? = null
        val vm = newViewModel(
            setGoal = object : UseCase<Int, Unit> {
                override suspend fun invoke(params: Int): Resource<Unit> {
                    persisted = params
                    return Resource.Success(Unit)
                }
            },
        )
        vm.onGoalChanged(15)
        assertEquals(15, persisted)
    }

    @Test
    fun `a later goal emission rescales dailyProgress`() = runTest {
        val dailyState = MutableStateFlow(DailyProgress(4, 10))
        val vm = newViewModel(dailyProgressFlow = dailyState)
        assertEquals(0.4f, vm.state.value.dailyProgress?.fraction)

        dailyState.value = DailyProgress(4, 20)
        assertEquals(0.2f, vm.state.value.dailyProgress?.fraction)
    }
}
