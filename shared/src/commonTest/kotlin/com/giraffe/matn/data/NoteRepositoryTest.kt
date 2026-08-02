package com.giraffe.matn.data

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.repository.NoteRepositoryImpl
import com.giraffe.matn.testseed.TestContentSeeder
import com.giraffe.matn.testseed.SeedAudio
import com.giraffe.matn.testseed.SeedMatn
import com.giraffe.matn.testseed.SeedVerse
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.error.NoteError
import com.giraffe.matn.newTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** contracts/annotations-contract.md § Tests (specs/006-search-bookmarks-notes T042). */
class NoteRepositoryTest {

    private suspend fun seededDb(): ContentDatabase {
        val db = newTestDatabase()
        val loader = TestContentSeeder(db)
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

    private fun repo(db: ContentDatabase): NoteRepositoryImpl {
        var clockValue = 1_000L
        var idCounter = 0
        return NoteRepositoryImpl(db, clock = { clockValue++ }, newId = { "note-${idCounter++}" })
    }

    @Test
    fun `create then edit keeps the same id and refreshes updated_at then delete removes it`() = runTest {
        val db = seededDb()
        val repo = repo(db)

        val created = (repo.save("v1", "أول ملاحظة") as Resource.Success).data
        val edited = (repo.save("v1", "ملاحظة معدّلة") as Resource.Success).data

        assertEquals(created.id, edited.id)
        assertEquals("ملاحظة معدّلة", edited.text)
        assertTrue(edited.updatedAtMs > created.updatedAtMs)

        val deleteResult = repo.delete("v1")
        assertTrue(deleteResult is Resource.Success)
        assertNull((repo.get("v1") as Resource.Success).data)
    }

    @Test
    fun `blank or whitespace save is rejected and writes nothing`() = runTest {
        val db = seededDb()
        val repo = repo(db)

        val blankResult = repo.save("v1", "   ")
        assertTrue(blankResult is Resource.Failure)
        assertEquals(NoteError.EmptyNote, (blankResult as Resource.Failure).error)
        assertNull((repo.get("v1") as Resource.Success).data)
    }

    @Test
    fun `blank save over an existing note leaves the original text intact`() = runTest {
        val db = seededDb()
        val repo = repo(db)
        repo.save("v1", "نص أصلي")

        val blankResult = repo.save("v1", "")
        assertTrue(blankResult is Resource.Failure)

        val current = (repo.get("v1") as Resource.Success).data
        assertEquals("نص أصلي", current?.text)
    }

    @Test
    fun `observeAll is newest-first`() = runTest {
        val db = seededDb()
        val repo = repo(db)
        repo.save("v1", "أولى")
        repo.save("v2", "ثانية")

        val entries = repo.observeAll().first()
        assertEquals(listOf("v2", "v1"), entries.map { it.note.verseId })
    }

    @Test
    fun `observeNotedVerseIds is scoped per matn`() = runTest {
        val db = seededDb()
        val repo = repo(db)
        repo.save("v1", "ملاحظة")
        assertEquals(setOf("v1"), repo.observeNotedVerseIds("m1").first())
    }

    @Test
    fun `context JOIN carries correct matn and verse fields`() = runTest {
        val db = seededDb()
        val repo = repo(db)
        repo.save("v2", "ملاحظة على البيت الثاني")

        val entry = repo.observeAll().first().single()
        assertEquals("m1", entry.ref.matnId)
        assertEquals("متن", entry.ref.matnTitle)
        assertEquals(2, entry.ref.verseNumber)
        assertEquals("بيت اثنان", entry.ref.verseText)
    }

    @Test
    fun `deleting a verse's audio does not remove its note`() = runTest {
        val db = seededDb()
        val repo = repo(db)
        repo.save("v1", "ملاحظة")
        db.contentQueries.deleteAudioByMatn("m1")
        assertTrue((repo.get("v1") as Resource.Success).data != null)
    }
}
