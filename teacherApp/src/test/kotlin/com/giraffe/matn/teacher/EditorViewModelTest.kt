package com.giraffe.matn.teacher

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.catalog.CatalogEntry
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.MatnDraftFactory
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.usecase.SaveDraftUseCase
import com.giraffe.matn.domain.usecase.UploadCoverImageUseCase
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

    private fun newViewModel(repo: CatalogRepository, draft: MatnDraft = newDraft()): EditorViewModel = EditorViewModel(
        initialDraft = draft,
        saveDraft = SaveDraftUseCase(repo),
        uploadCoverImage = UploadCoverImageUseCase(repo),
        newId = { "gen-id" },
        nowMillis = { 0L },
    )

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
}
