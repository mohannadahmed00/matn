package com.giraffe.matn.data.remote.postgrest

import com.giraffe.matn.domain.catalog.AudioCompleteness
import com.giraffe.matn.domain.catalog.CatalogEntry
import com.giraffe.matn.domain.catalog.DraftAudio
import com.giraffe.matn.domain.catalog.DraftChapter
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.model.StructureKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The `public.matns` row (`contracts/postgres-schema.md` §2). Columns are snake_case, the
 * PostgREST convention; the two `jsonb` columns keep the **camelCase** field names of the domain
 * objects they hold, because they are opaque documents to SQL and mirroring
 * [DraftChapter]/[DraftVerse] one-for-one is what keeps the mapping obvious.
 *
 * Every field has a default so the overview projection — which selects a subset of columns
 * (FR-012, FR-035) — decodes into the same type as a full read.
 */
@Serializable
data class MatnRow(
    val id: String,
    val title: String = "",
    val author: String = "",
    val description: String = "",
    @SerialName("cover_image_ref") val coverImageRef: String? = null,
    @SerialName("structure_kind") val structureKind: String = StructureKind.SIMPLE.name,
    @SerialName("default_reciter_id") val defaultReciterId: String = "",
    val published: Boolean = false,
    @SerialName("audio_completeness") val audioCompleteness: String = AudioCompleteness.NONE.name,
    @SerialName("verse_count") val verseCount: Int = 0,
    @SerialName("declared_size_bytes") val declaredSizeBytes: Long = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val chapters: List<ChapterRow> = emptyList(),
    val verses: List<VerseRow> = emptyList(),
    val revision: Long? = null,
)

@Serializable
data class ChapterRow(
    val id: String,
    val title: String = "",
    val order: Int = 0,
    /** Absent on rows written before chapter starts existed, which decode to `null` — exactly the
     * "not said" the domain uses, so those matns keep whatever assignment they already had. */
    val startVerseNumber: Int? = null,
)

@Serializable
data class VerseRow(
    val id: String,
    val chapterId: String? = null,
    val displayNumber: Int = 0,
    val arabicText: String = "",
    val durationMs: Long = 0,
    val audio: AudioRow? = null,
)

@Serializable
data class AudioRow(
    val id: String,
    val fileRef: String,
    val durationMs: Long,
    val sizeBytes: Long = 0L,
    val sampleRate: Int = 0,
    val channels: Int = 0,
)

/** Lenient on unknown columns so adding one server-side does not break an older build. */
internal val matnRowJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** Column list for a full read. */
val MATN_FULL_COLUMNS: List<String> = listOf(
    "id", "title", "author", "description", "cover_image_ref", "structure_kind",
    "default_reciter_id", "published", "audio_completeness", "verse_count",
    "declared_size_bytes", "created_at", "updated_at", "chapters", "verses", "revision",
)

/** The overview projection (FR-012, FR-035): the `verses` and `chapters` jsonb never travel. */
val MATN_OVERVIEW_COLUMNS: List<String> = listOf(
    "id", "title", "author", "description", "cover_image_ref", "published",
    "audio_completeness", "verse_count", "declared_size_bytes", "updated_at",
)

fun JsonObject.toMatnRow(): MatnRow = matnRowJson.decodeFromJsonElement(MatnRow.serializer(), this)

/**
 * The write payload. `revision` is deliberately absent: it is server-owned, bumped by a trigger on
 * update and defaulted on insert, so sending it would let a client forge agreement with a stale
 * read. Nulls **are** written explicitly — omitting `cover_image_ref` from a `PATCH` would leave a
 * removed cover in place rather than clearing it.
 */
@OptIn(ExperimentalTime::class)
fun MatnDraft.toRowJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("title", title)
    put("author", author)
    put("description", description)
    put("cover_image_ref", coverImageRef?.let { JsonPrimitive(it) } ?: JsonNull)
    put("structure_kind", structureKind.name)
    put("default_reciter_id", defaultReciterId)
    put("published", publicationState == PublicationState.PUBLISHED)
    put("audio_completeness", audioCompleteness.name)
    put("verse_count", verseCount)
    put("declared_size_bytes", declaredSizeBytes)
    put("created_at", Instant.fromEpochMilliseconds(createdAt).toString())
    put("updated_at", Instant.fromEpochMilliseconds(updatedAt).toString())
    put(
        "chapters",
        buildJsonArray {
            chapters.forEach { chapter ->
                add(
                    buildJsonObject {
                        put("id", chapter.id)
                        put("title", chapter.title)
                        put("order", chapter.order)
                        put("startVerseNumber", chapter.startVerseNumber?.let { JsonPrimitive(it) } ?: JsonNull)
                    },
                )
            }
        },
    )
    put(
        "verses",
        buildJsonArray {
            verses.forEach { verse ->
                add(
                    buildJsonObject {
                        put("id", verse.id)
                        put("chapterId", verse.chapterId?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("displayNumber", verse.displayNumber)
                        put("arabicText", verse.arabicText)
                        put("durationMs", verse.durationMs)
                        put(
                            "audio",
                            verse.audio?.let { audio ->
                                buildJsonObject {
                                    put("id", audio.id)
                                    put("fileRef", audio.fileRef)
                                    put("durationMs", audio.durationMs)
                                    put("sizeBytes", audio.sizeBytes)
                                    put("sampleRate", audio.sampleRate)
                                    put("channels", audio.channels)
                                }
                            } ?: JsonNull,
                        )
                    },
                )
            }
        },
    )
}

@OptIn(ExperimentalTime::class)
fun MatnRow.toMatnDraft(): MatnDraft = MatnDraft(
    id = id,
    title = title,
    author = author,
    description = description,
    coverImageRef = coverImageRef,
    structureKind = StructureKind.fromStorageOrNull(structureKind) ?: StructureKind.SIMPLE,
    defaultReciterId = defaultReciterId,
    chapters = chapters.map {
        DraftChapter(id = it.id, title = it.title, order = it.order, startVerseNumber = it.startVerseNumber)
    },
    verses = verses.map {
        DraftVerse(
            id = it.id,
            chapterId = it.chapterId,
            displayNumber = it.displayNumber,
            arabicText = it.arabicText,
            audio = it.audio?.let { audio ->
                DraftAudio(
                    id = audio.id,
                    fileRef = audio.fileRef,
                    durationMs = audio.durationMs,
                    sizeBytes = audio.sizeBytes,
                    sampleRate = audio.sampleRate,
                    channels = audio.channels,
                )
            },
            durationMs = it.durationMs,
        )
    },
    publicationState = if (published) PublicationState.PUBLISHED else PublicationState.DRAFT,
    createdAt = createdAt.toEpochMillis(),
    updatedAt = updatedAt.toEpochMillis(),
    remoteRevision = revision?.toString(),
)

fun MatnRow.toCatalogEntry(): CatalogEntry = CatalogEntry(
    id = id,
    title = title,
    author = author,
    description = description,
    coverImageRef = coverImageRef,
    verseCount = verseCount,
    declaredSizeBytes = declaredSizeBytes,
    publicationState = if (published) PublicationState.PUBLISHED else PublicationState.DRAFT,
    audioCompleteness = runCatching { AudioCompleteness.valueOf(audioCompleteness) }.getOrDefault(AudioCompleteness.NONE),
    updatedAt = updatedAt.toEpochMillis(),
)

/** Postgres renders `timestamptz` with a `+00:00` offset and microsecond precision; an
 * unparseable or absent value degrades to the epoch rather than failing the whole read. */
@OptIn(ExperimentalTime::class)
private fun String?.toEpochMillis(): Long =
    this?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: 0L
