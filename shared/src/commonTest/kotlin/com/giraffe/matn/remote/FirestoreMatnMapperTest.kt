package com.giraffe.matn.remote

import com.giraffe.matn.data.remote.firestore.matnDraftFromFields
import com.giraffe.matn.data.remote.firestore.toFirestoreFields
import com.giraffe.matn.domain.catalog.DraftChapter
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.model.StructureKind
import kotlin.test.Test
import kotlin.test.assertEquals

class FirestoreMatnMapperTest {

    @Test
    fun `a SIMPLE matn round-trips through Firestore fields`() {
        val draft = MatnDraft(
            id = "m1",
            title = "Title",
            author = "Author",
            description = "Desc",
            coverImageRef = "matns/m1/cover.png",
            structureKind = StructureKind.SIMPLE,
            defaultReciterId = "reciter-1",
            chapters = emptyList(),
            verses = listOf(
                DraftVerse(id = "v1", chapterId = null, displayNumber = 1, arabicText = "بسم الله", audio = null, durationMs = 0L),
            ),
            publicationState = PublicationState.DRAFT,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_001_000L,
            remoteUpdateTime = "token-1",
        )

        val fields = draft.toFirestoreFields()
        val roundTripped = matnDraftFromFields(id = draft.id, fields = fields, updateTime = draft.remoteUpdateTime)

        assertEquals(draft, roundTripped)
    }

    @Test
    fun `a STRUCTURED matn with chapters round-trips through Firestore fields`() {
        val draft = MatnDraft(
            id = "m2",
            title = "Title 2",
            author = "Author 2",
            description = "",
            coverImageRef = null,
            structureKind = StructureKind.STRUCTURED,
            defaultReciterId = "reciter-2",
            chapters = listOf(DraftChapter(id = "c1", title = "Chapter 1", order = 0)),
            verses = listOf(
                DraftVerse(id = "v1", chapterId = "c1", displayNumber = 1, arabicText = "نص أول", audio = null, durationMs = 0L),
                DraftVerse(id = "v2", chapterId = "c1", displayNumber = 2, arabicText = "نص ثاني", audio = null, durationMs = 0L),
            ),
            publicationState = PublicationState.PUBLISHED,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_002_000L,
            remoteUpdateTime = null,
        )

        val fields = draft.toFirestoreFields()
        val roundTripped = matnDraftFromFields(id = draft.id, fields = fields, updateTime = draft.remoteUpdateTime)

        assertEquals(draft, roundTripped)
    }
}
