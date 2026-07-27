package com.giraffe.matn.domain.catalog

import com.giraffe.matn.domain.model.StructureKind

/**
 * The only place a matn, chapter, or verse comes into existence (`data-model.md` §12).
 * `newId`/`nowMillis` are injected lambdas, not `Uuid.random()` calls inline, so this is
 * testable — the same pattern `ContentModule.kt` uses for `BookmarkRepositoryImpl`.
 */
object MatnDraftFactory {

    /** FR-007a: the fixed institutional reciter every authored matn is assigned. */
    const val INSTITUTIONAL_RECITER_ID = "b6e6b8a0-6b0b-4b0b-9b0b-6b0b6b0b6b0b"

    fun newDraft(
        newId: () -> String,
        nowMillis: () -> Long,
        title: String = "",
        author: String = "",
        structureKind: StructureKind = StructureKind.SIMPLE,
    ): MatnDraft {
        val now = nowMillis()
        return MatnDraft(
            id = newId(),
            title = title,
            author = author,
            description = "",
            coverImageRef = null,
            structureKind = structureKind,
            defaultReciterId = INSTITUTIONAL_RECITER_ID,
            chapters = emptyList(),
            verses = emptyList(),
            publicationState = PublicationState.DRAFT,
            createdAt = now,
            updatedAt = now,
            remoteUpdateTime = null,
        )
    }

    fun newChapter(newId: () -> String, title: String = "", order: Int = 0): DraftChapter =
        DraftChapter(id = newId(), title = title, order = order)

    fun newVerse(newId: () -> String, chapterId: String? = null, displayNumber: Int = 1, arabicText: String = ""): DraftVerse =
        DraftVerse(
            id = newId(),
            chapterId = chapterId,
            displayNumber = displayNumber,
            arabicText = arabicText,
            audio = null,
            durationMs = 0L,
        )
}
