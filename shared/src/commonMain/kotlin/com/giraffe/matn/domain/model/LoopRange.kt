package com.giraffe.matn.domain.model

/**
 * The A–B loop selection, identified by **stable verse UUIDs**, not list positions (Principle VI
 * / data-model.md §1.2) — the range survives scrolling, list rebuilds, and later persistence.
 * `startVerseId == endVerseId` is legal and yields a single-verse loop.
 */
data class LoopRange(val startVerseId: String, val endVerseId: String)
