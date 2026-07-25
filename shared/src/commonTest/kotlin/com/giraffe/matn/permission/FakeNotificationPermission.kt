package com.giraffe.matn.permission

import com.giraffe.matn.domain.model.PermissionStatus
import com.giraffe.matn.domain.permission.NotificationPermission

/**
 * In-memory fake [NotificationPermission] for tests (T031). Holds scriptable `status`/`request`
 * results and counts `request()` calls so a test can assert "asked at most once" guarantees
 * (FR-022, contract onboarding-permissions §4). Tests are single-threaded; a plain counter is
 * sufficient.
 */
class FakeNotificationPermission(
    initialStatus: PermissionStatus = PermissionStatus.NOT_DETERMINED,
    private val requestResult: PermissionStatus = PermissionStatus.DENIED,
) : NotificationPermission {

    var status: PermissionStatus = initialStatus
        private set

    var requestCount: Int = 0
        private set

    override suspend fun status(): PermissionStatus = status

    override suspend fun request(): PermissionStatus {
        requestCount += 1
        status = requestResult
        return requestResult
    }

    override fun openSystemSettings() { /* no-op for tests */ }
}