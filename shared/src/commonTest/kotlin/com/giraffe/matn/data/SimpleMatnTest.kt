package com.giraffe.matn.data

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.repository.AudioAssetRepositoryImpl
import com.giraffe.matn.data.repository.MatnRepositoryImpl
import com.giraffe.matn.data.repository.VerseRepositoryImpl
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.newTestDatabase
import com.giraffe.matn.domain.error.ContentIntegrityError
import com.giraffe.matn.parseSeed
import com.giraffe.matn.SIMPLE_MATN_JSON
import com.giraffe.matn.INVALID_DUPLICATE_ORDER_JSON
import com.giraffe.matn.INVALID_MISSING_AUDIO_JSON
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimpleMatnRoundTripTest {

    private data class Repos(
        val matn: MatnRepositoryImpl,
        val verse: VerseRepositoryImpl,
        val audio: AudioAssetRepositoryImpl,
        val loader: ContentSeedLoaderImpl,
    )

    private fun newRepos(): Repos {
        val db = newTestDatabase()
        return Repos(
            matn = MatnRepositoryImpl(db),
            verse = VerseRepositoryImpl(db),
            audio = AudioAssetRepositoryImpl(db),
            loader = ContentSeedLoaderImpl(db),
        )
    }

    @Test
    fun simpleMatnRoundTrip() = runTest {
        val r = newRepos()
        val payload = parseSeed(SIMPLE_MATN_JSON)
        val result = r.loader.load(payload)
        assertTrue(result is Resource.Success)

        val matn = (r.matn.getMatn(payload.id) as Resource.Success<*>).data as com.giraffe.matn.domain.model.Matn?
        assertEquals(payload.id, matn?.id)
        assertEquals("الأجرومية", matn?.title)
        assertEquals("ابن آجُرُّوم", matn?.author)
        assertEquals("متن مختصر في علم النحو", matn?.description)
        assertEquals("covers/ajurrumiyya.jpg", matn?.coverImageRef)
        assertEquals(com.giraffe.matn.domain.model.StructureKind.SIMPLE, matn?.structureKind)

        val verses = r.verse.observeVerses(payload.id).first()
        assertEquals(4, verses.size)
        assertEquals(listOf(1, 2, 3, 4), verses.map { it.displayNumber })
        assertEquals(payload.id, verses[0].matnId)
        verses.forEach { assertNull(it.chapterId) }
    }

    @Test
    fun diacriticsPreserved() = runTest {
        val r = newRepos()
        val payload = parseSeed(SIMPLE_MATN_JSON)
        r.loader.load(payload)
        val verses = r.verse.observeVerses(payload.id).first()
        val first = verses.first { it.displayNumber == 1 }
        assertEquals("الكَلامُ هُوَ اللَّفظُ المُرَكَّبُ المُفيدُ بِالوَضعِ", first.arabicText)
    }

    @Test
    fun atomicRejectDuplicateOrder() = runTest {
        val r = newRepos()
        val payload = parseSeed(INVALID_DUPLICATE_ORDER_JSON)

        val result = r.loader.load(payload)
        assertTrue(result is Resource.Failure)
        assertTrue(findAggregate(result.error).any { it is ContentIntegrityError.DuplicateDisplayNumber })

        val matn = (r.matn.getMatn(payload.id) as Resource.Success<*>).data
        assertNull(matn)
        val verses = r.verse.observeVerses(payload.id).first()
        assertTrue(verses.isEmpty())
    }

    @Test
    fun atomicRejectMissingAudio() = runTest {
        val r = newRepos()
        val payload = parseSeed(INVALID_MISSING_AUDIO_JSON)

        val result = r.loader.load(payload)
        assertTrue(result is Resource.Failure)
        assertTrue(findAggregate(result.error).any { it is ContentIntegrityError.MissingAudio })

        val matn = (r.matn.getMatn(payload.id) as Resource.Success<*>).data
        assertNull(matn)
        val verses = r.verse.observeVerses(payload.id).first()
        assertTrue(verses.isEmpty())
    }

    @Test
    fun reloadDedup() = runTest {
        val r = newRepos()
        val payload = parseSeed(SIMPLE_MATN_JSON)

        r.loader.load(payload)
        val firstVerses = r.verse.observeVerses(payload.id).first()
        val firstIds = firstVerses.map { it.id }.sorted()

        r.loader.load(payload)
        val secondVerses = r.verse.observeVerses(payload.id).first()
        val secondIds = secondVerses.map { it.id }.sorted()

        assertEquals(firstIds, secondIds)
        assertEquals(firstVerses.size, secondVerses.size)
        assertEquals(4, secondVerses.size)
    }

    @Test
    fun reloadRemovesStaleChildren() = runTest {
        val r = newRepos()
        val full = parseSeed(SIMPLE_MATN_JSON)
        r.loader.load(full)

        // Reload a shrunk payload with the last verse removed (FR-009 / SC-007: stale
        // this-matn rows whose ids are absent from the new payload must be deleted).
        val removed = full.verses.last()
        val shrunk = full.copy(verses = full.verses.dropLast(1))
        val result = r.loader.load(shrunk)
        assertTrue(result is Resource.Success)

        val verses = r.verse.observeVerses(full.id).first()
        assertEquals(full.verses.size - 1, verses.size)
        assertTrue(verses.none { it.id == removed.id })

        // The removed verse's audio asset must be gone too.
        val staleAudio = r.audio.getAudioForVerse(removed.id)
        assertTrue(staleAudio is Resource.Success)
        assertNull((staleAudio as Resource.Success).data)
    }

    @Test
    fun atomicRejectDuplicateId() = runTest {
        val r = newRepos()
        val base = parseSeed(SIMPLE_MATN_JSON)
        // Two verses sharing one authored id would silently collapse under upsert-on-conflict.
        val collidingId = base.verses[0].id
        val payload = base.copy(
            verses = base.verses.mapIndexed { index, v ->
                if (index == 1) v.copy(id = collidingId) else v
            },
        )

        val result = r.loader.load(payload)
        assertTrue(result is Resource.Failure)
        assertTrue(findAggregate(result.error).any { it is ContentIntegrityError.DuplicateId })

        assertNull((r.matn.getMatn(payload.id) as Resource.Success<*>).data)
        assertTrue(r.verse.observeVerses(payload.id).first().isEmpty())
    }

    @Test
    fun notFound() = runTest {
        val r = newRepos()
        val result = r.matn.getMatn("does-not-exist")
        assertTrue(result is Resource.Success)
        assertNull((result as Resource.Success<*>).data)
    }

    private fun findAggregate(error: AppError): List<ContentIntegrityError> =
        if (error is ContentIntegrityError.Aggregate) error.problems
        else listOf(error as ContentIntegrityError)
}