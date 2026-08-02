package com.giraffe.matn.data

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.repository.ProgressRepositoryImpl
import com.giraffe.matn.testseed.TestContentSeeder
import com.giraffe.matn.testseed.SeedAudio
import com.giraffe.matn.testseed.SeedMatn
import com.giraffe.matn.testseed.SeedVerse
import com.giraffe.matn.newTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * SC-001 guard (contracts/progress-contract.md § Test obligations): marking a verse updates the
 * matn percentage within budget over a >=1,000-verse, >=3-متون corpus. Flake guard per T013b (same
 * policy as [SearchPerformanceTest]): if unstable on slow CI hosts, relax the constant and document
 * that SC-001's real 1s budget is verified by the quickstart device check — never delete the test.
 */
class ProgressPerformanceTest {

    @Test
    fun markingAVerseUpdatesProgressUnderBudget() = runTest(UnconfinedTestDispatcher()) {
        val db = newTestDatabase()
        val loader = TestContentSeeder(db)

        val matnCount = 3
        val versesPerMatn = 400 // 3 * 400 = 1200 >= 1,000
        repeat(matnCount) { matnIndex ->
            val verses = (1..versesPerMatn).map { i ->
                SeedVerse(
                    id = "perf-progress-verse-$matnIndex-$i",
                    displayNumber = i,
                    arabicText = "نص البيت رقم $i من المتن رقم $matnIndex في اختبار الأداء",
                    durationMs = 1_000L + i,
                    audio = SeedAudio(
                        id = "perf-progress-audio-$matnIndex-$i",
                        fileRef = "perf_progress_${matnIndex}_$i.mp3",
                        durationMs = 1_000L + i,
                    ),
                )
            }
            val payload = SeedMatn(
                id = "perf-progress-matn-$matnIndex",
                title = "متن الأداء رقم $matnIndex",
                author = "مؤلف الأداء",
                description = "اختبار أداء التقدم",
                structureKind = "SIMPLE",
                defaultReciterId = "reciter-perf",
                verses = verses,
            )
            assertTrue(loader.load(payload) is Resource.Success)
        }

        var clockValue = 1_000L
        var idCounter = 0
        val repo = ProgressRepositoryImpl(
            db = db,
            today = { 19_000L },
            clock = { clockValue++ },
            newId = { "perf-id-${idCounter++}" },
        )

        val targetMatnId = "perf-progress-matn-0"
        val targetVerseId = "perf-progress-verse-0-1"

        val initial = repo.observeMatnProgress(targetMatnId).first()
        assertTrue(initial.memorizedCount == 0)

        val mark = TimeSource.Monotonic.markNow()
        repo.setVerseMemorized(targetVerseId, true)
        val updated = repo.observeMatnProgress(targetMatnId).first()
        val elapsedMs = mark.elapsedNow().inWholeMilliseconds

        assertTrue(updated.memorizedCount == 1)
        assertTrue(
            elapsedMs < 1000,
            "marking a verse over ${matnCount * versesPerMatn} verses took $elapsedMs ms; must be < 1000 ms (SC-001).",
        )
    }
}
