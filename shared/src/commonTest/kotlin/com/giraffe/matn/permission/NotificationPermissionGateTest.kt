package com.giraffe.matn.permission

import com.giraffe.matn.domain.model.PermissionStatus
import com.giraffe.matn.domain.usecase.EnsureNotificationPermissionUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T080 (US3, onboarding-permissions-contract.md §4) — all five branches against
 * [FakeNotificationPermission]: the asked flag is set on every terminal path including
 * dismissal, `request()` fires at most once across repeated sessions, and a denied result never
 * blocks playback (the use case only ever returns a UI signal, never a failure).
 */
class NotificationPermissionGateTest {

    @Test
    fun `branch 1 - already asked proceeds with no prompt`() = runTest {
        val permission = FakeNotificationPermission(initialStatus = PermissionStatus.DENIED)
        val repo = FakeNotificationPermissionAskedRepository(initial = true)
        val gate = EnsureNotificationPermissionUseCase(permission, repo)

        val result = gate()

        assertEquals(EnsureNotificationPermissionUseCase.Result.Proceed, result)
        assertEquals(0, permission.requestCount)
    }

    @Test
    fun `branch 2 - already granted sets the flag and proceeds`() = runTest {
        val permission = FakeNotificationPermission(initialStatus = PermissionStatus.GRANTED)
        val repo = FakeNotificationPermissionAskedRepository()
        val gate = EnsureNotificationPermissionUseCase(permission, repo)

        val result = gate()

        assertEquals(EnsureNotificationPermissionUseCase.Result.Proceed, result)
        assertTrue(repo.asked)
        assertEquals(0, permission.requestCount)
    }

    @Test
    fun `branch 3 - not determined shows the rationale without setting the flag yet`() = runTest {
        val permission = FakeNotificationPermission(initialStatus = PermissionStatus.NOT_DETERMINED)
        val repo = FakeNotificationPermissionAskedRepository()
        val gate = EnsureNotificationPermissionUseCase(permission, repo)

        val result = gate()

        assertEquals(EnsureNotificationPermissionUseCase.Result.ShowRationale, result)
        assertEquals(false, repo.asked)
    }

    @Test
    fun `branch 4 - continue requests the system prompt then sets the flag whatever the result`() = runTest {
        val permission = FakeNotificationPermission(
            initialStatus = PermissionStatus.NOT_DETERMINED,
            requestResult = PermissionStatus.DENIED,
        )
        val repo = FakeNotificationPermissionAskedRepository()
        val gate = EnsureNotificationPermissionUseCase(permission, repo)

        val outcome = gate.onRationaleContinue()

        assertEquals(PermissionStatus.DENIED, outcome)
        assertEquals(1, permission.requestCount)
        assertTrue(repo.asked)
    }

    @Test
    fun `branch 5 - dismissal sets the flag without requesting`() = runTest {
        val permission = FakeNotificationPermission(initialStatus = PermissionStatus.NOT_DETERMINED)
        val repo = FakeNotificationPermissionAskedRepository()
        val gate = EnsureNotificationPermissionUseCase(permission, repo)

        gate.onRationaleDismissed()

        assertTrue(repo.asked)
        assertEquals(0, permission.requestCount)
    }

    @Test
    fun `request fires at most once across repeated sessions`() = runTest {
        val permission = FakeNotificationPermission(
            initialStatus = PermissionStatus.NOT_DETERMINED,
            requestResult = PermissionStatus.DENIED,
        )
        val repo = FakeNotificationPermissionAskedRepository()
        val gate = EnsureNotificationPermissionUseCase(permission, repo)

        // Session 1: not determined -> rationale -> continue -> request() fires once.
        assertEquals(EnsureNotificationPermissionUseCase.Result.ShowRationale, gate())
        gate.onRationaleContinue()
        assertEquals(1, permission.requestCount)

        // Session 2, 3: the flag is now set — every later session's gate() short-circuits.
        assertEquals(EnsureNotificationPermissionUseCase.Result.Proceed, gate())
        assertEquals(EnsureNotificationPermissionUseCase.Result.Proceed, gate())
        assertEquals(1, permission.requestCount)
    }

    @Test
    fun `a denied outcome is a UI signal only - it is never a failure the caller must handle`() = runTest {
        val permission = FakeNotificationPermission(
            initialStatus = PermissionStatus.NOT_DETERMINED,
            requestResult = PermissionStatus.DENIED,
        )
        val gate = EnsureNotificationPermissionUseCase(permission, FakeNotificationPermissionAskedRepository())

        // onRationaleContinue returns a plain PermissionStatus, not a Resource<...> the caller
        // could branch a failure on — there is no failure path to gate playback on.
        val outcome = gate.onRationaleContinue()
        assertEquals(PermissionStatus.DENIED, outcome)
    }
}
