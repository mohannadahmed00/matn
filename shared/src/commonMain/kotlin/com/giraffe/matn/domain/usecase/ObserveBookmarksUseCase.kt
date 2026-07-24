package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.BookmarkEntry
import com.giraffe.matn.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow

class ObserveBookmarksUseCase(private val repo: BookmarkRepository) : FlowUseCase<Unit, List<BookmarkEntry>> {
    override fun invoke(params: Unit): Flow<List<BookmarkEntry>> = repo.observeAll()
}
