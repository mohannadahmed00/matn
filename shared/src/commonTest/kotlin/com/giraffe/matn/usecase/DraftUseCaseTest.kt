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
}

private fun sampleDraft(remoteUpdateTime: String? = "token-1") = MatnDraft(
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
    remoteUpdateTime = remoteUpdateTime,
)

class DraftUseCaseTest {

    @Test
    fun `SaveDraftUseCase passes remoteUpdateTime through and returns the refreshed value`() = runTest {
        val draft = sampleDraft(remoteUpdateTime = "old-token")
        var capturedPrecondition: String? = null
        val repo = FakeCatalogRepository(saveResult = { d ->
            capturedPrecondition = d.remoteUpdateTime
            Resource.Success(d.copy(remoteUpdateTime = "new-token"))
        })

        val result = SaveDraftUseCase(repo)(draft)

        assertEquals("old-token", capturedPrecondition)
        assertEquals("new-token", (result as Resource.Success).data.remoteUpdateTime)
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
