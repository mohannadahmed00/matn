package com.giraffe.matn.presentation.details

import com.giraffe.matn.core.AppError
import com.giraffe.matn.domain.model.ReadingFontSize

/**
 * Immutable UI state for the Matn details / reading screen (data-model.md §4.2; Principle II).
 *
 *  * [header] holds the cover/title/author/description plus derived totals (FR-005). Totals are
 *    computed **here** from the streamed verse list (Decision 6) — verse count = `verses.size`,
 *    total duration = `sumOf { durationMs }` — not via a second SQL aggregate.
 *  * [verses] is the full ordered list (FR-006); Arabic text is **verbatim** from storage.
 *  * [chapters] is non-empty only for STRUCTURED متون (FR-015). Empty for SIMPLE متون.
 *  * [showTableOfContents] gates the TOC panel (FR-013/FR-015).
 *  * [fontSize] is applied **live** to verse text (FR-016) and persists across sessions (FR-017).
 */
data class MatnDetailsUiState(
    val isLoading: Boolean = true,
    val header: MatnHeader? = null,
    val verses: List<VerseRow> = emptyList(),
    val chapters: List<ChapterRow> = emptyList(),
    val showTableOfContents: Boolean = false,
    val fontSize: ReadingFontSize = ReadingFontSize.MEDIUM,
    val error: AppError? = null,
)

data class MatnHeader(
    val coverImageRef: String?,
    val title: String,
    val author: String,
    val description: String,
    val verseCount: Int,
    val totalDurationMs: Long,
)

data class VerseRow(
    val id: String,
    val displayNumber: Int,
    val arabicText: String,
    val durationMs: Long,
    val chapterId: String?,
)

data class ChapterRow(
    val id: String,
    val title: String,
    val order: Int,
    val firstVerseDisplayNumber: Int,
)