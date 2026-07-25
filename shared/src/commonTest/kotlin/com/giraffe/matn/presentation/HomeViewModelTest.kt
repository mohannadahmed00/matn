package com.giraffe.matn.presentation

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.data.repository.MatnRepositoryImpl
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.ContinueLearningEntry
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
import kotlinx.coroutines.flow.MutableStateFlow
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

    private fun homeViewModelWithAvailability(
        libraryFlow: Flow<List<MatnSummary>>,
        availabilityFlow: Flow<Map<String, ContentAvailability>>,
    ): HomeViewModel = HomeViewModel(
        observeLibrary = object : FlowUseCase<Unit, List<MatnSummary>> {
            override fun invoke(params: Unit): Flow<List<MatnSummary>> = libraryFlow
        },
        observeLibraryAvailability = object : FlowUseCase<Unit, Map<String, ContentAvailability>> {
            override fun invoke(params: Unit): Flow<Map<String, ContentAvailability>> = availabilityFlow
        },
    )

    @Test
    fun `T048 per-card availability reaches the state map`() = runTest {
        val vm = homeViewModelWithAvailability(
            libraryFlow = flowOf(listOf(summary("m1", "الأجرومية", count = 4, total = 31_300))),
            availabilityFlow = flowOf(mapOf("m1" to ContentAvailability.Installed(2_400_000))),
        )
        val availability = vm.state.value.availability["m1"]
        assertEquals(ContentAvailability.Installed(2_400_000), availability)
    }

    @Test
    fun `T048 a mid-flight availability change reaches the state object`() = runTest {
        val availabilityFlow = MutableStateFlow<Map<String, ContentAvailability>>(
            mapOf("m1" to ContentAvailability.NotInstalled()),
        )
        val vm = homeViewModelWithAvailability(
            libraryFlow = flowOf(listOf(summary("m1", "الأجرومية", count = 4, total = 31_300))),
            availabilityFlow = availabilityFlow,
        )
        assertEquals(ContentAvailability.NotInstalled(), vm.state.value.availability["m1"])

        // Simulate a backgrounding/resume re-collection reporting a newly-installed matn — this
        // is what proves the state comes from Flow re-collection, never a cached snapshot
        // (storage-ui-contract.md §5, FR-005).
        availabilityFlow.value = mapOf("m1" to ContentAvailability.Installed(2_400_000))
        assertEquals(ContentAvailability.Installed(2_400_000), vm.state.value.availability["m1"])
    }

    @Test
    fun `T060 Continue Learning offers reinstall when its matn is not installed`() = runTest {
        val entry = ContinueLearningEntry(matnId = "m1", matnTitle = "الأجرومية", verseDisplayNumber = 3, verseId = "v3")
        val vm = HomeViewModel(
            observeLibrary = object : FlowUseCase<Unit, List<MatnSummary>> {
                override fun invoke(params: Unit): Flow<List<MatnSummary>> = flowOf(emptyList())
            },
            observeContinueLearning = object : FlowUseCase<Unit, ContinueLearningEntry?> {
                override fun invoke(params: Unit): Flow<ContinueLearningEntry?> = flowOf(entry)
            },
            observeLibraryAvailability = object : FlowUseCase<Unit, Map<String, ContentAvailability>> {
                override fun invoke(params: Unit): Flow<Map<String, ContentAvailability>> =
                    flowOf(mapOf("m1" to ContentAvailability.NotInstalled()))
            },
        )
        assertFalse(vm.state.value.isContinueLearningContentInstalled)
    }

    @Test
    fun `T060 Continue Learning never reports installed for a resume that would fail`() = runTest {
        val entry = ContinueLearningEntry(matnId = "m1", matnTitle = "الأجرومية", verseDisplayNumber = 3, verseId = "v3")
        val vm = HomeViewModel(
            observeLibrary = object : FlowUseCase<Unit, List<MatnSummary>> {
                override fun invoke(params: Unit): Flow<List<MatnSummary>> = flowOf(emptyList())
            },
            observeContinueLearning = object : FlowUseCase<Unit, ContinueLearningEntry?> {
                override fun invoke(params: Unit): Flow<ContinueLearningEntry?> = flowOf(entry)
            },
            observeLibraryAvailability = object : FlowUseCase<Unit, Map<String, ContentAvailability>> {
                override fun invoke(params: Unit): Flow<Map<String, ContentAvailability>> = flowOf(emptyMap())
            },
        )
        // Absent from the map (availability not yet resolved) must never read as installed —
        // that would offer a resume that fails the playback gate (SC-007).
        assertFalse(vm.state.value.isContinueLearningContentInstalled)
    }
}