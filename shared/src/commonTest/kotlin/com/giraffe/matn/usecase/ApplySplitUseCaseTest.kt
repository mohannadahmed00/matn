package com.giraffe.matn.usecase

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.audio.ByteArrayByteSource
import com.giraffe.matn.data.audio.ByteSource
import com.giraffe.matn.data.audio.Mp3FrameIndex
import com.giraffe.matn.domain.audio.AudioProfile
import com.giraffe.matn.domain.audio.AudioSlicer
import com.giraffe.matn.domain.audio.PendingUpload
import com.giraffe.matn.domain.audio.SourceRecording
import com.giraffe.matn.domain.audio.SplitPlan
import com.giraffe.matn.domain.audio.VerseRange
import com.giraffe.matn.domain.audio.VerseSlice
import com.giraffe.matn.domain.catalog.CatalogEntry
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.DraftAudio
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.error.SplitBlockedError
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.domain.usecase.AttachVerseAudioUseCase
import com.giraffe.matn.domain.usecase.ApplySplitUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Deterministic per-range bytes — models "content-tagged path" without needing a real decode: the
 * same range always produces the same bytes, a different range always produces different bytes. */
private class FakeSlicer : AudioSlicer {
    override suspend fun slice(source: ByteSource, index: Mp3FrameIndex, ranges: List<VerseRange>): Resource<List<VerseSlice>> =
        Resource.Success(
            ranges.map { r -> VerseSlice(r.verseId, "${r.verseId}:${r.startMs}:${r.endMs}".encodeToByteArray(), r.endMs - r.startMs) },
        )
}

private class RecordingRepository : CatalogRepository {
    var applySplitCallCount = 0
    var lastPayloads: List<PendingUpload> = emptyList()
    override fun observeAuthored(): Flow<List<CatalogEntry>> = flowOf(emptyList())
    override suspend fun load(matnId: String): Resource<MatnDraft> = Resource.Failure(AppError.NotFound)
    override suspend fun save(draft: MatnDraft): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun publish(draft: MatnDraft): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun unpublish(matnId: String): Resource<MatnDraft> = Resource.Failure(AppError.NotFound)
    override suspend fun uploadCover(matnId: String, bytes: ByteArray, ext: String): Resource<String> = Resource.Success("ref")
    override suspend fun attachVerseAudio(draft: MatnDraft, verseId: String, audio: DraftAudio, bytes: ByteArray): Resource<MatnDraft> =
        Resource.Success(draft.copy(verses = draft.verses.map { if (it.id == verseId) it.copy(audio = audio) else it }))
    override suspend fun removeVerseAudio(draft: MatnDraft, verseId: String): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun applySplit(draft: MatnDraft, updates: Map<String, DraftAudio>, payloads: List<PendingUpload>): Resource<MatnDraft> {
        applySplitCallCount++
        lastPayloads = payloads
        return Resource.Success(
            draft.copy(verses = draft.verses.map { verse -> updates[verse.id]?.let { verse.copy(audio = it, durationMs = it.durationMs) } ?: verse }),
        )
    }
}

private fun verse(id: String, audio: DraftAudio? = null) =
    DraftVerse(id = id, chapterId = null, displayNumber = 1, arabicText = "t", audio = audio, durationMs = audio?.durationMs ?: 0L)

private fun draft(verses: List<DraftVerse>) = MatnDraft(
    id = "m1", title = "t", author = "a", description = "d", coverImageRef = null,
    structureKind = StructureKind.SIMPLE, defaultReciterId = "r1", chapters = emptyList(),
    verses = verses, publicationState = PublicationState.DRAFT, createdAt = 0L, updatedAt = 0L, remoteRevision = "1",
)

private val fakeIndex = kotlinx.coroutines.runBlocking {
    Mp3FrameIndex.build(ByteArrayByteSource(com.giraffe.matn.audio.Mp3Fixtures.mp3(frames = 10)))
} ?: error("fixture failed to build")

private fun source() = SourceRecording(localPath = "/tmp/rec.mp3", sizeBytes = 1000, durationMs = 10_000, profile = AudioProfile(44100, 1), frameCount = 10)

class ApplySplitUseCaseTest {

    @Test
    fun `a plan with a blocking problem never reaches the repository`() = runTest {
        val repo = RecordingRepository()
        val useCase = ApplySplitUseCase(repo, FakeSlicer(), newId = { "id" })
        // v2 has no range: R1 MissingRange.
        val plan = SplitPlan(source(), scopeVerseIds = listOf("v1", "v2"), ranges = listOf(VerseRange("v1", 0, 1000)))

        val result = useCase(ApplySplitUseCase.Params(draft(listOf(verse("v1"), verse("v2"))), ByteArrayByteSource(ByteArray(0)), fakeIndex, plan))

        assertIs<Resource.Failure>(result)
        assertIs<SplitBlockedError>(result.error)
        assertEquals(0, repo.applySplitCallCount)
    }

    @Test
    fun `a valid plan produces one payload per in-scope verse`() = runTest {
        val repo = RecordingRepository()
        val useCase = ApplySplitUseCase(repo, FakeSlicer(), newId = { "id" })
        val plan = SplitPlan(
            source(),
            scopeVerseIds = listOf("v1", "v2"),
            ranges = listOf(VerseRange("v1", 0, 1000), VerseRange("v2", 1000, 2000)),
        )

        val result = useCase(ApplySplitUseCase.Params(draft(listOf(verse("v1"), verse("v2"))), ByteArrayByteSource(ByteArray(0)), fakeIndex, plan))

        assertIs<Resource.Success<MatnDraft>>(result)
        assertEquals(2, repo.lastPayloads.size)
    }

    @Test
    fun `verses outside the scope are untouched`() = runTest {
        val repo = RecordingRepository()
        val useCase = ApplySplitUseCase(repo, FakeSlicer(), newId = { "id" })
        val outOfScopeAudio = DraftAudio("keep", "matns/m1/verses/v3-keep.mp3", 500, 10, 44100, 1)
        val plan = SplitPlan(source(), scopeVerseIds = listOf("v1"), ranges = listOf(VerseRange("v1", 0, 1000)))

        val result = useCase(
            ApplySplitUseCase.Params(draft(listOf(verse("v1"), verse("v3", outOfScopeAudio))), ByteArrayByteSource(ByteArray(0)), fakeIndex, plan),
        )

        assertIs<Resource.Success<MatnDraft>>(result)
        assertEquals(outOfScopeAudio, result.data.verses.first { it.id == "v3" }.audio)
    }

    @Test
    fun `a re-run with identical ranges produces identical object paths`() = runTest {
        val repo = RecordingRepository()
        val useCase = ApplySplitUseCase(repo, FakeSlicer(), newId = { "id" })
        val plan = SplitPlan(source(), scopeVerseIds = listOf("v1"), ranges = listOf(VerseRange("v1", 0, 1000)))
        val params = ApplySplitUseCase.Params(draft(listOf(verse("v1"))), ByteArrayByteSource(ByteArray(0)), fakeIndex, plan)

        val first = (useCase(params) as Resource.Success).data.verses.first().audio!!.fileRef
        val second = (useCase(params) as Resource.Success).data.verses.first().audio!!.fileRef

        assertEquals(first, second)
    }

    @Test
    fun `a materially changed range produces a different object path`() = runTest {
        val repo = RecordingRepository()
        val useCase = ApplySplitUseCase(repo, FakeSlicer(), newId = { "id" })
        val plan1 = SplitPlan(source(), scopeVerseIds = listOf("v1"), ranges = listOf(VerseRange("v1", 0, 1000)))
        val plan2 = SplitPlan(source(), scopeVerseIds = listOf("v1"), ranges = listOf(VerseRange("v1", 0, 2000)))

        val first = (useCase(ApplySplitUseCase.Params(draft(listOf(verse("v1"))), ByteArrayByteSource(ByteArray(0)), fakeIndex, plan1)) as Resource.Success)
            .data.verses.first().audio!!.fileRef
        val second = (useCase(ApplySplitUseCase.Params(draft(listOf(verse("v1"))), ByteArrayByteSource(ByteArray(0)), fakeIndex, plan2)) as Resource.Success)
            .data.verses.first().audio!!.fileRef

        assertNotEquals(first, second)
    }

    @Test
    fun `a split-produced DraftAudio is shape-identical to an attach-produced one`() = runTest {
        val repo = RecordingRepository()
        val splitUseCase = ApplySplitUseCase(repo, FakeSlicer(), newId = { "same-id" })
        val plan = SplitPlan(source(), scopeVerseIds = listOf("v1"), ranges = listOf(VerseRange("v1", 0, 1000)))
        val splitResult = splitUseCase(ApplySplitUseCase.Params(draft(listOf(verse("v1"))), ByteArrayByteSource(ByteArray(0)), fakeIndex, plan))
        val splitAudio = (splitResult as Resource.Success).data.verses.first().audio!!

        val attachUseCase = AttachVerseAudioUseCase(
            repo,
            object : com.giraffe.matn.domain.audio.AudioProbe {
                override suspend fun probe(source: ByteSource) =
                    Resource.Success(com.giraffe.matn.domain.audio.ProbeResult(1000, AudioProfile(44100, 1), 1))
                override suspend fun peaks(source: ByteSource, buckets: Int) = Resource.Success(FloatArray(buckets))
            },
            newId = { "same-id" },
        )
        val attachResult = attachUseCase(AttachVerseAudioUseCase.Params(draft(listOf(verse("v1"))), "v1", "v1:0:1000".encodeToByteArray()))
        val attachAudio = (attachResult as Resource.Success).data.verses.first().audio!!

        // Same fields populated, same path grammar — not necessarily the same fileRef (different
        // AttachVerseAudioUseCase vs. ApplySplitUseCase byte pipelines aren't guaranteed to tag
        // identically unless the exact same bytes are stored, which they are here by construction).
        assertEquals(splitAudio.fileRef, attachAudio.fileRef)
        assertEquals(splitAudio.durationMs, attachAudio.durationMs)
        assertEquals(splitAudio.sampleRate, attachAudio.sampleRate)
        assertEquals(splitAudio.channels, attachAudio.channels)
        assertTrue(splitAudio.fileRef.startsWith("matns/m1/verses/v1-"))
    }
}
