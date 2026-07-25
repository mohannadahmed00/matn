package com.giraffe.matn.permission

import com.giraffe.matn.domain.model.PermissionStatus
import com.giraffe.matn.domain.permission.NotificationPermission

/**
 * Desktop adapter for [NotificationPermission] (research D11, contract onboarding-permissions §3).
 * Desktop JVM has no OS-level runtime notification permission to gate — the notification affordance
 * is always available, so this reports [PermissionStatus.GRANTED] unconditionally, mirroring the
 * pre-API-33 branch of `AndroidNotificationPermission`.
 */
class DesktopNotificationPermission : NotificationPermission {
    override suspend fun status(): PermissionStatus = PermissionStatus.GRANTED
    override suspend fun request(): PermissionStatus = PermissionStatus.GRANTED
    override fun openSystemSettings() {}
}
