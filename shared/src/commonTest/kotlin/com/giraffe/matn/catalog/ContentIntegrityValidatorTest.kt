package com.giraffe.matn.catalog

import com.giraffe.matn.domain.catalog.AudioCompleteness
import com.giraffe.matn.domain.catalog.ContentIntegrityValidator
import com.giraffe.matn.domain.catalog.DraftAudio
import com.giraffe.matn.domain.catalog.DraftChapter
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.error.ContentIntegrityError
import com.giraffe.matn.domain.model.StructureKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentIntegrityValidatorTest {

    private fun draft(
        chapters: List<DraftChapter> = emptyList(),
        verses: List<DraftVerse> = emptyList(),
        structureKind: StructureKind = StructureKind.SIMPLE,
    ): MatnDraft = MatnDraft(
        id = "matn-1",
        title = "Title",
        author = "Author",
        description = "",
        coverImageRef = null,
        structureKind = structureKind,
        defaultReciterId = "reciter-1",
        chapters = chapters,
        verses = verses,
        publicationState = PublicationState.DRAFT,
        createdAt = 0L,
        updatedAt = 0L,
        remoteUpdateTime = null,
    )

    private fun verse(id: String, displayNumber: Int, chapterId: String? = null, audio: DraftAudio? = null) =
        DraftVerse(id = id, chapterId = chapterId, displayNumber = displayNumber, arabicText = "text", audio = audio, durationMs = 0L)

    @Test
    fun `V1 InvalidId flags a blank id as blocking`() {
        val report = ContentIntegrityValidator.validate(draft(verses = listOf(verse(id = "", displayNumber = 1))))
        assertTrue(report.blocking.any { it is ContentIntegrityError.InvalidId })
    }

    @Test
    fun `V1b DuplicateId flags two entities sharing an id as blocking`() {
        val report = ContentIntegrityValidator.validate(
            draft(verses = listOf(verse(id = "v1", displayNumber = 1), verse(id = "v1", displayNumber = 2))),
        )
        assertTrue(report.blocking.any { it is ContentIntegrityError.DuplicateId })
    }

    @Test
    fun `V2 DuplicateDisplayNumber flags two verses sharing a number as blocking`() {
        val report = ContentIntegrityValidator.validate(
            draft(verses = listOf(verse(id = "v1", displayNumber = 1), verse(id = "v2", displayNumber = 1))),
        )
        assertTrue(report.blocking.any { it is ContentIntegrityError.DuplicateDisplayNumber })
    }

    @Test
    fun `V2b DuplicateChapterOrder flags two chapters sharing an order as blocking`() {
        val report = ContentIntegrityValidator.validate(
            draft(
                structureKind = StructureKind.STRUCTURED,
                chapters = listOf(DraftChapter("c1", "Ch1", 0), DraftChapter("c2", "Ch2", 0)),
                verses = listOf(verse(id = "v1", displayNumber = 1, chapterId = "c1")),
            ),
        )
        assertTrue(report.blocking.any { it is ContentIntegrityError.DuplicateChapterOrder })
    }

    @Test
    fun `V4 MissingAudio is evaluated and routed to deferred`() {
        val report = ContentIntegrityValidator.validate(draft(verses = listOf(verse(id = "v1", displayNumber = 1))))
        assertTrue(report.deferred.any { it is ContentIntegrityError.MissingAudio })
        assertTrue(report.blocking.none { it is ContentIntegrityError.MissingAudio })
    }

    @Test
    fun `V5 DuplicateAudioRef is evaluated and routed to deferred`() {
        val audio = DraftAudio(id = "a", fileRef = "ref", durationMs = 0L)
        val report = ContentIntegrityValidator.validate(
            draft(
                verses = listOf(
                    verse(id = "v1", displayNumber = 1, audio = audio.copy(id = "a1")),
                    verse(id = "v2", displayNumber = 2, audio = audio.copy(id = "a2")),
                ),
            ),
        )
        assertTrue(report.deferred.any { it is ContentIntegrityError.DuplicateAudioRef })
    }

    @Test
    fun `V6 OrphanChapterRef flags a verse naming a nonexistent chapter as blocking`() {
        val report = ContentIntegrityValidator.validate(
            draft(
                structureKind = StructureKind.STRUCTURED,
                chapters = listOf(DraftChapter("c1", "Ch1", 0)),
                verses = listOf(verse(id = "v1", displayNumber = 1, chapterId = "missing")),
            ),
        )
        assertTrue(report.blocking.any { it is ContentIntegrityError.OrphanChapterRef })
    }

    @Test
    fun `V7 StructureMismatch flags a STRUCTURED matn with no chapters as blocking`() {
        val report = ContentIntegrityValidator.validate(
            draft(structureKind = StructureKind.STRUCTURED, verses = listOf(verse(id = "v1", displayNumber = 1))),
        )
        assertTrue(report.blocking.any { it is ContentIntegrityError.StructureMismatch })
    }

    @Test
    fun `V8 EmptyMatn flags a matn with no verses as blocking`() {
        val report = ContentIntegrityValidator.validate(draft())
        assertTrue(report.blocking.any { it is ContentIntegrityError.EmptyMatn })
    }

    @Test
    fun `V9 DocumentTooLarge flags a projected document over the size guard as blocking`() {
        val hugeText = "a".repeat(1_000_000)
        val report = ContentIntegrityValidator.validate(
            draft(verses = listOf(DraftVerse("v1", null, 1, hugeText, null, 0L))),
        )
        assertTrue(report.blocking.any { it is ContentIntegrityError.DocumentTooLarge })
    }

    @Test
    fun `a valid text-only matn can publish with deferred MissingAudio per verse`() {
        val report = ContentIntegrityValidator.validate(
            draft(verses = listOf(verse(id = "v1", displayNumber = 1), verse(id = "v2", displayNumber = 2))),
        )
        assertEquals(emptyList(), report.blocking)
        assertEquals(2, report.deferred.count { it is ContentIntegrityError.MissingAudio })
        assertTrue(report.canPublish)
    }

    @Test
    fun `AudioCompleteness of an empty verse list is NONE`() {
        assertEquals(AudioCompleteness.NONE, AudioCompleteness.of(emptyList()))
    }

    @Test
    fun `AudioCompleteness with no verse carrying audio is NONE`() {
        val verses = listOf(verse(id = "v1", displayNumber = 1), verse(id = "v2", displayNumber = 2))
        assertEquals(AudioCompleteness.NONE, AudioCompleteness.of(verses))
    }

    @Test
    fun `AudioCompleteness with some verses carrying audio is PARTIAL`() {
        val audio = DraftAudio(id = "a1", fileRef = "ref", durationMs = 0L)
        val verses = listOf(verse(id = "v1", displayNumber = 1, audio = audio), verse(id = "v2", displayNumber = 2))
        assertEquals(AudioCompleteness.PARTIAL, AudioCompleteness.of(verses))
    }

    @Test
    fun `AudioCompleteness with every verse carrying audio is COMPLETE`() {
        val audio = DraftAudio(id = "a1", fileRef = "ref", durationMs = 0L)
        val verses = listOf(
            verse(id = "v1", displayNumber = 1, audio = audio.copy(id = "a1")),
            verse(id = "v2", displayNumber = 2, audio = audio.copy(id = "a2")),
        )
        assertEquals(AudioCompleteness.COMPLETE, AudioCompleteness.of(verses))
    }
}
