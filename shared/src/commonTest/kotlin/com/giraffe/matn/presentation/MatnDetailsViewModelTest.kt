package com.giraffe.matn.presentation

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.Chapter
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.MatnDetails
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.domain.model.Verse
import com.giraffe.matn.presentation.details.MatnDetailsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatnDetailsViewModelTest {

    @BeforeTest
    fun setUp() {
        // viewModelScope defaults to Dispatchers.Main, which is absent on host tests.
        // UnconfinedTestDispatcher runs ViewModel coroutines eagerly so state is observable
        // synchronously within runTest.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val simpleMatn = Matn(
        id = "simple-1",
        title = "الأجرومية",
        author = "ابن آجُرُّوم",
        description = "متن مختصر",
        coverImageRef = "covers/simple.jpg",
        structureKind = StructureKind.SIMPLE,
    )

    private val structuredMatn = Matn(
        id = "structured-1",
        title = "متن الآجرومية مبوب",
        author = "ابن آجُرُّوم",
        description = "مبوب",
        coverImageRef = null,
        structureKind = StructureKind.STRUCTURED,
    )

    private val simpleVerses = listOf(
        Verse("v1", "simple-1", null, 1, "الكَلامُ هُوَ اللَّفظُ", 8200),
        Verse("v2", "simple-1", null, 2, "وَأَقسامُهُ ثَلاثَةٌ", 7400),
        Verse("v3", "simple-1", null, 3, "فَالاسمُ يُعرَفُ", 6900),
    )

    private val structuredChapters = listOf(
        Chapter("c1", "structured-1", "باب الكلام", 1),
        Chapter("c2", "structured-1", "باب الإعراب", 2),
    )

    private val structuredVerses = listOf(
        Verse("sv1", "structured-1", "c1", 1, "الكَلامُ", 8200),
        Verse("sv2", "structured-1", "c1", 2, "وَأَقسامُهُ", 7400),
        Verse("sv3", "structured-1", "c2", 3, "الإِعرابُ", 8100),
    )

    @Test
    fun `verses preserved in the order the repository emits`() = runTest {
        // FR-006: the repository guarantees matn-global displayNumber order; the ViewModel
        // must preserve that order without reordering. Pass an already-ordered list (matching
        // the real VerseRepository.observeVerses SQL ORDER BY display_number) and assert.
        val vm = newViewModel(
            matnDetails = MatnDetails(structuredMatn, structuredChapters, true),
            verses = structuredVerses,
        )
        assertEquals(listOf(1, 2, 3), vm.state.value.verses.map { it.displayNumber })
    }

    @Test
    fun `arabicText is byte-for-byte identical including diacritics`() = runTest {
        val vm = newViewModel(
            matnDetails = MatnDetails(simpleMatn, emptyList(), false),
            verses = simpleVerses,
        )
        vm.state.value.verses.forEachIndexed { i, row ->
            assertEquals(simpleVerses[i].arabicText, row.arabicText)
        }
    }

    @Test
    fun `header verseCount and totalDurationMs derived from streamed verses`() = runTest {
        val vm = newViewModel(
            matnDetails = MatnDetails(simpleMatn, emptyList(), false),
            verses = simpleVerses,
        )
        val header = vm.state.value.header
        assertNotNull(header)
        assertEquals(3, header.verseCount)
        assertEquals(8200L + 7400L + 6900L, header.totalDurationMs)
    }

    @Test
    fun `structured matn shows table of contents`() = runTest {
        val vm = newViewModel(
            matnDetails = MatnDetails(structuredMatn, structuredChapters, true),
            verses = structuredVerses,
        )
        assertTrue(vm.state.value.showTableOfContents)
        assertEquals(2, vm.state.value.chapters.size)
    }

    @Test
    fun `simple matn hides table of contents`() = runTest {
        val vm = newViewModel(
            matnDetails = MatnDetails(simpleMatn, emptyList(), false),
            verses = simpleVerses,
        )
        assertEquals(false, vm.state.value.showTableOfContents)
        assertTrue(vm.state.value.chapters.isEmpty())
    }

    @Test
    fun `chapter firstVerseDisplayNumber equals min displayNumber of chapter verses`() = runTest {
        val vm = newViewModel(
            matnDetails = MatnDetails(structuredMatn, structuredChapters, true),
            verses = structuredVerses,
        )
        val c1 = vm.state.value.chapters.first { it.id == "c1" }
        assertEquals(1, c1.firstVerseDisplayNumber)
        val c2 = vm.state.value.chapters.first { it.id == "c2" }
        assertEquals(3, c2.firstVerseDisplayNumber)
    }

    @Test
    fun `chapters ordered by chapter order`() = runTest {
        val chapters = listOf(
            Chapter("c2", "structured-1", "باب الإعراب", 2),
            Chapter("c1", "structured-1", "باب الكلام", 1),
        )
        val vm = newViewModel(
            matnDetails = MatnDetails(structuredMatn, chapters, true),
            verses = structuredVerses,
        )
        assertEquals(listOf("c1", "c2"), vm.state.value.chapters.map { it.id })
    }

    @Test
    fun `missing matn sets NotFound error and not loading`() = runTest {
        val vm = MatnDetailsViewModel(
            matnId = "missing",
            getMatnDetails = FakeUseCase { Resource.Failure(AppError.NotFound) },
            observeVerses = FakeFlowUseCase { flow { } },
            getFontSize = FakeFlowUseCase { flowOf(ReadingFontSize.MEDIUM) },
            setFontSize = FakeUseCase { Resource.Success(Unit) },
        )
        assertEquals(AppError.NotFound, vm.state.value.error)
        assertEquals(false, vm.state.value.isLoading)
        assertNull(vm.state.value.header)
    }

    @Test
    fun `storage failure surfaces as Storage error`() = runTest {
        val vm = MatnDetailsViewModel(
            matnId = "x",
            getMatnDetails = FakeUseCase { Resource.Failure(AppError.Storage("boom")) },
            observeVerses = FakeFlowUseCase { flow { } },
            getFontSize = FakeFlowUseCase { flowOf(ReadingFontSize.MEDIUM) },
            setFontSize = FakeUseCase { Resource.Success(Unit) },
        )
        assertTrue(vm.state.value.error is AppError.Storage)
    }

    @Test
    fun `font size preference flows into state live`() = runTest {
        val sizeState = MutableStateFlow(ReadingFontSize.MEDIUM)
        val vm = newViewModel(
            matnDetails = MatnDetails(simpleMatn, emptyList(), false),
            verses = simpleVerses,
            fontFlow = sizeState,
        )
        assertEquals(ReadingFontSize.MEDIUM, vm.state.value.fontSize)
        sizeState.value = ReadingFontSize.XLARGE
        assertEquals(ReadingFontSize.XLARGE, vm.state.value.fontSize)
    }

    @Test
    fun `onFontSizeChanged persists via SetFontSizeUseCase`() = runTest {
        var persisted: ReadingFontSize? = null
        val vm = newViewModel(
            matnDetails = MatnDetails(simpleMatn, emptyList(), false),
            verses = simpleVerses,
            setFont = FakeUseCase { size -> persisted = size; Resource.Success(Unit) },
        )
        vm.onFontSizeChanged(ReadingFontSize.LARGE)
        assertEquals(ReadingFontSize.LARGE, persisted)
    }

    private fun newViewModel(
        matnDetails: MatnDetails,
        verses: List<Verse>,
        fontFlow: Flow<ReadingFontSize> = flowOf(ReadingFontSize.MEDIUM),
        setFont: UseCase<ReadingFontSize, Unit> = FakeUseCase { Resource.Success(Unit) },
    ): MatnDetailsViewModel {
        val versesState = MutableStateFlow(verses)
        return MatnDetailsViewModel(
            matnId = matnDetails.matn.id,
            getMatnDetails = FakeUseCase { Resource.Success(matnDetails) },
            observeVerses = FakeFlowUseCase<String, List<Verse>> { versesState },
            getFontSize = FakeFlowUseCase { fontFlow },
            setFontSize = setFont,
        )
    }
}

private class FakeUseCase<P, R>(private val block: suspend (P) -> Resource<R>) : UseCase<P, R> {
    override suspend fun invoke(params: P): Resource<R> = block(params)
}

private class FakeFlowUseCase<P, R>(private val block: (P) -> Flow<R>) : FlowUseCase<P, R> {
    override fun invoke(params: P): Flow<R> = block(params)
}