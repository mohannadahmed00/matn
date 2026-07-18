package com.giraffe.matn.data

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.data.seed.SeedAudio
import com.giraffe.matn.data.seed.SeedMatn
import com.giraffe.matn.data.seed.SeedVerse
import com.giraffe.matn.data.repository.VerseRepositoryImpl
import com.giraffe.matn.newTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.time.TimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceTest {

    @Test
    fun observe500VersesUnder1Second() = runTest {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        val verseRepo = VerseRepositoryImpl(db)

        val verseCount = 500
        val verses = (1..verseCount).map { i ->
            SeedVerse(
                id = "perf-verse-$i",
                chapterId = null,
                displayNumber = i,
                arabicText = "نص البيت رقم $i",
                durationMs = 1_000L + i,
                audio = SeedAudio(
                    id = "perf-audio-$i",
                    fileRef = "perf_verse_${i}.mp3",
                    durationMs = 1_000L + i,
                ),
            )
        }
        val payload = SeedMatn(
            id = "perf-matn-001",
            title = "متن الأداء",
            author = "مؤلف الأداء",
            description = "اختبار الأداء لخمسمائة بيت",
            coverImageRef = null,
            structureKind = "SIMPLE",
            defaultReciterId = "reciter-default-v1",
            chapters = emptyList(),
            verses = verses,
        )

        val loadResult = loader.load(payload)
        assertTrue(loadResult is Resource.Success)

        val mark = TimeSource.Monotonic.markNow()
        val list = verseRepo.observeVerses(payload.id).first()
        assertEquals(verseCount, list.size)
        val elapsedMs = mark.elapsedNow().inWholeMilliseconds

        assertTrue(
            elapsedMs < 1000,
            "observeVerses(500) took $elapsedMs ms; must be < 1000 ms (SC-006).",
        )
    }
}