package com.giraffe.matn.data.repository

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.identity.IdentityToolkitClient
import com.giraffe.matn.data.remote.identity.TokenRefresher
import com.giraffe.matn.domain.auth.TeacherAuthRepository
import com.giraffe.matn.domain.auth.TeacherSession
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.secret.SecretStore
import kotlinx.coroutines.flow.Flow

/**
 * Implements [TeacherAuthRepository] over [IdentityToolkitClient], [TokenRefresher], and
 * [SecretStore]. Persists **only** the refresh token (FR-003a), and only after a successful
 * sign-in.
 *
 * Known limitation of the wire contract (`contracts/rest-contract.md` §3.3): the Secure Token
 * API's refresh response carries no `displayName`/`email`, so a session restored purely from a
 * persisted refresh token (app restart with no prior in-memory session) carries empty strings for
 * both until the teacher signs in again. There is no lookup endpoint in the contract to fill them
 * in without inventing one.
 */
class IdentityTeacherAuthRepository(
    private val client: IdentityToolkitClient,
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
