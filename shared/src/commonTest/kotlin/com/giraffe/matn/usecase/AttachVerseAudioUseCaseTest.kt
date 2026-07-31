package com.giraffe.matn.usecase

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.audio.ByteSource
import com.giraffe.matn.domain.audio.AudioProbe
import com.giraffe.matn.domain.audio.AudioProfile
import com.giraffe.matn.domain.audio.PendingUpload
import com.giraffe.matn.domain.audio.ProbeResult
import com.giraffe.matn.domain.catalog.CatalogEntry
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.DraftAudio
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.error.AudioAttachError
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.domain.usecase.AttachVerseAudioUseCase
import com.giraffe.matn.domain.usecase.RemoveVerseAudioUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A byte string that decodes to [profile]/[durationMs] unless it is [unreadableMarker]. */
private class FakeAudioProbe(
    private val profile: AudioProfile = AudioProfile(44100, 1),
    private val durationMs: Long = 5000L,
) : AudioProbe {
    override suspend fun probe(source: ByteSource): Resource<ProbeResult> {
        val bytes = source.read(0, source.size.toInt())
        if (bytes.decodeToString() == "unreadable") return Resource.Failure(AppError.Storage("bad"))
        return Resource.Success(ProbeResult(durationMs = durationMs, profile = profile, frameCount = 10))
    }
    override suspend fun peaks(source: ByteSource, buckets: Int): Resource<FloatArray> = Resource.Success(FloatArray(buckets))
}

private class RecordingFakeRepository : CatalogRepository {
    var attachCallCount = 0
    override fun observeAuthored(): Flow<List<CatalogEntry>> = flowOf(emptyList())
    override suspend fun load(matnId: String): Resource<MatnDraft> = Resource.Failure(AppError.NotFound)
    override suspend fun save(draft: MatnDraft): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun publish(draft: MatnDraft): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun unpublish(matnId: String): Resource<MatnDraft> = Resource.Failure(AppError.NotFound)
    override suspend fun uploadCover(matnId: String, bytes: ByteArray, ext: String): Resource<String> = Resource.Success("ref")
    override suspend fun attachVerseAudio(draft: MatnDraft, verseId: String, audio: DraftAudio, bytes: ByteArray): Resource<MatnDraft> {
        attachCallCount++
        return Resource.Success(
            draft.copy(verses = draft.verses.map { if (it.id == verseId) it.copy(audio = audio, durationMs = audio.durationMs) else it }),
        )
    }
    override suspend fun removeVerseAudio(draft: MatnDraft, verseId: String): Resource<MatnDraft> =
        Resource.Success(draft.copy(verses = draft.verses.map { if (it.id == verseId) it.copy(audio = null, durationMs = 0L) else it }))
    override suspend fun applySplit(draft: MatnDraft, updates: Map<String, DraftAudio>, payloads: List<PendingUpload>): Resource<MatnDraft> =
        Resource.Success(draft)
}

private fun audio(id: String = "a1", sampleRate: Int = 44100, channels: Int = 1) =
    DraftAudio(id = id, fileRef = "matns/m1/verses/v1-old.mp3", durationMs = 1000, sizeBytes = 10, sampleRate = sampleRate, channels = channels)

private fun verse(id: String, audio: DraftAudio? = null) =
    DraftVerse(id = id, chapterId = null, displayNumber = 1, arabicText = "t", audio = audio, durationMs = audio?.durationMs ?: 0L)

private fun draft(verses: List<DraftVerse>, publicationState: PublicationState = PublicationState.DRAFT) = MatnDraft(
    id = "m1", title = "t", author = "a", description = "d", coverImageRef = null,
    structureKind = StructureKind.SIMPLE, defaultReciterId = "r1", chapters = emptyList(),
    verses = verses, publicationState = publicationState, createdAt = 0L, updatedAt = 0L, remoteRevision = "1",
)

class AttachVerseAudioUseCaseTest {

    @Test
    fun `first attach establishes the profile`() = runTest {
        val repo = RecordingFakeRepository()
        val useCase = AttachVerseAudioUseCase(repo, FakeAudioProbe(profile = AudioProfile(48000, 2)), newId = { "new-id" })

        val result = useCase(AttachVerseAudioUseCase.Params(draft(listOf(verse("v1"))), "v1", "bytes".encodeToByteArray()))

        assertIs<Resource.Success<MatnDraft>>(result)
        val attached = result.data.verses.first().audio!!
        assertEquals(48000, attached.sampleRate)
        assertEquals(2, attached.channels)
        assertEquals(1, repo.attachCallCount)
    }

    @Test
    fun `a mismatched profile is rejected and the verse keeps its previous audio`() = runTest {
        val repo = RecordingFakeRepository()
        val existing = audio(sampleRate = 44100, channels = 1)
        val useCase = AttachVerseAudioUseCase(repo, FakeAudioProbe(profile = AudioProfile(48000, 2)))

        val result = useCase(
            AttachVerseAudioUseCase.Params(draft(listOf(verse("v1", existing), verse("v2"))), "v2", "bytes".encodeToByteArray()),
        )

        assertIs<Resource.Failure>(result)
        assertIs<AudioAttachError.ProfileMismatch>(result.error)
        assertEquals(0, repo.attachCallCount)
    }

    @Test
    fun `a replacement keeps DraftAudio id stable`() = runTest {
        val repo = RecordingFakeRepository()
        val existing = audio(id = "stable-id")
        val useCase = AttachVerseAudioUseCase(repo, FakeAudioProbe(), newId = { "should-not-be-used" })

        val result = useCase(AttachVerseAudioUseCase.Params(draft(listOf(verse("v1", existing))), "v1", "new-bytes".encodeToByteArray()))

        assertIs<Resource.Success<MatnDraft>>(result)
        assertEquals("stable-id", result.data.verses.first().audio!!.id)
    }

    @Test
    fun `unparseable bytes are rejected as WrongFormat with no upload attempted`() = runTest {
        val repo = RecordingFakeRepository()
        val useCase = AttachVerseAudioUseCase(repo, FakeAudioProbe())

        val result = useCase(AttachVerseAudioUseCase.Params(draft(listOf(verse("v1"))), "v1", "unreadable".encodeToByteArray()))

        assertIs<Resource.Failure>(result)
        assertEquals(AudioAttachError.WrongFormat, result.error)
        assertEquals(0, repo.attachCallCount)
    }

    @Test
    fun `remove-on-published is refused`() = runTest {
        val repo = RecordingFakeRepository()
        val useCase = RemoveVerseAudioUseCase(repo)

        val result = useCase(RemoveVerseAudioUseCase.Params(draft(listOf(verse("v1", audio())), PublicationState.PUBLISHED), "v1"))

        assertIs<Resource.Failure>(result)
    }

    @Test
    fun `remove-on-draft succeeds and clears the audio`() = runTest {
        val repo = RecordingFakeRepository()
        val useCase = RemoveVerseAudioUseCase(repo)

        val result = useCase(RemoveVerseAudioUseCase.Params(draft(listOf(verse("v1", audio()))), "v1"))

        assertIs<Resource.Success<MatnDraft>>(result)
        assertNull(result.data.verses.first().audio)
    }
}
