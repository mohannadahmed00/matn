package com.giraffe.matn.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.auth.TeacherAuthRepository
import com.giraffe.matn.domain.auth.TeacherSession
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.secret.SecretStore
import com.giraffe.matn.domain.usecase.RestoreSessionUseCase
import com.giraffe.matn.domain.usecase.SignInUseCase
import com.giraffe.matn.domain.usecase.SignOutUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val SESSION_KEY = "teacher_refresh_token"

private class FakeSecretStore : SecretStore {
    private val store = mutableMapOf<String, String>()
    override val isProtected: Boolean = true
    override suspend fun put(key: String, value: String): Resource<Unit> {
        store[key] = value
        return Resource.Success(Unit)
    }
    override suspend fun get(key: String): Resource<String?> = Resource.Success(store[key])
    override suspend fun clear(key: String): Resource<Unit> {
        store.remove(key)
        return Resource.Success(Unit)
    }
}

private class FakeTeacherAuthRepository(
    val secretStore: FakeSecretStore,
    private val signInResult: Resource<TeacherSession>? = null,
    private val restoreResult: Resource<TeacherSession> = Resource.Failure(RemoteError.Unauthorized),
) : TeacherAuthRepository {
    private val sessionFlow = MutableStateFlow<TeacherSession?>(null)

    override suspend fun signIn(email: String, password: String): Resource<TeacherSession> {
        val result = signInResult ?: error("signInResult not configured")
        if (result is Resource.Success) {
            sessionFlow.value = result.data
            secretStore.put(SESSION_KEY, result.data.refreshToken)
        }
        return result
    }

    override suspend fun restoreSession(): Resource<TeacherSession> = restoreResult

    override suspend fun signOut(): Resource<Unit> {
        sessionFlow.value = null
        return secretStore.clear(SESSION_KEY)
    }

    override fun observeSession(): Flow<TeacherSession?> = sessionFlow
}

private val sampleSession = TeacherSession(
    uid = "u1",
    displayName = "Teacher",
    email = "teacher@example.com",
    accessToken = "id-token",
    refreshToken = "refresh-token",
    accessTokenExpiresAt = 0L,
)

class AuthUseCaseTest {

    @Test
    fun `SignInUseCase returns the session on success`() = runTest {
        val repo = FakeTeacherAuthRepository(FakeSecretStore(), signInResult = Resource.Success(sampleSession))
        val result = SignInUseCase(repo)(SignInUseCase.Params("teacher@example.com", "pw"))
        assertEquals(Resource.Success(sampleSession), result)
    }

    @Test
    fun `SignInUseCase returns the mapped error on failure`() = runTest {
        val repo = FakeTeacherAuthRepository(FakeSecretStore(), signInResult = Resource.Failure(RemoteError.Unauthorized))
        val result = SignInUseCase(repo)(SignInUseCase.Params("teacher@example.com", "wrong"))
        assertEquals(Resource.Failure(RemoteError.Unauthorized), result)
    }

    @Test
    fun `SignOutUseCase clears the secret store`() = runTest {
        val secretStore = FakeSecretStore()
        secretStore.put(SESSION_KEY, "some-refresh-token")
        val repo = FakeTeacherAuthRepository(secretStore)

        SignOutUseCase(repo)(Unit)

        assertNull((secretStore.get(SESSION_KEY) as Resource.Success).data)
    }

    @Test
    fun `RestoreSessionUseCase returns the session when a refresh token exists`() = runTest {
        val repo = FakeTeacherAuthRepository(FakeSecretStore(), restoreResult = Resource.Success(sampleSession))
        val result = RestoreSessionUseCase(repo)(Unit)
        assertEquals(Resource.Success(sampleSession), result)
    }

    @Test
    fun `RestoreSessionUseCase returns null when no refresh token exists`() = runTest {
        val repo = FakeTeacherAuthRepository(FakeSecretStore(), restoreResult = Resource.Failure(RemoteError.Unauthorized))
        val result = RestoreSessionUseCase(repo)(Unit)
        assertEquals(Resource.Success(null), result)
    }
}
