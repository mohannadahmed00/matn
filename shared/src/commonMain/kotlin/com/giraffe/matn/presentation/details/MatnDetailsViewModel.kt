package com.giraffe.matn.presentation.details

import androidx.lifecycle.viewModelScope
import com.giraffe.matn.core.usecase.FlowUseCase
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.model.Chapter
import com.giraffe.matn.domain.model.MatnDetails
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.domain.model.Verse
import com.giraffe.matn.playback.PlaybackController
import com.giraffe.matn.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

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
                    )
                },
                chapters = details?.chapters.orEmpty().toChapterRows(verseRows),
                showTableOfContents = details?.showTableOfContents ?: false,
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