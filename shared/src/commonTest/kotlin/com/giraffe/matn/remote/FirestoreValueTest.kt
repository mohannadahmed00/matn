package com.giraffe.matn.remote

import com.giraffe.matn.data.remote.firestore.FirestoreValue
import com.giraffe.matn.data.remote.firestore.toJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class FirestoreValueTest {

    private fun roundTrip(value: FirestoreValue): FirestoreValue = FirestoreValue.fromJson(value.toJson())

    @Test
    fun `StringValue round-trips`() {
        val value = FirestoreValue.StringValue("hello")
        assertEquals(value, roundTrip(value))
    }

    @Test
    fun `IntegerValue round-trips`() {
        val value = FirestoreValue.IntegerValue(500L)
        assertEquals(value, roundTrip(value))
    }

    @Test
    fun `BooleanValue round-trips`() {
        val value = FirestoreValue.BooleanValue(true)
        assertEquals(value, roundTrip(value))
    }

    @Test
    fun `TimestampValue round-trips`() {
        val value = FirestoreValue.TimestampValue("2026-07-26T12:00:00Z")
        assertEquals(value, roundTrip(value))
    }

    @Test
    fun `NullValue round-trips`() {
        assertEquals(FirestoreValue.NullValue, roundTrip(FirestoreValue.NullValue))
    }

    @Test
    fun `ArrayValue round-trips`() {
        val value = FirestoreValue.ArrayValue(listOf(FirestoreValue.StringValue("a"), FirestoreValue.IntegerValue(1L)))
        assertEquals(value, roundTrip(value))
    }

    @Test
    fun `MapValue round-trips`() {
        val value = FirestoreValue.MapValue(
            mapOf("title" to FirestoreValue.StringValue("t"), "verseCount" to FirestoreValue.IntegerValue(3L)),
        )
        assertEquals(value, roundTrip(value))
    }

    @Test
    fun `integerValue is encoded as a JSON string, not a number`() {
        val json = FirestoreValue.IntegerValue(500L).toJson().jsonObject
        assertEquals("500", json.getValue("integerValue").jsonPrimitive.content)
    }

    @Test
    fun `timestampValue is encoded as an RFC 3339 string verbatim`() {
        val rfc3339 = "2026-07-26T09:30:00.123Z"
        val json = FirestoreValue.TimestampValue(rfc3339).toJson().jsonObject
        assertEquals(rfc3339, json.getValue("timestampValue").jsonPrimitive.content)
    }
}
