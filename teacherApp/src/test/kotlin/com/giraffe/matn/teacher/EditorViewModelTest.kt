package com.giraffe.matn.teacher

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.catalog.CatalogEntry
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.MatnDraftFactory
import com.giraffe.matn.domain.error.RemoteError
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
) : CatalogRepository {
    var saveCallCount = 0

    override fun observeAuthored(): Flow<List<CatalogEntry>> = flowOf(emptyList())
    override suspend fun load(matnId: String): Resource<MatnDraft> = Resource.Failure(AppError.NotFound)
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
    fun `assigning a nonexistent chapter id is rejected`() = runTest {
        val vm = newViewModel(FakeCatalogRepository(saveResult = { Resource.Success(it) }))
        vm.onAddVerse()
        val verseId = vm.state.value.draft.verses[0].id

        vm.onAssignChapter(verseId, "no-such-chapter")

        assertEquals(null, vm.state.value.draft.verses[0].chapterId)
    }
}
