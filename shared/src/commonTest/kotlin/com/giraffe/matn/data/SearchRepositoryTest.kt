package com.giraffe.matn.data

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.repository.SearchRepositoryImpl
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.data.seed.SeedAudio
import com.giraffe.matn.data.seed.SeedChapter
import com.giraffe.matn.data.seed.SeedMatn
import com.giraffe.matn.data.seed.SeedVerse
import com.giraffe.matn.domain.model.SearchResult
import com.giraffe.matn.newTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * contracts/search-contract.md § Tests (specs/006-search-bookmarks-notes T016). Two متون: one
 * SIMPLE with a vocalized/hamza-carrier verse, one STRUCTURED with two chapters (one populated,
 * one empty — the `firstVerseId == null` fallback).
 */
class SearchRepositoryTest {

    private fun audioFor(verseId: String) = SeedAudio(id = "audio-$verseId", fileRef = "$verseId.mp3", durationMs = 4000)

    private val simpleMatn = SeedMatn(
        id = "m-simple",
        title = "الأجرومية",
        author = "ابن آجُرُّوم",
        description = "متن مختصر",
        structureKind = "SIMPLE",
        defaultReciterId = "reciter-1",
        verses = listOf(
            SeedVerse(id = "v1", displayNumber = 1, arabicText = "الإِسْلَامُ دِينُ الفِطرَةِ", durationMs = 4000, audio = audioFor("v1")),
            SeedVerse(id = "v2", displayNumber = 2, arabicText = "وَأَقسامُهُ ثَلاثَةٌ", durationMs = 4000, audio = audioFor("v2")),
        ),
    )

    private val structuredMatn = SeedMatn(
        id = "m-structured",
        title = "متن الطهارة",
        author = "مؤلف آخر",
        description = "متن مبوب",
        structureKind = "STRUCTURED",
        defaultReciterId = "reciter-1",
        chapters = listOf(
            SeedChapter(id = "c1", title = "باب الطهارة", order = 1),
            SeedChapter(id = "c2", title = "باب فارغ", order = 2),
        ),
        verses = listOf(
            SeedVerse(id = "sv1", chapterId = "c1", displayNumber = 1, arabicText = "نص بيت الطهارة", durationMs = 4000, audio = audioFor("sv1")),
        ),
    )

    private suspend fun seededDb(): com.giraffe.matn.db.ContentDatabase {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        assertTrue(loader.load(simpleMatn) is Resource.Success)
        assertTrue(loader.load(structuredMatn) is Resource.Success)
        return db
    }

    @Test
    fun `matn title match`() = runTest {
        val repo = SearchRepositoryImpl(seededDb())
        val results = repo.search("الطهارة").first()
        assertTrue(results.any { it is SearchResult.MatnMatch && it.matnId == "m-structured" })
    }

    @Test
    fun `chapter title match`() = runTest {
        val repo = SearchRepositoryImpl(seededDb())
        val results = repo.search("باب الطهارة").first()
        val chapterMatch = results.filterIsInstance<SearchResult.ChapterMatch>().single()
        assertEquals("c1", chapterMatch.chapterId)
        assertEquals("sv1", chapterMatch.firstVerseId)
    }

    @Test
    fun `chapter with no verses falls back to null firstVerseId`() = runTest {
        val repo = SearchRepositoryImpl(seededDb())
        val results = repo.search("باب فارغ").first()
        val chapterMatch = results.filterIsInstance<SearchResult.ChapterMatch>().single()
        assertEquals("c2", chapterMatch.chapterId)
        assertNull(chapterMatch.firstVerseId)
    }

    @Test
    fun `verse text match ignores diacritics and alef-form folding`() = runTest {
        val repo = SearchRepositoryImpl(seededDb())
        // "الاسلام دين" without diacritics/hamza — must still match the vocalized stored form.
        val results = repo.search("الاسلام دين").first()
        val verseMatch = results.filterIsInstance<SearchResult.VerseMatch>().single()
        assertEquals("v1", verseMatch.ref.verseId)
    }

    @Test
    fun `verse number match via Arabic-Indic digits`() = runTest {
        val repo = SearchRepositoryImpl(seededDb())
        val results = repo.search("٢").first() // Arabic-Indic 2
        val verseMatches = results.filterIsInstance<SearchResult.VerseMatch>()
        assertTrue(verseMatches.any { it.ref.verseId == "v2" })
    }

    @Test
    fun `blank query emits empty list`() = runTest {
        val repo = SearchRepositoryImpl(seededDb())
        assertEquals(emptyList(), repo.search("   ").first())
        assertEquals(emptyList(), repo.search("").first())
    }

    @Test
    fun `ordering is deterministic across repeated calls`() = runTest {
        val repo = SearchRepositoryImpl(seededDb())
        val first = repo.search("ا").first()
        val second = repo.search("ا").first()
        assertEquals(first, second)
    }

    @Test
    fun `results grouped by matn library order then chapter then verse`() = runTest {
        val repo = SearchRepositoryImpl(seededDb())
        // "الطهارة" appears ONLY in m-structured's title, chapter title, and verse text (unlike
        // a bare "ط", which also hits m-simple's verse — keeping this single-group avoids
        // conflating cross-matn grouping with within-matn ordering).
        val results = repo.search("الطهارة").first()
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.belongsTo("m-structured") })
        // Assert matn-match precedes chapter-match precedes verse-match within that group
        // (FR-006 within-matn ordering).
        val kindsInOrder = results.map {
            when (it) {
                is SearchResult.MatnMatch -> 0
                is SearchResult.ChapterMatch -> 1
                is SearchResult.VerseMatch -> 2
            }
        }
        assertEquals(kindsInOrder.sorted(), kindsInOrder)
    }

    @Test
    fun `results re-emit when content changes`() = runTest {
        val db = seededDb()
        val repo = SearchRepositoryImpl(db)
        val before = repo.search("جديد").first()
        assertTrue(before.isEmpty())

        db.contentQueries.upsertVerse(
            id = "v3",
            matn_id = simpleMatn.id,
            chapter_id = null,
            display_number = 3,
            arabic_text = "بيت جديد للاختبار",
            duration_ms = 4000,
        )

        val after = repo.search("جديد").first()
        assertTrue(after.any { it is SearchResult.VerseMatch && it.ref.verseId == "v3" })
    }

    private fun SearchResult.belongsTo(matnId: String): Boolean = when (this) {
        is SearchResult.MatnMatch -> this.matnId == matnId
        is SearchResult.ChapterMatch -> this.matnId == matnId
        is SearchResult.VerseMatch -> this.ref.matnId == matnId
    }
}
