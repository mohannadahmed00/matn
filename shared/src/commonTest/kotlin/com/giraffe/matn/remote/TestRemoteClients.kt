package com.giraffe.matn.remote

import com.giraffe.matn.data.remote.SupabaseConfig
import com.giraffe.matn.data.remote.auth.AnonymousAccessTokenProvider
import com.giraffe.matn.data.remote.createHttpClient
import com.giraffe.matn.data.remote.postgrest.PostgrestClient
import com.giraffe.matn.data.remote.storage.StorageRestClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType

/** The config every test uses. Values are inert — `MockEngine` never dials out. */
val testSupabaseConfig: SupabaseConfig = SupabaseConfig(
    projectUrl = "https://test.supabase.co",
    anonKey = "test-anon-key",
    bucket = "matn-content",
)

/** Records the requests a client issued, so header and call-count assertions are possible. */
class RecordingEngine(private val handler: (HttpRequestData) -> MockResponse) {
    val requests: MutableList<HttpRequestData> = mutableListOf()

    val engine: MockEngine = MockEngine { request ->
        requests += request
        when (val response = handler(request)) {
            is MockResponse.Json -> respond(
                content = response.body,
                status = response.status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
            is MockResponse.Bytes -> respond(content = response.body, status = HttpStatusCode.OK)
            is MockResponse.Error -> respondError(response.status)
        }
    }
}

sealed interface MockResponse {
    data class Json(val body: String, val status: HttpStatusCode = HttpStatusCode.OK) : MockResponse
    data class Bytes(val body: ByteArray) : MockResponse
    data class Error(val status: HttpStatusCode) : MockResponse
}

fun anonymousPostgrestClient(engine: MockEngine): PostgrestClient =
    PostgrestClient(createHttpClient(engine), testSupabaseConfig, AnonymousAccessTokenProvider())

fun anonymousStorageClient(engine: MockEngine): StorageRestClient =
    StorageRestClient(createHttpClient(engine), testSupabaseConfig, AnonymousAccessTokenProvider())

/**
 * A client whose engine fails every request. Used by tests that must prove **no request is made** —
 * if one ever is, the test fails loudly rather than silently succeeding against a stub.
 */
fun throwingPostgrestClient(): PostgrestClient =
    anonymousPostgrestClient(MockEngine { error("unexpected network call") })

fun throwingStorageClient(): StorageRestClient =
    anonymousStorageClient(MockEngine { error("unexpected network call") })
