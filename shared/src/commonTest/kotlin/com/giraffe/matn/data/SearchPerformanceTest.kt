package com.giraffe.matn.data

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.repository.SearchRepositoryImpl
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.data.seed.SeedAudio
import com.giraffe.matn.data.seed.SeedMatn
import com.giraffe.matn.data.seed.SeedVerse
import com.giraffe.matn.newTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * SC-001 guard (contracts/search-contract.md § Tests): a single 2-word query over a
 * >=1,000-verse, >=3-متون corpus completes in < 1s. Flake guard per T017: if this proves
 * unstable on slow CI hosts, the assertion constant may be relaxed (documented), but the test
 * itself must never be deleted.
 */
class SearchPerformanceTest {

    @Test
    fun searchOver1000VersesUnder1Second() = runTest {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)

        val matnCount = 3
        val versesPerMatn = 400 // 3 * 400 = 1200 >= 1,000
        repeat(matnCount) { matnIndex ->
            val verses = (1..versesPerMatn).map { i ->
                SeedVerse(
                    id = "perf-search-verse-$matnIndex-$i",
                    displayNumber = i,
                    arabicText = "نص البيت رقم $i من المتن رقم $matnIndex في اختبار الأداء",
                    durationMs = 1_000L + i,
                    audio = SeedAudio(
                        id = "perf-search-audio-$matnIndex-$i",
                        fileRef = "perf_search_${matnIndex}_$i.mp3",
                        durationMs = 1_000L + i,
                    ),
                )
            }
            val payload = SeedMatn(
                id = "perf-search-matn-$matnIndex",
                title = "متن الأداء رقم $matnIndex",
                author = "مؤلف الأداء",
                description = "اختبار أداء البحث",
                structureKind = "SIMPLE",
                defaultReciterId = "reciter-perf",
                verses = verses,
            )
            val result = loader.load(payload)
            assertTrue(result is Resource.Success)
        }

        val repo = SearchRepositoryImpl(db)
        val mark = TimeSource.Monotonic.markNow()
        val results = repo.search("نص البيت").first()
        val elapsedMs = mark.elapsedNow().inWholeMilliseconds

        assertTrue(results.isNotEmpty())
        assertTrue(
            elapsedMs < 1000,
            "search over ${matnCount * versesPerMatn} verses took $elapsedMs ms; must be < 1000 ms (SC-001).",
        )
    }
}
