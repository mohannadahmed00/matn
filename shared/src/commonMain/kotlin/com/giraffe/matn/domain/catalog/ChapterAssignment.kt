package com.giraffe.matn.domain.catalog

/**
 * Turns each chapter's [DraftChapter.startVerseNumber] into every verse's [DraftVerse.chapterId].
 *
 * A teacher marking up a matn thinks in terms of "chapter two starts at verse 15", not "these
 * forty verses each carry this chapter's id" — so the start is what they enter, and the
 * per-verse assignment the rest of the app reads is computed from it.
 *
 * A verse belongs to the **last** chapter that starts at or before it. Verses ahead of the first
 * chapter's start belong to none, which is a real state worth representing: a preamble before
 * chapter one is ordinary, and the validator will say so at publish time if it should not be.
 */
object ChapterAssignment {

    /**
     * Returns [draft] with chapter ordering and verse assignment brought into agreement with the
     * chapter starts.
     *
     * **A draft where no chapter has a start is returned untouched.** Chapter starts arrived after
     * matns already existed whose verses carry a `chapterId` set some other way; recomputing from
     * an empty set of starts would silently unassign every one of them.
     */
    fun apply(draft: MatnDraft): MatnDraft {
        if (draft.chapters.none { it.startVerseNumber != null }) return draft

        // Chapters that name a start come first, in start order; the rest keep their relative order
        // behind them. `order` is reindexed from that, so it can never disagree with the layout the
        // starts describe (and never collides, which V2b treats as a blocking problem).
        val (placed, unplaced) = draft.chapters.partition { it.startVerseNumber != null }
        val ordered = placed.sortedBy { it.startVerseNumber } + unplaced.sortedBy { it.order }
        val renumbered = ordered.mapIndexed { index, chapter -> chapter.copy(order = index) }

        val starts = renumbered.filter { it.startVerseNumber != null }
        return draft.copy(
            chapters = renumbered,
            verses = draft.verses.map { verse ->
                val owner = starts.lastOrNull { it.startVerseNumber!! <= verse.displayNumber }
                if (verse.chapterId == owner?.id) verse else verse.copy(chapterId = owner?.id)
            },
        )
    }
}
