package com.giraffe.matn.data

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.repository.AudioAssetRepositoryImpl
import com.giraffe.matn.data.repository.MatnRepositoryImpl
import com.giraffe.matn.data.repository.VerseRepositoryImpl
import com.giraffe.matn.testseed.TestContentSeeder
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.repository.AudioAssetRepository
import com.giraffe.matn.SIMPLE_MATN_JSON
import com.giraffe.matn.newTestDatabase
import com.giraffe.matn.parseSeed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioResolutionTest {

    private data class Repos(
        val db: ContentDatabase,
        val matn: MatnRepositoryImpl,
        val verse: VerseRepositoryImpl,
        val loader: TestContentSeeder,
        val audio: AudioAssetRepositoryImpl,
    )

    private fun newRepos(): Repos {
        val db = newTestDatabase()
        return Repos(
            db = db,
            matn = MatnRepositoryImpl(db),
            verse = VerseRepositoryImpl(db),
            loader = TestContentSeeder(db),
            audio = AudioAssetRepositoryImpl(db),
        )
    }

    @Test
    fun oneAssetPerVerse() = runTest {
        val r = newRepos()
        val payload = parseSeed(SIMPLE_MATN_JSON)
        r.loader.load(payload)

        val verses = r.verse.observeVerses(payload.id).first()
        verses.forEach { verse ->
            val result = r.audio.getAudioForVerse(verse.id)
            assertTrue(result is Resource.Success)
            val asset = (result as Resource.Success).data
            assertEquals(verse.id, asset?.verseId)
            assertEquals(payload.defaultReciterId, asset?.reciterId)
            assertEquals(AudioAssetRepository.DEFAULT_RECITER, asset?.reciterId)
        }
    }

    @Test
    fun uniqueFileRefs() = runTest {
        val r = newRepos()
        val payload = parseSeed(SIMPLE_MATN_JSON)
        r.loader.load(payload)

        val verses = r.verse.observeVerses(payload.id).first()
        val refs = verses.mapNotNull { verse ->
            (r.audio.getAudioForVerse(verse.id) as Resource.Success).data?.fileRef
        }
        assertEquals(verses.size, refs.size)
        assertEquals(refs.size, refs.toSet().size)
    }

    @Test
    fun unknownVerseNull() = runTest {
        val r = newRepos()
        val result = r.audio.getAudioForVerse("nope")
        assertTrue(result is Resource.Success)
        assertNull((result as Resource.Success).data)
    }

    @Test
    fun multiReciterModel() = runTest {
        val r = newRepos()
        val payload = parseSeed(SIMPLE_MATN_JSON)
        r.loader.load(payload)

        val verses = r.verse.observeVerses(payload.id).first()
        val firstVerse = verses.first()
        val secondReciter = "reciter-alt-v1"

        r.db.transaction {
            r.db.contentQueries.upsertAudioAsset(
                id = "${firstVerse.id}-alt-audio",
                verse_id = firstVerse.id,
                reciter_id = secondReciter,
                file_ref = "alternate_verse_001.mp3",
                duration_ms = firstVerse.durationMs,
            )
        }

        val defaultResult = r.audio.getAudioForVerse(firstVerse.id, AudioAssetRepository.DEFAULT_RECITER)
        val altResult = r.audio.getAudioForVerse(firstVerse.id, secondReciter)

        assertTrue(defaultResult is Resource.Success)
        assertTrue(altResult is Resource.Success)
        val defaultAsset = (defaultResult as Resource.Success).data
        val altAsset = (altResult as Resource.Success).data
        assertEquals(firstVerse.id, defaultAsset?.verseId)
        assertEquals(firstVerse.id, altAsset?.verseId)
        assertEquals(AudioAssetRepository.DEFAULT_RECITER, defaultAsset?.reciterId)
        assertEquals(secondReciter, altAsset?.reciterId)
        assertTrue(defaultAsset?.fileRef != altAsset?.fileRef)
    }
}