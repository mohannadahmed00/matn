package com.giraffe.matn.data.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.giraffe.matn.db.ContentDatabase
import java.io.File

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val dbFile = File(System.getProperty("user.home"), ".matn/content.db")
        dbFile.parentFile?.mkdirs()
        val isFirstRun = !dbFile.exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        // SQLite ignores FOREIGN KEY constraints unless enabled per-connection, matching Android/iOS.
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)

        val target = ContentDatabase.Schema.version
        if (isFirstRun) {
            ContentDatabase.Schema.create(driver)
            driver.setUserVersion(target)
        } else {
            // Desktop needs this migration step written by hand. `AndroidSqliteDriver` and
            // `NativeSqliteDriver` are constructed WITH the schema and run migrations themselves;
            // `JdbcSqliteDriver` is not, so without the block below an existing database is opened
            // at whatever version it was created at, and every query against a newer table fails
            // with "no such table" (found by running the app against Phase 13's `catalog_overview`).
            //
            // Latent since the first schema bump — desktop had silently never migrated — and only
            // surfaced now because Phase 13 is the first change to add tables the very first screen
            // reads.
            val current = driver.userVersion()
            when {
                current in 1 until target -> {
                    ContentDatabase.Schema.migrate(driver, current, target)
                    driver.setUserVersion(target)
                }
                // A database created before `user_version` was ever written. Its true version is
                // unknowable, so rather than guess a migration start point, recreate the schema —
                // safe here because desktop content is entirely re-downloadable and personal data
                // lives in tables `CREATE TABLE IF NOT EXISTS` leaves alone.
                current == 0L -> {
                    ContentDatabase.Schema.create(driver)
                    driver.setUserVersion(target)
                }
            }
        }
        return driver
    }
}

private fun JdbcSqliteDriver.userVersion(): Long =
    executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        parameters = 0,
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0) ?: 0L)
        },
    ).value

/** `PRAGMA` takes a literal, not a bind parameter. */
private fun JdbcSqliteDriver.setUserVersion(version: Long) {
    execute(null, "PRAGMA user_version = $version", 0)
}
