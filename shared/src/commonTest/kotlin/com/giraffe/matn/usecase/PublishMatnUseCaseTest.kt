package com.giraffe.matn.usecase

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.catalog.AudioCompleteness
import com.giraffe.matn.domain.catalog.CatalogEntry
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.domain.usecase.PublishMatnUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class PublishFakeCatalogRepository : CatalogRepository {
    var publishCallCount = 0
    override fun observeAuthored(): Flow<List<CatalogEntry>> = flowOf(emptyList())
    override suspend fun load(matnId: String): Resource<MatnDraft> = Resource.Failure(AppError.NotFound)
    override suspend fun save(draft: MatnDraft): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun publish(draft: MatnDraft): Resource<MatnDraft> {
        publishCallCount++
        return Resource.Success(draft)
    }
    override suspend fun unpublish(matnId: String): Resource<MatnDraft> = Resource.Failure(AppError.NotFound)
    override suspend fun uploadCover(matnId: String, bytes: ByteArray, ext: String): Resource<String> = Resource.Success("ref")
    override suspend fun attachVerseAudio(draft: MatnDraft, verseId: String, audio: com.giraffe.matn.domain.catalog.DraftAudio, bytes: ByteArray): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun removeVerseAudio(draft: MatnDraft, verseId: String): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun applySplit(draft: MatnDraft, updates: Map<String, com.giraffe.matn.domain.catalog.DraftAudio>, payloads: List<com.giraffe.matn.domain.audio.PendingUpload>): Resource<MatnDraft> = Resource.Success(draft)
}

private fun draft(verses: List<DraftVerse> = emptyList()) = MatnDraft(
    id = "m1",
    title = "T",
    author = "A",
    description = "",
    coverImageRef = null,
    structureKind = StructureKind.SIMPLE,
    defaultReciterId = "r1",
    chapters = emptyList(),
    verses = verses,
    publicationState = PublicationState.DRAFT,
    createdAt = 0L,
    updatedAt = 0L,
    remoteRevision = null,
)

private fun verse(id: String, number: Int, audio: com.giraffe.matn.domain.catalog.DraftAudio? = null) =
    DraftVerse(id, null, number, "text", audio, audio?.durationMs ?: 0L)

private fun audio(fileRef: String) =
    com.giraffe.matn.domain.catalog.DraftAudio(id = "a-$fileRef", fileRef = fileRef, durationMs = 1000, sizeBytes = 10, sampleRate = 44100, channels = 1)

class PublishMatnUseCaseTest {

    @Test
    fun `a draft with a blocking problem is refused and no repository call is made`() = runTest {
        val repo = PublishFakeCatalogRepository()
        val result = PublishMatnUseCase(repo)(draft(listOf(verse("v1", 1), verse("v2", 1))))

        assertIs<Resource.Failure>(result)
        assertEquals(0, repo.publishCallCount)
    }

    @Test
    fun `a text-only draft with missing audio is refused — Phase 12 makes it blocking`() = runTest {
        val repo = PublishFakeCatalogRepository()
        val result = PublishMatnUseCase(repo)(draft(listOf(verse("v1", 1), verse("v2", 2))))

        assertIs<Resource.Failure>(result)
        assertEquals(0, repo.publishCallCount)
    }

    @Test
    fun `publishing a complete matn succeeds with audioCompleteness COMPLETE`() = runTest {
        val repo = PublishFakeCatalogRepository()
        val result = PublishMatnUseCase(repo)(draft(listOf(verse("v1", 1, audio("ref1")), verse("v2", 2, audio("ref2")))))

        assertIs<Resource.Success<MatnDraft>>(result)
        assertEquals(AudioCompleteness.COMPLETE, result.data.audioCompleteness)
        assertEquals(1, repo.publishCallCount)
    }

    @Test
    fun `an empty matn is refused`() = runTest {
        val repo = PublishFakeCatalogRepository()
        val result = PublishMatnUseCase(repo)(draft())

        assertIs<Resource.Failure>(result)
        assertEquals(0, repo.publishCallCount)
    }
}
