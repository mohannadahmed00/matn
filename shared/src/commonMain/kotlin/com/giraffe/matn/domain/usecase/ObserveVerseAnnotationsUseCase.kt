package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.domain.model.VerseAnnotations
import com.giraffe.matn.domain.repository.BookmarkRepository
import com.giraffe.matn.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Per-matn indicator map for the reading carousel (FR-011/FR-016). A verse id absent from the
 * map has no annotation — callers default to `isBookmarked = false, hasNote = false`. Combines
 * [BookmarkRepository.observeBookmarkedVerseIds] and [NoteRepository.observeNotedVerseIds] into
 * one map keyed by the union of both id sets (T044 — extends US2's T032 bookmark-only version).
 */
class ObserveVerseAnnotationsUseCase(
    private val bookmarkRepository: BookmarkRepository,
    private val noteRepository: NoteRepository,
) : FlowUseCase<String, Map<String, VerseAnnotations>> {
    override fun invoke(params: String): Flow<Map<String, VerseAnnotations>> =
        combine(
            bookmarkRepository.observeBookmarkedVerseIds(params),
            noteRepository.observeNotedVerseIds(params),
        ) { bookmarkedIds, notedIds ->
            (bookmarkedIds + notedIds).associateWith { verseId ->
                VerseAnnotations(
                    verseId = verseId,
                    isBookmarked = verseId in bookmarkedIds,
                    hasNote = verseId in notedIds,
                )
            }
        }
}
