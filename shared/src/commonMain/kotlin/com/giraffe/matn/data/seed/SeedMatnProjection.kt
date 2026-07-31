package com.giraffe.matn.data.seed

import com.giraffe.matn.domain.catalog.DraftAudio
import com.giraffe.matn.domain.catalog.DraftChapter
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.model.StructureKind

/**
 * `MatnDraft` ↔ `SeedMatn` (`data-model.md` §11, FR-009). This is the Phase 13 seam: the
 * authoring tool's aggregate and the app's bundled-content DTO correspond field for field.
 */
fun SeedMatn.toDraft(): MatnDraft = MatnDraft(
    id = id,
    title = title,
    author = author,
    description = description,
    coverImageRef = coverImageRef,
    structureKind = StructureKind.valueOf(structureKind),
    defaultReciterId = defaultReciterId,
    chapters = chapters.map { DraftChapter(id = it.id, title = it.title, order = it.order) },
    verses = verses.map { verse ->
        DraftVerse(
            id = verse.id,
            chapterId = verse.chapterId,
            displayNumber = verse.displayNumber,
            arabicText = verse.arabicText,
            audio = verse.audio?.let {
                DraftAudio(
                    id = it.id,
                    fileRef = it.fileRef,
                    durationMs = it.durationMs,
                    sizeBytes = it.sizeBytes,
                    sampleRate = it.sampleRate,
                    channels = it.channels,
                )
            },
            durationMs = verse.durationMs,
        )
    },
    publicationState = PublicationState.DRAFT,
    createdAt = 0L,
    updatedAt = 0L,
    remoteRevision = null,
)

fun MatnDraft.toSeedMatn(packId: String = "", declaredSizeBytes: Long = 0L): SeedMatn = SeedMatn(
    id = id,
    title = title,
    author = author,
    description = description,
    coverImageRef = coverImageRef,
    structureKind = structureKind.name,
    defaultReciterId = defaultReciterId,
    chapters = chapters.map { SeedChapter(id = it.id, title = it.title, order = it.order) },
    verses = verses.map { verse ->
        SeedVerse(
            id = verse.id,
            chapterId = verse.chapterId,
            displayNumber = verse.displayNumber,
            arabicText = verse.arabicText,
            durationMs = verse.durationMs,
            audio = verse.audio?.let {
                SeedAudio(
                    id = it.id,
                    fileRef = it.fileRef,
                    durationMs = it.durationMs,
                    sizeBytes = it.sizeBytes,
                    sampleRate = it.sampleRate,
                    channels = it.channels,
                )
            },
        )
    },
    packId = packId,
    declaredSizeBytes = declaredSizeBytes,
    isStarter = false,
)
