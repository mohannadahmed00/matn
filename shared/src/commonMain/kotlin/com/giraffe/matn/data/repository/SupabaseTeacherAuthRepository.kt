package com.giraffe.matn.data.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.auth.SupabaseAuthClient
import com.giraffe.matn.data.remote.auth.TokenRefresher
import com.giraffe.matn.domain.auth.TeacherAuthRepository
import com.giraffe.matn.domain.auth.TeacherSession
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.secret.SecretStore
import kotlinx.coroutines.flow.Flow

/**
 * Implements [TeacherAuthRepository] over [SupabaseAuthClient], [TokenRefresher], and
 * [SecretStore]. Persists **only** the refresh token (FR-003a), and only after a successful
 * sign-in.
 *
 * Supabase's refresh response carries the full `user` object, so a session restored from nothing
 * but a persisted refresh token has a real display name and email — the Firebase Secure Token API
 * returned neither, and the restored session used to show blanks until the next sign-in.
 */
class SupabaseTeacherAuthRepository(
    private val client: SupabaseAuthClient,
    private val tokenRefresher: TokenRefresher,
    private val secretStore: SecretStore,
) : TeacherAuthRepository {

    override suspend fun signIn(email: String, password: String): Resource<TeacherSession> =
        when (val result = client.signInWithPassword(email, password)) {
            is Resource.Success -> {
                tokenRefresher.setSession(result.data)
                secretStore.put(TokenRefresher.REFRESH_TOKEN_KEY, result.data.refreshToken)
                result
            }
            is Resource.Failure -> result
        }

    override suspend fun restoreSession(): Resource<TeacherSession> {
        val storedToken = when (val stored = secretStore.get(TokenRefresher.REFRESH_TOKEN_KEY)) {
            is Resource.Success -> stored.data
            is Resource.Failure -> null
        } ?: return Resource.Failure(RemoteError.Unauthorized)

        return when (val result = client.refresh(storedToken)) {
            is Resource.Success -> {
                tokenRefresher.setSession(result.data)
                // Supabase rotates the refresh token on use; storing the new one is what makes the
                // *next* restore work.
                secretStore.put(TokenRefresher.REFRESH_TOKEN_KEY, result.data.refreshToken)
                result
            }
            is Resource.Failure -> {
                secretStore.clear(TokenRefresher.REFRESH_TOKEN_KEY)
                result
            }
        }
    }

    override suspend fun signOut(): Resource<Unit> {
        tokenRefresher.setSession(null)
        return secretStore.clear(TokenRefresher.REFRESH_TOKEN_KEY)
    }

    override fun observeSession(): Flow<TeacherSession?> = tokenRefresher.session
}
