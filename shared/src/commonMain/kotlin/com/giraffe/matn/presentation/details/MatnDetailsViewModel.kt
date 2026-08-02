package com.giraffe.matn.presentation.details

import androidx.lifecycle.viewModelScope
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.model.AnnotatedVerseRef
import com.giraffe.matn.domain.model.Chapter
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.MatnDetails
import com.giraffe.matn.domain.model.MatnProgress
import com.giraffe.matn.domain.model.Note
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.domain.model.Verse
import com.giraffe.matn.domain.model.VerseAnnotations
import com.giraffe.matn.domain.usecase.MarkChapterMemorizedUseCase
import com.giraffe.matn.domain.usecase.SaveNoteParams
import com.giraffe.matn.domain.usecase.ToggleVerseMemorizedUseCase
import com.giraffe.matn.playback.PlaybackController
import com.giraffe.matn.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Owns the reading/details screen state (data-model.md §4.2; Principle II). Constructed with
 * the concrete use cases (no repositories — Principle I) and the route's `matnId`.
 *
 * On init:
 *  1. Runs [GetMatnDetailsUseCase] → sets the header (without totals yet), the chapter rows,
 *     and `showTableOfContents` (FR-013/FR-015).
 *  2. Collects [ObserveVersesUseCase] → maps each [Verse] to a [VerseRow] passing `arabicText`
 *     through **verbatim** (FR-008), assigns the ordered list to state, and **recalculates the
 *     header totals inline** from this list (Decision 6), so the header has correct `verseCount`
 *     and `totalDurationMs` (FR-005) despite no second SQL query. Also recomputes each
 *     `ChapterRow.firstVerseDisplayNumber` as the minimum `displayNumber` among that chapter's
 *     verses (FR-014) whenever verses arrive.
 *  3. Collects [GetFontSizeUseCase] into [MatnDetailsUiState.fontSize] (FR-016) — verse text
 *     re-renders live when the preference changes.
 *
 * [onFontSizeChanged] (US4) persists a new size via [SetFontSizeUseCase]; the observed flow then
 * re-emits and the UI updates automatically (FR-017/SC-007).
 */
class MatnDetailsViewModel(
    private val matnId: String,
    private val getMatnDetails: UseCase<String, MatnDetails>,
    private val observeVerses: FlowUseCase<String, List<Verse>>,
    private val getFontSize: FlowUseCase<Unit, ReadingFontSize>,
    private val setFontSize: UseCase<ReadingFontSize, Unit>,
    private val playbackController: PlaybackController,
    /** Phase 6 (research.md D5): optional initial carousel focus from the route. Honored once,
     *  only if present in the loaded verse list — never starts playback. */
    private val focusVerseId: String? = null,
    /** Phase 6 (US2 FR-011/FR-016): per-verse bookmark/note indicator map for this matn. */
    private val observeVerseAnnotations: FlowUseCase<String, Map<String, VerseAnnotations>>,
    /** Phase 6 (US2 FR-010): active-verse bookmark toggle. */
    private val toggleBookmark: UseCase<String, Boolean>,
    /** Phase 6 (US3 FR-015): note-editor prefill/save/delete. */
    private val getNote: UseCase<String, Note?>,
    private val saveNote: UseCase<SaveNoteParams, Note>,
    private val deleteNote: UseCase<String, Unit>,
    /** Phase 7 (US1 FR-006/FR-002): per-matn progress + memorized-verse indicators. Optional/
     *  defaulted so existing call sites and tests keep compiling. */
    private val observeMatnProgress: FlowUseCase<String, MatnProgress>? = null,
    private val observeVerseMemorization: FlowUseCase<String, Set<String>>? = null,
    private val toggleVerseMemorized: UseCase<ToggleVerseMemorizedUseCase.Params, Unit>? = null,
    private val markChapterMemorized: UseCase<MarkChapterMemorizedUseCase.Params, Unit>? = null,
    /** Phase 8 (US1 FR-002/FR-004/FR-005/FR-006): content-delivery availability + install/cancel
     *  intents for the header action. Optional/defaulted so existing call sites and tests keep
     *  compiling. */
    private val observeContentAvailability: FlowUseCase<String, ContentAvailability>? = null,
    private val installMatnContent: UseCase<String, Unit>? = null,
    private val cancelInstall: UseCase<String, Unit>? = null,
    /** Phase 8 (US2 FR-017/FR-018/FR-021): removal, gated behind explicit confirmation. */
    private val removeMatnContent: UseCase<String, RemovalOutcome>? = null,
    /** Phase 13 (FR-012): `matnId -> cached cover bytes`. Reads the disk cache the library grid
     *  populated; never fetches, so the reading path stays free of network calls (SC-004). */
    private val loadCachedCover: (suspend (String) -> ByteArray?)? = null,
) : BaseViewModel<MatnDetailsUiState>(MatnDetailsUiState()) {

    // The two async inputs (details load + verse stream) are cached here and folded into UI
    // state by [rebuild], so the header totals and each chapter's first-verse target are derived
    // in exactly ONE place regardless of which input arrives first (removes the earlier
    // duplicated/dead derivation across applyDetails/applyVerses).
    private var loadedDetails: MatnDetails? = null
    private var verseRows: List<VerseRow> = emptyList()

    init {
        loadDetails()
        observeVerseList()
        observeFontSize()
        observePlayback()
        observeAnnotations()
        observeProgress()
        observeMemorization()
        observeAvailability()
        loadCover()
    }

    /** FR-012: the header cover, from the disk cache only. A miss simply leaves the placeholder. */
    private fun loadCover() {
        val load = loadCachedCover ?: return
        viewModelScope.launch {
            val bytes = load(matnId) ?: return@launch
            setState { it.copy(coverBytes = bytes) }
        }
    }

    /** US1 FR-002: toggle "memorized" on [verseId] — the target boolean is the inverse of its
     *  current membership in [MatnDetailsUiState.memorizedVerseIds]. */
    fun onToggleMemorized(verseId: String) {
        val useCase = toggleVerseMemorized ?: return
        val target = verseId !in stateValue.memorizedVerseIds
        runUseCase(
            useCase = useCase,
            params = ToggleVerseMemorizedUseCase.Params(verseId, target),
            onSuccess = {/* observeMemorization()/observeProgress() re-emit and update state */ },
            onError = {/* best-effort; leave current indicator state */ },
        )
    }

    /** US1 FR-003: bulk mark/un-mark every verse in [chapterId]. */
    fun onMarkChapterMemorized(chapterId: String, memorized: Boolean) {
        val useCase = markChapterMemorized ?: return
        runUseCase(
            useCase = useCase,
            params = MarkChapterMemorizedUseCase.Params(chapterId, memorized),
            onSuccess = {/* observeMemorization()/observeProgress() re-emit and update state */ },
            onError = {/* best-effort; leave current indicator state */ },
        )
    }

    private fun observeProgress() {
        val useCase = observeMatnProgress ?: return
        useCase.invoke(matnId)
            .onEach { progress ->
                setState { current ->
                    current.copy(
                        progress = progress,
                        // Ordering hazard: `header` may not exist yet (details load races this
                        // emission). Re-applying from the stored `progress` inside rebuild()
                        // covers that case; this copy covers the reverse order.
                        header = current.header?.copy(
                            memorizedCount = progress.memorizedCount,
                            progressFraction = progress.fraction,
                        ),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeMemorization() {
        val useCase = observeVerseMemorization ?: return
        useCase.invoke(matnId)
            .onEach { ids -> setState { it.copy(memorizedVerseIds = ids) } }
            .launchIn(viewModelScope)
    }

    /** Phase 8 (FR-002, storage-ui-contract.md §5): its own collector, never gating [isLoading] —
     *  a mid-flight availability change (backgrounding, eviction, install completion) reaches the
     *  state object purely through Flow re-collection, never a cached snapshot. */
    private fun observeAvailability() {
        val useCase = observeContentAvailability ?: return
        useCase.invoke(matnId)
            .onEach { availability -> setState { it.copy(availability = availability) } }
            .launchIn(viewModelScope)
    }

    /** FR-004/FR-007/FR-015: install this matn's on-demand content. A refusal (starter, offline,
     *  insufficient space) is surfaced via [MatnDetailsUiState.installError] rather than silently
     *  dropped (storage-ui-contract.md §5 "never a silent failure"). */
    fun onInstall() {
        val useCase = installMatnContent ?: return
        runUseCase(
            useCase = useCase,
            params = matnId,
            onSuccess = { setState { it.copy(installError = null) } },
            onError = { error -> setState { it.copy(installError = error as? DeliveryError) } },
        )
    }

    /** FR-005/FR-006: cancel an in-flight install; [observeContentAvailability] re-emits and
     *  returns the header action to its not-installed state. */
    fun onCancelInstall() {
        val useCase = cancelInstall ?: return
        viewModelScope.launch { useCase.invoke(matnId) }
    }

    /** FR-018: open the removal confirmation. Removal never fires without it. */
    fun onRemoveRequested() {
        setState { it.copy(pendingRemovalConfirmation = true) }
    }

    /** FR-018: dismiss without removing. */
    fun onDismissRemoval() {
        setState { it.copy(pendingRemovalConfirmation = false) }
    }

    /** FR-017/FR-021/FR-027: confirmed removal. [observeAvailability] re-emits and reflects the
     *  new state; [MatnDetailsUiState.lastRemovalOutcome] carries the platform-honest outcome. */
    fun onConfirmRemoval() {
        val useCase = removeMatnContent ?: return
        setState { it.copy(pendingRemovalConfirmation = false) }
        runUseCase(
            useCase = useCase,
            params = matnId,
            onSuccess = { outcome -> setState { it.copy(lastRemovalOutcome = outcome) } },
            onError = {/* best-effort; availability stays whatever the repository last reported */ },
        )
    }

    /** US2 FR-010: toggle the bookmark on [verseId] (typically the active verse). Best-effort —
     *  the observed [observeVerseAnnotations] flow re-emits and updates state on success. */
    fun onToggleBookmark(verseId: String) {
        runUseCase(
            useCase = toggleBookmark,
            params = verseId,
            onSuccess = {/* observeAnnotations() re-emits and updates state */ },
            onError = {/* best-effort; leave current indicator state */ },
        )
    }

    private fun observeAnnotations() {
        observeVerseAnnotations.invoke(matnId)
            .onEach { annotations -> setState { it.copy(annotations = annotations) } }
            .launchIn(viewModelScope)
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
                setState { it.copy(noteEditor = it.noteEditor?.takeIf { e -> e.verseId == verseId }?.copy(initialText = note?.text)) }
            },
            onError = {/* prefill best-effort; sheet stays open as create-new */ },
        )
    }

    /** US3 FR-015/FR-019: persist [text] for the open editor's verse. A blank [text] surfaces
     *  [NoteEditorState.saveError] and keeps the sheet open — the Save button is disabled for
     *  blank drafts, so this only guards a defensive/programmatic call. */
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

    /** US3: dismiss without saving — the draft is discarded (FR-019). */
    fun onDismissNoteEditor() {
        setState { it.copy(noteEditor = null) }
    }

    /** User intent: persist a new font-size step (US4). */
    fun onFontSizeChanged(size: ReadingFontSize) {
        runUseCase(
            useCase = setFontSize,
            params = size,
            onSuccess = {/* the observed flow re-emits and updates state */ },
            onError = {/* font-size persistence is best-effort; leave current size */ },
        )
    }

    /** FR-001 (per-verse play): start playback from the tapped verse. */
    fun onVersePlayClicked(verseId: String) {
        playbackController.playFromVerse(matnId, verseId)
    }

    /** FR-001 (global play): start playback from the first verse. */
    fun onGlobalPlayClicked() {
        playbackController.playFromStart(matnId)
    }

    /** FR-011 (US2): mark the A/B loop boundaries, or clear the range. Forwarding only. */
    fun onSetLoopStart(verseId: String) = playbackController.setLoopStart(verseId)
    fun onSetLoopEnd(verseId: String) = playbackController.setLoopEnd(verseId)
    fun onClearLoop() = playbackController.clearLoop()

    private fun loadDetails() {
        setState { it.copy(isLoading = true, error = null) }
        runUseCase(
            useCase = getMatnDetails,
            params = matnId,
            onSuccess = { details ->
                loadedDetails = details
                rebuild()
            },
            onError = { error ->
                setState { it.copy(isLoading = false, error = error) }
            },
        )
    }

    private fun observeVerseList() {
        observeVerses.invoke(matnId)
            .onEach { verses ->
                verseRows = verses.map { it.toVerseRow() }
                rebuild()
            }
            .launchIn(viewModelScope)
    }

    private fun observeFontSize() {
        getFontSize.invoke(Unit)
            .onEach { size -> setState { it.copy(fontSize = size) } }
            .launchIn(viewModelScope)
    }

    /** Fold `PlaybackController.state` (active verse + playing flag) into the reading state. */
    private fun observePlayback() {
        playbackController.state
            .onEach { ps ->
                setState {
                    it.copy(
                        activeVerseId = if (ps.status == com.giraffe.matn.domain.model.PlaybackStatus.ENDED) {
                            null
                        } else {
                            ps.activeVerseId
                        },
                        isPlaying = ps.isPlaying,
                        loopRangeVerseIds = ps.loopRangeVerseIds,
                        loopRange = ps.settings.loopRange,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Single source of truth for the derived reading state. Header totals ([verseCount] /
     * [totalDurationMs]) and chapter first-verse targets are computed here from [verseRows]
     * (Decision 6 — no second SQL query). A failed details load leaves the error in place and
     * is not overwritten by a later verse emission.
     */
    private fun rebuild() {
        setState { current ->
            if (current.error != null) return@setState current
            val details = loadedDetails
            current.copy(
                isLoading = details == null,
                verses = verseRows,
                header = details?.let { d ->
                    MatnHeader(
                        coverImageRef = d.matn.coverImageRef,
                        title = d.matn.title,
                        author = d.matn.author,
                        description = d.matn.description,
                        verseCount = verseRows.size,
                        totalDurationMs = verseRows.sumOf { it.durationMs },
                        memorizedCount = current.progress?.memorizedCount ?: 0,
                        progressFraction = current.progress?.fraction ?: 0f,
                    )
                },
                chapters = details?.chapters.orEmpty().toChapterRows(verseRows),
                showTableOfContents = details?.showTableOfContents ?: false,
                focusVerseId = focusVerseId?.takeIf { id -> verseRows.any { it.id == id } },
                declaredSizeBytes = details?.declaredSizeBytes ?: current.declaredSizeBytes,
            )
        }
    }

    private fun Verse.toVerseRow(): VerseRow = VerseRow(
        id = id,
        displayNumber = displayNumber,
        arabicText = arabicText,
        durationMs = durationMs,
        chapterId = chapterId,
    )

    /** Chapters in authored order, each tagged with its first verse's displayNumber (FR-014). */
    private fun List<Chapter>.toChapterRows(verses: List<VerseRow>): List<ChapterRow> =
        sortedBy { it.order }.map { chapter ->
            ChapterRow(
                id = chapter.id,
                title = chapter.title,
                order = chapter.order,
                firstVerseDisplayNumber = verses
                    .filter { it.chapterId == chapter.id }
                    .minOfOrNull { it.displayNumber }
                    ?: 0,
            )
        }
}