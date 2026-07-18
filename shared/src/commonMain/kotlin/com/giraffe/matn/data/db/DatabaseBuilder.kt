package com.giraffe.matn.data.db

import com.giraffe.matn.db.ContentDatabase

fun buildDatabase(factory: DatabaseDriverFactory): ContentDatabase =
    ContentDatabase(factory.createDriver())