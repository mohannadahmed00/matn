package com.giraffe.matn.data

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.repository.SessionStateRepositoryImpl
import com.giraffe.matn.testseed.TestContentSeeder
import com.giraffe.matn.domain.model.LoopRange
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.domain.model.RepetitionSettings
import com.giraffe.matn.domain.model.SavedMatnSession
import com.giraffe.matn.newTestDatabase
import com.giraffe.matn.parseSeed
import com.giraffe.matn.SIMPLE_MATN_JSON
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionStateRepositoryTest {

    private val matnId = "b3f1e2a4-0000-4000-8000-000000000001"
    private val v1 = "b3f1e2a4-0000-4000-8000-0000000000v1"
    private val v3 = "b3f1e2a4-0000-4000-8000-0000000000v3"

    private suspend fun seedMatn(db: com.giraffe.matn.db.ContentDatabase) {
        TestContentSeeder(db).load(parseSeed(SIMPLE_MATN_JSON))
    }

    @Test
    fun `getSession on unknown matn returns null without throwing`() = runTest {
        val repo = SessionStateRepositoryImpl(newTestDatabase())
        assertNull(repo.getSession(matnId))
    }

    @Test
    fun `putSession and getSession round trip`() = runTest {
        val db = newTestDatabase()
        val repo = SessionStateRepositoryImpl(db)
        seedMatn(db)
        val saved = SavedMatnSession(
            matnId = matnId,
            lastVerseId = v3,
            lastVerseDisplayNumber = 3,
            positionMs = 12_345L,
            settings = RepetitionSettings(
                verseRepeat = RepeatCount.of(7),
                matnRepeat = RepeatCount.Unlimited,
                loopRange = LoopRange(startVerseId = v1, endVerseId = v3),
            ),
        )
        val putResult = repo.putSession(saved)
        assertTrue(putResult is Resource.Success)

        val loaded = repo.getSession(matnId)
        assertNotNull(loaded)
        assertEquals(saved.matnId, loaded.matnId)
        assertEquals(saved.lastVerseId, loaded.lastVerseId)
        assertEquals(saved.lastVerseDisplayNumber, loaded.lastVerseDisplayNumber)
        assertEquals(saved.positionMs, loaded.positionMs)
        assertEquals(RepeatCount.of(7), loaded.settings.verseRepeat)
        assertEquals(RepeatCount.Unlimited, loaded.settings.matnRepeat)
        assertNotNull(loaded.settings.loopRange)
        assertEquals(v1, loaded.settings.loopRange?.startVerseId)
        assertEquals(v3, loaded.settings.loopRange?.endVerseId)
    }

    @Test
    fun `unlimited survives a full DB round trip as Unlimited`() = runTest {
        val db = newTestDatabase()
        val repo = SessionStateRepositoryImpl(db)
        seedMatn(db)
        repo.putSession(
            SavedMatnSession(
                matnId = matnId,
                lastVerseId = v1,
                lastVerseDisplayNumber = 1,
                positionMs = 0L,
                settings = RepetitionSettings(verseRepeat = RepeatCount.Unlimited, matnRepeat = RepeatCount.Unlimited),
            ),
        )
        val loaded = repo.getSession(matnId)!!
        assertTrue(loaded.settings.verseRepeat is RepeatCount.Unlimited, "verseRepeat must stay Unlimited")
        assertTrue(loaded.settings.matnRepeat is RepeatCount.Unlimited, "matnRepeat must stay Unlimited")
        // FR-007 guard: unlimited never degrades to a finite value.
        assertFalse { loaded.settings.matnRepeat == RepeatCount.of(1) }
        assertFalse { loaded.settings.verseRepeat == RepeatCount.of(1) }
    }

    @Test
    fun `session with no loop returns null loopRange`() = runTest {
        val db = newTestDatabase()
        val repo = SessionStateRepositoryImpl(db)
        seedMatn(db)
        repo.putSession(
            SavedMatnSession(
                matnId = matnId,
                lastVerseId = v1,
                lastVerseDisplayNumber = 1,
                positionMs = 0L,
                settings = RepetitionSettings(verseRepeat = RepeatCount.of(3), matnRepeat = RepeatCount.ONE),
            ),
        )
        val loaded = repo.getSession(matnId)!!
        assertNull(loaded.settings.loopRange)
    }

    @Test
    fun `putSession is an upsert keyed by matn_id - never duplicates`() = runTest {
        val db = newTestDatabase()
        val repo = SessionStateRepositoryImpl(db)
        seedMatn(db)
        repo.putSession(savedSession(positionMs = 1_000L))
        repo.putSession(savedSession(positionMs = 9_999L))

        val loaded = repo.getSession(matnId)!!
        assertEquals(9_999L, loaded.positionMs)
        // Exactly one row for this matn — the upsert updated in place.
        val rows = db.contentQueries.selectSession(matnId).executeAsList()
        assertEquals(1, rows.size)
    }

    @Test
    fun `clearLastListenedMatnId leaves every matn_session row intact`() = runTest {
        val db = newTestDatabase()
        val repo = SessionStateRepositoryImpl(db)
        seedMatn(db)
        repo.putSession(savedSession())
        repo.setLastListenedMatnId(matnId)
        // Sanity: pointer is set.
        assertEquals(matnId, repo.getLastListenedMatnId())

        // Dismiss.
        val dismiss = repo.clearLastListenedMatnId()
        assertTrue(dismiss is Resource.Success)

        // Pointer cleared …
        assertNull(repo.getLastListenedMatnId())
        // … but the session row is still fully readable (P2, FR-017a). This is the test that
        // stops a future refactor turning dismissal into data loss.
        val loaded = repo.getSession(matnId)
        assertNotNull(loaded, "Dismiss must never touch matn_session rows.")
        assertEquals(5_000L, loaded.positionMs)
        val rows = db.contentQueries.selectSession(matnId).executeAsList()
        assertEquals(1, rows.size)
    }

    @Test
    fun `getLastListenedMatnId is null when unset`() = runTest {
        val repo = SessionStateRepositoryImpl(newTestDatabase())
        assertNull(repo.getLastListenedMatnId())
    }

    @Test
    fun `setLastListenedMatnId then getLastListenedMatnId round trips`() = runTest {
        val db = newTestDatabase()
        val repo = SessionStateRepositoryImpl(db)
        seedMatn(db)
        repo.setLastListenedMatnId(matnId)
        assertEquals(matnId, repo.getLastListenedMatnId())
    }

    @Test
    fun `getLastListenedMatnId is null when the matn no longer exists`() = runTest {
        val db = newTestDatabase()
        val repo = SessionStateRepositoryImpl(db)
        seedMatn(db)
        repo.setLastListenedMatnId(matnId)
        // Delete the matn the way content operations do: children first (their FKs to matn are
        // NO ACTION), then the matn itself. The session row's FK to matn is ON DELETE CASCADE,
        // so this also drops the session row.
        db.contentQueries.deleteAudioByMatn(matnId)
        db.contentQueries.deleteVersesByMatn(matnId)
        db.contentQueries.deleteChaptersByMatn(matnId)
        db.contentQueries.deleteMatnById(matnId)
        assertNull(repo.getLastListenedMatnId())
    }

    @Test
    fun `observeContinueLearning emits null when pointer is unset`() = runTest {
        val db = newTestDatabase()
        val repo = SessionStateRepositoryImpl(db)
        seedMatn(db)
        assertNull(repo.observeContinueLearning().first())
    }

    @Test
    fun `observeContinueLearning emits entry when pointer and session and matn are present`() = runTest {
        val db = newTestDatabase()
        val repo = SessionStateRepositoryImpl(db)
        seedMatn(db)
        repo.putSession(savedSession())
        repo.setLastListenedMatnId(matnId)

        val entry = repo.observeContinueLearning().first()
        assertNotNull(entry)
        assertEquals(matnId, entry.matnId)
        assertEquals("الأجرومية", entry.matnTitle)
        assertEquals(3, entry.verseDisplayNumber)
        assertEquals(v3, entry.verseId)
    }

    @Test
    fun `observeContinueLearning emits null when session row is missing`() = runTest {
        val db = newTestDatabase()
        val repo = SessionStateRepositoryImpl(db)
        seedMatn(db)
        repo.setLastListenedMatnId(matnId)
        // Pointer set, but no session row yet -> entry is null, never an error (P4).
        assertNull(repo.observeContinueLearning().first())
    }

    @Test
    fun `observeContinueLearning emits null when pointer dangling after matn delete`() = runTest {
        val db = newTestDatabase()
        val repo = SessionStateRepositoryImpl(db)
        seedMatn(db)
        repo.putSession(savedSession())
        repo.setLastListenedMatnId(matnId)
        assertNotNull(repo.observeContinueLearning().first())

        // FR-025: deleting the matn removes its session row (cascade) and the entry disappears.
        db.contentQueries.deleteAudioByMatn(matnId)
        db.contentQueries.deleteVersesByMatn(matnId)
        db.contentQueries.deleteChaptersByMatn(matnId)
        db.contentQueries.deleteMatnById(matnId)
        assertNull(repo.observeContinueLearning().first())
    }

    @Test
    fun `getSession never throws on an undecodable verse_repeat row`() = runTest {
        val db = newTestDatabase()
        val repo = SessionStateRepositoryImpl(db)
        seedMatn(db)
        // Write a corrupt row directly, bypassing the repository's encoding.
        db.contentQueries.upsertSession(
            matn_id = matnId,
            last_verse_id = v1,
            last_verse_display_number = 1L,
            position_ms = 0L,
            verse_repeat = "GARBAGE_NOT_A_REPEAT_COUNT",
            matn_repeat = "UNLIMITED",
            loop_start_verse_id = null,
            loop_end_verse_id = null,
        )

        // Must not throw; the unparseable counter falls back to ONE (FR-029, P1).
        val loaded = repo.getSession(matnId)
        assertNotNull(loaded)
        assertEquals(RepeatCount.ONE, loaded.settings.verseRepeat)
        assertTrue(loaded.settings.matnRepeat is RepeatCount.Unlimited)
    }

    private fun savedSession(positionMs: Long = 5_000L) = SavedMatnSession(
        matnId = matnId,
        lastVerseId = v3,
        lastVerseDisplayNumber = 3,
        positionMs = positionMs,
        settings = RepetitionSettings(
            verseRepeat = RepeatCount.of(2),
            matnRepeat = RepeatCount.ONE,
            loopRange = null,
        ),
    )
}