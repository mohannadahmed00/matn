package com.giraffe.matn.data.remote.auth

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.RemoteErrorMapper
import com.giraffe.matn.data.remote.SupabaseConfig
import com.giraffe.matn.domain.auth.TeacherSession
import com.giraffe.matn.domain.error.RemoteError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Sign-in and token refresh against Supabase Auth (`contracts/rest-contract.md` §3). Both are the
 * same endpoint — `POST /auth/v1/token` — distinguished only by the `grant_type` query parameter.
 *
 * Unlike Firebase's Secure Token API, the refresh response carries the full `user` object, so a
 * refreshed session has a real `displayName`/`email` and the old carry-over workaround is gone.
 */
class SupabaseAuthClient(
    private val httpClient: HttpClient,
    private val config: SupabaseConfig,
) {
    suspend fun signInWithPassword(email: String, password: String): Resource<TeacherSession> =
        token(grantType = "password", body = PasswordGrant(email = email, password = password))

    suspend fun refresh(refreshToken: String): Resource<TeacherSession> =
        token(grantType = "refresh_token", body = RefreshGrant(refreshToken = refreshToken))

    private suspend inline fun <reified T> token(grantType: String, body: T): Resource<TeacherSession> = try {
        val response = httpClient.post("${config.authBaseUrl}/token?grant_type=$grantType") {
            headers { append("apikey", config.anonKey) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (response.status.isSuccess()) {
            Resource.Success(response.body<TokenResponse>().toSession())
        } else {
            Resource.Failure(mapAuthError(response.status.value, response.bodyAsText()))
        }
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Resource.Failure(RemoteErrorMapper.mapThrowable(t))
    }

    @Serializable
    private data class PasswordGrant(val email: String, val password: String)

    @Serializable
    private data class RefreshGrant(@SerialName("refresh_token") val refreshToken: String)

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("expires_in") val expiresIn: Long,
        val user: User,
    ) {
        fun toSession(): TeacherSession = TeacherSession(
            uid = user.id,
            displayName = user.displayName(),
            email = user.email.orEmpty(),
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAt = nowMillis() + expiresIn * 1000,
        )
    }

    @Serializable
    private data class User(
        val id: String,
        val email: String? = null,
        @SerialName("user_metadata") val userMetadata: JsonObject? = null,
    ) {
        /** Supabase has no first-class display name: it lives in whatever `user_metadata` key the
         * account was provisioned with. Falls back to the email's local part so a list row is never
         * blank. */
        fun displayName(): String {
            val fromMetadata = DISPLAY_NAME_KEYS.firstNotNullOfOrNull { key ->
                userMetadata?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            }
            return fromMetadata ?: email?.substringBefore('@').orEmpty()
        }
    }

    private companion object {
        val DISPLAY_NAME_KEYS = listOf("display_name", "full_name", "name")
    }
}

@OptIn(ExperimentalTime::class)
private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * `contracts/rest-contract.md` §3.2. Supabase Auth has emitted two error shapes over its life —
 * the newer `{"code":400,"error_code":"invalid_credentials","msg":...}` and the OAuth-style
 * `{"error":"invalid_grant","error_description":...}` — and a self-hosted or older stack can still
 * return the latter, so both are read before falling back to the status code.
 */
private fun mapAuthError(status: Int, body: String): RemoteError {
    val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
    fun field(name: String) = json?.get(name)?.jsonPrimitive?.contentOrNull
    val code = field("error_code") ?: field("error")

    return when (code) {
        "invalid_credentials", "invalid_grant", "email_not_confirmed",
        "refresh_token_not_found", "refresh_token_already_used", "session_not_found",
        "bad_jwt", "no_authorization", "session_expired",
        -> RemoteError.Unauthorized

        "user_banned", "user_not_found", "signup_disabled" -> RemoteError.Forbidden
        "over_request_rate_limit", "over_email_send_rate_limit" -> RemoteError.Server

        else -> when {
            status == 400 || status == 401 -> RemoteError.Unauthorized
            status == 403 -> RemoteError.Forbidden
            status == 422 -> RemoteError.Unauthorized
            status == 429 -> RemoteError.Server
            status in 500..599 -> RemoteError.Server
            else -> RemoteError.Decode
        }
    }
}
