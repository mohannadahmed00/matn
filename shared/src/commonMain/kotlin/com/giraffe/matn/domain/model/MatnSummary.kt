package com.giraffe.matn.domain.model

/**
 * Library card projection (FR-002): a [Matn] plus its derived aggregate totals.
 *
 * Totals are derived by the data layer (SQL aggregate) for the library grid so the grid
 * does not need to load every verse of every matn. They are *not* persisted; see
 * [research.md Decision 6] for the deliberate derivation split (the details screen derives
 * the same totals from its already-loaded verse list).
 */
data class MatnSummary(
    val matn: Matn,
    val verseCount: Int,
    val totalDurationMs: Long,
)