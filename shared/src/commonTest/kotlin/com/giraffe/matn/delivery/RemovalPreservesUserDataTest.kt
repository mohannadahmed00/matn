package com.giraffe.matn.delivery

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.delivery.ContentFileStore
import com.giraffe.matn.data.delivery.DownloadedContentRepositoryImpl
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.newTestDatabase
import com.giraffe.matn.testseed.SeedAudio
import com.giraffe.matn.testseed.SeedMatn
import com.giraffe.matn.testseed.SeedVerse
import com.giraffe.matn.testseed.TestContentSeeder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * **The SC-010 guard.** Removing a matn must preserve 100% of the student's bookmarks, notes,
 * memorized marks, practice history and resume position.
 *
 * Phase 8's version of this test justified the guarantee as "structurally guaranteed — no edge runs
 * from `content_pack` to any personal-data table". Phase 13 destroyed that structure: a download now
 * owns the `matn`/`verse` rows and a removal **deletes** them, so the old `ON DELETE CASCADE` on
 * `bookmark`/`note`/`memorization`/`daily_practice`/`matn_session` would have taken the student's
 * entire history with it (research D5). `5.sqm` drops those foreign keys; this test is what proves
 * it end to end, through the real removal path rather than a raw `DELETE`.
 */
class RemovalPreservesUserDataTest {

    private val matnId = "matn-with-data"
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
                id = verseId,
                displayNumber = 1,
                arabicText = "نص البيت الأول",
                durationMs = 1_000,
                audio = SeedAudio(id = "$matnId-a1", fileRef = "audio.mp3", durationMs = 1_000),
            ),
        ),
    )

    @Test
    fun `removal preserves bookmarks notes memorization practice and the saved session`() = runTest {
        val db = newTestDatabase()
        TestContentSeeder(db).load(seedMatn())
        db.insertOverview(matnId, sizeBytes = 2_000L)

        db.contentQueries.insertBookmark(id = "bm-1", verse_id = verseId, created_at = 1_000L)
        db.contentQueries.upsertNote(id = "note-1", verse_id = verseId, text = "ملاحظتي", updated_at = 1_000L)
        db.contentQueries.insertMemorization(id = "mem-1", verse_id = verseId, memorized_at = 1_000L)
        db.contentQueries.insertDailyPractice(
            id = "dp-1",
            day_epoch = 20_000L,
            verse_id = verseId,
            practiced_at = 1_000L,
        )
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
        val storage = FakeDeviceStorage()
        val repo = DownloadedContentRepositoryImpl(
            db = db,
            engine = engine,
            storage = storage,
            files = ContentFileStore(storage),
            scope = this,
        )
        engine.completeDownload(matnId, occupiedBytes = 2_000L)

        assertIs<Resource.Success<*>>(repo.remove(matnId))
        runCurrent()

        // The matn's CONTENT is gone — this is what makes the assertions below meaningful. If the
        // verse row survived, the cascade would never have fired and the test would prove nothing.
        assertEquals(null, db.contentQueries.selectVerseById(verseId).executeAsOneOrNull())
        assertEquals(null, db.contentQueries.selectMatnById(matnId).executeAsOneOrNull())

        // …and every piece of personal data survived it.
        assertNotNull(db.contentQueries.selectBookmarkByVerse(verseId).executeAsOneOrNull())
        assertNotNull(db.contentQueries.selectNoteByVerse(verseId).executeAsOneOrNull())
        assertNotNull(db.contentQueries.selectMemorizationByVerse(verseId).executeAsOneOrNull())
        assertEquals(1L, db.contentQueries.selectDailyPracticeCount(20_000L).executeAsOne())
        val session = assertNotNull(db.contentQueries.selectSession(matnId).executeAsOneOrNull())
        assertEquals(verseId, session.last_verse_id)
        assertEquals(4_500L, session.position_ms)

        // The catalog entry stays browsable and re-downloadable (FR-029).
        assertNotNull(db.contentQueries.selectCatalogOverviewById(matnId).executeAsOneOrNull())
    }

    @Test
    fun `re-downloading resumes at the same verse and position`() = runTest {
        val db = newTestDatabase()
        TestContentSeeder(db).load(seedMatn())
        db.insertOverview(matnId, sizeBytes = 2_000L)
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
        val storage = FakeDeviceStorage()
        val repo = DownloadedContentRepositoryImpl(
            db = db,
            engine = engine,
            storage = storage,
            files = ContentFileStore(storage),
            scope = this,
        )
        engine.completeDownload(matnId, occupiedBytes = 2_000L)

        repo.remove(matnId)
        runCurrent()
        repo.download(matnId)
        runCurrent()

        assertIs<ContentAvailability.Downloaded>(repo.observeAvailability(matnId).first())
        val session = assertNotNull(db.contentQueries.selectSession(matnId).executeAsOneOrNull())
        assertEquals(verseId, session.last_verse_id)
        assertEquals(4_500L, session.position_ms)
    }
}
