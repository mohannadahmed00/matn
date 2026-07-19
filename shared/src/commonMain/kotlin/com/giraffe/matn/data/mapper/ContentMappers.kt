package com.giraffe.matn.data.mapper

import com.giraffe.matn.db.Audio_asset as AudioAssetRow
import com.giraffe.matn.db.Chapter as ChapterRow
import com.giraffe.matn.db.Matn as MatnRow
import com.giraffe.matn.db.SelectLibrarySummaries as LibrarySummaryRow
import com.giraffe.matn.db.Verse as VerseRow
import com.giraffe.matn.domain.model.AudioAsset
import com.giraffe.matn.domain.model.Chapter
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.MatnSummary
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.domain.model.Verse

internal fun MatnRow.toDomain(): Matn =
    Matn(
        id = id,
        title = title,
        author = author,
        description = description,
        coverImageRef = cover_image_ref,
        structureKind = StructureKind.valueOf(structure_kind),
    )

/** Non-throwing variant for stream paths: returns `null` on an unrecognized `structure_kind`. */
internal fun MatnRow.toDomainOrNull(): Matn? {
    val kind = StructureKind.fromStorageOrNull(structure_kind) ?: return null
    return Matn(
        id = id,
        title = title,
        author = author,
        description = description,
        coverImageRef = cover_image_ref,
        structureKind = kind,
    )
}

internal fun ChapterRow.toDomain(): Chapter =
    Chapter(
        id = id,
        matnId = matn_id,
        title = title,
        order = display_order.toInt(),
    )

internal fun VerseRow.toDomain(): Verse =
    Verse(
        id = id,
        matnId = matn_id,
        chapterId = chapter_id,
        displayNumber = display_number.toInt(),
        arabicText = arabic_text,
        durationMs = duration_ms,
    )

internal fun AudioAssetRow.toDomain(): AudioAsset =
    AudioAsset(
        id = id,
        verseId = verse_id,
        reciterId = reciter_id,
        fileRef = file_ref,
        durationMs = duration_ms,
    )

/**
 * Maps a Phase 1 `selectLibrarySummaries` row → [MatnSummary]. A null/blank `structure_kind`
 * is dropped at the stream level (consistent with [toDomainOrNull]); this mapper assumes a
 * validated row. `verse_count` is `Long` from SQLDelight's COUNT; coerced to [Int] (always
 * small in Phase 1). `total_duration_ms` is already `Long`.
 */
internal fun LibrarySummaryRow.toMatnSummary(): MatnSummary? {
    val kind = StructureKind.fromStorageOrNull(structure_kind) ?: return null
    return MatnSummary(
        matn = Matn(
            id = id,
            title = title,
            author = author,
            description = description,
            coverImageRef = cover_image_ref,
            structureKind = kind,
        ),
        verseCount = verse_count.toInt(),
        // SQLDelight types COALESCE(SUM(duration_ms), 0) as Double, so convert back to the
        // millisecond Long the domain uses.
        totalDurationMs = total_duration_ms.toLong(),
    )
}