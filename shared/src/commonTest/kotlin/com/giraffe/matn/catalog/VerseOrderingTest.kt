package com.giraffe.matn.catalog

import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.VerseOrdering
import kotlin.test.Test
import kotlin.test.assertEquals

class VerseOrderingTest {

    private fun verse(id: String) = DraftVerse(id = id, chapterId = null, displayNumber = 0, arabicText = id, audio = null, durationMs = 0L)

    private fun verses(vararg ids: String) = ids.map { verse(it) }

    private fun idsAndNumbers(list: List<DraftVerse>) = list.map { it.id to it.displayNumber }

    @Test
    fun `move forward renumbers 1 through n with no gaps`() {
        val result = VerseOrdering.move(verses("a", "b", "c", "d"), from = 0, to = 2)
        assertEquals(listOf("b" to 1, "c" to 2, "a" to 3, "d" to 4), idsAndNumbers(result))
    }

    @Test
    fun `move backward renumbers 1 through n with no gaps`() {
        val result = VerseOrdering.move(verses("a", "b", "c", "d"), from = 3, to = 1)
        assertEquals(listOf("a" to 1, "d" to 2, "b" to 3, "c" to 4), idsAndNumbers(result))
    }

    @Test
    fun `move to first renumbers 1 through n with no gaps`() {
        val result = VerseOrdering.move(verses("a", "b", "c"), from = 2, to = 0)
        assertEquals(listOf("c" to 1, "a" to 2, "b" to 3), idsAndNumbers(result))
    }

    @Test
    fun `move to last renumbers 1 through n with no gaps`() {
        val result = VerseOrdering.move(verses("a", "b", "c"), from = 0, to = 2)
        assertEquals(listOf("b" to 1, "c" to 2, "a" to 3), idsAndNumbers(result))
    }

    @Test
    fun `delete from the middle renumbers 1 through n with no gaps`() {
        val result = VerseOrdering.removeAt(verses("a", "b", "c", "d"), index = 1)
        assertEquals(listOf("a" to 1, "c" to 2, "d" to 3), idsAndNumbers(result))
    }

    @Test
    fun `add at the end renumbers 1 through n with no gaps`() {
        val result = VerseOrdering.append(verses("a", "b"), verse("c"))
        assertEquals(listOf("a" to 1, "b" to 2, "c" to 3), idsAndNumbers(result))
    }
}
