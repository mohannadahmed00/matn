package com.giraffe.matn.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.giraffe.matn.db.ContentDatabase

actual class DatabaseDriverFactory(private val context: android.content.Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(
            schema = ContentDatabase.Schema,
            context = context,
            name = "content.db",
            // SQLite ignores FOREIGN KEY constraints unless enabled per-connection.
            callback = object : AndroidSqliteDriver.Callback(ContentDatabase.Schema) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    super.onConfigure(db)
                    db.setForeignKeyConstraintsEnabled(true)
                }
            },
        )
}
