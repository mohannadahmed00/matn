package com.giraffe.matn.data

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.repository.BookmarkRepositoryImpl
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.data.seed.SeedAudio
import com.giraffe.matn.data.seed.SeedMatn
import com.giraffe.matn.data.seed.SeedVerse
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.inMemoryDriver
import com.giraffe.matn.newTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** contracts/annotations-contract.md § Tests (specs/006-search-bookmarks-notes T030). */
class BookmarkRepositoryTest {

    private suspend fun seededDb(): ContentDatabase {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        val verses = listOf(
            SeedVerse(id = "v1", displayNumber = 1, arabicText = "بيت واحد", durationMs = 1000, audio = SeedAudio("a1", "v1.mp3", 1000)),
            SeedVerse(id = "v2", displayNumber = 2, arabicText = "بيت اثنان", durationMs = 1000, audio = SeedAudio("a2", "v2.mp3", 1000)),
        )
        val payload = SeedMatn(
            id = "m1", title = "متن", author = "مؤلف", description = "",
            structureKind = "SIMPLE", defaultReciterId = "r1", verses = verses,
        )
        assertTrue(loader.load(payload) is Resource.Success)
        return db
    }

    private fun repo(db: ContentDatabase, ids: MutableList<String> = mutableListOf("bm-1", "bm-2", "bm-3"), clockStart: Long = 1_000L): BookmarkRepositoryImpl {
        var clockValue = clockStart
        val idQueue = ArrayDeque(ids)
        return BookmarkRepositoryImpl(
            db = db,
            clock = { clockValue++ },
            newId = { idQueue.removeFirst() },
        )
    }

    @Test
    fun `toggle on then off then on mints a new id on re-add`() = runTest {
        val db = seededDb()
        val repo = repo(db)

        val onResult = repo.toggle("v1")
        assertEquals(Resource.Success(true), onResult)
        val firstId = db.contentQueries.selectBookmarkByVerse("v1").executeAsOneOrNull()?.id
        assertNotNull(firstId)

        val offResult = repo.toggle("v1")
        assertEquals(Resource.Success(false), offResult)
        assertNull(db.contentQueries.selectBookmarkByVerse("v1").executeAsOneOrNull())

        val onAgainResult = repo.toggle("v1")
        assertEquals(Resource.Success(true), onAgainResult)
        val secondId = db.contentQueries.selectBookmarkByVerse("v1").executeAsOneOrNull()?.id
        assertNotNull(secondId)
        assertNotEquals(firstId, secondId)
    }

    @Test
    fun `observeAll is newest-first with deterministic id tiebreak`() = runTest {
        val db = seededDb()
        // Same clock value for both — the id DESC tiebreak must decide the order.
        val repo = BookmarkRepositoryImpl(db, clock = { 5_000L }, newId = { if (db.contentQueries.selectBookmarkByVerse("v1").executeAsOneOrNull() == null) "a-id" else "z-id" })
        repo.toggle("v1")
        repo.toggle("v2")

        val entries = repo.observeAll().first()
        assertEquals(listOf("v2", "v1"), entries.map { it.bookmark.verseId })
    }

    @Test
    fun `observeBookmarkedVerseIds is scoped per matn`() = runTest {
        val db = seededDb()
        val repo = repo(db)
        repo.toggle("v1")
        val ids = repo.observeBookmarkedVerseIds("m1").first()
        assertEquals(setOf("v1"), ids)
    }

    @Test
    fun `bookmark rows persist across a fresh query object on the same driver`() = runTest {
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
        val repo = repo(db)
        repo.toggle("v1")

        // A second ContentDatabase instance wrapping the SAME driver — proves the row lives in
        // the driver's storage, not merely in the first query object's in-memory state.
        val reopened = ContentDatabase(driver)
        assertEquals("v1", reopened.contentQueries.selectBookmarkByVerse("v1").executeAsOneOrNull()?.verse_id)
    }

    @Test
    fun `ten rapid sequential toggles settle to a consistent state`() = runTest {
        val db = seededDb()
        val repo = repo(db, ids = (1..10).map { "bm-$it" }.toMutableList())
        var last: Boolean? = null
        repeat(10) {
            val result = repo.toggle("v1") as Resource.Success
            last = result.data
        }
        val row = db.contentQueries.selectBookmarkByVerse("v1").executeAsOneOrNull()
        if (last == true) {
            assertNotNull(row)
        } else {
            assertNull(row)
        }
    }

    @Test
    fun `deleting a verse's audio does not remove its bookmark`() = runTest {
        val db = seededDb()
        val repo = repo(db)
        repo.toggle("v1")
        db.contentQueries.deleteAudioByMatn("m1")
        assertNotNull(db.contentQueries.selectBookmarkByVerse("v1").executeAsOneOrNull())
    }
}
