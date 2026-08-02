package com.giraffe.matn

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.giraffe.matn.appearance.AndroidAppearanceMirror
import com.giraffe.matn.audio.AndroidWakeLock
import com.giraffe.matn.audio.Media3AudioEngine
import com.giraffe.matn.data.db.DatabaseDriverFactory
import com.giraffe.matn.delivery.AndroidDeviceStorage
import com.giraffe.matn.di.flushSessionState
import com.giraffe.matn.data.remote.SupabaseConfig
import com.giraffe.matn.di.initMatnKoin
import com.giraffe.matn.permission.AndroidNotificationPermission
import com.giraffe.matn.permission.NotificationPermissionRequester
import com.giraffe.matn.preferences.AndroidMotionPreferences
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class MainActivity :
    ComponentActivity(),
    NotificationPermissionRequester {

    private val wakeLock: AndroidWakeLock = AndroidWakeLock()

    /** The one launcher the notification seam awaits — at most one in-flight request (T029, rule 5). */
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingPermissionContinuation?.resume(granted)
            pendingPermissionContinuation = null
        }
    private var pendingPermissionContinuation: Continuation<Boolean>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        applyMirroredAppearance()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        wakeLock.attach(this)
        initMatnKoin(
            driverFactory = DatabaseDriverFactory(this),
            audioEngine = Media3AudioEngine(applicationContext),
            wakeLock = wakeLock,
            deviceStorage = AndroidDeviceStorage(applicationContext),
            appearanceMirror = AndroidAppearanceMirror(applicationContext),
            notificationPermission = AndroidNotificationPermission(applicationContext, this),
            motionPreferences = AndroidMotionPreferences(applicationContext),
            supabaseConfig = SupabaseConfig(
                projectUrl = BuildConfig.SUPABASE_URL,
                anonKey = BuildConfig.SUPABASE_ANON_KEY,
                bucket = BuildConfig.SUPABASE_BUCKET,
            ),
        )

        setContent {
            App()
        }
    }

    /**
     * T049 (US1): the platform splash itself is unavoidably picked by device night mode, before
     * any Kotlin runs (research D9). Here — still before [super.onCreate] — the *pinned*
     * appearance [com.giraffe.matn.appearance.AndroidAppearanceMirror] last wrote is read back and
     * used to select the matching post-splash theme explicitly, so a pin overrides device night
     * mode rather than the reverse. A missing or unrecognized mirror value (first launch, or the
     * student has never overridden) leaves the manifest-declared theme in effect, which already
     * resolved to the correct SYSTEM default via the values/values-night qualifier
     * (data-model.md §1.2 — a stale or missing mirror degrades to system, never to an error).
     */
    private fun applyMirroredAppearance() {
        val prefs = getSharedPreferences(AndroidAppearanceMirror.PREFS_NAME, Context.MODE_PRIVATE)
        when (prefs.getString(AndroidAppearanceMirror.KEY_APPEARANCE, null)) {
            "LIGHT" -> setTheme(R.style.Theme_Matn_Main_Light)
            "DARK" -> setTheme(R.style.Theme_Matn_Main_Dark)
        }
    }

    override suspend fun requestPermission(permission: String): Boolean =
        suspendCancellableCoroutine { cont ->
            check(pendingPermissionContinuation == null) { "Concurrent permission requests unsupported" }
            pendingPermissionContinuation = cont
            cont.invokeOnCancellation { pendingPermissionContinuation = null }
            requestPermissionLauncher.launch(permission)
        }

    override fun onStop() {
        super.onStop()
        flushSessionState()
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock.detach()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}