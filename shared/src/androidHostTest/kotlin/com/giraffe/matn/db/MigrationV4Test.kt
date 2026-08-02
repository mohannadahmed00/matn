package com.giraffe.matn.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T011 (specs/008-storage-downloads) — proves the `content_pack` migration preserves existing content
 * and surfaces the new table queryable. Lives in `androidHostTest` next to
 * [MigrationV3Test]/[MigrationV2Test], for the same reason those files give: only the JDBC driver can
 * be handed a schema by hand (`ContentDatabase.Schema.create` always emits the *current* full
 * schema, so a fresh in-memory DB can never exercise an upgrade path).
 */
class MigrationV4Test {

    @Test
    fun schema_version_is_six() {
        assertEquals(6L, ContentDatabase.Schema.version)
    }

    private fun buildV4Driver(): JdbcSqliteDriver {
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
        driver.execute(null, "CREATE INDEX verse_by_matn_order ON verse(matn_id, display_number)", 0)
        driver.execute(null, "CREATE INDEX verse_by_chapter ON verse(chapter_id)", 0)
        driver.execute(null, "CREATE INDEX audio_by_verse ON audio_asset(verse_id)", 0)
        driver.execute(null, "PRAGMA user_version = 4", 0)
        return driver
    }

    @Test
    fun migration_preserves_seeded_content_and_adds_content_pack_table() {
        val driver = buildV4Driver()
        // Seed via raw SQL — the v4-schema driver has no ContentDatabase queries object yet.
        driver.execute(null, "INSERT INTO matn(id, title, author, description, cover_image_ref, structure_kind) VALUES ('m1', 'seed-title', 'seed-author', 'seed-desc', NULL, 'SIMPLE')", 0)
        driver.execute(null, "INSERT INTO verse(id, matn_id, chapter_id, display_number, arabic_text, duration_ms) VALUES ('v1', 'm1', NULL, 1, 'seed-verse', 1000)", 0)
        driver.execute(null, "INSERT INTO bookmark(id, verse_id, created_at) VALUES ('b1', 'v1', 1000)", 0)
        driver.execute(null, "INSERT INTO note(id, verse_id, text, updated_at) VALUES ('n1', 'v1', 'a note', 1000)", 0)
        driver.execute(null, "INSERT INTO matn_session(matn_id, last_verse_id, last_verse_display_number, position_ms, verse_repeat, matn_repeat, loop_start_verse_id, loop_end_verse_id) VALUES ('m1', 'v1', 1, 0, 'ONE', 'ONE', NULL, NULL)", 0)
        driver.execute(null, "INSERT INTO memorization(id, verse_id, memorized_at) VALUES ('mz1', 'v1', 1000)", 0)

        ContentDatabase.Schema.migrate(driver, 4, 5)
        val db = ContentDatabase(driver)

        // (a) pre-existing matn/verse/bookmark/note/session/memorization rows survive.
        val matn = db.contentQueries.selectMatnById("m1").executeAsOneOrNull()
        assertEquals("seed-title", matn?.title)
        val verse = db.contentQueries.selectVerseById("v1").executeAsOneOrNull()
        assertEquals("seed-verse", verse?.arabic_text)
        assertEquals("b1", db.contentQueries.selectBookmarkByVerse("v1").executeAsOneOrNull()?.id)
        assertEquals("a note", db.contentQueries.selectNoteByVerse("v1").executeAsOneOrNull()?.text)
        assertEquals("v1", db.contentQueries.selectSession("m1").executeAsOneOrNull()?.last_verse_id)
        assertEquals("mz1", db.contentQueries.selectMemorizationByVerse("v1").executeAsOneOrNull()?.id)

        // (b) content_pack exists at v5, is empty, and accepts a row.
        //
        // Phase 13 note: these assertions used the generated `selectContentPackByMatn` /
        // `insertContentPack` queries. `5.sqm` drops the table, so those queries no longer exist in
        // generated code and this test would not compile. The v4→v5 step itself is unchanged and
        // still worth proving for anyone upgrading from v4, so the assertions moved to raw SQL —
        // which is also the only honest way to inspect a table that the head schema has retired.
        assertEquals(0L, driver.countOf("SELECT COUNT(*) FROM content_pack"))

        driver.execute(null, "INSERT INTO content_pack(matn_id, pack_id, declared_size_bytes, is_starter) VALUES ('m1', 'matn_pack_one', 123, 0)", 0)
        assertEquals(1L, driver.countOf("SELECT COUNT(*) FROM content_pack"))
        assertEquals(0L, driver.countOf("SELECT is_starter FROM content_pack WHERE matn_id = 'm1'"))

        // (c) a starter row round-trips with is_starter = 1.
        driver.execute(null, "INSERT INTO matn(id, title, author, description, cover_image_ref, structure_kind) VALUES ('m_starter', 'starter', 'author', '', NULL, 'SIMPLE')", 0)
        driver.execute(null, "INSERT INTO content_pack(matn_id, pack_id, declared_size_bytes, is_starter) VALUES ('m_starter', 'matn_starter', 999, 1)", 0)
        assertEquals(1L, driver.countOf("SELECT is_starter FROM content_pack WHERE matn_id = 'm_starter'"))
    }

    @Test
    fun migration_on_empty_v4_database_leaves_content_pack_empty() {
        val driver = buildV4Driver()
        ContentDatabase.Schema.migrate(driver, 4, 5)
        assertEquals(0L, driver.countOf("SELECT COUNT(*) FROM content_pack"))
    }
}

/**
 * Reads a single scalar `INTEGER` through the raw driver. Needed because Phase 13 retires
 * `content_pack`, so the generated query API can no longer reach a v5-era table.
 */
internal fun JdbcSqliteDriver.countOf(sql: String): Long =
    executeQuery(
        identifier = null,
        sql = sql,
        parameters = 0,
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0) ?: 0L)
        },
    ).value