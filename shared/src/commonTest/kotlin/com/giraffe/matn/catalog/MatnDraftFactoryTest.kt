package com.giraffe.matn.catalog

import com.giraffe.matn.domain.catalog.MatnDraftFactory
import com.giraffe.matn.domain.catalog.PublicationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class MatnDraftFactoryTest {

    private var counter = 0
    private fun newId(): String = "id-${counter++}"

    @Test
    fun `two newDraft calls produce distinct ids`() {
        val a = MatnDraftFactory.newDraft(newId = ::newId, nowMillis = { 1L })
        val b = MatnDraftFactory.newDraft(newId = ::newId, nowMillis = { 1L })
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun `defaultReciterId is never blank and identical across calls`() {
        val a = MatnDraftFactory.newDraft(newId = ::newId, nowMillis = { 1L })
        val b = MatnDraftFactory.newDraft(newId = ::newId, nowMillis = { 1L })
        assertFalse(a.defaultReciterId.isBlank())
        assertEquals(a.defaultReciterId, b.defaultReciterId)
    }

    @Test
    fun `createdAt equals updatedAt on a fresh draft`() {
        val draft = MatnDraftFactory.newDraft(newId = ::newId, nowMillis = { 42L })
        assertEquals(draft.createdAt, draft.updatedAt)
    }

    @Test
    fun `a fresh draft is DRAFT with no remoteUpdateTime`() {
        val draft = MatnDraftFactory.newDraft(newId = ::newId, nowMillis = { 1L })
        assertEquals(PublicationState.DRAFT, draft.publicationState)
        assertNull(draft.remoteUpdateTime)
    }
}
