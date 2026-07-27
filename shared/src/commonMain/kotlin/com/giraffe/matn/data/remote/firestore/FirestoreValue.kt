package com.giraffe.matn.data.remote.firestore

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Firestore's REST document-field encoding (research D10): every field is a typed wrapper, not
 * plain JSON. Two wire quirks this codec handles: `integerValue` is a JSON **string**, and
 * `timestampValue` is RFC 3339.
 */
sealed interface FirestoreValue {
    data class StringValue(val value: String) : FirestoreValue
    data class IntegerValue(val value: Long) : FirestoreValue
    data class BooleanValue(val value: Boolean) : FirestoreValue
    data class TimestampValue(val value: String) : FirestoreValue
    data object NullValue : FirestoreValue
    data class ArrayValue(val values: List<FirestoreValue>) : FirestoreValue
    data class MapValue(val fields: Map<String, FirestoreValue>) : FirestoreValue

    companion object {
        fun fromJson(element: JsonElement): FirestoreValue {
            val obj = element.jsonObject
            return when {
                "stringValue" in obj -> StringValue(obj.getValue("stringValue").jsonPrimitive.content)
                "integerValue" in obj -> IntegerValue(obj.getValue("integerValue").jsonPrimitive.content.toLong())
                "booleanValue" in obj -> BooleanValue(obj.getValue("booleanValue").jsonPrimitive.boolean)
                "timestampValue" in obj -> TimestampValue(obj.getValue("timestampValue").jsonPrimitive.content)
                "nullValue" in obj -> NullValue
                "arrayValue" in obj -> {
                    val values = obj.getValue("arrayValue").jsonObject["values"]?.jsonArray ?: buildJsonArray {}
                    ArrayValue(values.map { fromJson(it) })
                }
                "mapValue" in obj -> {
                    val fields = obj.getValue("mapValue").jsonObject["fields"]?.jsonObject ?: JsonObject(emptyMap())
                    MapValue(fields.mapValues { (_, v) -> fromJson(v) })
                }
                else -> error("Unrecognized Firestore value: $obj")
            }
        }
    }
}

fun FirestoreValue.toJson(): JsonElement = when (this) {
    is FirestoreValue.StringValue -> buildJsonObject { put("stringValue", value) }
    is FirestoreValue.IntegerValue -> buildJsonObject { put("integerValue", value.toString()) }
    is FirestoreValue.BooleanValue -> buildJsonObject { put("booleanValue", value) }
    is FirestoreValue.TimestampValue -> buildJsonObject { put("timestampValue", value) }
    is FirestoreValue.NullValue -> buildJsonObject { put("nullValue", JsonNull) }
    is FirestoreValue.ArrayValue -> buildJsonObject {
        put("arrayValue", buildJsonObject { put("values", buildJsonArray { values.forEach { add(it.toJson()) } }) })
    }
    is FirestoreValue.MapValue -> buildJsonObject {
        put("mapValue", buildJsonObject { put("fields", buildJsonObject { fields.forEach { (k, v) -> put(k, v.toJson()) } }) })
    }
}
