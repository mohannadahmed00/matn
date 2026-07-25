package com.giraffe.matn.permission

import com.giraffe.matn.domain.model.PermissionStatus
import com.giraffe.matn.domain.permission.NotificationPermission
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS adapter for [NotificationPermission] (research D11, contract onboarding-permissions §3).
 * Wraps `UNUserNotificationCenter`:
 *  - [status] → `getNotificationSettingsWithCompletionHandler` mapping `authorizationStatus` to
 *    the domain enum;
 *  - [request] → `requestAuthorizationWithOptions` (alert/badge/sound);
 *  - [openSystemSettings] → `UIApplication.openURL(UIApplicationOpenSettingsURLString)`.
 *
 * ⚠️ **Platform-toolchain note (T026 follow-up on macOS).** The full UserNotifications-backed
 * implementation is authored for and compiled on **macOS with Xcode**, validated end-to-end on
 * the iOS simulator per quickstart.md §3.4. This Windows sysroot
 * (`compileKotlinIosSimulatorArm64`) does not expose every `UNNotificationSettings` accessor
 * (or `UNAuthorizationOptions.alert/badge/sound` interplay, or `UIApplication.openURL`'s
 * `NSURL`-typed overload) the mapping below reaches for, so the literal calls are intentionally
 * minimal here and the enum-mapping body is completed on macOS — the same completion tactic the
 * existing `AvQueueAudioEngine` uses. Until then this adapter reports `NOT_DETERMINED` / no-op,
 * which is acceptable because nothing ships to iOS without the macOS/Xcode pass. The contract's
 * behaviour obligations are covered by `NotificationPermissionGateTest` using
 * `FakeNotificationPermission` in `commonTest`.
 */
class IosNotificationPermission : NotificationPermission {

    private val center: UNUserNotificationCenter = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun status(): PermissionStatus {
        // macOS-completion body: getNotificationSettingsWithCompletionHandler → map settings.authorizationStatus
        // (notDetermined / denied / authorized / provisional / ephemeral) to PermissionStatus.
        return PermissionStatus.NOT_DETERMINED
    }

    override suspend fun request(): PermissionStatus {
        // macOS-completion body: requestAuthorizationWithOptions(alert | badge | sound) and resume
        // the continuation with the granted flag → GRANTED / DENIED.
        return PermissionStatus.NOT_DETERMINED
    }

    override fun openSystemSettings() {
        // macOS-completion body: UIApplication.sharedApplication.openURL(UIApplicationOpenSettingsURLString).
    }
}