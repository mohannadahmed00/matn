package com.giraffe.matn.audio

import com.giraffe.matn.domain.catalog.DraftAudio
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.VerseOrdering
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun audio(fileRef: String) =
    DraftAudio(id = "a-$fileRef", fileRef = fileRef, durationMs = 1000, sizeBytes = 10, sampleRate = 44100, channels = 1)

/**
 * True today by construction — [VerseOrdering] carries the whole [DraftVerse] (audio included)
 * when it reorders, so a `fileRef` travels with its verse rather than staying pinned to a display
 * number. This test is what stops a later reorder refactor (e.g. one that renumbers by mutating a
 * parallel array) from silently breaking that (FR-032).
 */
class VerseAudioOrderingTest {

    @Test
    fun `moving a verse carries its fileRef with it and renumbers 1 through n`() {
        val verses = (1..5).map { i ->
            DraftVerse(id = "v$i", chapterId = null, displayNumber = i, arabicText = "text $i", audio = null, durationMs = 0L)
        }
        val withAudio = verses.map { verse ->
            when (verse.id) {
                "v1" -> verse.copy(audio = audio("matns/m1/verses/v1-tag.mp3"))
                "v5" -> verse.copy(audio = audio("matns/m1/verses/v5-tag.mp3"))
                else -> verse
            }
        }

        // Move verse 5 (index 4) to position 2 (index 1).
        val reordered = VerseOrdering.move(withAudio, from = 4, to = 1)

        assertEquals(listOf(1, 2, 3, 4, 5), reordered.map { it.displayNumber })
        assertEquals("v5", reordered[1].id)
        assertEquals("matns/m1/verses/v5-tag.mp3", reordered[1].audio?.fileRef)
        val v1 = reordered.first { it.id == "v1" }
        assertEquals("matns/m1/verses/v1-tag.mp3", v1.audio?.fileRef)
        val untouched = reordered.filter { it.id !in setOf("v1", "v5") }
        assertEquals(3, untouched.size)
        assertNull(untouched.first().audio)
    }
}
