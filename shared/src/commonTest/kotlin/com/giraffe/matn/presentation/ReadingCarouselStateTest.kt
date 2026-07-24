package com.giraffe.matn.presentation

import com.giraffe.matn.presentation.details.VerseRow
import com.giraffe.matn.presentation.player.windowVersesForCarousel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure windowing tests for [windowVersesForCarousel] —
 * specs/010-design-system-adoption/spec.md Edge Cases (first/last verse, no active verse).
 * No fakes, no coroutines (Constitution Principle V).
 */
class ReadingCarouselStateTest {

    private fun verse(id: String, number: Int) =
        VerseRow(id = id, displayNumber = number, arabicText = "verse $number", durationMs = 1000, chapterId = null)

    private val verses = listOf(verse("v1", 1), verse("v2", 2), verse("v3", 3))

    @Test
    fun midMatn_hasBothNeighbors() {
        val state = windowVersesForCarousel(verses, activeVerseId = "v2")
        assertEquals("v1", state?.previousVerse?.id)
        assertEquals("v2", state?.activeVerse?.id)
        assertEquals("v3", state?.nextVerse?.id)
    }

    @Test
    fun firstVerse_hasNoPrevious() {
        val state = windowVersesForCarousel(verses, activeVerseId = "v1")
        assertNull(state?.previousVerse)
        assertEquals("v1", state?.activeVerse?.id)
        assertEquals("v2", state?.nextVerse?.id)
    }

    @Test
    fun lastVerse_hasNoNext() {
        val state = windowVersesForCarousel(verses, activeVerseId = "v3")
        assertEquals("v2", state?.previousVerse?.id)
        assertEquals("v3", state?.activeVerse?.id)
        assertNull(state?.nextVerse)
    }

    @Test
    fun singleVerseMatn_hasNoNeighbors() {
        val state = windowVersesForCarousel(listOf(verse("only", 1)), activeVerseId = "only")
        assertNull(state?.previousVerse)
        assertEquals("only", state?.activeVerse?.id)
        assertNull(state?.nextVerse)
    }

    @Test
    fun noActiveVerse_returnsNull() {
        assertNull(windowVersesForCarousel(verses, activeVerseId = null))
    }

    @Test
    fun activeVerseNotInList_returnsNull() {
        assertNull(windowVersesForCarousel(verses, activeVerseId = "missing"))
    }
}
