package com.giraffe.matn.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * T008 (specs/007-progress-daily-goals) — proves the `memorization`/`daily_practice` migration
 * preserves existing content and enforces `UNIQUE(day_epoch, verse_id)`. Lives in `androidHostTest`
 * next to [MigrationV2Test]/[MigrationTest], for the same reason those files give: only the JDBC
 * driver can be handed a schema by hand (`ContentDatabase.Schema.create` always emits the *current*
 * full schema, so a fresh in-memory DB can never exercise an upgrade path).
 */
class MigrationV3Test {

    @Test
    fun schema_version_is_four() {
        assertEquals(4L, ContentDatabase.Schema.version)
    }

    private fun buildV3Driver(): JdbcSqliteDriver {
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
        driver.execute(null, "CREATE INDEX verse_by_matn_order ON verse(matn_id, display_number)", 0)
        driver.execute(null, "CREATE INDEX verse_by_chapter ON verse(chapter_id)", 0)
        driver.execute(null, "CREATE INDEX audio_by_verse ON audio_asset(verse_id)", 0)
        driver.execute(null, "PRAGMA user_version = 3", 0)
        return driver
    }

    @Test
    fun migration_preserves_seeded_content_and_adds_memorization_and_daily_practice_tables() {
        val driver = buildV3Driver()
        // Seed via raw SQL — the v3-schema driver has no ContentDatabase queries object yet.
        driver.execute(null, "INSERT INTO matn(id, title, author, description, cover_image_ref, structure_kind) VALUES ('m1', 'seed-title', 'seed-author', 'seed-desc', NULL, 'SIMPLE')", 0)
        driver.execute(null, "INSERT INTO verse(id, matn_id, chapter_id, display_number, arabic_text, duration_ms) VALUES ('v1', 'm1', NULL, 1, 'seed-verse', 1000)", 0)
        driver.execute(null, "INSERT INTO bookmark(id, verse_id, created_at) VALUES ('b1', 'v1', 1000)", 0)
        driver.execute(null, "INSERT INTO note(id, verse_id, text, updated_at) VALUES ('n1', 'v1', 'a note', 1000)", 0)
        driver.execute(null, "INSERT INTO matn_session(matn_id, last_verse_id, last_verse_display_number, position_ms, verse_repeat, matn_repeat, loop_start_verse_id, loop_end_verse_id) VALUES ('m1', 'v1', 1, 0, 'ONE', 'ONE', NULL, NULL)", 0)

        ContentDatabase.Schema.migrate(driver, 3, 4)
        val db = ContentDatabase(driver)

        // (a) seeded content (matn, verse, bookmark, note, matn_session) is fully intact.
        val matn = db.contentQueries.selectMatnById("m1").executeAsOneOrNull()
        assertEquals("seed-title", matn?.title)
        val verse = db.contentQueries.selectVerseById("v1").executeAsOneOrNull()
        assertEquals("seed-verse", verse?.arabic_text)
        assertEquals("b1", db.contentQueries.selectBookmarkByVerse("v1").executeAsOneOrNull()?.id)
        assertEquals("a note", db.contentQueries.selectNoteByVerse("v1").executeAsOneOrNull()?.text)
        assertEquals("v1", db.contentQueries.selectSession("m1").executeAsOneOrNull()?.last_verse_id)

        // (b) the memorization and daily_practice tables accept inserts afterwards.
        db.contentQueries.insertMemorization("mz1", "v1", 1_000L)
        assertEquals("mz1", db.contentQueries.selectMemorizationByVerse("v1").executeAsOneOrNull()?.id)
        db.contentQueries.insertDailyPractice("dp1", 19000L, "v1", 1_000L)
        assertEquals(1L, db.contentQueries.selectDailyPracticeCount(19000L).executeAsOne())

        // (c) UNIQUE(day_epoch, verse_id) rejects (or ignores) a duplicate same-day row.
        db.contentQueries.insertDailyPractice("dp2", 19000L, "v1", 2_000L)
        assertEquals(1L, db.contentQueries.selectDailyPracticeCount(19000L).executeAsOne())

        // UNIQUE(verse_id) on memorization still rejects a genuine duplicate insert (not OR IGNORE).
        assertFailsWith<Exception> {
            driver.execute(null, "INSERT INTO memorization(id, verse_id, memorized_at) VALUES ('mz2', 'v1', 2000)", 0)
        }
    }

    @Test
    fun migration_on_empty_v3_database_leaves_new_tables_empty() {
        val driver = buildV3Driver()
        ContentDatabase.Schema.migrate(driver, 3, 4)
        val db = ContentDatabase(driver)
        assertNull(db.contentQueries.selectMemorizationByVerse("nope").executeAsOneOrNull())
        assertEquals(0L, db.contentQueries.selectDailyPracticeCount(19000L).executeAsOne())
    }
}
