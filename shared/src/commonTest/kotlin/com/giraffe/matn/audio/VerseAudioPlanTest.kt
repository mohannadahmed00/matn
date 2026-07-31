package com.giraffe.matn.audio

import com.giraffe.matn.domain.audio.PendingUpload
import com.giraffe.matn.domain.audio.buildPlan
import com.giraffe.matn.domain.catalog.DraftAudio
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.model.StructureKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun audio(fileRef: String) =
    DraftAudio(id = "a-$fileRef", fileRef = fileRef, durationMs = 1000, sizeBytes = 10, sampleRate = 44100, channels = 1)

private fun verse(id: String, audio: DraftAudio? = null) =
    DraftVerse(id = id, chapterId = null, displayNumber = 1, arabicText = "t", audio = audio, durationMs = audio?.durationMs ?: 0L)

private fun matn(verses: List<DraftVerse>) = MatnDraft(
    id = "m1", title = "t", author = "a", description = "d", coverImageRef = null,
    structureKind = StructureKind.SIMPLE, defaultReciterId = "r1", chapters = emptyList(),
    verses = verses, publicationState = PublicationState.DRAFT, createdAt = 0L, updatedAt = 0L, remoteRevision = null,
)

private fun pendingUpload(verseId: String, objectPath: String, size: Int = 10) = PendingUpload(
    verseId = verseId,
    objectPath = objectPath,
    bytes = ByteArray(size),
    audio = audio(objectPath),
)

class VerseAudioPlanTest {

    @Test
    fun `an unchanged verse produces no upload`() {
        val before = matn(listOf(verse("v1", audio("ref1"))))
        val after = before

        val plan = buildPlan(before, after, payloads = emptyList(), existingObjects = mapOf("ref1" to 10L))

        assertTrue(plan.uploads.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun `a replaced verse produces one upload and one delete`() {
        val before = matn(listOf(verse("v1", audio("ref-old"))))
        val after = matn(listOf(verse("v1", audio("ref-new"))))
        val payload = pendingUpload("v1", "ref-new")

        val plan = buildPlan(before, after, payloads = listOf(payload), existingObjects = emptyMap())

        assertEquals(listOf(payload), plan.uploads)
        assertEquals(listOf("ref-old"), plan.deletes)
    }

    @Test
    fun `a removed verse produces only a delete`() {
        val before = matn(listOf(verse("v1", audio("ref1")), verse("v2", audio("ref2"))))
        val after = matn(listOf(verse("v1", audio("ref1"))))

        val plan = buildPlan(before, after, payloads = emptyList(), existingObjects = emptyMap())

        assertTrue(plan.uploads.isEmpty())
        assertEquals(listOf("ref2"), plan.deletes)
    }

    @Test
    fun `an already-present object of the right size is skipped`() {
        val payload = pendingUpload("v1", "ref-new", size = 10)

        val plan = buildPlan(matn(emptyList()), matn(emptyList()), payloads = listOf(payload), existingObjects = mapOf("ref-new" to 10L))

        assertEquals(listOf("ref-new"), plan.skips)
        assertTrue(plan.uploads.isEmpty())
    }

    @Test
    fun `an existing object of the wrong size is not skipped`() {
        val payload = pendingUpload("v1", "ref-new", size = 10)

        val plan = buildPlan(matn(emptyList()), matn(emptyList()), payloads = listOf(payload), existingObjects = mapOf("ref-new" to 3L))

        assertEquals(listOf(payload), plan.uploads)
        assertTrue(plan.skips.isEmpty())
    }
}
