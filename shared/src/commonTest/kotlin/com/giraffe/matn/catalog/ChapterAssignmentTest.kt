package com.giraffe.matn.catalog

import com.giraffe.matn.domain.catalog.ChapterAssignment
import com.giraffe.matn.domain.catalog.DraftChapter
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.model.StructureKind
import kotlin.test.Test
import kotlin.test.assertEquals

private fun draft(chapters: List<DraftChapter>, verseCount: Int, chapterIds: List<String?> = List(verseCount) { null }) = MatnDraft(
    id = "m1",
    title = "T",
    author = "A",
    description = "",
    coverImageRef = null,
    structureKind = StructureKind.STRUCTURED,
    defaultReciterId = "",
    chapters = chapters,
    verses = (1..verseCount).map { n ->
        DraftVerse(id = "v$n", chapterId = chapterIds[n - 1], displayNumber = n, arabicText = "t$n", audio = null, durationMs = 0L)
    },
    publicationState = PublicationState.DRAFT,
    createdAt = 0L,
    updatedAt = 0L,
    remoteRevision = null,
)

private fun MatnDraft.ownerOf(verseNumber: Int): String? = verses.first { it.displayNumber == verseNumber }.chapterId

class ChapterAssignmentTest {

    @Test
    fun `a verse belongs to the last chapter that starts at or before it`() {
        val result = ChapterAssignment.apply(
            draft(
                chapters = listOf(
                    DraftChapter("c1", "One", order = 0, startVerseNumber = 1),
                    DraftChapter("c2", "Two", order = 1, startVerseNumber = 3),
                ),
                verseCount = 4,
            ),
        )

        assertEquals("c1", result.ownerOf(1))
        assertEquals("c1", result.ownerOf(2))
        assertEquals("c2", result.ownerOf(3))
        assertEquals("c2", result.ownerOf(4))
    }

    /** A preamble before chapter one is ordinary, so "belongs to no chapter" has to be expressible.
     * The publish-time validator is where that becomes a problem, if it is one. */
    @Test
    fun `verses ahead of the first chapter belong to none`() {
        val result = ChapterAssignment.apply(
            draft(chapters = listOf(DraftChapter("c1", "One", order = 0, startVerseNumber = 3)), verseCount = 4),
        )

        assertEquals(null, result.ownerOf(1))
        assertEquals(null, result.ownerOf(2))
        assertEquals("c1", result.ownerOf(3))
    }

    /** Chapters entered out of sequence are reordered by where they start, so `order` can never
     * describe a different layout from the starts — and never collides, which the validator treats
     * as blocking. */
    @Test
    fun `order follows the starts, however the chapters were entered`() {
        val result = ChapterAssignment.apply(
            draft(
                chapters = listOf(
                    DraftChapter("late", "Later", order = 0, startVerseNumber = 5),
                    DraftChapter("early", "Earlier", order = 1, startVerseNumber = 2),
                ),
                verseCount = 6,
            ),
        )

        assertEquals(listOf("early" to 0, "late" to 1), result.chapters.map { it.id to it.order })
        assertEquals("early", result.ownerOf(4))
        assertEquals("late", result.ownerOf(5))
    }

    /** A chapter with no start owns nothing — it has not been placed yet, and guessing somewhere
     * for it would quietly claim verses the teacher never assigned. */
    @Test
    fun `a chapter without a start claims no verses and sorts last`() {
        val result = ChapterAssignment.apply(
            draft(
                chapters = listOf(
                    DraftChapter("placed", "Placed", order = 0, startVerseNumber = 1),
                    DraftChapter("unplaced", "Unplaced", order = 1, startVerseNumber = null),
                ),
                verseCount = 2,
            ),
        )

        assertEquals("placed", result.ownerOf(1))
        assertEquals("placed", result.ownerOf(2))
        assertEquals(1, result.chapters.first { it.id == "unplaced" }.order)
    }

    /**
     * Chapter starts arrived after matns already existed whose verses carry a `chapterId` set some
     * other way. Recomputing from an empty set of starts would unassign every one of them, so a
     * draft where no chapter names a start is returned exactly as it came in.
     */
    @Test
    fun `a draft with no chapter starts is left untouched`() {
        val existing = draft(
            chapters = listOf(DraftChapter("c1", "One", order = 0, startVerseNumber = null)),
            verseCount = 2,
            chapterIds = listOf("c1", "c1"),
        )

        assertEquals(existing, ChapterAssignment.apply(existing))
    }
}
