package com.giraffe.matn.data.remote.auth

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.auth.TeacherSession
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.secret.SecretStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds the current [TeacherSession] in memory and refreshes it proactively, within 5 minutes of
 * `accessTokenExpiresAt` (`contracts/rest-contract.md` §3.3). The [Mutex] makes concurrent callers
 * single-flight: whichever caller enters first performs the refresh; the rest await the same lock
 * and then observe the already-refreshed session.
 *
 * Supabase rotates the refresh token on every use, so the newly issued one is persisted each time —
 * keeping the old one would invalidate the next restore.
 */
class TokenRefresher(
    private val client: SupabaseAuthClient,
    private val secretStore: SecretStore,
    private val nowMillis: () -> Long,
) : AccessTokenProvider {
    private val mutex = Mutex()
    private val _session = MutableStateFlow<TeacherSession?>(null)
    val session: StateFlow<TeacherSession?> = _session.asStateFlow()

    fun setSession(newSession: TeacherSession?) {
        _session.value = newSession
    }

    override suspend fun currentAccessToken(): Resource<String?> = mutex.withLock {
        val current = _session.value ?: return@withLock Resource.Failure(RemoteError.Unauthorized)
        if (current.accessTokenExpiresAt - nowMillis() > REFRESH_WINDOW_MS) {
            return@withLock Resource.Success(current.accessToken)
        }
        when (val result = client.refresh(current.refreshToken)) {
            is Resource.Success -> {
                _session.value = result.data
                secretStore.put(REFRESH_TOKEN_KEY, result.data.refreshToken)
                Resource.Success(result.data.accessToken)
            }
            is Resource.Failure -> {
                if (result.error == RemoteError.Unauthorized) {
                    _session.value = null
                    secretStore.clear(REFRESH_TOKEN_KEY)
                }
                Resource.Failure(result.error)
            }
        }
    }

    companion object {
        const val REFRESH_TOKEN_KEY = "teacher_refresh_token"
        private const val REFRESH_WINDOW_MS = 5 * 60 * 1000L
    }
}
