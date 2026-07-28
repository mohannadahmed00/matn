package com.giraffe.matn.data.remote.firestore

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.FirebaseConfig
import com.giraffe.matn.data.remote.RemoteErrorMapper
import com.giraffe.matn.data.remote.identity.TokenRefresher
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class FirestoreDocument(
    val name: String,
    val fields: Map<String, FirestoreValue>,
    val updateTime: String?,
)

data class FirestoreDocumentList(
    val documents: List<FirestoreDocument>,
    val nextPageToken: String?,
)

/**
 * Firestore document reads/writes (`contracts/rest-contract.md` §4). **Full-body writes only** —
 * never a partial `updateMask` — so a document is never a mixture of two edits (FR-033).
 */
class FirestoreRestClient(
    private val httpClient: HttpClient,
    private val config: FirebaseConfig,
    private val tokenRefresher: TokenRefresher,
) {
    private val documentsBaseUrl: String
        get() = "${config.firestoreBaseUrl}/projects/${config.projectId}/databases/(default)/documents"

    suspend fun getDocument(path: String, mask: List<String>? = null): Resource<FirestoreDocument> {
        val tokenResult = tokenRefresher.currentIdToken()
        if (tokenResult is Resource.Failure) return Resource.Failure(tokenResult.error)
        val token = (tokenResult as Resource.Success).data
        return runRequest {
            httpClient.get("$documentsBaseUrl/$path") {
                headers { append(HttpHeaders.Authorization, "Bearer $token") }
                mask?.forEach { field -> parameter("mask.fieldPaths", field) }
            }
        }
    }

    suspend fun patchDocument(
        path: String,
        fields: Map<String, FirestoreValue>,
        updateTimePrecondition: String?,
        requireNotExists: Boolean = false,
    ): Resource<FirestoreDocument> {
        val tokenResult = tokenRefresher.currentIdToken()
        if (tokenResult is Resource.Failure) return Resource.Failure(tokenResult.error)
        val token = (tokenResult as Resource.Success).data
        val body = buildJsonObject {
            put("fields", buildJsonObject { fields.forEach { (k, v) -> put(k, v.toJson()) } })
        }
        return runRequest {
            httpClient.patch("$documentsBaseUrl/$path") {
                headers { append(HttpHeaders.Authorization, "Bearer $token") }
                contentType(ContentType.Application.Json)
                setBody(body)
                if (updateTimePrecondition != null) parameter("currentDocument.updateTime", updateTimePrecondition)
                if (requireNotExists) parameter("currentDocument.exists", "false")
            }
        }
    }

    /** Overview reads (FR-012, FR-035): [mask] keeps verse arrays off the wire entirely — see
     * `contracts/firestore-schema.md` §4.1. */
    suspend fun listDocuments(collection: String, mask: List<String>, pageToken: String? = null): Resource<FirestoreDocumentList> {
        val tokenResult = tokenRefresher.currentIdToken()
        if (tokenResult is Resource.Failure) return Resource.Failure(tokenResult.error)
        val token = (tokenResult as Resource.Success).data
        return try {
            val response = httpClient.get("$documentsBaseUrl/$collection") {
                headers { append(HttpHeaders.Authorization, "Bearer $token") }
                mask.forEach { field -> parameter("mask.fieldPaths", field) }
                parameter("pageSize", "100")
                pageToken?.let { parameter("pageToken", it) }
            }
            if (response.status.isSuccess()) {
                val json = response.body<JsonObject>()
                val documents = (json["documents"]?.jsonArray ?: JsonArray(emptyList())).map { it.jsonObject.toFirestoreDocument() }
                Resource.Success(FirestoreDocumentList(documents, json["nextPageToken"]?.jsonPrimitive?.contentOrNull))
            } else {
                Resource.Failure(RemoteErrorMapper.mapHttpError(response.status.value, response.bodyAsText()))
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Resource.Failure(RemoteErrorMapper.mapThrowable(t))
        }
    }

    private suspend fun runRequest(call: suspend () -> io.ktor.client.statement.HttpResponse): Resource<FirestoreDocument> =
        try {
            val response = call()
            if (response.status.isSuccess()) {
                Resource.Success(response.body<JsonObject>().toFirestoreDocument())
            } else {
                val body = response.bodyAsText()
                println("DIAG firestore request failed: status=${response.status.value} body=$body")
                Resource.Failure(RemoteErrorMapper.mapHttpError(response.status.value, body))
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Resource.Failure(RemoteErrorMapper.mapThrowable(t))
        }
}

private fun JsonObject.toFirestoreDocument(): FirestoreDocument {
    val name = this["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val updateTime = this["updateTime"]?.jsonPrimitive?.contentOrNull
    val fieldsObject = this["fields"]?.jsonObject ?: JsonObject(emptyMap())
    val fields = fieldsObject.mapValues { (_, v) -> FirestoreValue.fromJson(v) }
    return FirestoreDocument(name = name, fields = fields, updateTime = updateTime)
}
