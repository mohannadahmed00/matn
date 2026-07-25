package com.giraffe.matn

import androidx.compose.ui.window.ComposeUIViewController
import com.giraffe.matn.audio.AvQueueAudioEngine
import com.giraffe.matn.audio.IosWakeLock
import com.giraffe.matn.data.db.DatabaseDriverFactory
import com.giraffe.matn.delivery.IosDeviceStorage
import com.giraffe.matn.delivery.OnDemandResourcesEngine
import com.giraffe.matn.di.initMatnKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initMatnKoin(
        driverFactory = DatabaseDriverFactory(),
        audioEngine = AvQueueAudioEngine(),
        wakeLock = IosWakeLock(),
        deliveryEngine = OnDemandResourcesEngine(),
        deviceStorage = IosDeviceStorage(),
    )
    return ComposeUIViewController { App() }
}