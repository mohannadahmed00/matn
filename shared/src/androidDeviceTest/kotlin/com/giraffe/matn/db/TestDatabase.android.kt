package com.giraffe.matn

import app.cash.sqldelight.db.SqlDriver

actual fun inMemoryDriver(): SqlDriver {
    error("Phase 0 declares no device tests; inMemoryDriver is not used on the Android device test target.")
}