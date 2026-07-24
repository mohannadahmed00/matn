package com.giraffe.matn.data

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.repository.ProgressRepositoryImpl
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.data.seed.SeedAudio
import com.giraffe.matn.data.seed.SeedChapter
import com.giraffe.matn.data.seed.SeedMatn
import com.giraffe.matn.data.seed.SeedVerse
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.inMemoryDriver
import com.giraffe.matn.newTestDatabase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * contracts/progress-contract.md § Test obligations (specs/007-progress-daily-goals T013/T013a).
 */
class ProgressRepositoryTest {

    private suspend fun seededDb(): ContentDatabase {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        val verses = listOf(
            SeedVerse(id = "v1", displayNumber = 1, arabicText = "بيت واحد", durationMs = 1000, audio = SeedAudio("a1", "v1.mp3", 1000)),
            SeedVerse(id = "v2", displayNumber = 2, arabicText = "بيت اثنان", durationMs = 1000, audio = SeedAudio("a2", "v2.mp3", 1000)),
            SeedVerse(id = "v3", displayNumber = 3, arabicText = "بيت ثلاثة", durationMs = 1000, audio = SeedAudio("a3", "v3.mp3", 1000)),
        )
        val payload = SeedMatn(
            id = "m1", title = "متن", author = "مؤلف", description = "",
            structureKind = "SIMPLE", defaultReciterId = "r1", verses = verses,
        )
        assertTrue(loader.load(payload) is Resource.Success)
        return db
    }

    private suspend fun structuredDb(): ContentDatabase {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        val payload = SeedMatn(
            id = "m-structured", title = "متن مبوب", author = "مؤلف", description = "",
            structureKind = "STRUCTURED", defaultReciterId = "r1",
            chapters = listOf(SeedChapter(id = "c1", title = "باب", order = 1)),
            verses = listOf(
                SeedVerse(id = "cv1", chapterId = "c1", displayNumber = 1, arabicText = "بيت1", durationMs = 1000, audio = SeedAudio("ca1", "cv1.mp3", 1000)),
                SeedVerse(id = "cv2", chapterId = "c1", displayNumber = 2, arabicText = "بيت2", durationMs = 1000, audio = SeedAudio("ca2", "cv2.mp3", 1000)),
                SeedVerse(id = "cv3", chapterId = "c1", displayNumber = 3, arabicText = "بيت3", durationMs = 1000, audio = SeedAudio("ca3", "cv3.mp3", 1000)),
            ),
        )
        assertTrue(loader.load(payload) is Resource.Success)
        return db
    }

    private suspend fun emptyMatnDb(): ContentDatabase {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        val payload = SeedMatn(
            id = "m-empty", title = "متن فارغ", author = "مؤلف", description = "",
            structureKind = "SIMPLE", defaultReciterId = "r1", verses = emptyList(),
        )
        assertTrue(loader.load(payload) is Resource.Success)
        return db
    }

    private fun repo(
        db: ContentDatabase,
        todayRef: LongArray = longArrayOf(19_000L),
        ids: MutableList<String> = (1..200).map { "id-$it" }.toMutableList(),
        clockStart: Long = 1_000L,
        dayCheckIntervalMs: Long = 60_000L,
    ): ProgressRepositoryImpl {
        var clockValue = clockStart
        val idQueue = ArrayDeque(ids)
        return ProgressRepositoryImpl(
            db = db,
            today = { todayRef[0] },
            clock = { clockValue++ },
            newId = { idQueue.removeFirst() },
            dayCheckIntervalMs = dayCheckIntervalMs,
        )
    }

    @Test
    fun mark_updates_indicator_set_and_matn_progress_fraction() = runTest {
        val db = seededDb()
        val repo = repo(db)

        repo.setVerseMemorized("v1", true)

        assertEquals(setOf("v1"), repo.observeMemorizedVerseIds("m1").first())
        val progress = repo.observeMatnProgress("m1").first()
        assertEquals(1, progress.memorizedCount)
        assertEquals(3, progress.totalCount)
    }

    @Test
    fun un_mark_removes_indicator_and_resets_progress_but_keeps_daily_count() = runTest {
        val db = seededDb()
        val repo = repo(db)

        repo.setVerseMemorized("v1", true)
        val countAfterMark = repo.observeTodayPracticeCount().first()

        repo.setVerseMemorized("v1", false)

        assertEquals(emptySet(), repo.observeMemorizedVerseIds("m1").first())
        val progress = repo.observeMatnProgress("m1").first()
        assertEquals(0, progress.memorizedCount)
        assertEquals(0f, progress.fraction)
        // FR-014: un-marking never drops the day's practice count.
        assertEquals(countAfterMark, repo.observeTodayPracticeCount().first())
    }

    @Test
    fun marking_credits_todays_practice_exactly_once() = runTest {
        val db = seededDb()
        val repo = repo(db)

        repo.setVerseMemorized("v1", true)

        assertEquals(1, repo.observeTodayPracticeCount().first())
    }

    @Test
    fun marking_same_verse_twice_credits_practice_once() = runTest {
        val db = seededDb()
        val repo = repo(db)

        repo.setVerseMemorized("v1", true)
        repo.setVerseMemorized("v1", true) // idempotent, no-op

        assertEquals(1, repo.observeTodayPracticeCount().first())
        assertEquals(1, repo.observeMatnProgress("m1").first().memorizedCount)
    }

    @Test
    fun chapter_bulk_mark_credits_only_newly_memorized_verses() = runTest {
        val db = structuredDb()
        val repo = repo(db)

        // Pre-existing state: cv1 already memorized (and already credited today).
        repo.setVerseMemorized("cv1", true)
        val countBeforeBulk = repo.observeTodayPracticeCount().first()
        assertEquals(1, countBeforeBulk)

        repo.setChapterMemorized("c1", true)

        assertEquals(setOf("cv1", "cv2", "cv3"), repo.observeMemorizedVerseIds("m-structured").first())
        // Only cv2 and cv3 are newly credited (cv1 was already counted).
        assertEquals(3, repo.observeTodayPracticeCount().first())

        repo.setChapterMemorized("c1", false)
        assertEquals(emptySet(), repo.observeMemorizedVerseIds("m-structured").first())
    }

    @Test
    fun zero_verse_matn_progress_is_zero_fraction_no_exception() = runTest {
        val db = emptyMatnDb()
        val repo = repo(db)

        val progress = repo.observeMatnProgress("m-empty").first()
        assertEquals(0, progress.totalCount)
        assertEquals(0f, progress.fraction)
    }

    @Test
    fun fully_memorized_matn_has_fraction_one() = runTest {
        val db = seededDb()
        val repo = repo(db)

        repo.setVerseMemorized("v1", true)
        repo.setVerseMemorized("v2", true)
        repo.setVerseMemorized("v3", true)

        assertEquals(1f, repo.observeMatnProgress("m1").first().fraction)
    }

    @Test
    fun deleting_verse_audio_leaves_memorization_intact() = runTest {
        val db = seededDb()
        val repo = repo(db)

        repo.setVerseMemorized("v1", true)
        db.contentQueries.deleteAudioByMatn("m1")

        assertEquals(setOf("v1"), repo.observeMemorizedVerseIds("m1").first())
    }

    @Test
    fun day_rollover_on_fresh_subscription_resets_count_but_not_memorization() = runTest {
        val db = seededDb()
        val todayRef = longArrayOf(19_000L)
        val repo = repo(db, todayRef = todayRef)

        repo.setVerseMemorized("v1", true)
        assertEquals(1, repo.observeTodayPracticeCount().first())

        todayRef[0] = 19_001L // a new local day

        assertEquals(0, repo.observeTodayPracticeCount().first())
        assertEquals(setOf("v1"), repo.observeMemorizedVerseIds("m1").first())
    }

    @Test
    fun record_practice_twice_same_day_one_row_different_days_two_rows() = runTest {
        val db = seededDb()
        val todayRef = longArrayOf(19_000L)
        val repo = repo(db, todayRef = todayRef)

        repo.recordPractice("v1")
        repo.recordPractice("v1")
        assertEquals(1, repo.observeTodayPracticeCount().first())

        todayRef[0] = 19_001L
        repo.recordPractice("v2")
        assertEquals(1, repo.observeTodayPracticeCount().first())

        assertEquals(1L, db.contentQueries.selectDailyPracticeCount(19_000L).executeAsOne())
        assertEquals(1L, db.contentQueries.selectDailyPracticeCount(19_001L).executeAsOne())
    }

    // --- T013a: four additional edge-case guards ---

    @Test
    fun rollover_while_collecting_resets_open_collector_without_resubscription() = runTest(UnconfinedTestDispatcher()) {
        val db = seededDb()
        val todayRef = longArrayOf(19_000L)
        val repo = repo(db, todayRef = todayRef, dayCheckIntervalMs = 1_000L)

        // A real Channel (not a plain list) — observeTodayPracticeCount()'s query mapping runs on
        // Dispatchers.Default (a real thread), so the emission needs a thread-safe handoff rather
        // than a value read racing an unrelated virtual-time advance.
        val emissions = Channel<Int>(Channel.UNLIMITED)
        val job = launch {
            repo.observeTodayPracticeCount().collect { emissions.send(it) }
        }

        assertEquals(0, emissions.receive()) // initial emission for day 19_000

        repo.recordPractice("v1")
        assertEquals(1, emissions.receive())

        todayRef[0] = 19_001L
        advanceTimeBy(1_001L)
        assertEquals(0, emissions.receive())

        repo.recordPractice("v2")
        assertEquals(1, emissions.receive())

        job.cancel()
    }

    @Test
    fun clock_time_zone_change_backwards_reemits_that_days_count() = runTest(UnconfinedTestDispatcher()) {
        val db = seededDb()
        val todayRef = longArrayOf(19_001L)
        val repo = repo(db, todayRef = todayRef, dayCheckIntervalMs = 1_000L)

        repo.recordPractice("v1") // credited under day 19_001

        val emissions = Channel<Int>(Channel.UNLIMITED)
        val job = launch {
            repo.observeTodayPracticeCount().collect { emissions.send(it) }
        }
        assertEquals(1, emissions.receive()) // initial emission for day 19_001

        todayRef[0] = 19_000L // device clock/time-zone moves back a day
        advanceTimeBy(1_001L)

        // Day 19_000 has no practiced verses yet — reads 0, never throws or "sticks".
        assertEquals(0, emissions.receive())

        job.cancel()
    }

    @Test
    fun rapid_toggling_settles_to_consistent_final_state() = runTest {
        val db = seededDb()
        val repo = repo(db)

        var last: Resource<Unit>? = null
        repeat(10) { i ->
            last = repo.setVerseMemorized("v1", memorized = i % 2 == 0)
        }
        assertTrue(last is Resource.Success)

        // 10 alternations starting at index 0 (true) end at index 9 (false).
        assertEquals(emptySet(), repo.observeMemorizedVerseIds("m1").first())
        assertNull(db.contentQueries.selectMemorizationByVerse("v1").executeAsOneOrNull())
    }

    @Test
    fun restart_persistence_through_fresh_query_object() = runTest {
        val driver = inMemoryDriver()
        val db = ContentDatabase(driver)
        val loader = ContentSeedLoaderImpl(db)
        assertTrue(
            loader.load(
                SeedMatn(
                    id = "m1", title = "متن", author = "مؤلف", description = "",
                    structureKind = "SIMPLE", defaultReciterId = "r1",
                    verses = listOf(SeedVerse(id = "v1", displayNumber = 1, arabicText = "بيت", durationMs = 1000, audio = SeedAudio("a1", "v1.mp3", 1000))),
                ),
            ) is Resource.Success,
        )
        val todayRef = longArrayOf(19_000L)
        val repo = repo(db, todayRef = todayRef)
        repo.setVerseMemorized("v1", true)

        // A second ContentDatabase instance wrapping the SAME driver — proves the rows live in
        // the driver's storage, not merely in the first query object's in-memory state.
        val reopened = ContentDatabase(driver)
        assertNotNull(reopened.contentQueries.selectMemorizationByVerse("v1").executeAsOneOrNull())
        assertEquals(1L, reopened.contentQueries.selectDailyPracticeCount(19_000L).executeAsOne())
    }
}
