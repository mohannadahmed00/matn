package com.giraffe.matn.data.db

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
        if (isFirstRun) ContentDatabase.Schema.create(driver)
        return driver
    }
}
