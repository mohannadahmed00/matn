package com.giraffe.matn.domain.model

/**
 * Aggregate read used to render a matn's details header + table of contents decision.
 *
 * @param showTableOfContents `true` only for a [StructureKind.STRUCTURED] matn with at least
 *   one chapter (FR-013/FR-015). Simple matns never show a TOC.
 */
data class MatnDetails(
    val matn: Matn,
    val chapters: List<Chapter>,
    val showTableOfContents: Boolean,
    /**
     * The catalog overview's published download size (FR-002). Always available offline, so the
     * details screen can state a size without a network round-trip.
     *
     * Phase 13 removed `isStarter` alongside it: no matn is permanently present or exempt from
     * removal any more (FR-031, FR-040).
     */
    val declaredSizeBytes: Long = 0L,
)