package com.giraffe.matn.presentation.details

import com.giraffe.matn.core.AppError
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.model.AnnotatedVerseRef
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.LoopRange
import com.giraffe.matn.domain.model.MatnProgress
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.domain.model.RemovalOutcome
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
    /** Phase 8 (FR-002/FR-004): this matn's content-delivery availability, driving the header's
     *  [com.giraffe.matn.presentation.common.ContentActionButton]. `null` until the first
     *  emission arrives. */
    val availability: ContentAvailability? = null,
    /** Phase 8 (FR-003): the catalog's declared install size (data-model.md §1.1). */
    val declaredSizeBytes: Long = 0L,
    /** Phase 8 (FR-007/FR-015): the reason the last install attempt was refused (offline /
     *  required-vs-available bytes) — the screen maps this to a localized message, mirroring
     *  [errorMessage] for [error]. `null` when there is nothing to report. */
    val installError: DeliveryError? = null,
    /** Phase 8 (FR-018, US2): true while [ConfirmRemovalDialog][com.giraffe.matn.presentation.common.ConfirmRemovalDialog]
     *  is open. Removal never fires without this confirmation (spec edge case "Repeated taps on
     *  install/remove"). */
    val pendingRemovalConfirmation: Boolean = false,
    /** Phase 8 (FR-017/FR-021): the most recent removal's outcome, so honest post-removal copy
     *  can be shown (`Reclaimed` vs `ReleasedPendingSystemReclaim`). `null` before any removal. */
    val lastRemovalOutcome: RemovalOutcome? = null,
    /** Phase 13 (FR-012): the cached cover's bytes, read from disk only — the library grid is what
     *  fetches. `null` keeps [com.giraffe.matn.presentation.common.CoverImage]'s placeholder. */
    val coverBytes: ByteArray? = null,
) {
    // `coverBytes` is a ByteArray, so the generated equals/hashCode would compare it by identity and
    // make two otherwise-equal states differ. It is assigned once from the cache and never rebuilt,
    // so comparing the reference is correct here — spelled out rather than left to surprise a reader.
    override fun equals(other: Any?): Boolean = this === other || (other is MatnDetailsUiState && compare(other))

    private fun compare(other: MatnDetailsUiState): Boolean =
        isLoading == other.isLoading && header == other.header && verses == other.verses &&
            chapters == other.chapters && showTableOfContents == other.showTableOfContents &&
            fontSize == other.fontSize && error == other.error && activeVerseId == other.activeVerseId &&
            isPlaying == other.isPlaying && loopRangeVerseIds == other.loopRangeVerseIds &&
            loopRange == other.loopRange && focusVerseId == other.focusVerseId &&
            annotations == other.annotations && noteEditor == other.noteEditor &&
            memorizedVerseIds == other.memorizedVerseIds && progress == other.progress &&
            availability == other.availability && declaredSizeBytes == other.declaredSizeBytes &&
            installError == other.installError &&
            pendingRemovalConfirmation == other.pendingRemovalConfirmation &&
            lastRemovalOutcome == other.lastRemovalOutcome && coverBytes === other.coverBytes

    override fun hashCode(): Int {
        var result = isLoading.hashCode()
        result = 31 * result + (header?.hashCode() ?: 0)
        result = 31 * result + verses.hashCode()
        result = 31 * result + chapters.hashCode()
        result = 31 * result + showTableOfContents.hashCode()
        result = 31 * result + fontSize.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + (activeVerseId?.hashCode() ?: 0)
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + loopRangeVerseIds.hashCode()
        result = 31 * result + (loopRange?.hashCode() ?: 0)
        result = 31 * result + (focusVerseId?.hashCode() ?: 0)
        result = 31 * result + annotations.hashCode()
        result = 31 * result + (noteEditor?.hashCode() ?: 0)
        result = 31 * result + memorizedVerseIds.hashCode()
        result = 31 * result + (progress?.hashCode() ?: 0)
        result = 31 * result + (availability?.hashCode() ?: 0)
        result = 31 * result + declaredSizeBytes.hashCode()
        result = 31 * result + (installError?.hashCode() ?: 0)
        result = 31 * result + pendingRemovalConfirmation.hashCode()
        result = 31 * result + (lastRemovalOutcome?.hashCode() ?: 0)
        return result
    }
}

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