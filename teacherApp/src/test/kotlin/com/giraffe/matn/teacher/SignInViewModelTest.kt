package com.giraffe.matn.teacher

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.auth.TeacherAuthRepository
import com.giraffe.matn.domain.auth.TeacherSession
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.usecase.SignInUseCase
import com.giraffe.matn.teacher.presentation.signin.SignInViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

private class FakeTeacherAuthRepository(
    private val signInResult: Resource<TeacherSession>,
) : TeacherAuthRepository {
    private val sessionFlow = MutableStateFlow<TeacherSession?>(null)
    val lastSession: TeacherSession? get() = sessionFlow.value

    override suspend fun signIn(email: String, password: String): Resource<TeacherSession> {
        if (signInResult is Resource.Success) sessionFlow.value = signInResult.data
        return signInResult
    }

    override suspend fun restoreSession(): Resource<TeacherSession> = Resource.Failure(RemoteError.Unauthorized)

    override suspend fun signOut(): Resource<Unit> {
        sessionFlow.value = null
        return Resource.Success(Unit)
    }

    override fun observeSession(): Flow<TeacherSession?> = sessionFlow
}

private val sampleSession = TeacherSession(
    uid = "u1",
    displayName = "Teacher",
    email = "t@example.com",
    idToken = "id-token",
    refreshToken = "refresh-token",
    idTokenExpiresAt = 0L,
)

class SignInViewModelTest {

    @BeforeTest
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `success clears submitting and error, and sets the session`() = runTest {
        val repo = FakeTeacherAuthRepository(Resource.Success(sampleSession))
        val vm = SignInViewModel(SignInUseCase(repo), secretStoreUnprotected = false)
        vm.onEmailChange("t@example.com")
        vm.onPasswordChange("pw")

        vm.onSubmit()

        assertFalse(vm.state.value.isSubmitting)
        assertNull(vm.state.value.error)
        assertEquals(sampleSession, repo.lastSession)
    }

    @Test
    fun `failure surfaces the error and leaves the typed email in state`() = runTest {
        val repo = FakeTeacherAuthRepository(Resource.Failure(RemoteError.Unauthorized))
        val vm = SignInViewModel(SignInUseCase(repo), secretStoreUnprotected = false)
        vm.onEmailChange("t@example.com")
        vm.onPasswordChange("wrong")

        vm.onSubmit()

        assertEquals(RemoteError.Unauthorized, vm.state.value.error)
        assertEquals("t@example.com", vm.state.value.email)
        assertFalse(vm.state.value.isSubmitting)
    }
}
