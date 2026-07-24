package com.giraffe.matn

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.giraffe.matn.audio.AndroidWakeLock
import com.giraffe.matn.audio.Media3AudioEngine
import com.giraffe.matn.data.db.DatabaseDriverFactory
import com.giraffe.matn.di.flushSessionState
import com.giraffe.matn.di.initMatnKoin

class MainActivity : ComponentActivity() {

    private val wakeLock: AndroidWakeLock = AndroidWakeLock()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result intentionally ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        wakeLock.attach(this)
        initMatnKoin(
            driverFactory = DatabaseDriverFactory(this),
            audioEngine = Media3AudioEngine(applicationContext),
            wakeLock = wakeLock,
        )
        requestPostNotificationsIfNeeded()

        setContent {
            App()
        }
    }

    override fun onStop() {
        super.onStop()
        flushSessionState()
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock.detach()
    }

    /** Android 13+ requires the POST_NOTIFICATIONS runtime permission for the media notification (T037). */
    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}