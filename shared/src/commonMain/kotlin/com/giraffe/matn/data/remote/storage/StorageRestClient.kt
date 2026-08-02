package com.giraffe.matn.data.remote.storage

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.RemoteErrorMapper
import com.giraffe.matn.data.remote.SupabaseConfig
import com.giraffe.matn.data.remote.auth.AccessTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Cover uploads and storage-usage reads, on Supabase Storage (`contracts/rest-contract.md` §5).
 * Storage was the first piece to move off Firebase (`design-notes.md`); it now shares the same
 * Supabase access token as the rest of the backend, and the third-party-auth bridge it used to need
 * is gone.
 */
class StorageRestClient(
    private val httpClient: HttpClient,
    private val config: SupabaseConfig,
    private val tokenProvider: AccessTokenProvider,
) {
    /** [objectPath] is deterministic per matn (e.g. `matns/{matnId}/cover.png`) so a re-upload
     * overwrites (`x-upsert: true`) rather than accumulating orphans. Returns the same
     * [objectPath] on success. */
    suspend fun upload(objectPath: String, bytes: ByteArray, contentType: String): Resource<String> {
        val tokenResult = tokenProvider.currentAccessToken()
        if (tokenResult is Resource.Failure) return Resource.Failure(tokenResult.error)
        val token = (tokenResult as Resource.Success).data
        return try {
            val response = httpClient.post("${config.storageBaseUrl}/object/${config.bucket}/$objectPath") {
                headers {
                    if (token != null) append(HttpHeaders.Authorization, "Bearer $token")
                    append("apikey", config.anonKey)
                    append("x-upsert", "true")
                }
                contentType(ContentType.parse(contentType))
                setBody(bytes)
            }
            if (response.status.isSuccess()) {
                Resource.Success(objectPath)
            } else {
                Resource.Failure(RemoteErrorMapper.mapHttpError(response.status.value, response.bodyAsText()))
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Resource.Failure(RemoteErrorMapper.mapThrowable(t))
        }
    }

    /** Sums each object's size under [prefix] (portal storage-usage row). */
    suspend fun totalUsageBytes(prefix: String): Resource<Long> =
        when (val result = listWithSizes(prefix)) {
            is Resource.Success -> Resource.Success(result.data.values.sum())
            is Resource.Failure -> result
        }

    /** object name → byte size under [prefix], paginated via offset — Supabase's list endpoint has
     * no page-token, just `limit`/`offset` (the resume predicate, `contracts/storage-contract.md` §1). */
    suspend fun listWithSizes(prefix: String): Resource<Map<String, Long>> {
        val tokenResult = tokenProvider.currentAccessToken()
        if (tokenResult is Resource.Failure) return Resource.Failure(tokenResult.error)
        val token = (tokenResult as Resource.Success).data
        return try {
            val sizes = mutableMapOf<String, Long>()
            var offset = 0
            while (true) {
                val response = httpClient.post("${config.storageBaseUrl}/object/list/${config.bucket}") {
                    headers {
                        if (token != null) append(HttpHeaders.Authorization, "Bearer $token")
                        append("apikey", config.anonKey)
                    }
                    contentType(ContentType.Application.Json)
                    setBody(
                        buildJsonObject {
                            put("prefix", prefix)
                            put("limit", PAGE_SIZE)
                            put("offset", offset)
                        },
                    )
                }
                if (!response.status.isSuccess()) {
                    return Resource.Failure(RemoteErrorMapper.mapHttpError(response.status.value, response.bodyAsText()))
                }
                val items = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                items.forEach { item ->
                    val obj = item.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val size = obj["metadata"]?.jsonObject?.get("size")?.jsonPrimitive?.longOrNull ?: 0L
                    sizes["$prefix$name"] = size
                }
                if (items.size < PAGE_SIZE) break
                offset += PAGE_SIZE
            }
            Resource.Success(sizes)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Resource.Failure(RemoteErrorMapper.mapThrowable(t))
        }
    }

    /** `GET /object/{bucket}/{path}` — used by the preview cache and split-source profile checks. */
    suspend fun download(objectPath: String): Resource<ByteArray> {
        val tokenResult = tokenProvider.currentAccessToken()
        if (tokenResult is Resource.Failure) return Resource.Failure(tokenResult.error)
        val token = (tokenResult as Resource.Success).data
        return try {
            val response = httpClient.get("${config.storageBaseUrl}/object/${config.bucket}/$objectPath") {
                headers {
                    if (token != null) append(HttpHeaders.Authorization, "Bearer $token")
                    append("apikey", config.anonKey)
                }
            }
            if (response.status.isSuccess()) {
                Resource.Success(response.bodyAsBytes())
            } else {
                Resource.Failure(RemoteErrorMapper.mapHttpError(response.status.value, response.bodyAsText()))
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Resource.Failure(RemoteErrorMapper.mapThrowable(t))
        }
    }

    /** `DELETE /object/{bucket}/{path}` — step 4 of the commit ordering
     * (`contracts/audio-artifact-contract.md` §4). Callers must treat a failure as a logged
     * non-event (FR-033b), never surface it as an operation failure. */
    suspend fun delete(objectPath: String): Resource<Unit> {
        val tokenResult = tokenProvider.currentAccessToken()
        if (tokenResult is Resource.Failure) return Resource.Failure(tokenResult.error)
        val token = (tokenResult as Resource.Success).data
        return try {
            val response = httpClient.delete("${config.storageBaseUrl}/object/${config.bucket}/$objectPath") {
                headers {
                    if (token != null) append(HttpHeaders.Authorization, "Bearer $token")
                    append("apikey", config.anonKey)
                }
            }
            if (response.status.isSuccess()) {
                Resource.Success(Unit)
            } else {
                Resource.Failure(RemoteErrorMapper.mapHttpError(response.status.value, response.bodyAsText()))
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Resource.Failure(RemoteErrorMapper.mapThrowable(t))
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}
