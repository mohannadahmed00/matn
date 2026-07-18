package com.giraffe.matn

import app.cash.sqldelight.db.SqlDriver
import com.giraffe.matn.db.ContentDatabase

expect fun inMemoryDriver(): SqlDriver

fun newTestDatabase(): ContentDatabase = ContentDatabase(inMemoryDriver())