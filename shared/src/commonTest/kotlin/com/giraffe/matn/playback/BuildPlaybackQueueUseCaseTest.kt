package com.giraffe.matn.playback

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.audio.AudioSourceResolver
import com.giraffe.matn.domain.model.AudioAsset
import com.giraffe.matn.domain.model.Verse
import com.giraffe.matn.domain.repository.AudioAssetRepository
import com.giraffe.matn.domain.repository.VerseRepository
import com.giraffe.matn.domain.usecase.BuildPlaybackQueueUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildPlaybackQueueUseCaseTest {

    private val matnId = "matn-1"
    private val reciter = AudioAssetRepository.DEFAULT_RECITER

    private fun verses(vararg ids: String): List<Verse> = ids.mapIndexed { i, id ->
        Verse(id, matnId, null, displayNumber = i + 1, arabicText = "text-$id", durationMs = 1_000L * (i + 1))
    }

    private fun audio(verseId: String, fileRef: String, durationMs: Long = 5_000): AudioAsset =
        AudioAsset(id = "$verseId-a", verseId = verseId, reciterId = reciter, fileRef = fileRef, durationMs = durationMs)

    @Test
    fun `tracks come back in ascending displayNumber`() = runTest {
        // Verses emitted by the repository in displayNumber order (the real SQL ORDER BY).
        val verses = verses("v1", "v2", "v3")
        // Audio deliberately shuffled — the use case must zip by verseId, not by audio order.
        val shuffledAudio = listOf(
            audio("v3", "v3.mp3"),
            audio("v1", "v1.mp3"),
            audio("v2", "v2.mp3"),
        )
        val useCase = BuildPlaybackQueueUseCase(
            verseRepository = FakeVerseRepo(verses),
            audioRepository = FakeAudioRepo(shuffledAudio),
            audioSourceResolver = FakeResolver(),
        )
        val result = useCase.invoke(BuildPlaybackQueueUseCase.Params(matnId))
        assertTrue(result is Resource.Success)
        val tracks = (result as Resource.Success).data.tracks
        // queue order follows verse displayNumber, not the audio list order
        assertEquals(listOf("v1", "v2", "v3"), tracks.map { it.verseId })
        assertEquals(listOf(1, 2, 3), tracks.map { it.displayNumber })
    }

    @Test
    fun `startIndex resolves from startVerseId`() = runTest {
        val verses = verses("v1", "v2", "v3", "v4")
        val useCase = BuildPlaybackQueueUseCase(
            verseRepository = FakeVerseRepo(verses),
            audioRepository = FakeAudioRepo(verses.map { audio(it.id, "${it.id}.mp3") }),
            audioSourceResolver = FakeResolver(),
        )
        val result = useCase.invoke(
            BuildPlaybackQueueUseCase.Params(matnId, startVerseId = "v3")
        )
        assertTrue(result is Resource.Success)
        assertEquals(2, (result as Resource.Success).data.startIndex)
    }

    @Test
    fun `null startVerseId starts at index 0`() = runTest {
        val verses = verses("v1", "v2")
        val useCase = BuildPlaybackQueueUseCase(
            verseRepository = FakeVerseRepo(verses),
            audioRepository = FakeAudioRepo(verses.map { audio(it.id, "${it.id}.mp3") }),
            audioSourceResolver = FakeResolver(),
        )
        val result = useCase.invoke(BuildPlaybackQueueUseCase.Params(matnId))
        assertTrue(result is Resource.Success)
        assertEquals(0, (result as Resource.Success).data.startIndex)
    }

    @Test
    fun `verse with no audio row is defensively omitted`() = runTest {
        val verses = verses("v1", "v2", "v3")
        // only v1 and v3 have audio; v2 is the defensive "no audio row" case
        val audioList = listOf(audio("v1", "v1.mp3"), audio("v3", "v3.mp3"))
        val useCase = BuildPlaybackQueueUseCase(
            verseRepository = FakeVerseRepo(verses),
            audioRepository = FakeAudioRepo(audioList),
            audioSourceResolver = FakeResolver(),
        )
        val result = useCase.invoke(BuildPlaybackQueueUseCase.Params(matnId))
        assertTrue(result is Resource.Success)
        val tracks = (result as Resource.Success).data.tracks
        assertEquals(listOf("v1", "v3"), tracks.map { it.verseId })
        // no notice is produced here at all (nothing playable to reach) — defensive omission only
        assertNull((result as Resource.Success).data.tracks.firstOrNull { it.verseId == "v2" })
    }

    @Test
    fun `empty matn returns NotFound`() = runTest {
        val useCase = BuildPlaybackQueueUseCase(
            verseRepository = FakeVerseRepo(emptyList()),
            audioRepository = FakeAudioRepo(emptyList()),
            audioSourceResolver = FakeResolver(),
        )
        val result = useCase.invoke(BuildPlaybackQueueUseCase.Params(matnId))
        assertTrue(result is Resource.Failure)
        assertEquals(AppError.NotFound, (result as Resource.Failure).error)
    }

    @Test
    fun `uri resolved via AudioSourceResolver and duration falls back to verse`() = runTest {
        val verses = verses("v1")
        val useCase = BuildPlaybackQueueUseCase(
            verseRepository = FakeVerseRepo(verses),
            audioRepository = FakeAudioRepo(listOf(audio("v1", "v1.mp3", durationMs = 0))),
            audioSourceResolver = FakeResolver { "resolved://$it" },
        )
        val result = useCase.invoke(BuildPlaybackQueueUseCase.Params(matnId))
        assertTrue(result is Resource.Success)
        val track = (result as Resource.Success).data.tracks.single()
        assertEquals("resolved://v1.mp3", track.uri)
        // durationMs 0 on the asset → fall back to verse.durationMs
        assertEquals(verses[0].durationMs, track.durationMs)
    }

    private class FakeVerseRepo(private val verses: List<Verse>) : VerseRepository {
        override fun observeVerses(matnId: String): Flow<List<Verse>> = flowOf(verses)
        override suspend fun getVersesByChapter(chapterId: String): Resource<List<Verse>> =
            Resource.Success(verses)
        override suspend fun getVerse(id: String): Resource<Verse?> =
            Resource.Success(verses.firstOrNull { it.id == id })
    }

    private class FakeAudioRepo(private val assets: List<AudioAsset>) : AudioAssetRepository {
        override suspend fun getAudioForVerse(verseId: String, reciterId: String): Resource<AudioAsset?> =
            Resource.Success(assets.firstOrNull { it.verseId == verseId })
        override suspend fun getAudioForMatn(matnId: String, reciterId: String): Resource<List<AudioAsset>> =
            Resource.Success(assets)
    }

    private class FakeResolver(private val block: (String) -> String = { "file://audio/$it" }) :
        AudioSourceResolver {
        override suspend fun resolve(fileRef: String): String = block(fileRef)
    }
}