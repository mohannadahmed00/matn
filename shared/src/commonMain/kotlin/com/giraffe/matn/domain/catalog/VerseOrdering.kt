package com.giraffe.matn.domain.catalog

/**
 * Pure verse reorder/renumber arithmetic (FR-019, FR-020, research D11). No Compose, no Android,
 * no I/O — the risky part of drag-reorder is this arithmetic, not the gesture.
 */
object VerseOrdering {

    fun move(verses: List<DraftVerse>, from: Int, to: Int): List<DraftVerse> {
        if (from == to || from !in verses.indices || to !in verses.indices) return renumber(verses)
        val mutable = verses.toMutableList()
        val moved = mutable.removeAt(from)
        mutable.add(to, moved)
        return renumber(mutable)
    }

    /** Assigns display numbers `1..n` from list position — applied after every move/add/delete. */
    fun renumber(verses: List<DraftVerse>): List<DraftVerse> =
        verses.mapIndexed { index, verse -> verse.copy(displayNumber = index + 1) }

    fun removeAt(verses: List<DraftVerse>, index: Int): List<DraftVerse> {
        if (index !in verses.indices) return renumber(verses)
        val mutable = verses.toMutableList()
        mutable.removeAt(index)
        return renumber(mutable)
    }

    fun append(verses: List<DraftVerse>, verse: DraftVerse): List<DraftVerse> =
        renumber(verses + verse)
}
