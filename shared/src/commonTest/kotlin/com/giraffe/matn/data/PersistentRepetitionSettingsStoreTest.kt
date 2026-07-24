package com.giraffe.matn.data

import com.giraffe.matn.data.repository.PersistentRepetitionSettingsStore
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.domain.model.LoopRange
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.domain.model.RepetitionSettings
import com.giraffe.matn.newTestDatabase
import com.giraffe.matn.parseSeed
import com.giraffe.matn.SIMPLE_MATN_JSON
import com.giraffe.matn.STRUCTURED_MATN_JSON
import com.giraffe.matn.db.ContentDatabase
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PersistentRepetitionSettingsStoreTest {

    private val matnA = "b3f1e2a4-0000-4000-8000-000000000001"
    private val v1 = "b3f1e2a4-0000-4000-8000-0000000000v1"
    private val v3 = "b3f1e2a4-0000-4000-8000-0000000000v3"
    private val matnB = "e5c5c5c5-0000-4000-8000-000000000001"

    private suspend fun seedMatnA(db: ContentDatabase) {
        ContentSeedLoaderImpl(db).load(parseSeed(SIMPLE_MATN_JSON))
    }

    private suspend fun seedMatnB(db: ContentDatabase) {
        ContentSeedLoaderImpl(db).load(parseSeed(STRUCTURED_MATN_JSON))
    }

    private fun TestScope.newStore(db: ContentDatabase): PersistentRepetitionSettingsStore =
        PersistentRepetitionSettingsStore(db, backgroundScope)

    @Test
    fun `get on unknown matn returns defaults - never null never throws`() = runTest {
        val store = newStore(newTestDatabase())
        assertEquals(RepetitionSettings(), store.get(matnA))
    }

    @Test
    fun `per-matn isolation - matn A put never affects matn B`() = runTest {
        val db = newTestDatabase()
        seedMatnA(db)
        seedMatnB(db)
        val store = newStore(db)
        store.put(matnA, RepetitionSettings(verseRepeat = RepeatCount.of(7), matnRepeat = RepeatCount.Unlimited))
        store.put(matnB, RepetitionSettings(verseRepeat = RepeatCount.of(3)))
        runCurrent()

        assertEquals(RepeatCount.of(7), store.get(matnA).verseRepeat)
        assertTrue(store.get(matnA).matnRepeat is RepeatCount.Unlimited)
        assertEquals(RepeatCount.of(3), store.get(matnB).verseRepeat)
        assertEquals(RepeatCount.ONE, store.get(matnB).matnRepeat)
    }

    @Test
    fun `unlimited survives a round trip as Unlimited`() = runTest {
        val db = newTestDatabase()
        seedMatnA(db)
        val store = newStore(db)
        store.put(matnA, RepetitionSettings(verseRepeat = RepeatCount.Unlimited, matnRepeat = RepeatCount.Unlimited))
        runCurrent()

        // A brand new store over the same DB stands in for an app restart.
        val fresh = newStore(db)
        fresh.warmCache(matnA)
        val loaded = fresh.get(matnA)
        assertTrue(loaded.verseRepeat is RepeatCount.Unlimited)
        assertTrue(loaded.matnRepeat is RepeatCount.Unlimited)
    }

    @Test
    fun `loop range survives a round trip`() = runTest {
        val db = newTestDatabase()
        seedMatnA(db)
        val store = newStore(db)
        store.put(matnA, RepetitionSettings(verseRepeat = RepeatCount.of(2), loopRange = LoopRange(v1, v3)))
        runCurrent()

        val fresh = newStore(db)
        fresh.warmCache(matnA)
        val loaded = fresh.get(matnA)
        assertNotNull(loaded.loopRange)
        assertEquals(v1, loaded.loopRange?.startVerseId)
        assertEquals(v3, loaded.loopRange?.endVerseId)
    }

    @Test
    fun `put with no prior row creates one - configured but unplayed drill survives`() = runTest {
        val db = newTestDatabase()
        seedMatnA(db)
        val store = newStore(db)
        store.put(matnA, RepetitionSettings(verseRepeat = RepeatCount.of(5)))
        runCurrent()

        // The matn_session row now exists even though no playback ever ran (FR-002b).
        val row = db.contentQueries.selectSession(matnA).executeAsOneOrNull()
        assertNotNull(row)
        assertEquals(5, row.verse_repeat.toInt())

        // A fresh store reads the saved counters after a simulated restart.
        val fresh = newStore(db)
        fresh.warmCache(matnA)
        assertEquals(RepeatCount.of(5), fresh.get(matnA).verseRepeat)
    }
}