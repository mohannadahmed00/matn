package com.giraffe.matn.catalog

import com.giraffe.matn.testseed.SeedAudio
import com.giraffe.matn.testseed.SeedChapter
import com.giraffe.matn.testseed.SeedMatn
import com.giraffe.matn.testseed.SeedVerse
import com.giraffe.matn.testseed.toDraft
import com.giraffe.matn.testseed.toSeedMatn
import kotlin.test.Test
import kotlin.test.assertEquals

class SeedMatnProjectionTest {

    @Test
    fun `SIMPLE matn round-trips every field through MatnDraft`() {
        val seed = SeedMatn(
            id = "m1",
            title = "Title",
            author = "Author",
            description = "Desc",
            coverImageRef = "cover.png",
            structureKind = "SIMPLE",
            defaultReciterId = "reciter-1",
            chapters = emptyList(),
            verses = listOf(
                SeedVerse(
                    id = "v1",
                    chapterId = null,
                    displayNumber = 1,
                    arabicText = "بسم الله",
                    durationMs = 0L,
                    audio = SeedAudio(id = "a1", fileRef = "ref1", durationMs = 100L),
                ),
            ),
        )

        val roundTripped = seed.toDraft().toSeedMatn(packId = seed.packId, declaredSizeBytes = seed.declaredSizeBytes)

        assertEquals(seed, roundTripped)
    }

    @Test
    fun `STRUCTURED matn with chapters round-trips every field through MatnDraft`() {
        val seed = SeedMatn(
            id = "m2",
            title = "Title 2",
            author = "Author 2",
            description = "",
            coverImageRef = null,
            structureKind = "STRUCTURED",
            defaultReciterId = "reciter-2",
            chapters = listOf(SeedChapter(id = "c1", title = "Chapter 1", order = 0)),
            verses = listOf(
                SeedVerse(id = "v1", chapterId = "c1", displayNumber = 1, arabicText = "نص أول", durationMs = 0L),
                SeedVerse(id = "v2", chapterId = "c1", displayNumber = 2, arabicText = "نص ثاني", durationMs = 0L),
            ),
        )

        val roundTripped = seed.toDraft().toSeedMatn(packId = seed.packId, declaredSizeBytes = seed.declaredSizeBytes)

        assertEquals(seed, roundTripped)
    }
}
