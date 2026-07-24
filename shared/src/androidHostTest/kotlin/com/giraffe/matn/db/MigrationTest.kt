package com.giraffe.matn.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.giraffe.matn.db.ContentDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T006 — the only test that can catch the v1→v2 upgrade-path bug; fresh-install tests
 * structurally cannot. Lives in `androidHostTest` (not `commonTest`) because building a v1
 * database by hand needs a *schemaless* driver, and only the JVM driver can be constructed
 * without a schema — the native in-memory helper always creates the full current schema.
 * The migration under test is plain SQL in `1.sqm`, identical on every platform.
 */
class MigrationTest {

    @Test
    fun schema_version_is_three() {
        // Was 2 through Phase 4/5; specs/006-search-bookmarks-notes added `db/2.sqm`
        // (bookmark/note tables), bumping this to 3 — see MigrationV2Test in this package.
        assertEquals(3L, ContentDatabase.Schema.version)
    }

    @Test
    fun migrating_from_version_1_makes_matn_session_queryable() {
        // A v1 database = the pre-Phase-4 schema (everything except matn_session).
        // Built by hand because Schema.create() always emits the full v2 schema,
        // so a fresh-install test can never reach the upgrade path this guards.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        driver.execute(null, "CREATE TABLE matn (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, author TEXT NOT NULL, description TEXT NOT NULL, cover_image_ref TEXT, structure_kind TEXT NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE chapter (id TEXT NOT NULL PRIMARY KEY, matn_id TEXT NOT NULL, title TEXT NOT NULL, display_order INTEGER NOT NULL, FOREIGN KEY (matn_id) REFERENCES matn(id), UNIQUE (matn_id, display_order))", 0)
        driver.execute(null, "CREATE TABLE verse (id TEXT NOT NULL PRIMARY KEY, matn_id TEXT NOT NULL, chapter_id TEXT, display_number INTEGER NOT NULL, arabic_text TEXT NOT NULL, duration_ms INTEGER NOT NULL, FOREIGN KEY (matn_id) REFERENCES matn(id), FOREIGN KEY (chapter_id) REFERENCES chapter(id), UNIQUE (matn_id, display_number))", 0)
        driver.execute(null, "CREATE TABLE audio_asset (id TEXT NOT NULL PRIMARY KEY, verse_id TEXT NOT NULL, reciter_id TEXT NOT NULL, file_ref TEXT NOT NULL, duration_ms INTEGER NOT NULL, FOREIGN KEY (verse_id) REFERENCES verse(id), UNIQUE (verse_id, reciter_id))", 0)
        driver.execute(null, "CREATE TABLE app_setting (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)", 0)
        driver.execute(null, "CREATE INDEX verse_by_matn_order ON verse(matn_id, display_number)", 0)
        driver.execute(null, "CREATE INDEX verse_by_chapter ON verse(chapter_id)", 0)
        driver.execute(null, "CREATE INDEX audio_by_verse ON audio_asset(verse_id)", 0)
        driver.execute(null, "PRAGMA user_version = 1", 0)

        // The upgrade path that 1.sqm owns.
        ContentDatabase.Schema.migrate(driver, 1, 2)

        // Queryable -> returns null (no row) rather than throwing "no such table".
        val db = ContentDatabase(driver)
        assertNull(db.contentQueries.selectSession("nope").executeAsOneOrNull())
    }
}