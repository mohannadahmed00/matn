package com.giraffe.matn.teacher

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.catalog.AudioCompleteness
import com.giraffe.matn.domain.catalog.CatalogEntry
import com.giraffe.matn.domain.catalog.CatalogLoadException
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.usecase.ListAuthoredMatnsUseCase
import com.giraffe.matn.domain.usecase.UnpublishMatnUseCase
import com.giraffe.matn.teacher.presentation.library.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class LibraryFakeCatalogRepository(
    private val observeResult: Flow<List<CatalogEntry>> = flowOf(emptyList()),
    private val observeCalls: (Int) -> Flow<List<CatalogEntry>> = { observeResult },
    private val unpublishResult: Resource<MatnDraft> = Resource.Failure(AppError.NotFound),
) : CatalogRepository {
    var observeCallCount = 0
    override fun observeAuthored(): Flow<List<CatalogEntry>> = observeCalls(observeCallCount++)
    override suspend fun load(matnId: String): Resource<MatnDraft> = Resource.Failure(AppError.NotFound)
    override suspend fun save(draft: MatnDraft): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun publish(draft: MatnDraft): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun unpublish(matnId: String): Resource<MatnDraft> = unpublishResult
    override suspend fun uploadCover(matnId: String, bytes: ByteArray, ext: String): Resource<String> = Resource.Success("ref")
    override suspend fun downloadCover(objectPath: String): Resource<ByteArray> = Resource.Success(ByteArray(0))
    override suspend fun attachVerseAudio(draft: MatnDraft, verseId: String, audio: com.giraffe.matn.domain.catalog.DraftAudio, bytes: ByteArray): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun removeVerseAudio(draft: MatnDraft, verseId: String): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun applySplit(draft: MatnDraft, updates: Map<String, com.giraffe.matn.domain.catalog.DraftAudio>, payloads: List<com.giraffe.matn.domain.audio.PendingUpload>): Resource<MatnDraft> = Resource.Success(draft)
}

private fun entry(id: String) = CatalogEntry(
    id = id, title = "T", author = "A", description = "", coverImageRef = null,
    verseCount = 1, declaredSizeBytes = 0L, publicationState = PublicationState.DRAFT,
    audioCompleteness = AudioCompleteness.NONE, updatedAt = 0L,
)

class LibraryViewModelTest {

    @BeforeTest
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `loaded state renders the fetched entries`() {
        val repo = LibraryFakeCatalogRepository(flowOf(listOf(entry("m1"), entry("m2"))))
        val vm = LibraryViewModel(ListAuthoredMatnsUseCase(repo), UnpublishMatnUseCase(repo))

        assertEquals(2, vm.state.value.entries.size)
        assertEquals(false, vm.state.value.isLoading)
        assertEquals(null, vm.state.value.error)
    }

    @Test
    fun `empty state renders an empty entries list without error`() {
        val repo = LibraryFakeCatalogRepository(flowOf(emptyList()))
        val vm = LibraryViewModel(ListAuthoredMatnsUseCase(repo), UnpublishMatnUseCase(repo))

        assertTrue(vm.state.value.entries.isEmpty())
        assertEquals(null, vm.state.value.error)
    }

    @Test
    fun `error state renders the mapped RemoteError`() {
        val repo = LibraryFakeCatalogRepository(flow { throw CatalogLoadException(RemoteError.Network) })
        val vm = LibraryViewModel(ListAuthoredMatnsUseCase(repo), UnpublishMatnUseCase(repo))

        assertEquals(RemoteError.Network, vm.state.value.error)
        assertEquals(false, vm.state.value.isLoading)
    }

    /** A slow load kicked off on screen entry must not resolve *after* a same-session unpublish's
     * own reload and clobber the correct post-unpublish state with the stale pre-unpublish one —
     * this is what forced navigating away and back to see an unpublish actually take effect. */
    @Test
    fun `a slow initial load does not overwrite a fresher unpublish-triggered reload`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val staleEntry = entry("m1").copy(publicationState = PublicationState.PUBLISHED)
        val freshEntry = entry("m1").copy(publicationState = PublicationState.DRAFT)
        val repo = LibraryFakeCatalogRepository(
            observeCalls = { callIndex ->
                if (callIndex == 0) flow { delay(10_000); emit(listOf(staleEntry)) } else flowOf(listOf(freshEntry))
            },
        )
        val vm = LibraryViewModel(ListAuthoredMatnsUseCase(repo), UnpublishMatnUseCase(repo))

        vm.onRequestUnpublish("m1")
        vm.onConfirmUnpublish()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PublicationState.DRAFT, vm.state.value.entries.first().publicationState)

        // The stale call's 10s delay finally elapses — its response must not land, since `load()`
        // cancelled it before the fresh reload started.
        dispatcher.scheduler.advanceTimeBy(11_000)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PublicationState.DRAFT, vm.state.value.entries.first().publicationState)
    }
}
