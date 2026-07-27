package com.giraffe.matn.teacher

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.catalog.CatalogEntry
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.MatnDraftFactory
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.usecase.LoadMatnForEditUseCase
import com.giraffe.matn.domain.usecase.PublishMatnUseCase
import com.giraffe.matn.domain.usecase.SaveDraftUseCase
import com.giraffe.matn.domain.usecase.UploadCoverImageUseCase
import com.giraffe.matn.domain.usecase.ValidateMatnUseCase
import com.giraffe.matn.teacher.presentation.editor.EditorViewModel
import com.giraffe.matn.teacher.presentation.editor.SaveState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeCatalogRepository(
    private val saveResult: (MatnDraft) -> Resource<MatnDraft>,
    private val loadResult: (String) -> Resource<MatnDraft> = { Resource.Failure(AppError.NotFound) },
) : CatalogRepository {
    var saveCallCount = 0

    override fun observeAuthored(): Flow<List<CatalogEntry>> = flowOf(emptyList())
    override suspend fun load(matnId: String): Resource<MatnDraft> = loadResult(matnId)
    override suspend fun save(draft: MatnDraft): Resource<MatnDraft> {
        saveCallCount++
        return saveResult(draft)
    }
    override suspend fun publish(draft: MatnDraft): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun unpublish(matnId: String): Resource<MatnDraft> = Resource.Failure(AppError.NotFound)
    override suspend fun uploadCover(matnId: String, bytes: ByteArray, ext: String): Resource<String> = Resource.Success("ref")
}

private fun newDraft() = MatnDraftFactory.newDraft(
    newId = { "matn-id" },
    nowMillis = { 0L },
    title = "Title",
    author = "Author",
)

class EditorViewModelTest {

    @BeforeTest
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    private fun newViewModel(repo: CatalogRepository, draft: MatnDraft = newDraft()): EditorViewModel {
        var counter = 0
        return EditorViewModel(
            initialDraft = draft,
            saveDraft = SaveDraftUseCase(repo),
            uploadCoverImage = UploadCoverImageUseCase(repo),
            validateMatn = ValidateMatnUseCase(),
            publishMatn = PublishMatnUseCase(repo),
            loadMatnForEdit = LoadMatnForEditUseCase(repo),
            newId = { "gen-id-${counter++}" },
            nowMillis = { 0L },
        )
    }

    @Test
    fun `a failed save preserves on-screen state and reports the error`() = runTest {
        val repo = FakeCatalogRepository(saveResult = { Resource.Failure(RemoteError.Network) })
        val vm = newViewModel(repo)
        vm.onTitleChange("Changed Title")

        vm.onSaveDraft()

        assertEquals("Changed Title", vm.state.value.draft.title)
        assertEquals(SaveState.Failed(RemoteError.Network), vm.state.value.saveState)
        assertTrue(RemoteError.Network.retryable)
    }

    @Test
    fun `a successful save updates remoteUpdateTime`() = runTest {
        val repo = FakeCatalogRepository(saveResult = { draft -> Resource.Success(draft.copy(remoteUpdateTime = "new-token")) })
        val vm = newViewModel(repo)

        vm.onSaveDraft()

        assertEquals("new-token", vm.state.value.draft.remoteUpdateTime)
        assertTrue(vm.state.value.saveState is SaveState.Saved)
    }

    @Test
    fun `saving with a missing title is refused with the field flagged and no repository call`() = runTest {
        val repo = FakeCatalogRepository(saveResult = { Resource.Success(it) })
        val vm = newViewModel(repo, draft = newDraft().copy(title = ""))

        vm.onSaveDraft()

        assertTrue(vm.state.value.missingTitle)
        assertEquals(0, repo.saveCallCount)
    }

    @Test
    fun `saving with a missing author is refused with the field flagged and no repository call`() = runTest {
        val repo = FakeCatalogRepository(saveResult = { Resource.Success(it) })
        val vm = newViewModel(repo, draft = newDraft().copy(author = ""))

        vm.onSaveDraft()

        assertTrue(vm.state.value.missingAuthor)
        assertEquals(0, repo.saveCallCount)
    }

    @Test
    fun `adding a verse appends it with the next display number`() = runTest {
        val vm = newViewModel(FakeCatalogRepository(saveResult = { Resource.Success(it) }))

        vm.onAddVerse()
        vm.onAddVerse()

        val verses = vm.state.value.draft.verses
        assertEquals(listOf(1, 2), verses.map { it.displayNumber })
    }

    @Test
    fun `editing a verse's text updates only that verse`() = runTest {
        val vm = newViewModel(FakeCatalogRepository(saveResult = { Resource.Success(it) }))
        vm.onAddVerse()
        vm.onAddVerse()
        val secondId = vm.state.value.draft.verses[1].id

        vm.onVerseTextChange(secondId, "نص جديد")

        val verses = vm.state.value.draft.verses
        assertEquals("", verses[0].arabicText)
        assertEquals("نص جديد", verses[1].arabicText)
    }

    @Test
    fun `reordering verses renumbers 1 through n`() = runTest {
        val vm = newViewModel(FakeCatalogRepository(saveResult = { Resource.Success(it) }))
        vm.onAddVerse()
        vm.onAddVerse()
        vm.onAddVerse()

        vm.onMoveVerse(0, 2)

        val verses = vm.state.value.draft.verses
        assertEquals(listOf(1, 2, 3), verses.map { it.displayNumber })
    }

    @Test
    fun `deleting a verse leaves no numbering gap`() = runTest {
        val vm = newViewModel(FakeCatalogRepository(saveResult = { Resource.Success(it) }))
        vm.onAddVerse()
        vm.onAddVerse()
        vm.onAddVerse()
        val secondId = vm.state.value.draft.verses[1].id

        vm.onDeleteVerse(secondId)

        val verses = vm.state.value.draft.verses
        assertEquals(2, verses.size)
        assertEquals(listOf(1, 2), verses.map { it.displayNumber })
    }

    @Test
    fun `checking for problems populates the validation report`() = runTest {
        val draft = newDraft().copy(
            verses = listOf(com.giraffe.matn.domain.catalog.DraftVerse("v1", null, 1, "text", null, 0L)),
        )
        val vm = newViewModel(FakeCatalogRepository(saveResult = { Resource.Success(it) }), draft = draft)

        vm.onCheckForProblems()

        assertTrue(vm.state.value.validation?.canPublish == true)
    }

    @Test
    fun `requesting publish shows the confirm dialog, and confirming an invalid draft surfaces blocking problems`() = runTest {
        val vm = newViewModel(
            FakeCatalogRepository(saveResult = { Resource.Success(it) }),
            draft = newDraft().copy(verses = emptyList()),
        )

        vm.onRequestPublish()
        assertTrue(vm.state.value.showPublishConfirm)

        vm.onConfirmPublish()

        assertTrue(vm.state.value.validation?.canPublish == false)
    }

    @Test
    fun `confirming publish on a valid draft flips publicationState to PUBLISHED`() = runTest {
        val draft = newDraft().copy(
            verses = listOf(com.giraffe.matn.domain.catalog.DraftVerse("v1", null, 1, "text", null, 0L)),
        )
        val vm = newViewModel(FakeCatalogRepository(saveResult = { Resource.Success(it) }), draft = draft)

        vm.onRequestPublish()
        vm.onConfirmPublish()

        assertEquals(com.giraffe.matn.domain.catalog.PublicationState.PUBLISHED, vm.state.value.draft.publicationState)
        assertTrue(vm.state.value.saveState is SaveState.Saved)
    }

    @Test
    fun `saving a published matn down to zero verses is refused with EmptyMatn and no repository call`() = runTest {
        val repo = FakeCatalogRepository(saveResult = { Resource.Success(it) })
        val draft = newDraft().copy(
            publicationState = com.giraffe.matn.domain.catalog.PublicationState.PUBLISHED,
            verses = emptyList(),
        )
        val vm = newViewModel(repo, draft = draft)

        vm.onSaveDraft()

        assertTrue(vm.state.value.validation?.blocking?.any { it is com.giraffe.matn.domain.error.ContentIntegrityError.EmptyMatn } == true)
        assertEquals(0, repo.saveCallCount)
    }

    @Test
    fun `reloading after a conflict replaces the draft with the server version and clears the failure`() = runTest {
        val serverDraft = newDraft().copy(title = "Server Title", remoteUpdateTime = "server-token")
        val repo = FakeCatalogRepository(
            saveResult = { Resource.Failure(RemoteError.Conflict) },
            loadResult = { Resource.Success(serverDraft) },
        )
        val vm = newViewModel(repo)
        vm.onSaveDraft()
        assertEquals(SaveState.Failed(RemoteError.Conflict), vm.state.value.saveState)

        vm.onReloadAfterConflict()

        assertEquals("Server Title", vm.state.value.draft.title)
        assertEquals(SaveState.Idle, vm.state.value.saveState)
    }

    @Test
    fun `assigning a nonexistent chapter id is rejected`() = runTest {
        val vm = newViewModel(FakeCatalogRepository(saveResult = { Resource.Success(it) }))
        vm.onAddVerse()
        val verseId = vm.state.value.draft.verses[0].id

        vm.onAssignChapter(verseId, "no-such-chapter")

        assertEquals(null, vm.state.value.draft.verses[0].chapterId)
    }

    @Test
    fun `a valid import stages a preview without touching the draft`() = runTest {
        val vm = newViewModel(FakeCatalogRepository(saveResult = { Resource.Success(it) }))

        vm.onImportRequested("first verse\nsecond verse".encodeToByteArray())

        assertEquals(listOf("first verse", "second verse"), vm.state.value.importPreview?.lines)
        assertTrue(vm.state.value.draft.verses.isEmpty())
    }

    @Test
    fun `confirming an import appends the previewed lines as verses and clears the preview`() = runTest {
        val vm = newViewModel(FakeCatalogRepository(saveResult = { Resource.Success(it) }))
        vm.onAddVerse()
        vm.onImportRequested("second verse\nthird verse".encodeToByteArray())

        vm.onImportConfirm()

        val verses = vm.state.value.draft.verses
        assertEquals(null, vm.state.value.importPreview)
        assertEquals(listOf("", "second verse", "third verse"), verses.map { it.arabicText })
        assertEquals(listOf(1, 2, 3), verses.map { it.displayNumber })
    }

    @Test
    fun `cancelling an import leaves the verse list untouched`() = runTest {
        val vm = newViewModel(FakeCatalogRepository(saveResult = { Resource.Success(it) }))
        vm.onAddVerse()
        vm.onImportRequested("second verse".encodeToByteArray())

        vm.onImportCancel()

        assertEquals(null, vm.state.value.importPreview)
        assertEquals(1, vm.state.value.draft.verses.size)
    }

    @Test
    fun `an invalid-encoding import surfaces an error without staging a preview`() = runTest {
        val vm = newViewModel(FakeCatalogRepository(saveResult = { Resource.Success(it) }))

        vm.onImportRequested(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00))

        assertTrue(vm.state.value.importError)
        assertEquals(null, vm.state.value.importPreview)
    }

    /** T101 (`quickstart.md` §5, FR-025/SC-009): a proxy for the manual frame-timing pass this
     * environment cannot run (no display). Confirms the data-layer operations behind "import 500
     * lines, drag 400→5" stay correct and cheap at scale — `VerseRow` holding no text state of its
     * own (verified by inspection) is what keeps the actual UI recomposition scoped to one row. */
    @Test
    fun `importing 500 lines then moving verse 400 to position 5 stays correct and fast`() = runTest {
        val vm = newViewModel(FakeCatalogRepository(saveResult = { Resource.Success(it) }))
        val fiveHundredLines = (1..500).joinToString("\n") { "verse text $it" }

        val importElapsed = kotlin.time.measureTime { vm.onImportRequested(fiveHundredLines.encodeToByteArray()) }
        vm.onImportConfirm()
        val moveElapsed = kotlin.time.measureTime { vm.onMoveVerse(399, 4) }

        val verses = vm.state.value.draft.verses
        assertEquals(500, verses.size)
        assertEquals((1..500).toList(), verses.map { it.displayNumber })
        assertEquals("verse text 400", verses[4].arabicText)
        assertTrue(importElapsed.inWholeMilliseconds < 1000, "parsing+staging 500 lines took $importElapsed")
        assertTrue(moveElapsed.inWholeMilliseconds < 1000, "reordering 500 verses took $moveElapsed")
    }
}
