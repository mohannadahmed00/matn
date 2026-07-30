package com.giraffe.matn.domain.catalog

/** The overview projection a list row needs — nothing more (FR-012, FR-035, `data-model.md` §7). */
data class CatalogEntry(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val coverImageRef: String?,
    val verseCount: Int,
    val declaredSizeBytes: Long,
    val publicationState: PublicationState,
    val audioCompleteness: AudioCompleteness,
    val updatedAt: Long,
)
