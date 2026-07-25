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
    /** Phase 8 (FR-002/FR-003): the catalog's declared install size — always available offline,
     *  even before the delivery engine has been asked (data-model.md §1.1). `0L` for a matn with
     *  no `content_pack` row (defensive default; every seeded matn has one, T021). */
    val declaredSizeBytes: Long = 0L,
)