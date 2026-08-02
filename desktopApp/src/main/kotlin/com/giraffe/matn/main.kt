package com.giraffe.matn

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.giraffe.matn.appearance.DesktopAppearanceMirror
import com.giraffe.matn.audio.DesktopAudioEngine
import com.giraffe.matn.data.db.DatabaseDriverFactory
import com.giraffe.matn.delivery.DesktopDeviceStorage
import com.giraffe.matn.di.initMatnKoin
import com.giraffe.matn.domain.audio.NoOpWakeLock
import com.giraffe.matn.permission.DesktopNotificationPermission
import com.giraffe.matn.preferences.DesktopMotionPreferences

fun main() {
    initMatnKoin(
        driverFactory = DatabaseDriverFactory(),
        audioEngine = DesktopAudioEngine(),
        wakeLock = NoOpWakeLock,
        deviceStorage = DesktopDeviceStorage(),
        appearanceMirror = DesktopAppearanceMirror(),
        notificationPermission = DesktopNotificationPermission(),
        motionPreferences = DesktopMotionPreferences(),
        supabaseConfig = studentSupabaseConfig(),
    )

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "متن",
        ) {
            App()
        }
    }
}
