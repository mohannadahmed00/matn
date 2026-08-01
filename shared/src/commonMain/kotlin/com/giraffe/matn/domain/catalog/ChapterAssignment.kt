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
     * Returns [draft] with every verse's [DraftVerse.chapterId] recomputed from the chapter starts.
     *
     * **[DraftChapter.order] is left alone.** Reindexing it from the starts is tempting — the two
     * would then always agree — but this runs on every keystroke, and re-sorting the list while a
     * start is half-typed makes the row being edited jump around the screen (`15` sorts as `1`
     * first). Order is the teacher's arrangement of the chapters; the start says which verses each
     * one holds. Two separate facts, and rewriting one while they type the other is not this
     * function's business.
     *
     * **A draft where no chapter has a start is returned untouched.** Chapter starts arrived after
     * matns already existed whose verses carry a `chapterId` set some other way; recomputing from
     * an empty set of starts would silently unassign every one of them.
     */
    fun apply(draft: MatnDraft): MatnDraft {
        val starts = draft.chapters.filter { it.startVerseNumber != null }.sortedBy { it.startVerseNumber }
        if (starts.isEmpty()) return draft

        return draft.copy(
            verses = draft.verses.map { verse ->
                val owner = starts.lastOrNull { it.startVerseNumber!! <= verse.displayNumber }
                if (verse.chapterId == owner?.id) verse else verse.copy(chapterId = owner?.id)
            },
        )
    }
}
