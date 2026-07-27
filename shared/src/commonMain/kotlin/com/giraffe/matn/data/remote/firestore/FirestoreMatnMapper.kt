package com.giraffe.matn.data.remote.firestore

import com.giraffe.matn.domain.catalog.AudioCompleteness
import com.giraffe.matn.domain.catalog.CatalogEntry
import com.giraffe.matn.domain.catalog.DraftChapter
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.model.StructureKind
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Maps [MatnDraft] to/from the `matns/{matnId}` document fields (`contracts/firestore-schema.md` §2). */
@OptIn(ExperimentalTime::class)
fun MatnDraft.toFirestoreFields(): Map<String, FirestoreValue> = mapOf(
    "id" to FirestoreValue.StringValue(id),
    "title" to FirestoreValue.StringValue(title),
    "author" to FirestoreValue.StringValue(author),
    "description" to FirestoreValue.StringValue(description),
    "coverImageRef" to (coverImageRef?.let { FirestoreValue.StringValue(it) } ?: FirestoreValue.NullValue),
    "structureKind" to FirestoreValue.StringValue(structureKind.name),
    "defaultReciterId" to FirestoreValue.StringValue(defaultReciterId),
    "published" to FirestoreValue.BooleanValue(publicationState == PublicationState.PUBLISHED),
    "audioCompleteness" to FirestoreValue.StringValue(audioCompleteness.name),
    "verseCount" to FirestoreValue.IntegerValue(verseCount.toLong()),
    "declaredSizeBytes" to FirestoreValue.IntegerValue(declaredSizeBytes),
    "createdAt" to FirestoreValue.TimestampValue(Instant.fromEpochMilliseconds(createdAt).toString()),
    "updatedAt" to FirestoreValue.TimestampValue(Instant.fromEpochMilliseconds(updatedAt).toString()),
    "chapters" to FirestoreValue.ArrayValue(chapters.map { it.toFirestoreValue() }),
    "verses" to FirestoreValue.ArrayValue(verses.map { it.toFirestoreValue() }),
)

private fun DraftChapter.toFirestoreValue(): FirestoreValue = FirestoreValue.MapValue(
    mapOf(
        "id" to FirestoreValue.StringValue(id),
        "title" to FirestoreValue.StringValue(title),
        "order" to FirestoreValue.IntegerValue(order.toLong()),
    ),
)

private fun DraftVerse.toFirestoreValue(): FirestoreValue = FirestoreValue.MapValue(
    mapOf(
        "id" to FirestoreValue.StringValue(id),
        "chapterId" to (chapterId?.let { FirestoreValue.StringValue(it) } ?: FirestoreValue.NullValue),
        "displayNumber" to FirestoreValue.IntegerValue(displayNumber.toLong()),
        "arabicText" to FirestoreValue.StringValue(arabicText),
        "durationMs" to FirestoreValue.IntegerValue(durationMs),
        // Always nullValue in Phase 11 (FR-045) — the shape is reserved for Phase 12.
        "audio" to FirestoreValue.NullValue,
    ),
)

@OptIn(ExperimentalTime::class)
fun matnDraftFromFields(id: String, fields: Map<String, FirestoreValue>, updateTime: String?): MatnDraft {
    fun string(key: String): String = (fields[key] as? FirestoreValue.StringValue)?.value.orEmpty()
    fun nullableString(key: String): String? = (fields[key] as? FirestoreValue.StringValue)?.value
    fun timestamp(key: String): Long =
        (fields[key] as? FirestoreValue.TimestampValue)?.value?.let { Instant.parse(it).toEpochMilliseconds() } ?: 0L
    fun bool(key: String): Boolean = (fields[key] as? FirestoreValue.BooleanValue)?.value ?: false

    val chapters = ((fields["chapters"] as? FirestoreValue.ArrayValue)?.values.orEmpty()).map { it.toDraftChapter() }
    val verses = ((fields["verses"] as? FirestoreValue.ArrayValue)?.values.orEmpty()).map { it.toDraftVerse() }

    return MatnDraft(
        id = nullableString("id") ?: id,
        title = string("title"),
        author = string("author"),
        description = string("description"),
        coverImageRef = nullableString("coverImageRef"),
        structureKind = StructureKind.valueOf(string("structureKind")),
        defaultReciterId = string("defaultReciterId"),
        chapters = chapters,
        verses = verses,
        publicationState = if (bool("published")) PublicationState.PUBLISHED else PublicationState.DRAFT,
        createdAt = timestamp("createdAt"),
        updatedAt = timestamp("updatedAt"),
        remoteUpdateTime = updateTime,
    )
}

/** The overview projection (FR-012, FR-035) read via a field mask — verse arrays never travel. */
@OptIn(ExperimentalTime::class)
fun catalogEntryFromFields(id: String, fields: Map<String, FirestoreValue>): CatalogEntry {
    fun string(key: String): String = (fields[key] as? FirestoreValue.StringValue)?.value.orEmpty()
    fun nullableString(key: String): String? = (fields[key] as? FirestoreValue.StringValue)?.value
    fun long(key: String): Long = (fields[key] as? FirestoreValue.IntegerValue)?.value ?: 0L
    fun timestamp(key: String): Long =
        (fields[key] as? FirestoreValue.TimestampValue)?.value?.let { Instant.parse(it).toEpochMilliseconds() } ?: 0L
    fun bool(key: String): Boolean = (fields[key] as? FirestoreValue.BooleanValue)?.value ?: false

    return CatalogEntry(
        id = nullableString("id") ?: id,
        title = string("title"),
        author = string("author"),
        description = string("description"),
        coverImageRef = nullableString("coverImageRef"),
        verseCount = long("verseCount").toInt(),
        declaredSizeBytes = long("declaredSizeBytes"),
        publicationState = if (bool("published")) PublicationState.PUBLISHED else PublicationState.DRAFT,
        audioCompleteness = runCatching { AudioCompleteness.valueOf(string("audioCompleteness")) }.getOrDefault(AudioCompleteness.NONE),
        updatedAt = timestamp("updatedAt"),
    )
}

private fun FirestoreValue.toDraftChapter(): DraftChapter {
    val map = (this as FirestoreValue.MapValue).fields
    return DraftChapter(
        id = (map.getValue("id") as FirestoreValue.StringValue).value,
        title = (map.getValue("title") as FirestoreValue.StringValue).value,
        order = (map.getValue("order") as FirestoreValue.IntegerValue).value.toInt(),
    )
}

private fun FirestoreValue.toDraftVerse(): DraftVerse {
    val map = (this as FirestoreValue.MapValue).fields
    return DraftVerse(
        id = (map.getValue("id") as FirestoreValue.StringValue).value,
        chapterId = (map["chapterId"] as? FirestoreValue.StringValue)?.value,
        displayNumber = (map.getValue("displayNumber") as FirestoreValue.IntegerValue).value.toInt(),
        arabicText = (map.getValue("arabicText") as FirestoreValue.StringValue).value,
        audio = null,
        durationMs = (map.getValue("durationMs") as FirestoreValue.IntegerValue).value,
    )
}
