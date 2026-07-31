package com.giraffe.matn.usecase

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.catalog.CatalogEntry
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.domain.usecase.LoadMatnForEditUseCase
import com.giraffe.matn.domain.usecase.SaveDraftUseCase
import com.giraffe.matn.domain.usecase.UploadCoverImageUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeCatalogRepository(
    private val saveResult: (MatnDraft) -> Resource<MatnDraft> = { Resource.Success(it) },
    private val loadResult: Resource<MatnDraft> = Resource.Failure(AppError.NotFound),
) : CatalogRepository {
    var uploadCoverCalled = false

    override fun observeAuthored(): Flow<List<CatalogEntry>> = flowOf(emptyList())
    override suspend fun load(matnId: String): Resource<MatnDraft> = loadResult
    override suspend fun save(draft: MatnDraft): Resource<MatnDraft> = saveResult(draft)
    override suspend fun publish(draft: MatnDraft): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun unpublish(matnId: String): Resource<MatnDraft> = loadResult
    override suspend fun uploadCover(matnId: String, bytes: ByteArray, ext: String): Resource<String> {
        uploadCoverCalled = true
        return Resource.Success("matns/$matnId/cover.$ext")
    }
    override suspend fun attachVerseAudio(draft: MatnDraft, verseId: String, audio: com.giraffe.matn.domain.catalog.DraftAudio, bytes: ByteArray): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun removeVerseAudio(draft: MatnDraft, verseId: String): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun applySplit(draft: MatnDraft, updates: Map<String, com.giraffe.matn.domain.catalog.DraftAudio>, payloads: List<com.giraffe.matn.domain.audio.PendingUpload>): Resource<MatnDraft> = Resource.Success(draft)
}

private fun sampleDraft(remoteRevision: String? = "token-1") = MatnDraft(
    id = "m1",
    title = "T",
    author = "A",
    description = "",
    coverImageRef = null,
    structureKind = StructureKind.SIMPLE,
    defaultReciterId = "r1",
    chapters = emptyList(),
    verses = emptyList(),
    publicationState = PublicationState.DRAFT,
    createdAt = 0L,
    updatedAt = 0L,
    remoteRevision = remoteRevision,
)

class DraftUseCaseTest {

    @Test
    fun `SaveDraftUseCase passes remoteRevision through and returns the refreshed value`() = runTest {
        val draft = sampleDraft(remoteRevision = "old-token")
        var capturedPrecondition: String? = null
        val repo = FakeCatalogRepository(saveResult = { d ->
            capturedPrecondition = d.remoteRevision
            Resource.Success(d.copy(remoteRevision = "new-token"))
        })

        val result = SaveDraftUseCase(repo)(draft)

        assertEquals("old-token", capturedPrecondition)
        assertEquals("new-token", (result as Resource.Success).data.remoteRevision)
    }

    @Test
    fun `SaveDraftUseCase refuses saving a published matn with missing audio`() = runTest {
        val verse = com.giraffe.matn.domain.catalog.DraftVerse("v1", null, 1, "text", null, 0L)
        val draft = sampleDraft().copy(publicationState = PublicationState.PUBLISHED, verses = listOf(verse))
        val repo = FakeCatalogRepository(saveResult = { Resource.Success(it) })

        val result = SaveDraftUseCase(repo)(draft)

        assertIs<Resource.Failure>(result)
        val error = result.error as com.giraffe.matn.domain.error.ContentIntegrityError.Aggregate
        assertTrue(error.problems.any { it is com.giraffe.matn.domain.error.ContentIntegrityError.MissingAudio && it.verseId == "v1" })
    }

    @Test
    fun `SaveDraftUseCase allows saving a published matn with complete audio`() = runTest {
        val audio = com.giraffe.matn.domain.catalog.DraftAudio("a1", "matns/m1/verses/v1-tag.mp3", 1000, 10, 44100, 1)
        val verse = com.giraffe.matn.domain.catalog.DraftVerse("v1", null, 1, "text", audio, 1000L)
        val draft = sampleDraft().copy(publicationState = PublicationState.PUBLISHED, verses = listOf(verse))
        val repo = FakeCatalogRepository(saveResult = { Resource.Success(it) })

        val result = SaveDraftUseCase(repo)(draft)

        assertIs<Resource.Success<MatnDraft>>(result)
    }

    @Test
    fun `LoadMatnForEditUseCase maps a NotFound failure without throwing`() = runTest {
        val repo = FakeCatalogRepository(loadResult = Resource.Failure(AppError.NotFound))

        val result = LoadMatnForEditUseCase(repo)("missing-id")

        assertIs<Resource.Failure>(result)
        assertEquals(AppError.NotFound, result.error)
    }

    @Test
    fun `UploadCoverImageUseCase rejects an oversized image before any upload call`() = runTest {
        val repo = FakeCatalogRepository()
        val oversized = ByteArray(6 * 1024 * 1024)

        val result = UploadCoverImageUseCase(repo)(UploadCoverImageUseCase.Params("m1", oversized, "png"))

        assertIs<Resource.Failure>(result)
        assertFalse(repo.uploadCoverCalled)
    }

    @Test
    fun `UploadCoverImageUseCase rejects a wrong content type before any upload call`() = runTest {
        val repo = FakeCatalogRepository()

        val result = UploadCoverImageUseCase(repo)(UploadCoverImageUseCase.Params("m1", ByteArray(10), "zip"))

        assertIs<Resource.Failure>(result)
        assertFalse(repo.uploadCoverCalled)
    }
}
