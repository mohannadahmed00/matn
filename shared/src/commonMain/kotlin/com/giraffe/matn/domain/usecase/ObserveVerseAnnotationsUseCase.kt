package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.VerseAnnotations
import com.giraffe.matn.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Per-matn indicator map for the reading carousel (FR-011/FR-016). A verse id absent from the
 * map has no annotation — callers default to `isBookmarked = false, hasNote = false`.
 *
 * US2 (this task, T032): built from [BookmarkRepository.observeBookmarkedVerseIds] alone, with
 * `hasNote` always `false`. US3 (T044) extends this to `combine` bookmark AND note id-flows.
 */
class ObserveVerseAnnotationsUseCase(
    private val bookmarkRepository: BookmarkRepository,
) : FlowUseCase<String, Map<String, VerseAnnotations>> {
    override fun invoke(params: String): Flow<Map<String, VerseAnnotations>> =
        bookmarkRepository.observeBookmarkedVerseIds(params).map { bookmarkedIds ->
            bookmarkedIds.associateWith { verseId ->
                VerseAnnotations(verseId = verseId, isBookmarked = true, hasNote = false)
            }
        }
}
