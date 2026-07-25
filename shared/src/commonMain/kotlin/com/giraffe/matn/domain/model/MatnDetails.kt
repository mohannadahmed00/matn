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
    /** Phase 8 (FR-002/FR-003): the catalog's declared install size (data-model.md §1.1). */
    val declaredSizeBytes: Long = 0L,
    /** Phase 8 (FR-027): true for the bundled, non-removable starter matn. */
    val isStarter: Boolean = false,
)