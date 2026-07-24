package com.giraffe.matn.presentation.details

import com.giraffe.matn.core.AppError
import com.giraffe.matn.domain.model.AnnotatedVerseRef
import com.giraffe.matn.domain.model.LoopRange
import com.giraffe.matn.domain.model.MatnProgress
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.domain.model.VerseAnnotations

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
    val activeVerseId: String? = null,
    val isPlaying: Boolean = false,
    val loopRangeVerseIds: Set<String> = emptySet(),
    val loopRange: LoopRange? = null,
    /** Phase 6 (research.md D5): the route's optional `focusVerseId`, honored only while no
     *  playback session is active (`activeVerseId == null`) — see [MatnDetailsScreen]'s carousel
     *  windowing. Never starts playback by itself. */
    val focusVerseId: String? = null,
    /** Phase 6 (US2/US3): per-verse bookmark/note indicator flags, keyed by verse id. A verse
     *  absent from this map has no annotation. */
    val annotations: Map<String, VerseAnnotations> = emptyMap(),
    /** Phase 6 (US3): the note-editor bottom sheet's state, or `null` when closed. */
    val noteEditor: NoteEditorState? = null,
    /** Phase 7 (FR-002): memorized verse ids for this matn — feeds the carousel indicator. */
    val memorizedVerseIds: Set<String> = emptySet(),
    /** Phase 7 (FR-006/FR-008): the latest progress emission, held independently of [header] so
     *  neither arrival order (progress vs. details load) loses data — see [MatnDetailsViewModel]. */
    val progress: MatnProgress? = null,
)

/**
 * US3 note-editor sheet state. [initialText] is `null` until [com.giraffe.matn.domain.usecase.GetNoteUseCase]
 * resolves (prefill may arrive after the sheet opens, or stay `null` for a genuinely new note) —
 * the sheet's live draft text is local Compose state in the host (the
 * [com.giraffe.matn.presentation.player.RepetitionSetupSheet] precedent), seeded from
 * [initialText]. [saveError] surfaces a rejected blank save (FR-019) without closing the sheet.
 */
data class NoteEditorState(
    val verseId: String,
    val verseRef: AnnotatedVerseRef,
    val initialText: String?,
    val saveError: Boolean = false,
)

data class MatnHeader(
    val coverImageRef: String?,
    val title: String,
    val author: String,
    val description: String,
    val verseCount: Int,
    val totalDurationMs: Long,
    /** Phase 7 (FR-006): memorized-verse count and fraction, derived from [MatnDetailsUiState.progress]. */
    val memorizedCount: Int = 0,
    val progressFraction: Float = 0f,
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