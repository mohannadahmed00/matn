package com.giraffe.matn.catalog

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.domain.usecase.ValidateMatnUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidateMatnUseCaseTest {

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

    private fun verse(id: String, number: Int) = DraftVerse(id, null, number, "text", null, 0L)

    @Test
    fun `an empty matn cannot publish`() = runTest {
        val report = (ValidateMatnUseCase()(draft()) as Resource.Success).data
        assertFalse(report.canPublish)
    }

    @Test
    fun `an audio-less matn can publish with deferred populated`() = runTest {
        val report = (ValidateMatnUseCase()(draft(listOf(verse("v1", 1), verse("v2", 2)))) as Resource.Success).data
        assertTrue(report.canPublish)
        assertTrue(report.deferred.isNotEmpty())
    }

    @Test
    fun `a duplicate display number blocks`() = runTest {
        val report = (ValidateMatnUseCase()(draft(listOf(verse("v1", 1), verse("v2", 1)))) as Resource.Success).data
        assertFalse(report.canPublish)
    }
}
