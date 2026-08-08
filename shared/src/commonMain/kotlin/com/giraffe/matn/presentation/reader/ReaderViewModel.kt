package com.giraffe.matn.presentation.reader

import androidx.lifecycle.viewModelScope
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.AnnotatedVerseRef
import com.giraffe.matn.domain.model.MatnDetails
import com.giraffe.matn.domain.model.Note
import com.giraffe.matn.domain.model.PlaybackStatus
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.domain.model.Verse
import com.giraffe.matn.domain.model.VerseAnnotations
import com.giraffe.matn.domain.usecase.SaveNoteParams
import com.giraffe.matn.domain.usecase.ToggleVerseMemorizedUseCase
import com.giraffe.matn.playback.PlaybackController
import com.giraffe.matn.presentation.base.BaseViewModel
import com.giraffe.matn.presentation.details.NoteEditorState
import com.giraffe.matn.presentation.details.VerseRow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Owns the reader route's state (Principle II). Constructed with concrete use cases (no
 * repositories — Principle I), the route's `matnId`, and its optional `?v=` verse.
 *
 * Split out of `MatnDetailsViewModel`, which carried both surfaces. What moved here is everything
 * that only matters while a verse is being *worked on*: the carousel window, playback, the loop
 * range, per-verse annotation toggles and the note editor. What stayed behind is everything about
 * the matn as an object you are deciding to open: cover, description, contents, download control.
 *
 * [autoplay] is honoured **once**, after the verse list resolves. It is what makes a play tap on
 * Details a single gesture rather than "navigate, then press play again" — but it deliberately does
 * nothing when the caller has already started a session (Continue Learning does exactly that, so it
 * navigates with `autoplay = false` and this ViewModel simply adopts the live session).
 */
class ReaderViewModel(
    private val matnId: String,
    requestedVerseId: String? = null,
    private val autoplay: Boolean = false,
    private val getMatnDetails: UseCase<String, MatnDetails>,
    private val observeVerses: FlowUseCase<String, List<Verse>>,
    private val getFontSize: FlowUseCase<Unit, ReadingFontSize>,
    private val setFontSize: UseCase<ReadingFontSize, Unit>,
    private val playbackController: PlaybackController,
    private val observeVerseAnnotations: FlowUseCase<String, Map<String, VerseAnnotations>>,
    private val toggleBookmark: UseCase<String, Boolean>,
    private val getNote: UseCase<String, Note?>,
    private val saveNote: UseCase<SaveNoteParams, Note>,
    private val deleteNote: UseCase<String, Unit>,
    private val observeVerseMemorization: FlowUseCase<String, Set<String>>? = null,
    private val toggleVerseMemorized: UseCase<ToggleVerseMemorizedUseCase.Params, Unit>? = null,
) : BaseViewModel<ReaderUiState>(ReaderUiState()) {

    private val requested: String? = requestedVerseId?.takeIf { it.isNotBlank() }
    private var loadedDetails: MatnDetails? = null
    private var verseRows: List<VerseRow> = emptyList()
    /** Guards [autoplay] so a later verse emission — a font change, an annotation edit — cannot
     *  restart playback the student has since paused. */
    private var autoplayConsumed = false

    init {
        loadDetails()
        observeVerseList()
        observeFontSize()
        observePlayback()
        observeAnnotations()
        observeMemorization()
    }

    // ------------------------------------------------------------------------------ Playback
    //
    // Transport (play/pause/next/previous/seek/speed) is deliberately absent: PlayerBarViewModel
    // already owns it and is hosted alongside this ViewModel on the same route. Duplicating it here
    // would put two objects in charge of the same controller, which is exactly how a pause ends up
    // fighting a resume.

    /** FR-011 (US2): mark the A/B loop boundaries, or clear the range. Forwarding only. */
    fun onSetLoopStart(verseId: String) = playbackController.setLoopStart(verseId)
    fun onSetLoopEnd(verseId: String) = playbackController.setLoopEnd(verseId)
    fun onClearLoop() = playbackController.clearLoop()

    // -------------------------------------------------------------------------- Annotations

    /** US2 FR-010: toggle the bookmark on [verseId]. Best-effort — the observed annotations flow
     *  re-emits and updates state on success. */
    fun onToggleBookmark(verseId: String) {
        runUseCase(
            useCase = toggleBookmark,
            params = verseId,
            onSuccess = {/* observeAnnotations() re-emits and updates state */ },
            onError = {/* best-effort; leave current indicator state */ },
        )
    }

    /** US1 FR-002: toggle "memorized" on [verseId] — the target is the inverse of its current
     *  membership in [ReaderUiState.memorizedVerseIds]. */
    fun onToggleMemorized(verseId: String) {
        val useCase = toggleVerseMemorized ?: return
        val target = verseId !in stateValue.memorizedVerseIds
        runUseCase(
            useCase = useCase,
            params = ToggleVerseMemorizedUseCase.Params(verseId, target),
            onSuccess = {/* observeMemorization() re-emits and updates state */ },
            onError = {/* best-effort; leave current indicator state */ },
        )
    }

    /** US3 FR-015: open the editor for [verseId], prefilling via [getNote] once it resolves
     *  (best-effort — a failed prefill still opens the sheet as a create-new note). */
    fun onOpenNoteEditor(verseId: String) {
        val verseRow = verseRows.firstOrNull { it.id == verseId } ?: return
        val ref = AnnotatedVerseRef(
            matnId = matnId,
            matnTitle = loadedDetails?.matn?.title.orEmpty(),
            verseId = verseId,
            verseNumber = verseRow.displayNumber,
            verseText = verseRow.arabicText,
        )
        setState { it.copy(noteEditor = NoteEditorState(verseId = verseId, verseRef = ref, initialText = null)) }
        runUseCase(
            useCase = getNote,
            params = verseId,
            onSuccess = { note ->
                setState {
                    it.copy(noteEditor = it.noteEditor?.takeIf { e -> e.verseId == verseId }?.copy(initialText = note?.text))
                }
            },
            onError = {/* prefill best-effort; sheet stays open as create-new */ },
        )
    }

    /** US3 FR-015/FR-019: persist [text] for the open editor's verse. */
    fun onSaveNote(text: String) {
        val editor = stateValue.noteEditor ?: return
        runUseCase(
            useCase = saveNote,
            params = SaveNoteParams(editor.verseId, text),
            onSuccess = { setState { it.copy(noteEditor = null) } },
            onError = { setState { it.copy(noteEditor = it.noteEditor?.copy(saveError = true)) } },
        )
    }

    /** US3 FR-015: explicit delete (never triggered by an empty save, FR-019). */
    fun onDeleteNote() {
        val editor = stateValue.noteEditor ?: return
        runUseCase(
            useCase = deleteNote,
            params = editor.verseId,
            onSuccess = { setState { it.copy(noteEditor = null) } },
            onError = {/* best-effort; leave the sheet open so the user can retry */ },
        )
    }

    fun onDismissNoteEditor() {
        setState { it.copy(noteEditor = null) }
    }

    /** US4: persist a new font-size step; the observed flow re-emits and re-renders live. */
    fun onFontSizeChanged(size: ReadingFontSize) {
        runUseCase(
            useCase = setFontSize,
            params = size,
            onSuccess = {/* the observed flow re-emits and updates state */ },
            onError = {/* font-size persistence is best-effort; leave current size */ },
        )
    }

    // ----------------------------------------------------------------------------- Observers

    private fun loadDetails() {
        runUseCase(
            useCase = getMatnDetails,
            params = matnId,
            onSuccess = { details ->
                loadedDetails = details
                rebuild()
            },
            onError = { error -> setState { it.copy(isLoading = false, error = error) } },
        )
    }

    private fun observeVerseList() {
        observeVerses.invoke(matnId)
            .onEach { verses ->
                verseRows = verses.map { it.toVerseRow() }
                rebuild()
                maybeAutoplay()
            }
            .launchIn(viewModelScope)
    }

    /**
     * Starts the session the caller asked for, once, as soon as there is a verse list to start it
     * from. Guarded by [autoplayConsumed] rather than by "is anything playing": a student who opens
     * the reader and immediately pauses must not have playback restarted under them by the next
     * unrelated emission.
     */
    private fun maybeAutoplay() {
        if (!autoplay || autoplayConsumed || verseRows.isEmpty()) return
        autoplayConsumed = true
        val target = requested?.takeIf { id -> verseRows.any { it.id == id } }
        if (target != null) {
            playbackController.playFromVerse(matnId, target)
        } else {
            playbackController.playFromStart(matnId)
        }
    }

    private fun observeFontSize() {
        getFontSize.invoke(Unit)
            .onEach { size -> setState { it.copy(fontSize = size) } }
            .launchIn(viewModelScope)
    }

    private fun observePlayback() {
        playbackController.state
            .onEach { ps ->
                setState {
                    it.copy(
                        activeVerseId = if (ps.status == PlaybackStatus.ENDED) null else ps.activeVerseId,
                        isPlaying = ps.isPlaying,
                        loopRangeVerseIds = ps.loopRangeVerseIds,
                        loopRange = ps.settings.loopRange,
                        chapterTitle = chapterTitleFor(ps.activeVerseId ?: it.requestedVerseId),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeAnnotations() {
        observeVerseAnnotations.invoke(matnId)
            .onEach { annotations -> setState { it.copy(annotations = annotations) } }
            .launchIn(viewModelScope)
    }

    private fun observeMemorization() {
        val useCase = observeVerseMemorization ?: return
        useCase.invoke(matnId)
            .onEach { ids -> setState { it.copy(memorizedVerseIds = ids) } }
            .launchIn(viewModelScope)
    }

    /** Folds the two async inputs in one place, so neither arrival order loses data. */
    private fun rebuild() {
        setState { current ->
            if (current.error != null) return@setState current
            val details = loadedDetails
            val resolvedRequest = requested?.takeIf { id -> verseRows.any { it.id == id } }
            current.copy(
                isLoading = details == null,
                matnTitle = details?.matn?.title.orEmpty(),
                verses = verseRows,
                requestedVerseId = resolvedRequest,
                chapterTitle = chapterTitleFor(current.activeVerseId ?: resolvedRequest),
            )
        }
    }

    /** The chapter the given verse belongs to. `null` for a SIMPLE matn, or before either input
     *  has landed — the top bar simply renders one line in that case. */
    private fun chapterTitleFor(verseId: String?): String? {
        val chapters = loadedDetails?.chapters.orEmpty()
        if (chapters.isEmpty()) return null
        val chapterId = verseRows.firstOrNull { it.id == verseId }?.chapterId ?: return null
        return chapters.firstOrNull { it.id == chapterId }?.title
    }

    private fun Verse.toVerseRow(): VerseRow = VerseRow(
        id = id,
        displayNumber = displayNumber,
        arabicText = arabicText,
        durationMs = durationMs,
        chapterId = chapterId,
    )
}
