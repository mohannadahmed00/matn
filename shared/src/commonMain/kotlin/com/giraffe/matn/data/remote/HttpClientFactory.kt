package com.giraffe.matn.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * `commonMain`, no `expect`/`actual` — CIO is published for JVM, Android, and every iOS variant
 * (research D1). [engine] exists so tests can inject `MockEngine`.
 *
 * No Ktor `Auth` plugin: token refresh is explicit ([com.giraffe.matn.data.remote.identity.TokenRefresher]),
 * because a bearer token that silently refreshes would hide `Unauthorized` from FR-005's error
 * mapping. No logging plugin, so a credential can never reach a log (FR-003b).
 */
fun createHttpClient(engine: HttpClientEngine? = null): HttpClient {
    val config: HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
        }
    }
    return if (engine != null) HttpClient(engine, config) else HttpClient(CIO, config)
}
