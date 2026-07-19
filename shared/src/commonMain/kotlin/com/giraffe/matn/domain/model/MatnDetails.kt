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
)