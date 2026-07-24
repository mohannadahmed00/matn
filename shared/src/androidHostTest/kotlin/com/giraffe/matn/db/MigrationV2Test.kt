package com.giraffe.matn.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * T007 (specs/006-search-bookmarks-notes) — proves the `bookmark`/`note` migration preserves
 * existing content and enforces `UNIQUE(verse_id)`. Lives in `androidHostTest` next to
 * [MigrationTest], for the same reason that file gives: only the JDBC driver can be handed a
 * schema by hand (`ContentDatabase.Schema.create` always emits the *current* full schema, so a
 * fresh in-memory DB can never exercise an upgrade path).
 *
 * Naming note: this class is called `MigrationV2Test` per the feature's task list, which assumed
 * the live schema was still at version 1 when this feature was planned. It was actually already
 * at version 2 (Phase 4's `matn_session` migration, `db/1.sqm`) by implementation time, so the
 * migration this test exercises is really v2 -> v3 (`db/2.sqm`) — see that file's header comment.
 */
class MigrationV2Test {

    @Test
    fun schema_version_is_three() {
        assertEquals(3L, ContentDatabase.Schema.version)
    }

    private fun buildV2Driver(): JdbcSqliteDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        driver.execute(null, "CREATE TABLE matn (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, author TEXT NOT NULL, description TEXT NOT NULL, cover_image_ref TEXT, structure_kind TEXT NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE chapter (id TEXT NOT NULL PRIMARY KEY, matn_id TEXT NOT NULL, title TEXT NOT NULL, display_order INTEGER NOT NULL, FOREIGN KEY (matn_id) REFERENCES matn(id), UNIQUE (matn_id, display_order))", 0)
        driver.execute(null, "CREATE TABLE verse (id TEXT NOT NULL PRIMARY KEY, matn_id TEXT NOT NULL, chapter_id TEXT, display_number INTEGER NOT NULL, arabic_text TEXT NOT NULL, duration_ms INTEGER NOT NULL, FOREIGN KEY (matn_id) REFERENCES matn(id), FOREIGN KEY (chapter_id) REFERENCES chapter(id), UNIQUE (matn_id, display_number))", 0)
        driver.execute(null, "CREATE TABLE audio_asset (id TEXT NOT NULL PRIMARY KEY, verse_id TEXT NOT NULL, reciter_id TEXT NOT NULL, file_ref TEXT NOT NULL, duration_ms INTEGER NOT NULL, FOREIGN KEY (verse_id) REFERENCES verse(id), UNIQUE (verse_id, reciter_id))", 0)
        driver.execute(null, "CREATE TABLE app_setting (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE matn_session (matn_id TEXT NOT NULL PRIMARY KEY, last_verse_id TEXT NOT NULL, last_verse_display_number INTEGER NOT NULL, position_ms INTEGER NOT NULL, verse_repeat TEXT NOT NULL, matn_repeat TEXT NOT NULL, loop_start_verse_id TEXT, loop_end_verse_id TEXT, FOREIGN KEY (matn_id) REFERENCES matn(id) ON DELETE CASCADE)", 0)
        driver.execute(null, "CREATE INDEX verse_by_matn_order ON verse(matn_id, display_number)", 0)
        driver.execute(null, "CREATE INDEX verse_by_chapter ON verse(chapter_id)", 0)
        driver.execute(null, "CREATE INDEX audio_by_verse ON audio_asset(verse_id)", 0)
        driver.execute(null, "PRAGMA user_version = 2", 0)
        return driver
    }

    @Test
    fun migration_preserves_seeded_content_and_adds_bookmark_and_note_tables() {
        val driver = buildV2Driver()
        // Seed via raw SQL — the v2-schema driver has no ContentDatabase queries object yet.
        driver.execute(null, "INSERT INTO matn(id, title, author, description, cover_image_ref, structure_kind) VALUES ('m1', 'seed-title', 'seed-author', 'seed-desc', NULL, 'SIMPLE')", 0)
        driver.execute(null, "INSERT INTO verse(id, matn_id, chapter_id, display_number, arabic_text, duration_ms) VALUES ('v1', 'm1', NULL, 1, 'seed-verse', 1000)", 0)

        ContentDatabase.Schema.migrate(driver, 2, 3)
        val db = ContentDatabase(driver)

        // (a) seeded content intact.
        val matn = db.contentQueries.selectMatnById("m1").executeAsOneOrNull()
        assertEquals("seed-title", matn?.title)
        val verse = db.contentQueries.selectVerseById("v1").executeAsOneOrNull()
        assertEquals("seed-verse", verse?.arabic_text)

        // (b) bookmark/note tables accept inserts.
        db.contentQueries.insertBookmark("b1", "v1", 1_000L)
        assertEquals("b1", db.contentQueries.selectBookmarkByVerse("v1").executeAsOneOrNull()?.id)
        db.contentQueries.upsertNote("n1", "v1", "a note", 1_000L)
        assertEquals("a note", db.contentQueries.selectNoteByVerse("v1").executeAsOneOrNull()?.text)

        // (c) UNIQUE(verse_id) rejects a second bookmark row for the same verse.
        assertFailsWith<Exception> {
            db.contentQueries.insertBookmark("b2", "v1", 2_000L)
        }
    }

    @Test
    fun migration_on_empty_v2_database_leaves_annotation_tables_empty() {
        val driver = buildV2Driver()
        ContentDatabase.Schema.migrate(driver, 2, 3)
        val db = ContentDatabase(driver)
        assertNull(db.contentQueries.selectBookmarkByVerse("nope").executeAsOneOrNull())
        assertNull(db.contentQueries.selectNoteByVerse("nope").executeAsOneOrNull())
    }
}
