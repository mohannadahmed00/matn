package com.giraffe.matn.presentation

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.data.repository.MatnRepositoryImpl
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.domain.model.DailyProgress
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.MatnSummary
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.newTestDatabase
import com.giraffe.matn.parseSeed
import com.giraffe.matn.presentation.home.HomeViewModel
import com.giraffe.matn.SIMPLE_MATN_JSON
import com.giraffe.matn.STRUCTURED_MATN_JSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

class HomeViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `populated library emits one MatnSummary per matn with derived totals`() = runTest {
        val vm = homeViewModelOf(
            flowOf(
                listOf(
                    summary("m1", "الأجرومية", count = 4, total = 31_300),
                    summary("m2", "مبوب", count = 5, total = 39_900),
                ),
            ),
        )
        assertEquals(2, vm.state.value.items.size)
        val first = vm.state.value.items.first { it.matn.id == "m1" }
        assertEquals(4, first.verseCount)
        assertEquals(31_300L, first.totalDurationMs)
        assertFalse(vm.state.value.isLoading)
        assertFalse(vm.state.value.isEmpty)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `empty library sets isEmpty true with no error`() = runTest {
        val vm = homeViewModelOf(flowOf(emptyList()))
        assertEquals(true, vm.state.value.isEmpty)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test
    fun `selectLibrarySummaries returns COUNT and SUM against in-memory driver`() = runTest {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        val repo = MatnRepositoryImpl(db)
        loader.load(parseSeed(SIMPLE_MATN_JSON))

        val summaries = repo.observeLibrarySummaries().first()
        assertEquals(1, summaries.size)
        val only = summaries.first()
        assertEquals(parseSeed(SIMPLE_MATN_JSON).id, only.matn.id)
        assertEquals(4, only.verseCount)
        assertEquals(8_200L + 7_400L + 6_900L + 8_800L, only.totalDurationMs)
        assertEquals(StructureKind.SIMPLE, only.matn.structureKind)
    }

    @Test
    fun `two matns yield two summaries with each matn's own totals`() = runTest {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        val repo = MatnRepositoryImpl(db)
        loader.load(parseSeed(SIMPLE_MATN_JSON))
        loader.load(parseSeed(STRUCTURED_MATN_JSON))

        val summaries = repo.observeLibrarySummaries().first()
        assertEquals(2, summaries.size)
        val simple = summaries.first { it.matn.id == parseSeed(SIMPLE_MATN_JSON).id }
        assertEquals(4, simple.verseCount)
        val structured = summaries.first { it.matn.id == parseSeed(STRUCTURED_MATN_JSON).id }
        assertEquals(5, structured.verseCount)
    }

    @Test
    fun `matn with zero verses still appears with count zero via LEFT JOIN`() = runTest {
        val db = newTestDatabase()
        val repo = MatnRepositoryImpl(db)
        // Manually insert just a matn row (no verses).
        db.contentQueries.upsertMatn(
            id = "empty-matn",
            title = "فارغ",
            author = "مؤلف",
            description = "",
            cover_image_ref = null,
            structure_kind = "SIMPLE",
        )
        val summaries = repo.observeLibrarySummaries().first()
        assertEquals(1, summaries.size)
        assertEquals(0, summaries.first().verseCount)
        assertEquals(0L, summaries.first().totalDurationMs)
    }

    private fun summary(id: String, title: String, count: Int, total: Long): MatnSummary =
        MatnSummary(
            matn = Matn(
                id = id,
                title = title,
                author = "مؤلف",
                description = "",
                coverImageRef = null,
                structureKind = StructureKind.SIMPLE,
            ),
            verseCount = count,
            totalDurationMs = total,
        )

    private fun homeViewModelOf(flow: Flow<List<MatnSummary>>): HomeViewModel =
        HomeViewModel(object : FlowUseCase<Unit, List<MatnSummary>> {
            override fun invoke(params: Unit): Flow<List<MatnSummary>> = flow
        })

    private fun homeViewModelWithDailyProgress(
        libraryFlow: Flow<List<MatnSummary>>,
        dailyProgressFlow: Flow<DailyProgress>,
    ): HomeViewModel = HomeViewModel(
        observeLibrary = object : FlowUseCase<Unit, List<MatnSummary>> {
            override fun invoke(params: Unit): Flow<List<MatnSummary>> = libraryFlow
        },
        observeDailyProgress = object : FlowUseCase<Unit, DailyProgress> {
            override fun invoke(params: Unit): Flow<DailyProgress> = dailyProgressFlow
        },
    )

    @Test
    fun `DailyProgress 4 of 10 lands in state dailyGoal`() = runTest {
        val vm = homeViewModelWithDailyProgress(flowOf(emptyList()), flowOf(DailyProgress(4, 10)))
        assertEquals(4, vm.state.value.dailyGoal.practiced)
        assertEquals(10, vm.state.value.dailyGoal.goal)
        assertEquals(0.4f, vm.state.value.dailyGoal.fraction)
        assertFalse(vm.state.value.dailyGoal.isComplete)
    }

    @Test
    fun `DailyProgress 10 of 10 sets isComplete true`() = runTest {
        val vm = homeViewModelWithDailyProgress(flowOf(emptyList()), flowOf(DailyProgress(10, 10)))
        assertTrue(vm.state.value.dailyGoal.isComplete)
    }

    @Test
    fun `library grid still paints when the daily-progress flow never emits`() = runTest {
        val vm = homeViewModelWithDailyProgress(
            libraryFlow = flowOf(listOf(summary("m1", "الأجرومية", count = 4, total = 31_300))),
            dailyProgressFlow = flow { /* never emits */ },
        )
        assertFalse(vm.state.value.isLoading)
        assertEquals(1, vm.state.value.items.size)
        // dailyGoal simply stays at its default until the flow resolves.
        assertEquals(0, vm.state.value.dailyGoal.practiced)
    }
}