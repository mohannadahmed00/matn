package com.giraffe.matn.teacher

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.audio.PendingUpload
import com.giraffe.matn.domain.audio.PreviewPlayer
import com.giraffe.matn.domain.audio.PreviewState
import com.giraffe.matn.domain.audio.PreviewVerse
import com.giraffe.matn.domain.catalog.CatalogEntry
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.DraftAudio
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.MatnDraftFactory
import com.giraffe.matn.domain.usecase.PreviewMatnAudioUseCase
import com.giraffe.matn.teacher.presentation.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Mirrors [PreviewPlayer]'s contract for these tests: a verse with no `fileRef` is *reported*
 * (never skipped silently), matching FR-025 — stopping at the first one rather than continuing
 * (as the real `JvmPreviewPlayer` does) keeps the assertion about final state unambiguous. */
private class PsFakePreviewPlayer : PreviewPlayer {
    private val _state = MutableStateFlow<PreviewState>(PreviewState.Idle)
    override val state: StateFlow<PreviewState> = _state
    var lastStartIndex: Int = -1

    override suspend fun play(verses: List<PreviewVerse>, startIndex: Int) {
        lastStartIndex = startIndex
        for (i in startIndex until verses.size) {
            val verse = verses[i]
            if (verse.fileRef == null) {
                _state.value = PreviewState.MissingAudio(verse.displayNumber)
                return
            }
            _state.value = PreviewState.Playing(verse.displayNumber, 0)
        }
    }
    override suspend fun playClip(bytes: ByteArray, displayNumber: Int) {
        _state.value = PreviewState.Playing(displayNumber, 0)
    }
    override fun pause() = Unit
    override fun resume() = Unit
    override fun stop() { _state.value = PreviewState.Idle }
    fun setState(s: PreviewState) { _state.value = s }
}

private class PsNoOpRepository : CatalogRepository {
    override fun observeAuthored(): Flow<List<CatalogEntry>> = flowOf(emptyList())
    override suspend fun load(matnId: String): Resource<MatnDraft> = Resource.Failure(AppError.NotFound)
    override suspend fun save(draft: MatnDraft): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun publish(draft: MatnDraft): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun unpublish(matnId: String): Resource<MatnDraft> = Resource.Failure(AppError.NotFound)
    override suspend fun uploadCover(matnId: String, bytes: ByteArray, ext: String): Resource<String> = Resource.Success("ref")
    override suspend fun attachVerseAudio(draft: MatnDraft, verseId: String, audio: DraftAudio, bytes: ByteArray): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun removeVerseAudio(draft: MatnDraft, verseId: String): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun applySplit(draft: MatnDraft, updates: Map<String, DraftAudio>, payloads: List<PendingUpload>): Resource<MatnDraft> = Resource.Success(draft)
}

private fun audio(id: String) = DraftAudio(id, "matns/m1/verses/$id.mp3", 1000, 10, 44100, 1)

private fun draftWithAudio(vararg hasAudio: Boolean): MatnDraft {
    val base = MatnDraftFactory.newDraft(newId = { "m1" }, nowMillis = { 0L }, title = "T", author = "A")
    val verses = hasAudio.mapIndexed { i, has ->
        DraftVerse(id = "v${i + 1}", chapterId = null, displayNumber = i + 1, arabicText = "t", audio = if (has) audio("v${i + 1}") else null, durationMs = 0L)
    }
    return base.copy(verses = verses)
}

class PreviewStateTest {

    @BeforeTest
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `a missing verse produces MissingAudio naming its display number`() = runTest {
        val player = PsFakePreviewPlayer()
        val useCase = PreviewMatnAudioUseCase(player)
        val draft = draftWithAudio(true, false, true) // v2 has no audio

        useCase(PreviewMatnAudioUseCase.Params(draft))

        assertEquals(PreviewState.MissingAudio(2), player.state.value)
    }

    @Test
    fun `starting from verse N begins at N`() = runTest {
        val player = PsFakePreviewPlayer()
        val useCase = PreviewMatnAudioUseCase(player)
        val draft = draftWithAudio(true, true, true)

        useCase(PreviewMatnAudioUseCase.Params(draft, startVerseId = "v3"))

        assertEquals(2, player.lastStartIndex)
    }

    @Test
    fun `an edit during playback produces Idle`() = runTest {
        val player = PsFakePreviewPlayer()
        val vm = EditorViewModel(
            initialDraft = draftWithAudio(true, true),
            saveDraft = com.giraffe.matn.domain.usecase.SaveDraftUseCase(PsNoOpRepository()),
            uploadCoverImage = com.giraffe.matn.domain.usecase.UploadCoverImageUseCase(PsNoOpRepository()),
            validateMatn = com.giraffe.matn.domain.usecase.ValidateMatnUseCase(),
            publishMatn = com.giraffe.matn.domain.usecase.PublishMatnUseCase(PsNoOpRepository()),
            loadMatnForEdit = com.giraffe.matn.domain.usecase.LoadMatnForEditUseCase(PsNoOpRepository()),
            attachVerseAudio = com.giraffe.matn.domain.usecase.AttachVerseAudioUseCase(
                PsNoOpRepository(),
                object : com.giraffe.matn.domain.audio.AudioProbe {
                    override suspend fun probe(source: com.giraffe.matn.data.audio.ByteSource) =
                        Resource.Success(com.giraffe.matn.domain.audio.ProbeResult(1000, com.giraffe.matn.domain.audio.AudioProfile(44100, 1), 1))
                    override suspend fun peaks(source: com.giraffe.matn.data.audio.ByteSource, buckets: Int) = Resource.Success(FloatArray(buckets))
                },
            ),
            removeVerseAudio = com.giraffe.matn.domain.usecase.RemoveVerseAudioUseCase(PsNoOpRepository()),
            previewPlayer = player,
            previewMatnAudio = PreviewMatnAudioUseCase(player),
            newId = { "id" },
            nowMillis = { 0L },
        )
        player.setState(PreviewState.Playing(1, 500))
        // The collector that mirrors `previewPlayer.state` into `EditorUiState.previewState` runs
        // on the ViewModel's own `viewModelScope`; give it a turn before asserting.
        kotlinx.coroutines.yield()
        assertIs<PreviewState.Playing>(vm.state.value.previewState)

        vm.onTitleChange("New title")

        assertEquals(PreviewState.Idle, vm.state.value.previewState)
    }
}
