package com.giraffe.matn.domain.catalog

import com.giraffe.matn.domain.model.StructureKind

/**
 * The authoring aggregate the teacher edits, validates, saves, and publishes
 * (`data-model.md` §1). Owns its chapters and verses; nothing else may hold them.
 */
data class MatnDraft(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val coverImageRef: String?,
    val structureKind: StructureKind,
    val defaultReciterId: String,
    val chapters: List<DraftChapter>,
    val verses: List<DraftVerse>,
    val publicationState: PublicationState,
    val createdAt: Long,
    val updatedAt: Long,
    val remoteUpdateTime: String?,
) {
    val verseCount: Int get() = verses.size

    /** Sum of stored content bytes. Text-only this phase; grows with audio in Phase 12. */
    val declaredSizeBytes: Long
        get() {
            val metadataBytes = id.length + title.length + author.length + description.length +
                (coverImageRef?.length ?: 0) + defaultReciterId.length
            val chapterBytes = chapters.sumOf { it.id.length + it.title.length }
            val verseBytes = verses.sumOf { it.id.length + (it.chapterId?.length ?: 0) + it.arabicText.length }
            return (metadataBytes + chapterBytes + verseBytes).toLong()
        }

    val audioCompleteness: AudioCompleteness get() = AudioCompleteness.of(verses)
}

data class DraftChapter(
    val id: String,
    val title: String,
    val order: Int,
)

data class DraftVerse(
    val id: String,
    val chapterId: String?,
    val displayNumber: Int,
    val arabicText: String,
    val audio: DraftAudio?,
    val durationMs: Long,
)

/** Reserved for Phase 12. Always `null` on a `DraftVerse` in Phase 11 (FR-045). */
data class DraftAudio(
    val id: String,
    val fileRef: String,
    val durationMs: Long,
)
