package com.giraffe.matn.delivery

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.delivery.ContentPackRepositoryImpl
import com.giraffe.matn.data.seed.ContentSeedLoaderImpl
import com.giraffe.matn.data.seed.SeedAudio
import com.giraffe.matn.data.seed.SeedMatn
import com.giraffe.matn.data.seed.SeedVerse
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.repository.ContentPackRepository
import com.giraffe.matn.newTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * T056 (FR-019/FR-020/FR-023, SC-005) — removing a matn's content deletes bytes only. This test
 * seeds a bookmark, a note, a memorized verse, and a saved session, removes the matn's content,
 * and asserts every one of those rows is unchanged and the verse text is still readable and
 * searchable (structurally guaranteed — data-model.md §4: no edge runs from `content_pack` to any
 * personal-data table). Reinstalling then resumes at the same verse and position.
 */
class RemovalPreservesUserDataTest {

    private val matnId = "matn-with-data"
    private val packId = "matn_with_data_pack"
    private val verseId = "$matnId-v1"

    private fun seedMatn() = SeedMatn(
        id = matnId,
        title = "متن به بيانات",
        author = "author",
        description = "desc",
        coverImageRef = null,
        structureKind = "SIMPLE",
        defaultReciterId = "reciter-default-v1",
        verses = listOf(
            SeedVerse(
                id = verseId, displayNumber = 1, arabicText = "نص البيت الأول", durationMs = 1000,
                audio = SeedAudio(id = "$matnId-a1", fileRef = "audio.mp3", durationMs = 1000),
            ),
        ),
        packId = packId, declaredSizeBytes = 2_000L, isStarter = false,
    )

    @Test
    fun `removal preserves bookmarks notes memorization and the saved session`() = runTest {
        val db = newTestDatabase()
        val loader = ContentSeedLoaderImpl(db)
        loader.load(seedMatn())

        // Seed personal data directly against the schema (Phase 6/7 tables), independent of the
        // content_pack/delivery machinery under test.
        db.contentQueries.insertBookmark(id = "bm-1", verse_id = verseId, created_at = 1_000L)
        db.contentQueries.upsertNote(id = "note-1", verse_id = verseId, text = "ملاحظتي", updated_at = 1_000L)
        db.contentQueries.insertMemorization(id = "mem-1", verse_id = verseId, memorized_at = 1_000L)
        db.contentQueries.upsertSession(
            matn_id = matnId,
            last_verse_id = verseId,
            last_verse_display_number = 1L,
            position_ms = 4_500L,
            verse_repeat = "1",
            matn_repeat = "1",
            loop_start_verse_id = null,
            loop_end_verse_id = null,
        )

        val engine = FakeContentDeliveryEngine()
        engine.completeInstall(packId, occupiedBytes = 2_000L)
        val repo: ContentPackRepository = ContentPackRepositoryImpl(db, engine, FakeDeviceStorage())

        val outcome = repo.remove(matnId)
        assertIs<Resource.Success<*>>(outcome)

        // Personal data survives structurally unchanged.
        assertNotNull(db.contentQueries.selectBookmarkByVerse(verseId).executeAsOneOrNull())
        assertNotNull(db.contentQueries.selectNoteByVerse(verseId).executeAsOneOrNull())
        assertNotNull(db.contentQueries.selectMemorizationByVerse(verseId).executeAsOneOrNull())
        val sessionAfterRemove = db.contentQueries.selectSession(matnId).executeAsOneOrNull()
        assertNotNull(sessionAfterRemove)
        assertEquals(verseId, sessionAfterRemove.last_verse_id)
        assertEquals(4_500L, sessionAfterRemove.position_ms)

        // Verse text is still readable (no cascade touched `verse`/`audio_asset`).
        val verse = db.contentQueries.selectVerseById(verseId).executeAsOneOrNull()
        assertNotNull(verse)
        assertEquals("نص البيت الأول", verse.arabic_text)

        // Reinstall — the saved session resumes at the same verse and position (FR-023/SC-005).
        val reinstallOutcome = repo.install(matnId)
        assertIs<Resource.Success<*>>(reinstallOutcome)
        engine.completeInstall(packId, occupiedBytes = 2_000L)
        val availability = repo.observeAvailability(matnId).first()
        assertIs<ContentAvailability.Installed>(availability)

        val sessionAfterReinstall = db.contentQueries.selectSession(matnId).executeAsOneOrNull()
        assertNotNull(sessionAfterReinstall)
        assertEquals(verseId, sessionAfterReinstall.last_verse_id)
        assertEquals(4_500L, sessionAfterReinstall.position_ms)
    }
}
