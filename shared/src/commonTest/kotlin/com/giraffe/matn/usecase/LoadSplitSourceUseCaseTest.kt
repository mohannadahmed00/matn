package com.giraffe.matn.usecase

import com.giraffe.matn.audio.Mp3Fixtures
import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.audio.ByteArrayByteSource
import com.giraffe.matn.data.audio.ByteSource
import com.giraffe.matn.domain.audio.AudioProbe
import com.giraffe.matn.domain.audio.AudioProfile
import com.giraffe.matn.domain.audio.ProbeResult
import com.giraffe.matn.domain.catalog.DraftAudio
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.error.AudioAttachError
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.domain.usecase.LoadSplitSourceUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class StubProbe(
    private val durationMs: Long = 60_000,
    private val profile: AudioProfile = AudioProfile(44100, 1),
) : AudioProbe {
    override suspend fun probe(source: ByteSource): Resource<ProbeResult> =
        Resource.Success(ProbeResult(durationMs = durationMs, profile = profile, frameCount = 100))
    override suspend fun peaks(source: ByteSource, buckets: Int): Resource<FloatArray> = Resource.Success(FloatArray(buckets))
}

private fun draft(verses: List<DraftVerse> = emptyList()) = MatnDraft(
    id = "m1", title = "T", author = "A", description = "", coverImageRef = null,
    structureKind = StructureKind.SIMPLE, defaultReciterId = "r1", chapters = emptyList(),
    verses = verses, publicationState = PublicationState.DRAFT, createdAt = 0L, updatedAt = 0L, remoteRevision = null,
)

private fun realMp3Source() = ByteArrayByteSource(Mp3Fixtures.mp3(frames = 40))

private const val MB = 1024L * 1024

class LoadSplitSourceUseCaseTest {

    /**
     * The split source's ceiling is 300 MB, **not** the 10 MB per-verse one. Regression test for a
     * shipped bug where the file chooser applied the per-verse limit to a split source, so any
     * continuous recording over 10 MB — i.e. the normal case this whole path exists for — was
     * rejected before it ever reached this use case.
     */
    @Test
    fun `a source well over the per-verse 10 MB ceiling is accepted`() = runTest {
        val useCase = LoadSplitSourceUseCase(StubProbe())

        val result = useCase(LoadSplitSourceUseCase.Params(draft(), realMp3Source(), "/tmp/rec.mp3", sizeBytes = 120 * MB))

        assertIs<Resource.Success<LoadSplitSourceUseCase.Result>>(result)
        assertEquals(120 * MB, result.data.source.sizeBytes)
    }

    @Test
    fun `a source over 300 MB is rejected as SourceTooLarge`() = runTest {
        val useCase = LoadSplitSourceUseCase(StubProbe())

        val result = useCase(LoadSplitSourceUseCase.Params(draft(), realMp3Source(), "/tmp/rec.mp3", sizeBytes = 301 * MB))

        assertEquals(Resource.Failure(AudioAttachError.SourceTooLarge), result)
    }

    @Test
    fun `a source longer than 4 hours is rejected as SourceTooLong`() = runTest {
        val fourHoursAndOneMs = 4L * 60 * 60 * 1000 + 1
        val useCase = LoadSplitSourceUseCase(StubProbe(durationMs = fourHoursAndOneMs))

        val result = useCase(LoadSplitSourceUseCase.Params(draft(), realMp3Source(), "/tmp/rec.mp3", sizeBytes = 10 * MB))

        assertEquals(Resource.Failure(AudioAttachError.SourceTooLong), result)
    }

    @Test
    fun `a source whose profile mismatches the matn's is rejected naming both`() = runTest {
        val existing = DraftAudio("a1", "matns/m1/verses/v1.mp3", 1000, 10, sampleRate = 44100, channels = 1)
        val withAudio = draft(listOf(DraftVerse("v1", null, 1, "t", existing, 1000L)))
        val useCase = LoadSplitSourceUseCase(StubProbe(profile = AudioProfile(48000, 2)))

        val result = useCase(LoadSplitSourceUseCase.Params(withAudio, realMp3Source(), "/tmp/rec.mp3", sizeBytes = 10 * MB))

        assertIs<Resource.Failure>(result)
        val error = assertIs<AudioAttachError.ProfileMismatch>(result.error)
        assertEquals(AudioProfile(44100, 1), error.existing)
        assertEquals(AudioProfile(48000, 2), error.incoming)
    }

    @Test
    fun `unreadable content is rejected as WrongFormat`() = runTest {
        val useCase = LoadSplitSourceUseCase(StubProbe())
        val notAnMp3 = ByteArrayByteSource(ByteArray(64))

        val result = useCase(LoadSplitSourceUseCase.Params(draft(), notAnMp3, "/tmp/rec.mp3", sizeBytes = 64))

        assertEquals(Resource.Failure(AudioAttachError.WrongFormat), result)
    }
}
