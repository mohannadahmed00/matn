package com.giraffe.matn

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.giraffe.matn.db.ContentDatabase

actual fun inMemoryDriver(): SqlDriver {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    // Match the production drivers: enforce FOREIGN KEY constraints so tests exercise them.
    driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    ContentDatabase.Schema.create(driver)
    return driver
}
