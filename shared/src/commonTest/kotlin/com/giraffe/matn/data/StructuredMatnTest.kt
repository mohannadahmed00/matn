package com.giraffe.matn.data

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.repository.MatnRepositoryImpl
import com.giraffe.matn.data.repository.VerseRepositoryImpl
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.domain.error.ContentIntegrityError
import com.giraffe.matn.newTestDatabase
import com.giraffe.matn.STRUCTURED_MATN_JSON
import com.giraffe.matn.SIMPLE_MATN_JSON
import com.giraffe.matn.parseSeed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StructuredMatnTest {

    private data class Repos(
        val matn: MatnRepositoryImpl,
        val verse: VerseRepositoryImpl,
        val loader: ContentSeedLoaderImpl,
    )

    private fun newRepos(): Repos {
        val db = newTestDatabase()
        return Repos(
            matn = MatnRepositoryImpl(db),
            verse = VerseRepositoryImpl(db),
            loader = ContentSeedLoaderImpl(db),
        )
    }

    @Test
    fun chaptersInOrder() = runTest {
        val r = newRepos()
        val payload = parseSeed(STRUCTURED_MATN_JSON)
        r.loader.load(payload)

        val chapters = (r.matn.getChapters(payload.id) as Resource.Success).data
        assertEquals(2, chapters.size)
        assertEquals(listOf(1, 2), chapters.map { it.order })
        assertEquals(payload.chapters[0].id, chapters[0].id)
        assertEquals(payload.chapters[1].id, chapters[1].id)
    }

    @Test
    fun versesGroupedByChapter() = runTest {
        val r = newRepos()
        val payload = parseSeed(STRUCTURED_MATN_JSON)
        r.loader.load(payload)

        val chapter1Verses = (r.verse.getVersesByChapter(payload.chapters[0].id) as Resource.Success).data
        val chapter2Verses = (r.verse.getVersesByChapter(payload.chapters[1].id) as Resource.Success).data

        assertEquals(listOf(1, 2, 3), chapter1Verses.map { it.displayNumber })
        assertEquals(listOf(4, 5), chapter2Verses.map { it.displayNumber })
        chapter1Verses.forEach { v -> assertEquals(payload.chapters[0].id, v.chapterId) }
        chapter2Verses.forEach { v -> assertEquals(payload.chapters[1].id, v.chapterId) }
    }

    @Test
    fun fullSequenceCoherent() = runTest {
        val r = newRepos()
        val payload = parseSeed(STRUCTURED_MATN_JSON)
        r.loader.load(payload)

        val verses = r.verse.observeVerses(payload.id).first()
        assertEquals(listOf(1, 2, 3, 4, 5), verses.map { it.displayNumber })
        assertEquals(5, verses.size)
    }

    @Test
    fun simpleMatnHasNoChapters() = runTest {
        val r = newRepos()
        val payload = parseSeed(SIMPLE_MATN_JSON)
        r.loader.load(payload)

        val result = r.matn.getChapters(payload.id)
        assertTrue(result is Resource.Success)
        val chapters = (result as Resource.Success).data
        assertEquals(0, chapters.size)
    }

    @Test
    fun atomicRejectDuplicateChapterOrder() = runTest {
        val r = newRepos()
        val base = parseSeed(STRUCTURED_MATN_JSON)
        // Force two chapters to share display order; the DB UNIQUE(matn_id, display_order)
        // backstop would otherwise surface only as an opaque Storage failure.
        val payload = base.copy(
            chapters = base.chapters.mapIndexed { index, c ->
                if (index == 1) c.copy(order = base.chapters[0].order) else c
            },
        )

        val result = r.loader.load(payload)
        assertTrue(result is Resource.Failure)
        val problems =
            if (result.error is ContentIntegrityError.Aggregate)
                (result.error as ContentIntegrityError.Aggregate).problems
            else listOf(result.error as ContentIntegrityError)
        assertTrue(problems.any { it is ContentIntegrityError.DuplicateChapterOrder })

        assertNull((r.matn.getMatn(payload.id) as Resource.Success<*>).data)
        assertTrue(r.verse.observeVerses(payload.id).first().isEmpty())
    }

    @Test
    fun independentMatns() = runTest {
        val r = newRepos()
        val simple = parseSeed(SIMPLE_MATN_JSON)
        val structured = parseSeed(STRUCTURED_MATN_JSON)
        r.loader.load(simple)
        r.loader.load(structured)

        val simpleMatn = (r.matn.getMatn(simple.id) as Resource.Success).data
        val structuredMatn = (r.matn.getMatn(structured.id) as Resource.Success).data
        assertEquals(simple.id, simpleMatn?.id)
        assertEquals(structured.id, structuredMatn?.id)

        val simpleVerses = r.verse.observeVerses(simple.id).first()
        val structuredVerses = r.verse.observeVerses(structured.id).first()
        assertEquals(4, simpleVerses.size)
        assertEquals(5, structuredVerses.size)
        assertTrue(simpleVerses.all { it.matnId == simple.id })
        assertTrue(structuredVerses.all { it.matnId == structured.id })

        val simpleChapters = (r.matn.getChapters(simple.id) as Resource.Success).data
        val structuredChapters = (r.matn.getChapters(structured.id) as Resource.Success).data
        assertEquals(0, simpleChapters.size)
        assertEquals(2, structuredChapters.size)
    }
}