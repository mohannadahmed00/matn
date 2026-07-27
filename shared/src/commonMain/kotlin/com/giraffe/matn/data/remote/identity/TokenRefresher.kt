package com.giraffe.matn.data.remote.identity

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.auth.TeacherSession
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.secret.SecretStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the current [TeacherSession] in memory and refreshes it proactively, within 5 minutes of
 * `idTokenExpiresAt` (`contracts/rest-contract.md` §3.3). The [Mutex] makes concurrent callers
 * single-flight: whichever caller enters first performs the refresh; the rest await the same lock
 * and then observe the already-refreshed session.
 */
class TokenRefresher(
    private val client: IdentityToolkitClient,
    private val secretStore: SecretStore,
    private val nowMillis: () -> Long,
) {
    private val mutex = Mutex()
    private val _session = MutableStateFlow<TeacherSession?>(null)
    val session: StateFlow<TeacherSession?> = _session.asStateFlow()

    fun setSession(newSession: TeacherSession?) {
        _session.value = newSession
    }

    suspend fun currentIdToken(): Resource<String> = mutex.withLock {
        val current = _session.value ?: return@withLock Resource.Failure(RemoteError.Unauthorized)
        if (current.idTokenExpiresAt - nowMillis() > REFRESH_WINDOW_MS) {
            return@withLock Resource.Success(current.idToken)
        }
        when (val result = client.refresh(current.refreshToken)) {
            is Resource.Success -> {
                // The Secure Token API returns no displayName/email — carry them over.
                val merged = result.data.copy(displayName = current.displayName, email = current.email)
                _session.value = merged
                secretStore.put(REFRESH_TOKEN_KEY, merged.refreshToken)
                Resource.Success(merged.idToken)
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
