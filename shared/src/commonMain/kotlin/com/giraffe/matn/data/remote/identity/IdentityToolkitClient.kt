package com.giraffe.matn.data.remote.identity

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.FirebaseConfig
import com.giraffe.matn.data.remote.RemoteErrorMapper
import com.giraffe.matn.domain.auth.TeacherSession
import com.giraffe.matn.domain.error.RemoteError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Sign-in and token refresh against Identity Toolkit / Secure Token (`contracts/rest-contract.md` §3). */
class IdentityToolkitClient(
    private val httpClient: HttpClient,
    private val config: FirebaseConfig,
) {
    suspend fun signInWithPassword(email: String, password: String): Resource<TeacherSession> = try {
        val response = httpClient.post("${config.identityBaseUrl}/accounts:signInWithPassword?key=${config.apiKey}") {
            contentType(ContentType.Application.Json)
            setBody(SignInRequest(email = email, password = password))
        }
        if (response.status.isSuccess()) {
            Resource.Success(response.body<SignInResponse>().toSession())
        } else {
            Resource.Failure(mapIdentityError(response.bodyAsText()))
        }
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Resource.Failure(RemoteErrorMapper.mapThrowable(t))
    }

    /**
     * The Secure Token API's refresh response carries no `displayName`/`email` (unlike sign-in) —
     * the returned session's are empty; [com.giraffe.matn.data.remote.identity.TokenRefresher]
     * carries those two fields over from the session already held in memory.
     */
    suspend fun refresh(refreshToken: String): Resource<TeacherSession> = try {
        val response = httpClient.submitForm(
            url = "${config.secureTokenBaseUrl}/token?key=${config.apiKey}",
            formParameters = Parameters.build {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
            },
        )
        if (response.status.isSuccess()) {
            Resource.Success(response.body<RefreshResponse>().toSession())
        } else {
            Resource.Failure(mapIdentityError(response.bodyAsText()))
        }
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Resource.Failure(RemoteErrorMapper.mapThrowable(t))
    }

    @Serializable
    private data class SignInRequest(
        val email: String,
        val password: String,
        val returnSecureToken: Boolean = true,
    )

    @Serializable
    private data class SignInResponse(
        val localId: String,
        val displayName: String = "",
        val email: String,
        val idToken: String,
        val refreshToken: String,
        val expiresIn: String,
    ) {
        fun toSession(): TeacherSession = TeacherSession(
            uid = localId,
            displayName = displayName,
            email = email,
            idToken = idToken,
            refreshToken = refreshToken,
            idTokenExpiresAt = nowMillis() + expiresIn.toLong() * 1000,
        )
    }

    @Serializable
    private data class RefreshResponse(
        @SerialName("id_token") val idToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("expires_in") val expiresIn: String,
        @SerialName("user_id") val userId: String,
    ) {
        fun toSession(): TeacherSession = TeacherSession(
            uid = userId,
            displayName = "",
            email = "",
            idToken = idToken,
            refreshToken = refreshToken,
            idTokenExpiresAt = nowMillis() + expiresIn.toLong() * 1000,
        )
    }
}

@OptIn(ExperimentalTime::class)
private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** `contracts/rest-contract.md` §3.2. */
private fun mapIdentityError(body: String): RemoteError {
    val message = runCatching {
        Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    }.getOrNull()
    return when (message) {
        "EMAIL_NOT_FOUND", "INVALID_PASSWORD", "INVALID_LOGIN_CREDENTIALS" -> RemoteError.Unauthorized
        "USER_DISABLED" -> RemoteError.Forbidden
        "TOKEN_EXPIRED", "INVALID_REFRESH_TOKEN" -> RemoteError.Unauthorized
        "TOO_MANY_ATTEMPTS_TRY_LATER" -> RemoteError.Server
        else -> RemoteError.Decode
    }
}
