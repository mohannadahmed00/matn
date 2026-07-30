package com.giraffe.matn.data.remote.postgrest

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.RemoteErrorMapper
import com.giraffe.matn.data.remote.SupabaseConfig
import com.giraffe.matn.data.remote.auth.TokenRefresher
import com.giraffe.matn.domain.error.RemoteError
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** A `column=eq.value`-style PostgREST filter. [value] carries the operator, e.g. `eq.42`. */
data class PostgrestFilter(val column: String, val value: String)

/**
 * Table reads and writes over PostgREST (`contracts/rest-contract.md` §4), replacing the Firestore
 * REST client. **Full-row writes only** — every write sends the complete row — so a matn is never a
 * mixture of two edits (FR-033).
 *
 * Every call sends both the project `apikey` and the teacher's bearer token; row-level security is
 * what authorises, and it needs the bearer token to see a `sub` claim at all.
 *
 * Writes ask for `Prefer: return=representation` so the server-assigned `revision` comes back on
 * the same round trip — refetching it would reopen the race the revision exists to close.
 */
class PostgrestClient(
    private val httpClient: HttpClient,
    private val config: SupabaseConfig,
    private val tokenRefresher: TokenRefresher,
) {
    suspend fun select(
        table: String,
        columns: List<String>,
        filters: List<PostgrestFilter> = emptyList(),
        order: String? = null,
    ): Resource<List<JsonObject>> = withToken { token ->
        httpClient.get("${config.restBaseUrl}/$table") {
            standardHeaders(token)
            url.parameters.append("select", columns.joinToString(","))
            filters.forEach { url.parameters.append(it.column, it.value) }
            order?.let { url.parameters.append("order", it) }
        }
    }

    /** A duplicate primary key surfaces as HTTP 409, which [RemoteErrorMapper] maps to
     * `RemoteError.Conflict` — that is how "create, but only if it does not exist yet" is
     * expressed here (it was `currentDocument.exists=false` on Firestore). */
    suspend fun insert(table: String, row: JsonObject): Resource<List<JsonObject>> = withToken { token ->
        httpClient.post("${config.restBaseUrl}/$table") {
            standardHeaders(token)
            headers { append("Prefer", "return=representation") }
            contentType(ContentType.Application.Json)
            setBody(row)
        }
    }

    /**
     * Returns the rows the filters actually matched. An **empty** list is the caller's signal that
     * the write did not land — either the revision precondition failed or row-level security hid
     * the row; PostgREST reports both as "0 rows updated", so the caller disambiguates by reading
     * the row back (see `SupabaseCatalogRepository`).
     */
    suspend fun update(table: String, row: JsonObject, filters: List<PostgrestFilter>): Resource<List<JsonObject>> =
        withToken { token ->
            httpClient.patch("${config.restBaseUrl}/$table") {
                standardHeaders(token)
                headers { append("Prefer", "return=representation") }
                contentType(ContentType.Application.Json)
                filters.forEach { url.parameters.append(it.column, it.value) }
                setBody(row)
            }
        }

    private fun HttpRequestBuilder.standardHeaders(token: String) {
        headers {
            append("apikey", config.anonKey)
            append(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    private suspend fun withToken(call: suspend (String) -> HttpResponse): Resource<List<JsonObject>> {
        val tokenResult = tokenRefresher.currentAccessToken()
        if (tokenResult is Resource.Failure) return Resource.Failure(tokenResult.error)
        val token = (tokenResult as Resource.Success).data
        return try {
            val response = call(token)
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return Resource.Failure(RemoteErrorMapper.mapHttpError(response.status.value, body))
            }
            // Parsed separately from the transport so a malformed body is `Decode`, not `Network`.
            runCatching { Json.parseToJsonElement(body).jsonArray.map { it.jsonObject } }
                .fold({ Resource.Success(it) }, { Resource.Failure(RemoteError.Decode) })
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Resource.Failure(RemoteErrorMapper.mapThrowable(t))
        }
    }
}
