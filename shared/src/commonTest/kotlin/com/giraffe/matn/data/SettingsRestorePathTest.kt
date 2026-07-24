package com.giraffe.matn.data

import com.giraffe.matn.data.repository.PersistentRepetitionSettingsStore
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.domain.model.LoopRange
import com.giraffe.matn.domain.model.PlaybackMode
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.domain.model.RepetitionSettings
import com.giraffe.matn.newTestDatabase
import com.giraffe.matn.parseSeed
import com.giraffe.matn.SIMPLE_MATN_JSON
import com.giraffe.matn.STRUCTURED_MATN_JSON
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T037/T038 — the settings restore path across a simulated app restart. Constructing a **fresh**
 * [PersistentRepetitionSettingsStore] over the same database stands in for killing and relaunching
 * the app: the new store's cache is cold, exactly as after a real restart. This is the test for
 * the phase's highest-risk bug (T035/T036): a cold cache silently handing
 * `PlaybackController.startSession` default counters — right verse, wrong drill.
 */
class SettingsRestorePathTest {

    private val matnA = "b3f1e2a4-0000-4000-8000-000000000001"
    private val matnB = "e5c5c5c5-0000-4000-8000-000000000001"
    private val v1 = "b3f1e2a4-0000-4000-8000-0000000000v1"
    private val v3 = "b3f1e2a4-0000-4000-8000-0000000000v3"

    @Test
    fun `a fresh store over the same database restores counters and loop range`() = runTest {
        val db = newTestDatabase()
        ContentSeedLoaderImpl(db).load(parseSeed(SIMPLE_MATN_JSON))
        ContentSeedLoaderImpl(db).load(parseSeed(STRUCTURED_MATN_JSON))

        val saved = RepetitionSettings(
            verseRepeat = RepeatCount.of(7),
            matnRepeat = RepeatCount.Unlimited,
            loopRange = LoopRange(startVerseId = v1, endVerseId = v3),
        )
        val firstRun = PersistentRepetitionSettingsStore(db, this)
        firstRun.put(matnA, saved)
        advanceUntilIdle() // let the launched persist complete — this run is about to "die"

        // --- app restart: cold cache over the same database ---
        val secondRun = PersistentRepetitionSettingsStore(db, this)
        val restored = secondRun.get(matnA)

        assertEquals(RepeatCount.of(7), restored.verseRepeat)
        assertTrue(restored.matnRepeat is RepeatCount.Unlimited, "Unlimited must survive the restart")
        assertEquals(v1, restored.loopRange?.startVerseId)
        assertEquals(v3, restored.loopRange?.endVerseId)

        // SC-009 — zero cross-matn leakage: matn B never had settings saved and still gets defaults.
        assertEquals(RepetitionSettings(), secondRun.get(matnB))
    }

    @Test
    fun `each matn restores its own settings with no bleed`() = runTest {
        val db = newTestDatabase()
        ContentSeedLoaderImpl(db).load(parseSeed(SIMPLE_MATN_JSON))
        ContentSeedLoaderImpl(db).load(parseSeed(STRUCTURED_MATN_JSON))

        val firstRun = PersistentRepetitionSettingsStore(db, this)
        firstRun.put(matnA, RepetitionSettings(verseRepeat = RepeatCount.of(7)))
        firstRun.put(matnB, RepetitionSettings(verseRepeat = RepeatCount.of(2)))
        advanceUntilIdle()

        val secondRun = PersistentRepetitionSettingsStore(db, this)
        assertEquals(RepeatCount.of(7), secondRun.get(matnA).verseRepeat)
        assertEquals(RepeatCount.of(2), secondRun.get(matnB).verseRepeat)
    }

    // ---- T038: the restored mode is DERIVED, never stored (FR-005) -------

    @Test
    fun `a session restored with verseRepeat 7 reports MEMORIZATION mode`() = runTest {
        val db = newTestDatabase()
        ContentSeedLoaderImpl(db).load(parseSeed(SIMPLE_MATN_JSON))
        val firstRun = PersistentRepetitionSettingsStore(db, this)
        firstRun.put(matnA, RepetitionSettings(verseRepeat = RepeatCount.of(7)))
        advanceUntilIdle()

        val restored = PersistentRepetitionSettingsStore(db, this).get(matnA)
        // The mode was never written anywhere — `matn_session` has no mode column (FR-005);
        // it re-derives from the restored counters alone.
        assertEquals(PlaybackMode.MEMORIZATION, restored.mode)
        assertNull(restored.loopRange)
    }

    @Test
    fun `a session restored with a loop range reports A_B_LOOP mode`() = runTest {
        val db = newTestDatabase()
        ContentSeedLoaderImpl(db).load(parseSeed(SIMPLE_MATN_JSON))
        val firstRun = PersistentRepetitionSettingsStore(db, this)
        firstRun.put(matnA, RepetitionSettings(loopRange = LoopRange(v1, v3)))
        advanceUntilIdle()

        val restored = PersistentRepetitionSettingsStore(db, this).get(matnA)
        assertEquals(PlaybackMode.A_B_LOOP, restored.mode)
    }
}
