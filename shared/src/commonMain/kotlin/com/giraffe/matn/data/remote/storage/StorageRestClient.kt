package com.giraffe.matn.data.remote.storage

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.RemoteErrorMapper
import com.giraffe.matn.data.remote.SupabaseConfig
import com.giraffe.matn.data.remote.auth.TokenRefresher
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
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
    private val tokenRefresher: TokenRefresher,
) {
    /** [objectPath] is deterministic per matn (e.g. `matns/{matnId}/cover.png`) so a re-upload
     * overwrites (`x-upsert: true`) rather than accumulating orphans. Returns the same
     * [objectPath] on success. */
    suspend fun upload(objectPath: String, bytes: ByteArray, contentType: String): Resource<String> {
        val tokenResult = tokenRefresher.currentAccessToken()
        if (tokenResult is Resource.Failure) return Resource.Failure(tokenResult.error)
        val token = (tokenResult as Resource.Success).data
        return try {
            val response = httpClient.post("${config.storageBaseUrl}/object/${config.bucket}/$objectPath") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
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

    /** Sums each object's size under [prefix], paginated via offset — Supabase's list endpoint has
     * no page-token, just `limit`/`offset` (portal storage-usage row). */
    suspend fun totalUsageBytes(prefix: String): Resource<Long> {
        val tokenResult = tokenRefresher.currentAccessToken()
        if (tokenResult is Resource.Failure) return Resource.Failure(tokenResult.error)
        val token = (tokenResult as Resource.Success).data
        return try {
            var total = 0L
            var offset = 0
            while (true) {
                val response = httpClient.post("${config.storageBaseUrl}/object/list/${config.bucket}") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $token")
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
                total += items.sumOf { item ->
                    item.jsonObject["metadata"]?.jsonObject?.get("size")?.jsonPrimitive?.longOrNull ?: 0L
                }
                if (items.size < PAGE_SIZE) break
                offset += PAGE_SIZE
            }
            Resource.Success(total)
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
