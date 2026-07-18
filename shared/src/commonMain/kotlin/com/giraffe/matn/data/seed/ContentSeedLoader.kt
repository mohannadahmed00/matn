package com.giraffe.matn.data.seed

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.model.Matn

interface ContentSeedLoader {
    suspend fun load(payload: SeedMatn): Resource<Matn>
}