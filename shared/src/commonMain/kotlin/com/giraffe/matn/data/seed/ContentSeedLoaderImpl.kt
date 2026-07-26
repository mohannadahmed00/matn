package com.giraffe.matn.data.seed

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.error.ContentIntegrityError
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.StructureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@org.koin.core.annotation.Single(binds = [ContentSeedLoader::class])
class ContentSeedLoaderImpl(private val db: ContentDatabase) : ContentSeedLoader {

    override suspend fun load(payload: SeedMatn): Resource<Matn> {
        val problems = validate(payload)
        if (problems.isNotEmpty()) {
            return Resource.Failure(ContentIntegrityError.Aggregate(problems))
        }
        return withContext(Dispatchers.Default) {
            try {
                db.transaction {
                    val q = db.contentQueries
                    q.deleteAudioByMatn(payload.id)
                    q.deleteVersesByMatn(payload.id)
                    q.deleteChaptersByMatn(payload.id)

                    q.upsertMatn(
                        id = payload.id,
                        title = payload.title,
                        author = payload.author,
                        description = payload.description,
                        cover_image_ref = payload.coverImageRef,
                        structure_kind = payload.structureKind,
                    )

                    // Phase 8 (FR-001): the matn's content_pack row is written in the same
                    // transaction so the catalog is atomically consistent with the matn itself.
                    // is_starter is stored as 1L/0L per the SQLDelight INTEGER column contract.
                    q.insertContentPack(
                        matn_id = payload.id,
                        pack_id = payload.packId,
                        declared_size_bytes = payload.declaredSizeBytes,
                        is_starter = if (payload.isStarter) 1L else 0L,
                    )

                    payload.chapters.forEach { chapter ->
                        q.upsertChapter(
                            id = chapter.id,
                            matn_id = payload.id,
                            title = chapter.title,
                            display_order = chapter.order.toLong(),
                        )
                    }

                    payload.verses.forEach { verse ->
                        q.upsertVerse(
                            id = verse.id,
                            matn_id = payload.id,
                            chapter_id = verse.chapterId,
                            display_number = verse.displayNumber.toLong(),
                            arabic_text = verse.arabicText,
                            duration_ms = verse.durationMs,
                        )
                        val audio = verse.audio
                        if (audio != null) {
                            q.upsertAudioAsset(
                                id = audio.id,
                                verse_id = verse.id,
                                reciter_id = payload.defaultReciterId,
                                file_ref = audio.fileRef,
                                duration_ms = audio.durationMs,
                            )
                        }
                    }
                }
                Resource.Success(payload.toMatnDomain())
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Resource.Failure(AppError.Storage(t.message ?: "Failed to load matn ${payload.id}"))
            }
        }
    }

    private fun validate(payload: SeedMatn): List<ContentIntegrityError> {
        val problems = mutableListOf<ContentIntegrityError>()

        // V1 InvalidId: blank ids anywhere
        if (payload.id.isBlank()) {
            problems.add(ContentIntegrityError.InvalidId("matn id is blank"))
        }
        payload.chapters.forEach { chapter ->
            if (chapter.id.isBlank()) {
                problems.add(ContentIntegrityError.InvalidId("chapter id is blank"))
            }
        }
        payload.verses.forEach { verse ->
            if (verse.id.isBlank()) {
                problems.add(ContentIntegrityError.InvalidId("verse id is blank"))
            }
            val audio = verse.audio
            if (audio != null && audio.id.isBlank()) {
                problems.add(ContentIntegrityError.InvalidId("audio id is blank for verse ${verse.id}"))
            }
        }

        // V1b DuplicateId: authored UUIDs are globally unique across all entities. A collision
        // would make the `ON CONFLICT(id) DO UPDATE` upserts silently overwrite a sibling row,
        // dropping content with no error. Blank ids are already reported above; skip them here.
        val allIds = buildList {
            add(payload.id)
            payload.chapters.forEach { add(it.id) }
            payload.verses.forEach { verse ->
                add(verse.id)
                verse.audio?.let { add(it.id) }
            }
        }
        allIds.groupingBy { it }.eachCount()
            .filter { it.value > 1 && it.key.isNotBlank() }
            .forEach { (dupId, _) ->
                problems.add(ContentIntegrityError.DuplicateId(dupId))
            }

        // V2 DuplicateDisplayNumber
        val numberCounts = mutableMapOf<Int, Int>()
        payload.verses.forEach { verse ->
            numberCounts[verse.displayNumber] = (numberCounts[verse.displayNumber] ?: 0) + 1
        }
        numberCounts.entries
            .filter { it.value > 1 }
            .forEach { (number, _) ->
                problems.add(ContentIntegrityError.DuplicateDisplayNumber(payload.id, number))
            }

        // V2b DuplicateChapterOrder: `chapter.order` is unique within a matn (DB UNIQUE backstop).
        // Validate here so a collision surfaces as a typed integrity error in the atomic-reject
        // aggregate, not as an opaque Storage failure when the transaction hits the constraint.
        val orderCounts = mutableMapOf<Int, Int>()
        payload.chapters.forEach { chapter ->
            orderCounts[chapter.order] = (orderCounts[chapter.order] ?: 0) + 1
        }
        orderCounts.entries
            .filter { it.value > 1 }
            .forEach { (order, _) ->
                problems.add(ContentIntegrityError.DuplicateChapterOrder(payload.id, order))
            }

        // V4 MissingAudio
        payload.verses.forEach { verse ->
            if (verse.audio == null) {
                problems.add(ContentIntegrityError.MissingAudio(verse.id))
            }
        }

        // V5 DuplicateAudioRef
        val refCounts = mutableMapOf<String, Int>()
        payload.verses.forEach { verse ->
            val ref = verse.audio?.fileRef ?: return@forEach
            refCounts[ref] = (refCounts[ref] ?: 0) + 1
        }
        refCounts.entries
            .filter { it.value > 1 }
            .forEach { (fileRef, _) ->
                problems.add(ContentIntegrityError.DuplicateAudioRef(fileRef))
            }

        // V6 OrphanChapterRef
        val chapterIds = payload.chapters.map { it.id }.toSet()
        payload.verses.forEach { verse ->
            val chapterId = verse.chapterId ?: return@forEach
            if (chapterId !in chapterIds) {
                problems.add(ContentIntegrityError.OrphanChapterRef(verse.id))
            }
        }

        // V7 StructureMismatch
        when (payload.structureKind) {
            "STRUCTURED" -> {
                val hasNoChapters = payload.chapters.isEmpty()
                val anyVerseLacksChapter = payload.verses.any { it.chapterId == null }
                if (hasNoChapters || anyVerseLacksChapter) {
                    val detail = buildString {
                        if (hasNoChapters) append("STRUCTURED matn has no chapters. ")
                        if (anyVerseLacksChapter) append("At least one verse has null chapterId. ")
                    }.trim()
                    problems.add(ContentIntegrityError.StructureMismatch(payload.id, detail))
                }
            }
            "SIMPLE" -> {
                val hasChapters = payload.chapters.isNotEmpty()
                val anyVerseHasChapter = payload.verses.any { it.chapterId != null }
                if (hasChapters || anyVerseHasChapter) {
                    val detail = buildString {
                        if (hasChapters) append("SIMPLE matn declares chapters. ")
                        if (anyVerseHasChapter) append("At least one verse has a chapterId. ")
                    }.trim()
                    problems.add(ContentIntegrityError.StructureMismatch(payload.id, detail))
                }
            }
            else -> {
                problems.add(
                    ContentIntegrityError.StructureMismatch(
                        payload.id,
                        "unknown structureKind: ${payload.structureKind}",
                    ),
                )
            }
        }

        return problems
    }

    private fun SeedMatn.toMatnDomain(): Matn = Matn(
        id = id,
        title = title,
        author = author,
        description = description,
        coverImageRef = coverImageRef,
        structureKind = StructureKind.valueOf(structureKind),
    )
}