package com.giraffe.matn.presentation.player

import com.giraffe.matn.presentation.details.VerseRow

/**
 * UI-state for the focused reading carousel (data-model.md § ReadingCarouselUiState;
 * specs/010-design-system-adoption). A windowed view of the same [VerseRow] list
 * [MatnDetailsScreen][com.giraffe.matn.presentation.details.MatnDetailsScreen] already loads —
 * no new fetch, just previous/active/next relative to the currently playing verse.
 *
 * [previousVerse]/[nextVerse] are `null` at the first/last verse of the matn (spec Edge Cases) —
 * [ReadingCarousel] renders an empty slot rather than erroring.
 */
data class ReadingCarouselUiState(
    val previousVerse: VerseRow?,
    val activeVerse: VerseRow,
    val nextVerse: VerseRow?,
)

/**
 * Pure windowing derivation: locates [activeVerseId] in [verses] and returns its immediate
 * neighbors. Returns `null` when there is no active verse or it isn't present in the list (e.g.
 * playback hasn't started, or the verse was removed) — callers fall back to the browse verse list.
 */
fun windowVersesForCarousel(verses: List<VerseRow>, activeVerseId: String?): ReadingCarouselUiState? {
    if (activeVerseId == null) return null
    val index = verses.indexOfFirst { it.id == activeVerseId }
    if (index < 0) return null
    return ReadingCarouselUiState(
        previousVerse = verses.getOrNull(index - 1),
        activeVerse = verses[index],
        nextVerse = verses.getOrNull(index + 1),
    )
}
