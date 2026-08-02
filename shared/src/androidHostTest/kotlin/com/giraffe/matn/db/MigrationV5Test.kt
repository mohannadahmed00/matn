package com.giraffe.matn.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * T010 (specs/013-student-remote-catalog, research D5) — **the guard for the most dangerous change
 * in Phase 13.**
 *
 * Through v5, `bookmark`, `note`, `memorization`, `daily_practice` and `matn_session` all hung off
 * `verse`/`matn` with `ON DELETE CASCADE`. That was safe only because verse rows were seeded into
 * the app binary and never deleted — Phase 8's `RemovalPreservesUserDataTest` even called the
 * guarantee "structurally guaranteed". Phase 13 removes that structure: a download now owns the
 * verse rows and a removal deletes them, so those cascades would take the student's entire history
 * with them, violating FR-026, FR-029, SC-010 and Constitution Principle VI.
 *
 * `5.sqm` recreates the five tables without their foreign key. This test proves it on both axes:
 * existing personal data survives the migration itself, and — the part that actually matters —
 * deleting a verse afterwards no longer cascades.
 *
 * Lives in `androidHostTest` for the same reason [MigrationV4Test] and [MigrationTest] give: only
 * the JDBC driver can be handed a schema by hand, because `ContentDatabase.Schema.create` always
 * emits the *current* full schema, so a fresh in-memory database can never exercise an upgrade path.
 */
class MigrationV5Test {

    @Test
    fun schema_version_is_six() {
        assertEquals(6L, ContentDatabase.Schema.version)
    }

    /** The v5 schema: v4 plus `content_pack`, with every personal-data cascade still in place. */
    private fun buildV5Driver(): JdbcSqliteDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        driver.execute(null, "CREATE TABLE matn (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, author TEXT NOT NULL, description TEXT NOT NULL, cover_image_ref TEXT, structure_kind TEXT NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE chapter (id TEXT NOT NULL PRIMARY KEY, matn_id TEXT NOT NULL, title TEXT NOT NULL, display_order INTEGER NOT NULL, FOREIGN KEY (matn_id) REFERENCES matn(id), UNIQUE (matn_id, display_order))", 0)
        driver.execute(null, "CREATE TABLE verse (id TEXT NOT NULL PRIMARY KEY, matn_id TEXT NOT NULL, chapter_id TEXT, display_number INTEGER NOT NULL, arabic_text TEXT NOT NULL, duration_ms INTEGER NOT NULL, FOREIGN KEY (matn_id) REFERENCES matn(id), FOREIGN KEY (chapter_id) REFERENCES chapter(id), UNIQUE (matn_id, display_number))", 0)
        driver.execute(null, "CREATE TABLE audio_asset (id TEXT NOT NULL PRIMARY KEY, verse_id TEXT NOT NULL, reciter_id TEXT NOT NULL, file_ref TEXT NOT NULL, duration_ms INTEGER NOT NULL, FOREIGN KEY (verse_id) REFERENCES verse(id), UNIQUE (verse_id, reciter_id))", 0)
        driver.execute(null, "CREATE TABLE app_setting (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE matn_session (matn_id TEXT NOT NULL PRIMARY KEY, last_verse_id TEXT NOT NULL, last_verse_display_number INTEGER NOT NULL, position_ms INTEGER NOT NULL, verse_repeat TEXT NOT NULL, matn_repeat TEXT NOT NULL, loop_start_verse_id TEXT, loop_end_verse_id TEXT, FOREIGN KEY (matn_id) REFERENCES matn(id) ON DELETE CASCADE)", 0)
        driver.execute(null, "CREATE TABLE bookmark (id TEXT NOT NULL PRIMARY KEY, verse_id TEXT NOT NULL UNIQUE REFERENCES verse(id) ON DELETE CASCADE, created_at INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE note (id TEXT NOT NULL PRIMARY KEY, verse_id TEXT NOT NULL UNIQUE REFERENCES verse(id) ON DELETE CASCADE, text TEXT NOT NULL, updated_at INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE memorization (id TEXT NOT NULL PRIMARY KEY, verse_id TEXT NOT NULL UNIQUE REFERENCES verse(id) ON DELETE CASCADE, memorized_at INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE daily_practice (id TEXT NOT NULL PRIMARY KEY, day_epoch INTEGER NOT NULL, verse_id TEXT NOT NULL REFERENCES verse(id) ON DELETE CASCADE, practiced_at INTEGER NOT NULL, UNIQUE (day_epoch, verse_id))", 0)
        driver.execute(null, "CREATE TABLE content_pack (matn_id TEXT NOT NULL PRIMARY KEY REFERENCES matn(id) ON DELETE CASCADE, pack_id TEXT NOT NULL UNIQUE, declared_size_bytes INTEGER NOT NULL, is_starter INTEGER NOT NULL DEFAULT 0)", 0)
        driver.execute(null, "CREATE INDEX verse_by_matn_order ON verse(matn_id, display_number)", 0)
        driver.execute(null, "CREATE INDEX verse_by_chapter ON verse(chapter_id)", 0)
        driver.execute(null, "CREATE INDEX audio_by_verse ON audio_asset(verse_id)", 0)
        driver.execute(null, "PRAGMA user_version = 5", 0)
        return driver
    }

    private fun JdbcSqliteDriver.seedPersonalData() {
        execute(null, "INSERT INTO matn(id, title, author, description, cover_image_ref, structure_kind) VALUES ('m1', 'seed-title', 'seed-author', 'seed-desc', NULL, 'SIMPLE')", 0)
        execute(null, "INSERT INTO verse(id, matn_id, chapter_id, display_number, arabic_text, duration_ms) VALUES ('v1', 'm1', NULL, 1, 'seed-verse', 1000)", 0)
        execute(null, "INSERT INTO audio_asset(id, verse_id, reciter_id, file_ref, duration_ms) VALUES ('a1', 'v1', 'r1', 'v1.mp3', 1000)", 0)
        execute(null, "INSERT INTO bookmark(id, verse_id, created_at) VALUES ('b1', 'v1', 1000)", 0)
        execute(null, "INSERT INTO note(id, verse_id, text, updated_at) VALUES ('n1', 'v1', 'a note', 1000)", 0)
        execute(null, "INSERT INTO memorization(id, verse_id, memorized_at) VALUES ('mz1', 'v1', 1000)", 0)
        execute(null, "INSERT INTO daily_practice(id, day_epoch, verse_id, practiced_at) VALUES ('dp1', 20000, 'v1', 1000)", 0)
        execute(null, "INSERT INTO matn_session(matn_id, last_verse_id, last_verse_display_number, position_ms, verse_repeat, matn_repeat, loop_start_verse_id, loop_end_verse_id) VALUES ('m1', 'v1', 1, 4200, 'ONE', 'ONE', NULL, NULL)", 0)
    }

    @Test
    fun migration_preserves_every_piece_of_personal_data() {
        val driver = buildV5Driver()
        driver.seedPersonalData()

        ContentDatabase.Schema.migrate(driver, 5, 6)
        val db = ContentDatabase(driver)

        assertEquals("b1", db.contentQueries.selectBookmarkByVerse("v1").executeAsOneOrNull()?.id)
        assertEquals("a note", db.contentQueries.selectNoteByVerse("v1").executeAsOneOrNull()?.text)
        assertEquals("mz1", db.contentQueries.selectMemorizationByVerse("v1").executeAsOneOrNull()?.id)
        assertEquals(1L, db.contentQueries.selectDailyPracticeCount(20000).executeAsOne())
        assertEquals(4200L, db.contentQueries.selectSession("m1").executeAsOneOrNull()?.position_ms)
    }

    @Test
    fun migration_empties_content_tables() {
        val driver = buildV5Driver()
        driver.seedPersonalData()

        ContentDatabase.Schema.migrate(driver, 5, 6)
        val db = ContentDatabase(driver)

        // Spec Assumptions: the app has not shipped, so nothing reports as downloaded after upgrade.
        assertNull(db.contentQueries.selectMatnById("m1").executeAsOneOrNull())
        assertNull(db.contentQueries.selectVerseById("v1").executeAsOneOrNull())
        assertEquals(0L, driver.countOf("SELECT COUNT(*) FROM audio_asset"))
        assertEquals(0L, driver.countOf("SELECT COUNT(*) FROM chapter"))
    }

    /**
     * The heart of it. After the migration, deleting the verse a bookmark/note/mark/practice row
     * points at must NOT delete that row — which is exactly what removing a matn will do from now
     * on (FR-028). Before `5.sqm` every one of these assertions failed.
     */
    @Test
    fun deleting_a_verse_no_longer_cascades_into_personal_data() {
        val driver = buildV5Driver()
        driver.seedPersonalData()
        ContentDatabase.Schema.migrate(driver, 5, 6)
        val db = ContentDatabase(driver)

        // Re-seed content at v6 (the migration emptied it), keeping the same verse identity.
        db.contentQueries.upsertMatn("m1", "seed-title", "seed-author", "seed-desc", null, "SIMPLE")
        db.contentQueries.upsertVerse("v1", "m1", null, 1, "seed-verse", 1000)

        // What a removal does.
        driver.execute(null, "DELETE FROM verse WHERE id = 'v1'", 0)
        driver.execute(null, "DELETE FROM matn WHERE id = 'm1'", 0)

        assertNotNull(db.contentQueries.selectBookmarkByVerse("v1").executeAsOneOrNull())
        assertNotNull(db.contentQueries.selectNoteByVerse("v1").executeAsOneOrNull())
        assertNotNull(db.contentQueries.selectMemorizationByVerse("v1").executeAsOneOrNull())
        assertEquals(1L, db.contentQueries.selectDailyPracticeCount(20000).executeAsOne())
        assertNotNull(db.contentQueries.selectSession("m1").executeAsOneOrNull())

        // …and the orphans stay hidden from anything that needs verse context (research D5).
        assertEquals(0, db.contentQueries.selectAllBookmarksWithContext().executeAsList().size)
        assertEquals(0, db.contentQueries.selectAllNotesWithContext().executeAsList().size)
    }

    @Test
    fun phase_13_tables_are_created_and_queryable() {
        val driver = buildV5Driver()
        ContentDatabase.Schema.migrate(driver, 5, 6)
        val db = ContentDatabase(driver)

        assertEquals(0, db.contentQueries.selectAllCatalogOverviews().executeAsList().size)
        assertNull(db.contentQueries.selectSyncState().executeAsOneOrNull())
        assertEquals(0, db.contentQueries.selectAllDownloadedMatns().executeAsList().size)

        db.contentQueries.upsertCatalogOverview(
            matn_id = "m9",
            title = "متن",
            author = "مؤلف",
            description = "وصف",
            cover_image_ref = null,
            structure_kind = "SIMPLE",
            verse_count = 12,
            download_size_bytes = 4096,
            audio_completeness = "COMPLETE",
            revision = 3,
            withdrawn = 0,
        )
        val overview = db.contentQueries.selectCatalogOverviewById("m9").executeAsOneOrNull()
        assertEquals(4096L, overview?.download_size_bytes)
        assertEquals(3L, overview?.revision)

        // content_pack is gone.
        assertEquals(
            0L,
            driver.countOf("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='content_pack'"),
        )
    }
}
