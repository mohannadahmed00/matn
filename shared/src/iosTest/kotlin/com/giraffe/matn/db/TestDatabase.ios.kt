package com.giraffe.matn

import app.cash.sqldelight.driver.native.inMemoryDriver
import com.giraffe.matn.db.ContentDatabase

actual fun inMemoryDriver(): app.cash.sqldelight.db.SqlDriver =
    inMemoryDriver(ContentDatabase.Schema)