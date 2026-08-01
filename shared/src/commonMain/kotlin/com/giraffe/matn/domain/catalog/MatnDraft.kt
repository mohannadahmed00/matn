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
    /** The server-assigned `matns.revision` from the last read — the concurrency token (FR-043).
     * `null` for a draft that has never been saved. */
    val remoteRevision: String?,
) {
    val verseCount: Int get() = verses.size

    /** Sum of stored content bytes: text plus every verse's audio (FR-007). */
    val declaredSizeBytes: Long
        get() {
            val metadataBytes = id.length + title.length + author.length + description.length +
                (coverImageRef?.length ?: 0) + defaultReciterId.length
            val chapterBytes = chapters.sumOf { it.id.length + it.title.length }
            val verseBytes = verses.sumOf { it.id.length + (it.chapterId?.length ?: 0) + it.arabicText.length }
            val audioBytes = verses.sumOf { it.audio?.sizeBytes ?: 0L }
            return (metadataBytes + chapterBytes + verseBytes).toLong() + audioBytes
        }

    val audioCompleteness: AudioCompleteness get() = AudioCompleteness.of(verses)
}

data class DraftChapter(
    val id: String,
    val title: String,
    val order: Int,
    /**
     * The [DraftVerse.displayNumber] this chapter opens with, or `null` while the teacher has not
     * said. Every verse from here up to the next chapter's start belongs to this one — which is how
     * [ChapterAssignment] turns a handful of numbers into every verse's [DraftVerse.chapterId].
     *
     * Stored rather than derived from the assignment it produces: a chapter whose first verse has
     * not been written yet has nowhere to derive from, and losing the number the moment it is typed
     * is worse than keeping a field.
     */
    val startVerseNumber: Int? = null,
)

data class DraftVerse(
    val id: String,
    val chapterId: String?,
    val displayNumber: Int,
    val arabicText: String,
    val audio: DraftAudio?,
    val durationMs: Long,
)

/** One verse's recording (`data-model.md` §1). `id` is stable across a replacement (A5); the
 * others are measured from the stored bytes, never entered by the teacher (A2). */
data class DraftAudio(
    val id: String,
    val fileRef: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val sampleRate: Int,
    val channels: Int,
)
