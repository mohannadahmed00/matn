package com.giraffe.matn.presentation

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.model.Chapter
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.DeliveryFailure
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.MatnDetails
import com.giraffe.matn.domain.model.MatnProgress
import com.giraffe.matn.domain.model.Note
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.domain.model.Verse
import com.giraffe.matn.domain.usecase.MarkChapterMemorizedUseCase
import com.giraffe.matn.domain.usecase.SaveNoteParams
import com.giraffe.matn.domain.usecase.ToggleVerseMemorizedUseCase
import com.giraffe.matn.playback.FakeAudioEngine
import com.giraffe.matn.playback.FakeWakeLock
import com.giraffe.matn.playback.PlaybackController
import com.giraffe.matn.presentation.details.MatnDetailsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
        title = "Ø§Ù„Ø£Ø¬Ø±ÙˆÙ…ÙŠØ©",
        author = "Ø§Ø¨Ù† Ø¢Ø¬ÙØ±ÙÙ‘ÙˆÙ…",
        description = "Ù…ØªÙ† Ù…Ø®ØªØµØ±",
        coverImageRef = "covers/simple.jpg",
        structureKind = StructureKind.SIMPLE,
    )

    private val structuredMatn = Matn(
        id = "structured-1",
        title = "Ù…ØªÙ† Ø§Ù„Ø¢Ø¬Ø±ÙˆÙ…ÙŠØ© Ù…Ø¨ÙˆØ¨",
        author = "Ø§Ø¨Ù† Ø¢Ø¬ÙØ±ÙÙ‘ÙˆÙ…",
        description = "Ù…Ø¨ÙˆØ¨",
        coverImageRef = null,
        structureKind = StructureKind.STRUCTURED,
    )

    private val simpleVerses = listOf(
        Verse("v1", "simple-1", null, 1, "Ø§Ù„ÙƒÙŽÙ„Ø§Ù…Ù Ù‡ÙÙˆÙŽ Ø§Ù„Ù„ÙŽÙ‘ÙØ¸Ù", 8200),
        Verse("v2", "simple-1", null, 2, "ÙˆÙŽØ£ÙŽÙ‚Ø³Ø§Ù…ÙÙ‡Ù Ø«ÙŽÙ„Ø§Ø«ÙŽØ©ÙŒ", 7400),
        Verse("v3", "simple-1", null, 3, "ÙÙŽØ§Ù„Ø§Ø³Ù…Ù ÙŠÙØ¹Ø±ÙŽÙÙ", 6900),
    )

    private val structuredChapters = listOf(
        Chapter("c1", "structured-1", "Ø¨Ø§Ø¨ Ø§Ù„ÙƒÙ„Ø§Ù…", 1),
        Chapter("c2", "structured-1", "Ø¨Ø§Ø¨ Ø§Ù„Ø¥Ø¹Ø±Ø§Ø¨", 2),
    )

    private val structuredVerses = listOf(
        Verse("sv1", "structured-1", "c1", 1, "Ø§Ù„ÙƒÙŽÙ„Ø§Ù…Ù", 8200),
        Verse("sv2", "structured-1", "c1", 2, "ÙˆÙŽØ£ÙŽÙ‚Ø³Ø§Ù…ÙÙ‡Ù", 7400),
        Verse("sv3", "structured-1", "c2", 3, "Ø§Ù„Ø¥ÙØ¹Ø±Ø§Ø¨Ù", 8100),
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
            Chapter("c2", "structured-1", "Ø¨Ø§Ø¨ Ø§Ù„Ø¥Ø¹Ø±Ø§Ø¨", 2),
            Chapter("c1", "structured-1", "Ø¨Ø§Ø¨ Ø§Ù„ÙƒÙ„Ø§Ù…", 1),
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

    @Test
    fun `unknown focusVerseId is gracefully ignored`() = runTest {
        val vm = newViewModel(
            matnDetails = MatnDetails(simpleMatn, emptyList(), false),
            verses = simpleVerses,
            focusVerseId = "does-not-exist",
        )
        assertNull(vm.state.value.focusVerseId)
    }

    private fun newViewModel(
        matnDetails: MatnDetails,
        verses: List<Verse>,
        fontFlow: Flow<ReadingFontSize> = flowOf(ReadingFontSize.MEDIUM),
        setFont: UseCase<ReadingFontSize, Unit> = FakeUseCase { Resource.Success(Unit) },
        focusVerseId: String? = null,
        progressFlow: Flow<MatnProgress>? = null,
        memorizationFlow: Flow<Set<String>>? = null,
        toggleVerseMemorized: UseCase<ToggleVerseMemorizedUseCase.Params, Unit>? = null,
        markChapterMemorized: UseCase<MarkChapterMemorizedUseCase.Params, Unit>? = null,
        availabilityFlow: Flow<ContentAvailability>? = null,
        installMatnContent: UseCase<String, Unit>? = null,
        cancelInstall: UseCase<String, Unit>? = null,
        removeMatnContent: UseCase<String, RemovalOutcome>? = null,
    ): MatnDetailsViewModel {
        val versesState = MutableStateFlow(verses)
        return MatnDetailsViewModel(
            matnId = matnDetails.matn.id,
            getMatnDetails = FakeUseCase { Resource.Success(matnDetails) },
            observeVerses = FakeFlowUseCase<String, List<Verse>> { versesState },
            getFontSize = FakeFlowUseCase { fontFlow },
            setFontSize = setFont,
            focusVerseId = focusVerseId,
            observeMatnProgress = progressFlow?.let { flow -> FakeFlowUseCase { flow } },
            observeVerseMemorization = memorizationFlow?.let { flow -> FakeFlowUseCase { flow } },
            toggleVerseMemorized = toggleVerseMemorized,
            markChapterMemorized = markChapterMemorized,
            observeContentAvailability = availabilityFlow?.let { flow -> FakeFlowUseCase { flow } },
            installMatnContent = installMatnContent,
            cancelInstall = cancelInstall,
            removeMatnContent = removeMatnContent,
        )
    }

    @Test
    fun `progress flow lands in header memorizedCount and progressFraction`() = runTest {
        val progressState = MutableStateFlow(MatnProgress(matnId = "simple-1", memorizedCount = 1, totalCount = 3))
        val vm = newViewModel(
            matnDetails = MatnDetails(simpleMatn, emptyList(), false),
            verses = simpleVerses,
            progressFlow = progressState,
        )
        val header = vm.state.value.header
        assertNotNull(header)
        assertEquals(1, header.memorizedCount)
        assertEquals(1f / 3f, header.progressFraction)

        progressState.value = MatnProgress(matnId = "simple-1", memorizedCount = 3, totalCount = 3)
        assertEquals(3, vm.state.value.header?.memorizedCount)
        assertEquals(1f, vm.state.value.header?.progressFraction)
    }

    @Test
    fun `memorization flow lands in memorizedVerseIds`() = runTest {
        val memorizedState = MutableStateFlow(emptySet<String>())
        val vm = newViewModel(
            matnDetails = MatnDetails(simpleMatn, emptyList(), false),
            verses = simpleVerses,
            memorizationFlow = memorizedState,
        )
        assertEquals(emptySet(), vm.state.value.memorizedVerseIds)
        memorizedState.value = setOf("v1")
        assertEquals(setOf("v1"), vm.state.value.memorizedVerseIds)
    }

    @Test
    fun `onToggleMemorized targets the inverse of current membership`() = runTest {
        var lastParams: ToggleVerseMemorizedUseCase.Params? = null
        val vm = newViewModel(
            matnDetails = MatnDetails(simpleMatn, emptyList(), false),
            verses = simpleVerses,
            memorizationFlow = MutableStateFlow(setOf("v1")),
            toggleVerseMemorized = FakeUseCase { params -> lastParams = params; Resource.Success(Unit) },
        )
        vm.onToggleMemorized("v2") // not memorized -> target true
        assertEquals(ToggleVerseMemorizedUseCase.Params("v2", true), lastParams)

        vm.onToggleMemorized("v1") // already memorized -> target false
        assertEquals(ToggleVerseMemorizedUseCase.Params("v1", false), lastParams)

        // Playback state is untouched by toggling memorized state.
        assertEquals(false, vm.state.value.isPlaying)
        assertNull(vm.state.value.activeVerseId)
    }

    @Test
    fun `onMarkChapterMemorized forwards both arguments`() = runTest {
        var lastParams: MarkChapterMemorizedUseCase.Params? = null
        val vm = newViewModel(
            matnDetails = MatnDetails(structuredMatn, structuredChapters, true),
            verses = structuredVerses,
            markChapterMemorized = FakeUseCase { params -> lastParams = params; Resource.Success(Unit) },
        )
        vm.onMarkChapterMemorized("c1", true)
        assertEquals(MarkChapterMemorizedUseCase.Params("c1", true), lastParams)

        vm.onMarkChapterMemorized("c1", false)
        assertEquals(MarkChapterMemorizedUseCase.Params("c1", false), lastParams)

        assertEquals(false, vm.state.value.isPlaying)
        assertNull(vm.state.value.activeVerseId)
    }

    // ---- T050 (Phase 8, US1) ------------------------------------------------

    @Test
    fun `T050 availability drives the header state`() = runTest {
        val vm = newViewModel(
            matnDetails = MatnDetails(simpleMatn, emptyList(), false),
            verses = simpleVerses,
            availabilityFlow = flowOf(ContentAvailability.NotDownloaded()),
        )
        assertEquals(ContentAvailability.NotDownloaded(), vm.state.value.availability)
    }

    @Test
    fun `T050 install refused offline surfaces the reason`() = runTest {
        val vm = newViewModel(
            matnDetails = MatnDetails(simpleMatn, emptyList(), false),
            verses = simpleVerses,
            availabilityFlow = flowOf(ContentAvailability.NotDownloaded()),
            installMatnContent = FakeUseCase {
                Resource.Failure(DeliveryError.DeliveryFailed(DeliveryFailure.NoConnectivity))
            },
        )
        vm.onInstall()
        val error = vm.state.value.installError
        assertNotNull(error)
        assertTrue(error is DeliveryError.DeliveryFailed && error.failure is DeliveryFailure.NoConnectivity)
    }

    @Test
    fun `T050 cancel returns the state to not-installed`() = runTest {
        val availability = MutableStateFlow<ContentAvailability>(
            ContentAvailability.Downloading(
                com.giraffe.matn.domain.model.DeliveryProgress(
                    bytesTransferred = 1_000,
                    totalBytes = 2_000,
                    phase = com.giraffe.matn.domain.model.DeliveryPhase.TRANSFERRING,
                ),
            ),
        )
        var cancelInvoked = false
        val vm = newViewModel(
            matnDetails = MatnDetails(simpleMatn, emptyList(), false),
            verses = simpleVerses,
            availabilityFlow = availability,
            cancelInstall = FakeUseCase { cancelInvoked = true; Resource.Success(Unit) },
        )
        vm.onCancelInstall()
        assertTrue(cancelInvoked)
        // The use case's own effect (flipping the engine's state) is exercised in
        // CancelInstallUseCaseTest / ContentPackRepositoryTest; here we simulate its
        // observable consequence to prove the ViewModel's collector reflects it.
        availability.value = ContentAvailability.NotDownloaded()
        assertEquals(ContentAvailability.NotDownloaded(), vm.state.value.availability)
    }

    @Test
    fun `T058 removal does not fire without confirmation`() = runTest {
        var removeInvoked = false
        val vm = newViewModel(
            matnDetails = MatnDetails(simpleMatn, emptyList(), false),
            verses = simpleVerses,
            availabilityFlow = flowOf(ContentAvailability.Downloaded(2_400_000)),
            removeMatnContent = FakeUseCase {
                removeInvoked = true
                Resource.Success(RemovalOutcome.Reclaimed(2_400_000))
            },
        )
        vm.onRemoveRequested()
        assertTrue(vm.state.value.pendingRemovalConfirmation)
        assertTrue(!removeInvoked, "removal must not fire before confirmation")

        vm.onConfirmRemoval()
        assertTrue(removeInvoked)
        assertTrue(!vm.state.value.pendingRemovalConfirmation)
        assertEquals(RemovalOutcome.Reclaimed(2_400_000), vm.state.value.lastRemovalOutcome)
    }

    @Test
    fun `T058 dismissing the confirmation never removes`() = runTest {
        var removeInvoked = false
        val vm = newViewModel(
            matnDetails = MatnDetails(simpleMatn, emptyList(), false),
            verses = simpleVerses,
            availabilityFlow = flowOf(ContentAvailability.Downloaded(2_400_000)),
            removeMatnContent = FakeUseCase {
                removeInvoked = true
                Resource.Success(RemovalOutcome.Reclaimed(2_400_000))
            },
        )
        vm.onRemoveRequested()
        vm.onDismissRemoval()
        assertTrue(!vm.state.value.pendingRemovalConfirmation)
        assertTrue(!removeInvoked)
    }

    @Test
    fun `T050 a mid-flight availability change reaches the state object`() = runTest {
        val availability = MutableStateFlow<ContentAvailability>(ContentAvailability.NotDownloaded())
        val vm = newViewModel(
            matnDetails = MatnDetails(simpleMatn, emptyList(), false),
            verses = simpleVerses,
            availabilityFlow = availability,
        )
        assertEquals(ContentAvailability.NotDownloaded(), vm.state.value.availability)
        availability.value = ContentAvailability.Downloaded(2_400_000)
        assertEquals(ContentAvailability.Downloaded(2_400_000), vm.state.value.availability)
    }
}

/** Builds a real `PlaybackController` sitting idle (IDLE state) so the test only reads state. */
private fun idlePlaybackController(): PlaybackController {
    val buildQueue = object : com.giraffe.matn.domain.usecase.BuildPlaybackQueueUseCase(
        verseRepository = object : com.giraffe.matn.domain.repository.VerseRepository {
            override fun observeVerses(matnId: String): Flow<List<Verse>> = flowOf(emptyList())
            override suspend fun getVersesByChapter(chapterId: String): Resource<List<Verse>> =
                Resource.Success(emptyList())
            override suspend fun getVerse(id: String): Resource<Verse?> = Resource.Success(null)
        },
        audioRepository = object : com.giraffe.matn.domain.repository.AudioAssetRepository {
            override suspend fun getAudioForVerse(
                verseId: String,
                reciterId: String,
            ): Resource<com.giraffe.matn.domain.model.AudioAsset?> = Resource.Success(null)
            override suspend fun getAudioForMatn(
                matnId: String,
                reciterId: String,
            ): Resource<List<com.giraffe.matn.domain.model.AudioAsset>> = Resource.Success(emptyList())
        },
        audioSourceResolver = object : com.giraffe.matn.domain.audio.AudioSourceResolver {
            override suspend fun resolve(matnId: String, fileRef: String): String = ""
        },
    ) {}
    return PlaybackController(
        engine = FakeAudioEngine(),
        buildQueue = buildQueue,
        wakeLock = FakeWakeLock(),
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
    )
}

private class FakeUseCase<P, R>(private val block: suspend (P) -> Resource<R>) : UseCase<P, R> {
    override suspend fun invoke(params: P): Resource<R> = block(params)
}

private class FakeFlowUseCase<P, R>(private val block: (P) -> Flow<R>) : FlowUseCase<P, R> {
    override fun invoke(params: P): Flow<R> = block(params)
}