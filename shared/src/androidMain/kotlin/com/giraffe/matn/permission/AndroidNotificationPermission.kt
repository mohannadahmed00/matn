package com.giraffe.matn.permission

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.giraffe.matn.domain.model.PermissionStatus
import com.giraffe.matn.domain.permission.NotificationPermission
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Android adapter for [NotificationPermission] (research D11, contract onboarding-permissions §3).
 *
 * **No business logic** (Principle IV): it translates the OS permission state into the domain enum
 * and forwards `request()` to a launcher that `MainActivity` owns. Below API 33 the runtime
 * permission doesn't exist and the media notification is always allowed → [GRANTED].
 */
class AndroidNotificationPermission(
    private val context: Context,
    private val requester: NotificationPermissionRequester,
) : NotificationPermission {

    override suspend fun status(): PermissionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return PermissionStatus.GRANTED
        val granted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) PermissionStatus.GRANTED else PermissionStatus.NOT_DETERMINED
    }

    override suspend fun request(): PermissionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return PermissionStatus.GRANTED
        val granted = requester.requestPermission(android.Manifest.permission.POST_NOTIFICATIONS)
        return if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED
    }

    override fun openSystemSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/**
 * Owned by `MainActivity`, which registers an `ActivityResultLauncher` for
 * `RequestPermission`. Implemented as a small interface so the seam is testable in isolation
 * on the JVM side with a fake.
 */
interface NotificationPermissionRequester {
    /** Surfaces the system prompt and suspends until the OS reports the outcome. */
    suspend fun requestPermission(permission: String): Boolean
}