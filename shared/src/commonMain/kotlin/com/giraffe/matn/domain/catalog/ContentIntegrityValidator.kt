package com.giraffe.matn.domain.catalog

import com.giraffe.matn.domain.error.ContentIntegrityError
import com.giraffe.matn.domain.model.StructureKind

/**
 * Extracted from `ContentSeedLoaderImpl.validate()` (research D8) so the teacher tool and the
 * app's ingestion path enforce one rule set. Operates on the domain [MatnDraft] rather than the
 * data-layer `SeedMatn`, so a domain use case can depend on it without inverting Principle I's
 * dependency arrow.
 */
object ContentIntegrityValidator {

    /** Above this, `ValidateMatn`/`PublishMatn` refuse rather than let the write fail server-side. */
    private const val DOCUMENT_SIZE_LIMIT_BYTES = 900_000L

    fun validate(draft: MatnDraft): ValidationReport {
        val blocking = mutableListOf<ContentIntegrityError>()
        val deferred = mutableListOf<ContentIntegrityError>()

        // V1 InvalidId: blank ids anywhere
        if (draft.id.isBlank()) {
            blocking.add(ContentIntegrityError.InvalidId("matn id is blank"))
        }
        draft.chapters.forEach { chapter ->
            if (chapter.id.isBlank()) {
                blocking.add(ContentIntegrityError.InvalidId("chapter id is blank"))
            }
        }
        draft.verses.forEach { verse ->
            if (verse.id.isBlank()) {
                blocking.add(ContentIntegrityError.InvalidId("verse id is blank"))
            }
            val audio = verse.audio
            if (audio != null && audio.id.isBlank()) {
                blocking.add(ContentIntegrityError.InvalidId("audio id is blank for verse ${verse.id}"))
            }
        }

        // V1b DuplicateId: authored UUIDs are globally unique across all entities. A collision
        // would make an upsert silently overwrite a sibling row, dropping content with no error.
        // Blank ids are already reported above; skip them here.
        val allIds = buildList {
            add(draft.id)
            draft.chapters.forEach { add(it.id) }
            draft.verses.forEach { verse ->
                add(verse.id)
                verse.audio?.let { add(it.id) }
            }
        }
        allIds.groupingBy { it }.eachCount()
            .filter { it.value > 1 && it.key.isNotBlank() }
            .forEach { (dupId, _) ->
                blocking.add(ContentIntegrityError.DuplicateId(dupId))
            }

        // V2 DuplicateDisplayNumber
        val numberCounts = mutableMapOf<Int, Int>()
        draft.verses.forEach { verse ->
            numberCounts[verse.displayNumber] = (numberCounts[verse.displayNumber] ?: 0) + 1
        }
        numberCounts.entries
            .filter { it.value > 1 }
            .forEach { (number, _) ->
                blocking.add(ContentIntegrityError.DuplicateDisplayNumber(draft.id, number))
            }

        // V2b DuplicateChapterOrder
        val orderCounts = mutableMapOf<Int, Int>()
        draft.chapters.forEach { chapter ->
            orderCounts[chapter.order] = (orderCounts[chapter.order] ?: 0) + 1
        }
        orderCounts.entries
            .filter { it.value > 1 }
            .forEach { (order, _) ->
                blocking.add(ContentIntegrityError.DuplicateChapterOrder(draft.id, order))
            }

        // V4 MissingAudio — deferred in Phase 11 (FR-027); evaluated, not skipped.
        draft.verses.forEach { verse ->
            if (verse.audio == null) {
                deferred.add(ContentIntegrityError.MissingAudio(verse.id))
            }
        }

        // V5 DuplicateAudioRef — deferred in Phase 11 (FR-027).
        val refCounts = mutableMapOf<String, Int>()
        draft.verses.forEach { verse ->
            val ref = verse.audio?.fileRef ?: return@forEach
            refCounts[ref] = (refCounts[ref] ?: 0) + 1
        }
        refCounts.entries
            .filter { it.value > 1 }
            .forEach { (fileRef, _) ->
                deferred.add(ContentIntegrityError.DuplicateAudioRef(fileRef))
            }

        // V6 OrphanChapterRef
        val chapterIds = draft.chapters.map { it.id }.toSet()
        draft.verses.forEach { verse ->
            val chapterId = verse.chapterId ?: return@forEach
            if (chapterId !in chapterIds) {
                blocking.add(ContentIntegrityError.OrphanChapterRef(verse.id))
            }
        }

        // V7 StructureMismatch
        when (draft.structureKind) {
            StructureKind.STRUCTURED -> {
                val hasNoChapters = draft.chapters.isEmpty()
                val anyVerseLacksChapter = draft.verses.any { it.chapterId == null }
                if (hasNoChapters || anyVerseLacksChapter) {
                    val detail = buildString {
                        if (hasNoChapters) append("STRUCTURED matn has no chapters. ")
                        if (anyVerseLacksChapter) append("At least one verse has null chapterId. ")
                    }.trim()
                    blocking.add(ContentIntegrityError.StructureMismatch(draft.id, detail))
                }
            }
            StructureKind.SIMPLE -> {
                val hasChapters = draft.chapters.isNotEmpty()
                val anyVerseHasChapter = draft.verses.any { it.chapterId != null }
                if (hasChapters || anyVerseHasChapter) {
                    val detail = buildString {
                        if (hasChapters) append("SIMPLE matn declares chapters. ")
                        if (anyVerseHasChapter) append("At least one verse has a chapterId. ")
                    }.trim()
                    blocking.add(ContentIntegrityError.StructureMismatch(draft.id, detail))
                }
            }
        }

        // V8 EmptyMatn (FR-030)
        if (draft.verses.isEmpty()) {
            blocking.add(ContentIntegrityError.EmptyMatn(draft.id))
        }

        // V9 DocumentTooLarge (research D2 guard)
        val size = draft.declaredSizeBytes
        if (size > DOCUMENT_SIZE_LIMIT_BYTES) {
            blocking.add(ContentIntegrityError.DocumentTooLarge(draft.id, size, DOCUMENT_SIZE_LIMIT_BYTES))
        }

        return ValidationReport(blocking = blocking, deferred = deferred)
    }
}
