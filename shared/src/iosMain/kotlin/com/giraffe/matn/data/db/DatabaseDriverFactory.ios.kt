package com.giraffe.matn.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.giraffe.matn.db.ContentDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(
            schema = ContentDatabase.Schema,
            name = "content.db",
            // SQLite ignores FOREIGN KEY constraints unless enabled per-connection.
            onConfiguration = { config ->
                config.copy(
                    extendedConfig = config.extendedConfig.copy(foreignKeyConstraints = true),
                )
            },
        )
}
