package com.giraffe.matn.domain.permission

import com.giraffe.matn.domain.model.PermissionStatus

/**
 * Platform seam over the OS runtime-notification permission (research D11, contract
 * onboarding-permissions §3). Implemented by `AndroidNotificationPermission` and
 * `IosNotificationPermission`; faked in tests so every gate path is exercised without a device.
 */
interface NotificationPermission {
    /** The current OS-level status. Lambdas-returning-suspend avoid blocking the caller. */
    suspend fun status(): PermissionStatus

    /**
     * Surfaces the system prompt at most once per install (the "asked" fact is persisted by the
     * caller, not inferred from this return). Returns the resulting status.
     */
    suspend fun request(): PermissionStatus

    /** Opens the OS settings screen for this app's notifications (FR-023, `PERMANENTLY_DENIED`). */
    fun openSystemSettings()
}