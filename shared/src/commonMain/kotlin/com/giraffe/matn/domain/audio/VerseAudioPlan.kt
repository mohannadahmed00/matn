package com.giraffe.matn.domain.audio

import com.giraffe.matn.domain.catalog.DraftAudio
import com.giraffe.matn.domain.catalog.MatnDraft

/** One object to be written, with the [DraftAudio] the write will produce for its verse. */
data class PendingUpload(
    val verseId: String,
    val objectPath: String,
    val bytes: ByteArray,
    val audio: DraftAudio,
)

/**
 * The commit unit for both authoring paths (`data-model.md` §8, Principle III — one plan, one
 * commit routine). `deletes` = objects referenced by [before] but not by [after]; a [payloads] entry
 * is a skip when an object already sits at its expected path with the expected byte size (research D5).
 */
data class VerseAudioPlan(
    val uploads: List<PendingUpload>,
    val skips: List<String>,
    val deletes: List<String>,
)

fun buildPlan(
    before: MatnDraft,
    after: MatnDraft,
    payloads: List<PendingUpload>,
    existingObjects: Map<String, Long>,
): VerseAudioPlan {
    val uploads = mutableListOf<PendingUpload>()
    val skips = mutableListOf<String>()
    payloads.forEach { payload ->
        val existingSize = existingObjects[payload.objectPath]
        if (existingSize != null && existingSize == payload.bytes.size.toLong()) {
            skips.add(payload.objectPath)
        } else {
            uploads.add(payload)
        }
    }

    val beforeRefs = before.verses.mapNotNull { it.audio?.fileRef }.toSet()
    val afterRefs = after.verses.mapNotNull { it.audio?.fileRef }.toSet()
    val deletes = (beforeRefs - afterRefs).toList()

    return VerseAudioPlan(uploads = uploads, skips = skips, deletes = deletes)
}
