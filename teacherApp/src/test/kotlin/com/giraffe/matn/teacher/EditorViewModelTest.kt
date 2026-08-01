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
import com.giraffe.matn.teacher.presentation.editor.CoverError
import com.giraffe.matn.teacher.presentation.editor.EditorViewModel
import com.giraffe.matn.teacher.presentation.editor.SaveState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeCatalogRepository(
    private val saveResult: (MatnDraft) -> Resource<MatnDraft>,
    private val loadResult: (String) -> Resource<MatnDraft> = { Resource.Failure(AppError.NotFound) },
    private val attachVerseAudioResult: (suspend () -> Resource<Unit>)? = null,
    private val removeVerseAudioResult: (suspend () -> Resource<Unit>)? = null,
    private val uploadCoverResult: (() -> Resource<String>)? = null,
    private val downloadCoverResult: (() -> Resource<ByteArray>)? = null,
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
    override suspend fun uploadCover(matnId: String, bytes: ByteArray, ext: String): Resource<String> =
        uploadCoverResult?.invoke() ?: Resource.Success("matns/$matnId/cover.$ext")
    override suspend fun downloadCover(objectPath: String): Resource<ByteArray> =
        downloadCoverResult?.invoke() ?: Resource.Success(ByteArray(0))
    override suspend fun attachVerseAudio(draft: MatnDraft, verseId: String, audio: com.giraffe.matn.domain.catalog.DraftAudio, bytes: ByteArray): Resource<MatnDraft> {
        val gate = attachVerseAudioResult?.invoke() ?: Resource.Success(Unit)
        return when (gate) {
            is Resource.Failure -> Resource.Failure(gate.error)
            is Resource.Success -> Resource.Success(
                draft.copy(verses = draft.verses.map { if (it.id == verseId) it.copy(audio = audio, durationMs = audio.durationMs) else it }),
            )
        }
    }
    override suspend fun removeVerseAudio(draft: MatnDraft, verseId: String): Resource<MatnDraft> {
        val gate = removeVerseAudioResult?.invoke() ?: Resource.Success(Unit)
        return when (gate) {
            is Resource.Failure -> Resource.Failure(gate.error)
            is Resource.Success -> Resource.Success(
                draft.copy(verses = draft.verses.map { if (it.id == verseId) it.copy(audio = null, durationMs = 0L) else it }),
            )
        }
    }
    override suspend fun applySplit(draft: MatnDraft, updates: Map<String, com.giraffe.matn.domain.catalog.DraftAudio>, payloads: List<com.giraffe.matn.domain.audio.PendingUpload>): Resource<MatnDraft> = Resource.Success(draft)
}

private class FakeAudioProbe : com.giraffe.matn.domain.audio.AudioProbe {
    override suspend fun probe(source: com.giraffe.matn.data.audio.ByteSource): Resource<com.giraffe.matn.domain.audio.ProbeResult> =
        Resource.Success(com.giraffe.matn.domain.audio.ProbeResult(durationMs = 1000, profile = com.giraffe.matn.domain.audio.AudioProfile(44100, 1), frameCount = 1))
    override suspend fun peaks(source: com.giraffe.matn.data.audio.ByteSource, buckets: Int): Resource<FloatArray> = Resource.Success(FloatArray(buckets))
}

private class FakePreviewPlayer : com.giraffe.matn.domain.audio.PreviewPlayer {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow<com.giraffe.matn.domain.audio.PreviewState>(com.giraffe.matn.domain.audio.PreviewState.Idle)
    override val state: kotlinx.coroutines.flow.StateFlow<com.giraffe.matn.domain.audio.PreviewState> = _state
    override suspend fun play(verses: List<com.giraffe.matn.domain.audio.PreviewVerse>, startIndex: Int) {
        _state.value = com.giraffe.matn.domain.audio.PreviewState.Idle
    }
    override suspend fun playClip(bytes: ByteArray, displayNumber: Int) {
        _state.value = com.giraffe.matn.domain.audio.PreviewState.Idle
    }
    override fun pause() = Unit
    override fun resume() = Unit
    override fun stop() { _state.value = com.giraffe.matn.domain.audio.PreviewState.Idle }
}

private fun newDraft() = MatnDraftFactory.newDraft(
    newId = { "matn-id" },
    nowMillis = { 0L },
    title = "Title",
    author = "Author",
)

private const val AUTOSAVE_SETTLE_MS = 6_000L

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
            loadCoverImage = com.giraffe.matn.domain.usecase.LoadCoverImageUseCase(repo),
            validateMatn = ValidateMatnUseCase(),
            publishMatn = PublishMatnUseCase(repo),
            loadMatnForEdit = LoadMatnForEditUseCase(repo),
            attachVerseAudio = com.giraffe.matn.domain.usecase.AttachVerseAudioUseCase(repo, FakeAudioProbe()),
            removeVerseAudio = com.giraffe.matn.domain.usecase.RemoveVerseAudioUseCase(repo),
            previewPlayer = FakePreviewPlayer(),
            previewMatnAudio = com.giraffe.matn.domain.usecase.PreviewMatnAudioUseCase(FakePreviewPlayer()),
            newId = { "gen-id-${counter++}" },
            nowMillis = { 0L },
        )
    }

    /** The card shows the image, so the bytes must be there the moment they are picked — these are
     * the exact bytes being uploaded, and waiting for the round trip would delay an answer already
     * in hand. */
    @Test
    fun `picking a cover shows it before the upload finishes`() = runTest {
        val repo = FakeCatalogRepository(saveResult = { Resource.Success(it) })
        val vm = newViewModel(repo)
        val picked = byteArrayOf(1, 2, 3)

        vm.onCoverPicked(picked, "png")

        assertTrue(picked.contentEquals(vm.state.value.coverPreview))
    }

    /** …but never a cover that is not stored: a failed upload has to take the preview with it, or
     * the card claims a cover the matn does not have. */
    @Test
    fun `a failed cover upload clears the preview`() = runTest {
        val repo = FakeCatalogRepository(
            saveResult = { Resource.Success(it) },
            uploadCoverResult = { Resource.Failure(RemoteError.Network) },
        )
        val vm = newViewModel(repo)

        vm.onCoverPicked(byteArrayOf(1, 2, 3), "png")

        assertEquals(null, vm.state.value.coverPreview)
        assertEquals(CoverError.UPLOAD_FAILED, vm.state.value.coverError)
    }

    /** Reopening a matn has no local bytes, so the stored cover has to be fetched — the bucket is
     * private and there is no URL to point an image at. */
    @Test
    fun `an existing cover is fetched when the editor opens`() = runTest {
        val stored = byteArrayOf(9, 8, 7)
        val repo = FakeCatalogRepository(saveResult = { Resource.Success(it) }, downloadCoverResult = { Resource.Success(stored) })

        val vm = newViewModel(repo, draft = newDraft().copy(coverImageRef = "matns/m1/cover.png"))

        assertTrue(stored.contentEquals(vm.state.value.coverPreview))
    }

    /** A thumbnail that could not be fetched is not something the teacher can act on, so the card
     * degrades to its hint rather than reporting an error. */
    @Test
    fun `a failed cover fetch leaves the card empty and silent`() = runTest {
        val repo = FakeCatalogRepository(
            saveResult = { Resource.Success(it) },
            downloadCoverResult = { Resource.Failure(RemoteError.Network) },
        )

        val vm = newViewModel(repo, draft = newDraft().copy(coverImageRef = "matns/m1/cover.png"))

        assertEquals(null, vm.state.value.coverPreview)
        assertEquals(null, vm.state.value.coverError)
    }

    /** Pressing Save as draft or Publish means "done with this matn"; the portal watches this to
     * hand back a blank editor for the next one. */
    @Test
    fun `an explicit save reports the matn as finished`() = runTest {
        val repo = FakeCatalogRepository(saveResult = { Resource.Success(it) })
        val vm = newViewModel(repo)

        vm.onSaveDraft()

        assertTrue(vm.state.value.finished)
    }

    @Test
    fun `publishing reports the matn as finished`() = runTest {
        val audio = com.giraffe.matn.domain.catalog.DraftAudio("a1", "matns/m1/verses/v1-tag.mp3", 1000, 10, 44100, 1)
        val draft = newDraft().copy(
            verses = listOf(com.giraffe.matn.domain.catalog.DraftVerse("v1", null, 1, "text", audio, 1000L)),
        )
        val vm = newViewModel(FakeCatalogRepository(saveResult = { Resource.Success(it) }), draft = draft)

        vm.onConfirmPublish()

        assertTrue(vm.state.value.finished)
    }

    /** A publish the validator refuses leaves the teacher on the matn with the problem panel — the
     * screen may only clear once the work is actually stored. */
    @Test
    fun `a refused publish does not report the matn as finished`() = runTest {
        val vm = newViewModel(
            FakeCatalogRepository(saveResult = { Resource.Success(it) }),
            draft = newDraft().copy(verses = emptyList()),
        )

        vm.onConfirmPublish()

        assertFalse(vm.state.value.finished)
    }

    /**
     * Autosave fires while the teacher is mid-sentence. If it reported the matn finished, the
     * screen would clear itself out from under them — indistinguishable from losing the work, which
     * is the single most damaging thing this editor can do.
     */
    @Test
    fun `autosave never reports the matn as finished`() = runTest {
        val repo = FakeCatalogRepository(saveResult = { Resource.Success(it) })
        val vm = newViewModel(repo)

        vm.onTitleChange("Still typing")
        advanceTimeBy(AUTOSAVE_SETTLE_MS)
        runCurrent()

        assertTrue(repo.saveCallCount > 0, "the autosave under test never ran")
        assertFalse(vm.state.value.finished)
    }

    /** A save the server refused leaves the teacher on their work, with the error. Clearing the
     * screen for the next matn when this one was never stored would lose it outright. */
    @Test
    fun `a refused save does not report the matn as finished`() = runTest {
        val repo = FakeCatalogRepository(saveResult = { Resource.Failure(RemoteError.Network) })
        val vm = newViewModel(repo)

        vm.onSaveDraft()

        assertFalse(vm.state.value.finished)
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
    fun `a successful save updates remoteRevision`() = runTest {
        val repo = FakeCatalogRepository(saveResult = { draft -> Resource.Success(draft.copy(remoteRevision = "new-token")) })
        val vm = newViewModel(repo)

        vm.onSaveDraft()

        assertEquals("new-token", vm.state.value.draft.remoteRevision)
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
        val audio = com.giraffe.matn.domain.catalog.DraftAudio("a1", "matns/m1/verses/v1-tag.mp3", 1000, 10, 44100, 1)
        val draft = newDraft().copy(
            verses = listOf(com.giraffe.matn.domain.catalog.DraftVerse("v1", null, 1, "text", audio, 1000L)),
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
        val audio = com.giraffe.matn.domain.catalog.DraftAudio("a1", "matns/m1/verses/v1-tag.mp3", 1000, 10, 44100, 1)
        val draft = newDraft().copy(
            verses = listOf(com.giraffe.matn.domain.catalog.DraftVerse("v1", null, 1, "text", audio, 1000L)),
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
        val serverDraft = newDraft().copy(title = "Server Title", remoteRevision = "server-token")
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

    @Test
    fun `requesting clear-all shows the confirm dialog without touching the verse list`() = runTest {
        val vm = newViewModel(FakeCatalogRepository(saveResult = { Resource.Success(it) }))
        vm.onAddVerse()
        vm.onAddVerse()

        vm.onRequestClearAllVerses()

        assertTrue(vm.state.value.showClearAllConfirm)
        assertEquals(2, vm.state.value.draft.verses.size)
    }

    @Test
    fun `confirming clear-all wipes every verse and dismisses the dialog`() = runTest {
        val vm = newViewModel(FakeCatalogRepository(saveResult = { Resource.Success(it) }))
        vm.onAddVerse()
        vm.onAddVerse()
        vm.onAddVerse()
        vm.onRequestClearAllVerses()

        vm.onConfirmClearAllVerses()

        assertTrue(vm.state.value.draft.verses.isEmpty())
        assertEquals(false, vm.state.value.showClearAllConfirm)
    }

    @Test
    fun `dismissing clear-all leaves every verse untouched`() = runTest {
        val vm = newViewModel(FakeCatalogRepository(saveResult = { Resource.Success(it) }))
        vm.onAddVerse()
        vm.onAddVerse()
        vm.onRequestClearAllVerses()

        vm.onDismissClearAllVerses()

        assertEquals(false, vm.state.value.showClearAllConfirm)
        assertEquals(2, vm.state.value.draft.verses.size)
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

    /** [com.giraffe.matn.teacher.presentation.editor.EditorUiState.audioStateFor] reads the
     * `DraftVerse` passed to it, so — as in production, where the row is always rendered from
     * `state.draft.verses` — a test must re-fetch the verse from current state after each mutation
     * rather than reuse a pre-mutation reference. */
    private fun EditorViewModel.currentVerse(verseId: String) = state.value.draft.verses.first { it.id == verseId }

    @Test
    fun `attaching sets Uploading then Loaded`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Resource<Unit>>()
        val repo = FakeCatalogRepository(saveResult = { Resource.Success(it) }, attachVerseAudioResult = { gate.await() })
        val vm = newViewModel(repo)
        vm.onAddVerse()
        val verseId = vm.state.value.draft.verses.single().id

        vm.onAttachVerseAudio(verseId, "bytes".encodeToByteArray())
        assertEquals(
            com.giraffe.matn.teacher.presentation.common.VerseAudioUiState.Uploading(0L, 5L),
            vm.state.value.audioStateFor(vm.currentVerse(verseId)),
        )

        gate.complete(Resource.Success(Unit))
        val loaded = vm.state.value.audioStateFor(vm.currentVerse(verseId))
        assertTrue(loaded is com.giraffe.matn.teacher.presentation.common.VerseAudioUiState.Loaded)
        assertEquals(1000L, (loaded as com.giraffe.matn.teacher.presentation.common.VerseAudioUiState.Loaded).durationMs)
    }

    @Test
    fun `a failure sets Failed and leaves the verse's previous audio`() = runTest {
        val repo = FakeCatalogRepository(
            saveResult = { Resource.Success(it) },
            attachVerseAudioResult = { Resource.Failure(RemoteError.Server) },
        )
        val vm = newViewModel(repo)
        vm.onAddVerse()
        val verseId = vm.state.value.draft.verses.single().id

        vm.onAttachVerseAudio(verseId, "bytes".encodeToByteArray())

        val state = vm.state.value.audioStateFor(vm.currentVerse(verseId))
        assertTrue(state is com.giraffe.matn.teacher.presentation.common.VerseAudioUiState.Failed)
        assertEquals(null, vm.currentVerse(verseId).audio) // unchanged: still had no audio
    }

    @Test
    fun `removing returns the row to Empty`() = runTest {
        val repo = FakeCatalogRepository(saveResult = { Resource.Success(it) })
        val vm = newViewModel(repo)
        vm.onAddVerse()
        val verseId = vm.state.value.draft.verses.single().id
        vm.onAttachVerseAudio(verseId, "bytes".encodeToByteArray())
        assertTrue(vm.state.value.audioStateFor(vm.currentVerse(verseId)) is com.giraffe.matn.teacher.presentation.common.VerseAudioUiState.Loaded)

        vm.onRemoveVerseAudio(verseId)

        assertEquals(com.giraffe.matn.teacher.presentation.common.VerseAudioUiState.Empty, vm.state.value.audioStateFor(vm.currentVerse(verseId)))
    }

    @Test
    fun `the completeness badge state follows attach and remove`() = runTest {
        val repo = FakeCatalogRepository(saveResult = { Resource.Success(it) })
        val vm = newViewModel(repo)
        vm.onAddVerse()
        vm.onAddVerse()
        val (v1, v2) = vm.state.value.draft.verses

        assertEquals(com.giraffe.matn.domain.catalog.AudioCompleteness.NONE, vm.state.value.draft.audioCompleteness)

        vm.onAttachVerseAudio(v1.id, "bytes".encodeToByteArray())
        assertEquals(com.giraffe.matn.domain.catalog.AudioCompleteness.PARTIAL, vm.state.value.draft.audioCompleteness)

        vm.onAttachVerseAudio(v2.id, "bytes".encodeToByteArray())
        assertEquals(com.giraffe.matn.domain.catalog.AudioCompleteness.COMPLETE, vm.state.value.draft.audioCompleteness)

        vm.onRemoveVerseAudio(v1.id)
        assertEquals(com.giraffe.matn.domain.catalog.AudioCompleteness.PARTIAL, vm.state.value.draft.audioCompleteness)
    }
}
