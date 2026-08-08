package com.giraffe.matn.presentation

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.Chapter
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.MatnDetails
import com.giraffe.matn.domain.model.Note
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.domain.model.Verse
import com.giraffe.matn.domain.model.VerseAnnotations
import com.giraffe.matn.domain.usecase.SaveNoteParams
import com.giraffe.matn.domain.usecase.ToggleVerseMemorizedUseCase
import com.giraffe.matn.playback.PlaybackController
import com.giraffe.matn.presentation.reader.ReaderViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The reader route's ViewModel. Most of these behaviours used to live in `MatnDetailsViewModelTest`
 * against a screen that was both surfaces at once; what is new here is the route's `?v=` handling
 * and the precedence between it and a live session.
 */
class ReaderViewModelTest {

    @BeforeTest
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    private val matn = Matn("m1", "الأجرومية", "ابن آجُرُّوم", "", null, StructureKind.STRUCTURED)

    private val verses = listOf(
        Verse("v1", "m1", "c1", 1, "الكَلامُ هُوَ اللَّفْظُ المُرَكَّبُ المُفِيدُ بِالوَضْعِ", 7_000),
        Verse("v2", "m1", "c1", 2, "وَأَقْسَامُهُ ثَلاثَةٌ: اسْمٌ، وَفِعْلٌ، وَحَرْفٌ", 9_000),
        Verse("v3", "m1", "c2", 3, "فَالاسْمُ يُعْرَفُ بِالخَفْضِ وَالتَّنْوِينِ", 11_000),
    )

    private val chapters = listOf(
        Chapter("c1", "m1", "باب الكلام", 1),
        Chapter("c2", "m1", "باب الإعراب", 2),
    )

    private fun newViewModel(
        requestedVerseId: String? = null,
        autoplay: Boolean = false,
        details: MatnDetails = MatnDetails(matn, chapters, true),
        detailsResult: Resource<MatnDetails> = Resource.Success(details),
        verseList: List<Verse> = verses,
        fontFlow: Flow<ReadingFontSize> = flowOf(ReadingFontSize.MEDIUM),
        setFont: UseCase<ReadingFontSize, Unit> = ReaderFakeUseCase { Resource.Success(Unit) },
        annotationsFlow: Flow<Map<String, VerseAnnotations>> = flowOf(emptyMap()),
        toggleBookmark: UseCase<String, Boolean> = ReaderFakeUseCase { Resource.Success(true) },
        getNote: UseCase<String, Note?> = ReaderFakeUseCase { Resource.Success(null) },
        saveNote: UseCase<SaveNoteParams, Note> = ReaderFakeUseCase { p -> Resource.Success(Note("n", p.verseId, p.text, 0L)) },
        deleteNote: UseCase<String, Unit> = ReaderFakeUseCase { Resource.Success(Unit) },
        memorizationFlow: Flow<Set<String>>? = null,
        toggleVerseMemorized: UseCase<ToggleVerseMemorizedUseCase.Params, Unit>? = null,
    ) = ReaderViewModel(
        matnId = "m1",
        requestedVerseId = requestedVerseId,
        autoplay = autoplay,
        getMatnDetails = ReaderFakeUseCase { detailsResult },
        observeVerses = ReaderFakeFlowUseCase<String, List<Verse>> { MutableStateFlow(verseList) },
        getFontSize = ReaderFakeFlowUseCase { fontFlow },
        setFontSize = setFont,
        playbackController = idleController(),
        observeVerseAnnotations = ReaderFakeFlowUseCase { annotationsFlow },
        toggleBookmark = toggleBookmark,
        getNote = getNote,
        saveNote = saveNote,
        deleteNote = deleteNote,
        observeVerseMemorization = memorizationFlow?.let { f -> ReaderFakeFlowUseCase { f } },
        toggleVerseMemorized = toggleVerseMemorized,
    )

    // ------------------------------------------------------------------------ Route argument

    @Test
    fun `requested verse from the route becomes the focus`() = runTest {
        val vm = newViewModel(requestedVerseId = "v2")

        assertEquals("v2", vm.state.value.requestedVerseId)
        assertEquals("v2", vm.state.value.focusedVerseId)
    }

    /** A stale deep link must not focus a verse that is no longer in the matn. */
    @Test
    fun `a requested verse that is not in the matn is ignored`() = runTest {
        val vm = newViewModel(requestedVerseId = "gone")

        assertNull(vm.state.value.requestedVerseId)
        assertNull(vm.state.value.focusedVerseId)
    }

    @Test
    fun `a blank route argument is treated as absent`() = runTest {
        val vm = newViewModel(requestedVerseId = "")

        assertNull(vm.state.value.requestedVerseId)
    }

    /** The route only decides where an *idle* reader opens; a live session always wins. */
    @Test
    fun `an active verse takes precedence over the requested one`() = runTest {
        val vm = newViewModel(requestedVerseId = "v1")
        assertEquals("v1", vm.state.value.focusedVerseId)

        vm.state.value.let { assertEquals("v1", it.focusedVerseId) }
        // Simulating the controller emission the reader folds in: once a session is live the
        // carousel follows it, not the argument the route was opened with.
        val withSession = vm.state.value.copy(activeVerseId = "v3")
        assertEquals("v3", withSession.focusedVerseId)
        assertEquals("v1", withSession.requestedVerseId)
    }

    // ---------------------------------------------------------------------------- Top bar

    @Test
    fun `chapter title follows the focused verse`() = runTest {
        val vm = newViewModel(requestedVerseId = "v3")

        assertEquals("الأجرومية", vm.state.value.matnTitle)
        assertEquals("باب الإعراب", vm.state.value.chapterTitle)
    }

    /** A SIMPLE matn has no chapters, so the top bar renders one line rather than an empty second. */
    @Test
    fun `a simple matn reports no chapter title`() = runTest {
        val vm = newViewModel(
            requestedVerseId = "v1",
            details = MatnDetails(matn.copy(structureKind = StructureKind.SIMPLE), emptyList(), false),
        )

        assertNull(vm.state.value.chapterTitle)
    }

    // ------------------------------------------------------------------------ Verse content

    @Test
    fun `verses arrive in the order the repository emits, verbatim`() = runTest {
        val vm = newViewModel()

        assertEquals(listOf("v1", "v2", "v3"), vm.state.value.verses.map { it.id })
        assertEquals(verses[0].arabicText, vm.state.value.verses[0].arabicText)
    }

    @Test
    fun `a failed details load surfaces the error and stops loading`() = runTest {
        val vm = newViewModel(detailsResult = Resource.Failure(AppError.NotFound))

        assertEquals(AppError.NotFound, vm.state.value.error)
        assertEquals(false, vm.state.value.isLoading)
    }

    @Test
    fun `font size flows in live and onFontSizeChanged persists it`() = runTest {
        val sizes = MutableStateFlow(ReadingFontSize.MEDIUM)
        var persisted: ReadingFontSize? = null
        val vm = newViewModel(
            fontFlow = sizes,
            setFont = ReaderFakeUseCase { size -> persisted = size; Resource.Success(Unit) },
        )

        assertEquals(ReadingFontSize.MEDIUM, vm.state.value.fontSize)
        sizes.value = ReadingFontSize.XLARGE
        assertEquals(ReadingFontSize.XLARGE, vm.state.value.fontSize)

        vm.onFontSizeChanged(ReadingFontSize.SMALL)
        assertEquals(ReadingFontSize.SMALL, persisted)
    }

    // -------------------------------------------------------------------------- Annotations

    @Test
    fun `annotations flow into state`() = runTest {
        val vm = newViewModel(
            annotationsFlow = flowOf(mapOf("v2" to VerseAnnotations("v2", isBookmarked = true, hasNote = false))),
        )

        assertEquals(true, vm.state.value.annotations["v2"]?.isBookmarked)
    }

    @Test
    fun `onToggleBookmark invokes the use case with the verse id`() = runTest {
        var toggled: String? = null
        val vm = newViewModel(toggleBookmark = ReaderFakeUseCase { id -> toggled = id; Resource.Success(true) })

        vm.onToggleBookmark("v2")

        assertEquals("v2", toggled)
    }

    @Test
    fun `onToggleMemorized targets the inverse of current membership`() = runTest {
        val calls = mutableListOf<ToggleVerseMemorizedUseCase.Params>()
        val vm = newViewModel(
            memorizationFlow = flowOf(setOf("v1")),
            toggleVerseMemorized = ReaderFakeUseCase { p -> calls += p; Resource.Success(Unit) },
        )

        vm.onToggleMemorized("v1")
        vm.onToggleMemorized("v2")

        assertEquals(
            listOf(
                ToggleVerseMemorizedUseCase.Params("v1", memorized = false),
                ToggleVerseMemorizedUseCase.Params("v2", memorized = true),
            ),
            calls,
        )
    }

    // ------------------------------------------------------------------------- Note editor

    @Test
    fun `onOpenNoteEditor prefills from the existing note`() = runTest {
        val vm = newViewModel(getNote = ReaderFakeUseCase { Resource.Success(Note("n1", "v2", "ملاحظة", 0L)) })

        vm.onOpenNoteEditor("v2")

        val editor = vm.state.value.noteEditor
        assertEquals("v2", editor?.verseId)
        assertEquals("ملاحظة", editor?.initialText)
        // The ref carries enough context for the sheet to name the verse without a second lookup.
        assertEquals(2, editor?.verseRef?.verseNumber)
        assertEquals("الأجرومية", editor?.verseRef?.matnTitle)
    }

    @Test
    fun `onOpenNoteEditor for an unknown verse opens nothing`() = runTest {
        val vm = newViewModel()

        vm.onOpenNoteEditor("gone")

        assertNull(vm.state.value.noteEditor)
    }

    @Test
    fun `onSaveNote persists and closes the editor`() = runTest {
        var saved: SaveNoteParams? = null
        val vm = newViewModel(saveNote = ReaderFakeUseCase { p -> saved = p; Resource.Success(Note("n", p.verseId, p.text, 0L)) })

        vm.onOpenNoteEditor("v2")
        vm.onSaveNote("نص")

        assertEquals(SaveNoteParams("v2", "نص"), saved)
        assertNull(vm.state.value.noteEditor)
    }

    /** A rejected save keeps the sheet open so the draft is not lost (FR-019). */
    @Test
    fun `a failed save surfaces an error and keeps the sheet open`() = runTest {
        val vm = newViewModel(saveNote = ReaderFakeUseCase { Resource.Failure(AppError.Storage("boom")) })

        vm.onOpenNoteEditor("v2")
        vm.onSaveNote("")

        assertTrue(vm.state.value.noteEditor?.saveError == true)
    }

    @Test
    fun `onDeleteNote clears the editor`() = runTest {
        var deleted: String? = null
        val vm = newViewModel(deleteNote = ReaderFakeUseCase { id -> deleted = id; Resource.Success(Unit) })

        vm.onOpenNoteEditor("v2")
        vm.onDeleteNote()

        assertEquals("v2", deleted)
        assertNull(vm.state.value.noteEditor)
    }

    @Test
    fun `onDismissNoteEditor discards without persisting`() = runTest {
        var saved = false
        val vm = newViewModel(saveNote = ReaderFakeUseCase { p -> saved = true; Resource.Success(Note("n", p.verseId, p.text, 0L)) })

        vm.onOpenNoteEditor("v2")
        vm.onDismissNoteEditor()

        assertNull(vm.state.value.noteEditor)
        assertEquals(false, saved)
    }
}

// A controller with nothing to play: enough to satisfy the constructor and to leave the reader
// idle, which is what every assertion above depends on. Autoplay itself needs a controller with
// real queue data behind it and is covered by PlaybackControllerTest.
private fun idleController(): PlaybackController {
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
        engine = com.giraffe.matn.playback.FakeAudioEngine(),
        buildQueue = buildQueue,
        wakeLock = com.giraffe.matn.playback.FakeWakeLock(),
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
    )
}

private class ReaderFakeUseCase<P, R>(private val block: suspend (P) -> Resource<R>) : UseCase<P, R> {
    override suspend fun invoke(params: P): Resource<R> = block(params)
}

private class ReaderFakeFlowUseCase<P, R>(private val block: (P) -> Flow<R>) : FlowUseCase<P, R> {
    override fun invoke(params: P): Flow<R> = block(params)
}
