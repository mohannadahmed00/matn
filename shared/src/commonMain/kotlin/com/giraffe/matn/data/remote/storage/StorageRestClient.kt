package com.giraffe.matn.data.remote.storage

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.FirebaseConfig
import com.giraffe.matn.data.remote.RemoteErrorMapper
import com.giraffe.matn.data.remote.identity.TokenRefresher
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Cover uploads and storage-usage reads (`contracts/rest-contract.md` §5). */
class StorageRestClient(
    private val httpClient: HttpClient,
    private val config: FirebaseConfig,
    private val tokenRefresher: TokenRefresher,
) {
    /** [objectPath] is deterministic per matn (e.g. `matns/{matnId}/cover.png`) so a re-upload
     * overwrites rather than accumulating orphans. Returns the same [objectPath] on success. */
    suspend fun upload(objectPath: String, bytes: ByteArray, contentType: String): Resource<String> {
        val tokenResult = tokenRefresher.currentIdToken()
        if (tokenResult is Resource.Failure) return Resource.Failure(tokenResult.error)
        val token = (tokenResult as Resource.Success).data
        return try {
            val response = httpClient.post("${config.storageBaseUrl}/b/${config.storageBucket}/o") {
                headers { append(HttpHeaders.Authorization, "Bearer $token") }
                parameter("uploadType", "media")
                parameter("name", objectPath)
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

    /** Sums `size` across every object under [prefix], paginated (portal storage-usage row). */
    suspend fun totalUsageBytes(prefix: String): Resource<Long> {
        val tokenResult = tokenRefresher.currentIdToken()
        if (tokenResult is Resource.Failure) return Resource.Failure(tokenResult.error)
        val token = (tokenResult as Resource.Success).data
        return try {
            var total = 0L
            var pageToken: String? = null
            do {
                val response = httpClient.get("${config.storageBaseUrl}/b/${config.storageBucket}/o") {
                    headers { append(HttpHeaders.Authorization, "Bearer $token") }
                    parameter("prefix", prefix)
                    pageToken?.let { parameter("pageToken", it) }
                }
                if (!response.status.isSuccess()) {
                    return Resource.Failure(RemoteErrorMapper.mapHttpError(response.status.value, response.bodyAsText()))
                }
                val json = response.body<JsonObject>()
                val items = json["items"]?.jsonArray.orEmpty()
                total += items.sumOf { (it.jsonObject["size"]?.jsonPrimitive?.contentOrNull ?: "0").toLong() }
                pageToken = json["nextPageToken"]?.jsonPrimitive?.contentOrNull
            } while (pageToken != null)
            Resource.Success(total)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Resource.Failure(RemoteErrorMapper.mapThrowable(t))
        }
    }
}
