package com.giraffe.matn.domain.model

/**
 * One library-wide search hit (FR-001…FR-007; data-model.md § SearchResult). Transient — never
 * persisted. Each variant carries exactly what its navigation target needs (research.md D5: all
 * navigate through the matn route's optional `focusVerseId`).
 */
sealed interface SearchResult {
    /** Matches verse text or a verse's display number. Navigates to matn + `focusVerseId`. */
    data class VerseMatch(val ref: AnnotatedVerseRef) : SearchResult

    /** Matches a chapter title. `firstVerseId == null` when the chapter has no verses — falls
     *  back to the plain matn route (search-contract.md § 6). */
    data class ChapterMatch(
        val matnId: String,
        val matnTitle: String,
        val chapterId: String,
        val chapterTitle: String,
        val firstVerseId: String?,
    ) : SearchResult

    /** Matches a matn title. Navigates to the plain matn details route. */
    data class MatnMatch(val matnId: String, val matnTitle: String) : SearchResult
}
