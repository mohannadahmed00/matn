package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.repository.BookmarkRepository

@org.koin.core.annotation.Factory
class ToggleBookmarkUseCase(private val repo: BookmarkRepository) : UseCase<String, Boolean> {
    override suspend fun invoke(params: String): Resource<Boolean> = repo.toggle(params)
}
