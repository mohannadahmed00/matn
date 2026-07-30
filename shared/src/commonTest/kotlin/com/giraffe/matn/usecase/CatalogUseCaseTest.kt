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
import com.giraffe.matn.domain.usecase.ListAuthoredMatnsUseCase
import com.giraffe.matn.domain.usecase.UnpublishMatnUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class CatalogListFakeRepository(
    private val entries: List<CatalogEntry> = emptyList(),
    private val unpublishResult: (String) -> Resource<MatnDraft> = { Resource.Failure(AppError.NotFound) },
) : CatalogRepository {
    override fun observeAuthored(): Flow<List<CatalogEntry>> = flowOf(entries)
    override suspend fun load(matnId: String): Resource<MatnDraft> = Resource.Failure(AppError.NotFound)
    override suspend fun save(draft: MatnDraft): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun publish(draft: MatnDraft): Resource<MatnDraft> = Resource.Success(draft)
    override suspend fun unpublish(matnId: String): Resource<MatnDraft> = unpublishResult(matnId)
    override suspend fun uploadCover(matnId: String, bytes: ByteArray, ext: String): Resource<String> = Resource.Success("ref")
}

private fun entry(id: String, state: PublicationState) = CatalogEntry(
    id = id,
    title = "T",
    author = "A",
    description = "",
    coverImageRef = null,
    verseCount = 1,
    declaredSizeBytes = 0L,
    publicationState = state,
    audioCompleteness = AudioCompleteness.NONE,
    updatedAt = 0L,
)

class CatalogUseCaseTest {

    @Test
    fun `ListAuthoredMatnsUseCase emits entries with both publication states`() = runTest {
        val entries = listOf(entry("m1", PublicationState.DRAFT), entry("m2", PublicationState.PUBLISHED))
        val useCase = ListAuthoredMatnsUseCase(CatalogListFakeRepository(entries = entries))

        assertEquals(entries, useCase(Unit).first())
    }

    @Test
    fun `ListAuthoredMatnsUseCase emits an empty list without error`() = runTest {
        val useCase = ListAuthoredMatnsUseCase(CatalogListFakeRepository(entries = emptyList()))

        assertEquals(emptyList(), useCase(Unit).first())
    }

    @Test
    fun `UnpublishMatnUseCase returns a draft whose id and verse ids are unchanged`() = runTest {
        val published = MatnDraft(
            id = "m1",
            title = "T",
            author = "A",
            description = "",
            coverImageRef = null,
            structureKind = StructureKind.SIMPLE,
            defaultReciterId = "r1",
            chapters = emptyList(),
            verses = listOf(DraftVerse("v1", null, 1, "text", null, 0L)),
            publicationState = PublicationState.PUBLISHED,
            createdAt = 0L,
            updatedAt = 0L,
            remoteRevision = "t1",
        )
        val repo = CatalogListFakeRepository(unpublishResult = { Resource.Success(published.copy(publicationState = PublicationState.DRAFT)) })

        val result = (UnpublishMatnUseCase(repo)("m1") as Resource.Success).data

        assertEquals(PublicationState.DRAFT, result.publicationState)
        assertEquals("m1", result.id)
        assertEquals(listOf("v1"), result.verses.map { it.id })
    }
}
