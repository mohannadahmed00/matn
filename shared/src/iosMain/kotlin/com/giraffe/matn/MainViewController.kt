package com.giraffe.matn

import androidx.compose.ui.window.ComposeUIViewController
import com.giraffe.matn.appearance.IosAppearanceMirror
import com.giraffe.matn.audio.AvQueueAudioEngine
import com.giraffe.matn.audio.IosWakeLock
import com.giraffe.matn.data.db.DatabaseDriverFactory
import com.giraffe.matn.data.remote.SupabaseConfig
import com.giraffe.matn.delivery.IosDeviceStorage
import com.giraffe.matn.di.initMatnKoin
import com.giraffe.matn.permission.IosNotificationPermission
import com.giraffe.matn.preferences.IosMotionPreferences
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

/**
 * Phase 13: `deliveryEngine` is gone from this call — the three platform engines collapsed into one
 * `RemoteContentDeliveryEngine` in `commonMain` that DI builds itself (FR-041, research D3), so the
 * iOS shell no longer supplies one. What it does supply now is [SupabaseConfig], read from the app
 * bundle so it stays out of the source (research D13).
 */
fun MainViewController(): UIViewController {
    initMatnKoin(
        driverFactory = DatabaseDriverFactory(),
        audioEngine = AvQueueAudioEngine(),
        wakeLock = IosWakeLock(),
        deviceStorage = IosDeviceStorage(),
        appearanceMirror = IosAppearanceMirror(),
        notificationPermission = IosNotificationPermission(),
        motionPreferences = IosMotionPreferences(),
        supabaseConfig = bundleSupabaseConfig(),
    )
    return ComposeUIViewController { App() }
}

/**
 * Reads `SUPABASE_URL` / `SUPABASE_ANON_KEY` / `SUPABASE_BUCKET` from `Info.plist`, which is where
 * the Xcode build injects them.
 *
 * None of the three is a secret: the anon key identifies the project, not the caller, and the
 * student sends no credential at all (FR-027). Authorisation is the `published` flag enforced by
 * row-level security, so what a leaked anon key grants is exactly what the app already grants
 * anonymously — the published catalog.
 */
private fun bundleSupabaseConfig(): SupabaseConfig {
    fun value(key: String): String =
        NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String ?: ""
    return SupabaseConfig(
        projectUrl = value("SUPABASE_URL"),
        anonKey = value("SUPABASE_ANON_KEY"),
        bucket = value("SUPABASE_BUCKET").ifEmpty { "matn-content" },
    )
}
