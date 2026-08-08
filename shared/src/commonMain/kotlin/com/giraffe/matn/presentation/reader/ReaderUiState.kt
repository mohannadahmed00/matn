package com.giraffe.matn.presentation.reader

import com.giraffe.matn.core.AppError
import com.giraffe.matn.domain.model.LoopRange
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.domain.model.VerseAnnotations
import com.giraffe.matn.presentation.details.NoteEditorState
import com.giraffe.matn.presentation.details.VerseRow

/**
 * Immutable UI state for the reader — the immersive three-verse focus surface (Matn Design System
 * §05, *Reader*; Principle II).
 *
 * Split out of `MatnDetailsUiState`, which used to carry both surfaces and switch between them on
 * whether a verse was active. That switch is what made the reader unreachable by deep link and
 * unable to own a back-stack entry of its own: entering it was a side effect of playback starting,
 * so there was no state to return *to*. The design's rule is the other way round — the reader is
 * the one screen that earns full-screen status, so it gets a route, and playback becomes something
 * that happens *on* it rather than something that summons it.
 *
 * [VerseRow] and [NoteEditorState] are still the details package's types. They describe a verse and
 * a note-editor sheet, not a screen, and both surfaces genuinely need the same shapes — duplicating
 * them to avoid the import would be worse than the import.
 */
data class ReaderUiState(
    val isLoading: Boolean = true,
    val matnTitle: String = "",
    /** The active verse's chapter, for the top bar's second line. `null` for a SIMPLE matn. */
    val chapterTitle: String? = null,
    val verses: List<VerseRow> = emptyList(),
    val activeVerseId: String? = null,
    val isPlaying: Boolean = false,
    val fontSize: ReadingFontSize = ReadingFontSize.MEDIUM,
    val annotations: Map<String, VerseAnnotations> = emptyMap(),
    val memorizedVerseIds: Set<String> = emptySet(),
    val noteEditor: NoteEditorState? = null,
    val loopRangeVerseIds: Set<String> = emptySet(),
    /** The route's `?v=`, resolved against the loaded verse list. Retained rather than consumed, so
     *  it keeps deciding the focus until playback supersedes it. */
    val requestedVerseId: String? = null,
    val loopRange: LoopRange? = null,
    val error: AppError? = null,
) {
    /**
     * Which verse the carousel centres on. Playback wins whenever a session is live; the route's
     * requested verse only decides where an *idle* reader opens, which is what lets Continue
     * Learning restore a position without the arrival order of two flows deciding it.
     */
    val focusedVerseId: String? get() = activeVerseId ?: requestedVerseId
}
