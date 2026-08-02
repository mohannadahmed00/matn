package com.giraffe.matn.testseed

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.Matn

interface ContentSeedLoader {
    suspend fun load(payload: SeedMatn): Resource<Matn>
}