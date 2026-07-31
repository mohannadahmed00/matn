package com.giraffe.matn.remote

import com.giraffe.matn.data.remote.postgrest.MATN_OVERVIEW_COLUMNS
import com.giraffe.matn.data.remote.postgrest.toCatalogEntry
import com.giraffe.matn.data.remote.postgrest.toMatnDraft
import com.giraffe.matn.data.remote.postgrest.toMatnRow
import com.giraffe.matn.data.remote.postgrest.toRowJson
import com.giraffe.matn.domain.catalog.AudioCompleteness
import com.giraffe.matn.domain.catalog.DraftAudio
import com.giraffe.matn.domain.catalog.DraftChapter
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.model.StructureKind
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun sampleDraft(
    coverImageRef: String? = "matns/m1/cover.png",
    remoteRevision: String? = "4",
) = MatnDraft(
    id = "m1",
    title = "الآجرومية",
    author = "ابن آجروم",
    description = "متن في النحو",
    coverImageRef = coverImageRef,
    structureKind = StructureKind.STRUCTURED,
    defaultReciterId = "reciter-1",
    chapters = listOf(DraftChapter(id = "c1", title = "باب الكلام", order = 0)),
    verses = listOf(
        DraftVerse(id = "v1", chapterId = "c1", displayNumber = 1, arabicText = "الكلام هو اللفظ", audio = null, durationMs = 4200),
        DraftVerse(id = "v2", chapterId = null, displayNumber = 2, arabicText = "المركب المفيد", audio = null, durationMs = 0),
    ),
    publicationState = PublicationState.PUBLISHED,
    createdAt = 1_700_000_000_000L,
    updatedAt = 1_700_000_500_000L,
    remoteRevision = remoteRevision,
)

/** The server echoes the row back with `Prefer: return=representation`, revision included. */
private fun JsonObject.withRevision(revision: Long): JsonObject =
    JsonObject(toMap() + ("revision" to JsonPrimitive(revision)))

class MatnRowTest {

    @Test
    fun `a draft survives a round trip through the row encoding`() {
        val draft = sampleDraft()

        val roundTripped = draft.toRowJson().withRevision(4).toMatnRow().toMatnDraft()

        assertEquals(draft, roundTripped)
    }

    @Test
    fun `a null cover is written explicitly so a PATCH clears the column`() {
        val row = sampleDraft(coverImageRef = null).toRowJson()

        // Omitting the key would leave the previous cover in place — a removed cover would come back.
        assertTrue("cover_image_ref" in row)
        assertEquals(JsonNull, row["cover_image_ref"])
    }

    @Test
    fun `revision is never part of the write payload`() {
        // It is server-owned: defaulted on insert, bumped by a trigger on update. Sending it would
        // let a client forge agreement with a stale read.
        assertFalse("revision" in sampleDraft().toRowJson())
    }

    @Test
    fun `a never-saved draft has no revision after a round trip through an insert response`() {
        val draft = sampleDraft(remoteRevision = null)

        val saved = draft.toRowJson().withRevision(1).toMatnRow().toMatnDraft()

        assertEquals("1", saved.remoteRevision)
    }

    @Test
    fun `a verse with no audio serializes it as null`() {
        val verses = sampleDraft().toRowJson()["verses"]!!.jsonArray

        assertTrue(verses.all { it.jsonObject["audio"] == JsonNull })
    }

    @Test
    fun `a verse with audio survives a round trip through the row encoding`() {
        val audio = DraftAudio(
            id = "a1",
            fileRef = "matns/m1/verses/v1-abc123.mp3",
            durationMs = 4200,
            sizeBytes = 67_000,
            sampleRate = 44100,
            channels = 1,
        )
        val draft = sampleDraft().let { d ->
            d.copy(verses = d.verses.map { if (it.id == "v1") it.copy(audio = audio) else it })
        }

        val roundTripped = draft.toRowJson().withRevision(4).toMatnRow().toMatnDraft()

        assertEquals(draft, roundTripped)
        assertEquals(audio, roundTripped.verses.first { it.id == "v1" }.audio)
    }

    @Test
    fun `the overview projection decodes from its column subset alone`() {
        val overview = buildJsonObject {
            sampleDraft().toRowJson().forEach { (key, value) ->
                if (key in MATN_OVERVIEW_COLUMNS) put(key, value)
            }
        }

        val entry = overview.toMatnRow().toCatalogEntry()

        assertEquals("m1", entry.id)
        assertEquals("الآجرومية", entry.title)
        assertEquals(PublicationState.PUBLISHED, entry.publicationState)
        assertEquals(2, entry.verseCount)
        assertEquals(1_700_000_500_000L, entry.updatedAt)
        // The verse and chapter arrays never travelled, and nothing needed them.
        assertEquals(AudioCompleteness.NONE, entry.audioCompleteness)
    }

    @Test
    fun `a Postgres timestamptz with an offset and microseconds parses`() {
        val row = buildJsonObject {
            put("id", JsonPrimitive("m1"))
            put("updated_at", JsonPrimitive("2023-11-14T22:13:20.123456+00:00"))
        }

        assertEquals(1_700_000_000_123L, row.toMatnRow().toCatalogEntry().updatedAt)
    }

    @Test
    fun `an unrecognised structure kind degrades instead of throwing`() {
        val row = buildJsonObject {
            put("id", JsonPrimitive("m1"))
            put("structure_kind", JsonPrimitive("HIEROGLYPHIC"))
        }

        assertEquals(StructureKind.SIMPLE, row.toMatnRow().toMatnDraft().structureKind)
    }

    @Test
    fun `an absent timestamp degrades to the epoch rather than failing the read`() {
        val row = buildJsonObject { put("id", JsonPrimitive("m1")) }

        val draft = row.toMatnRow().toMatnDraft()

        assertEquals(0L, draft.createdAt)
        assertNull(draft.remoteRevision)
    }
}
