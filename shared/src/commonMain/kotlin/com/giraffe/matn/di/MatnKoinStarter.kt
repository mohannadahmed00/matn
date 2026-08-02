package com.giraffe.matn.di

import com.giraffe.matn.data.db.DatabaseDriverFactory
import com.giraffe.matn.data.remote.SupabaseConfig
import com.giraffe.matn.domain.appearance.AppearanceMirror
import com.giraffe.matn.domain.audio.AudioEngine
import com.giraffe.matn.domain.audio.WakeLock
import com.giraffe.matn.domain.delivery.DeviceStorage
import com.giraffe.matn.domain.permission.NotificationPermission
import com.giraffe.matn.domain.preferences.MotionPreferences
import com.giraffe.matn.playback.PracticeSignalRecorder
import com.giraffe.matn.playback.SessionStateRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.dsl.koinApplication

/**
 * Koin Annotations module for the platform-provided singletons (Android `Context`-backed /
 * iOS-bundle-backed instances constructed by the app shell). Unlike [ContentModule], these can't
 * be auto-wired from other Koin bindings — they're handed in as ready-made instances at
 * [initMatnKoin] call time — so each is exposed via a one-line provider function instead.
 */
@Module
class PlatformModule(
    private val driverFactory: DatabaseDriverFactory,
    private val audioEngine: AudioEngine,
    private val wakeLock: WakeLock,
    private val deviceStorage: DeviceStorage,
    private val appearanceMirror: AppearanceMirror,
    private val notificationPermission: NotificationPermission,
    private val motionPreferences: MotionPreferences,
    private val supabaseConfig: SupabaseConfig,
) {
    @Single
    fun driverFactory(): DatabaseDriverFactory = driverFactory

    @Single
    fun audioEngine(): AudioEngine = audioEngine

    @Single
    fun wakeLock(): WakeLock = wakeLock

    @Single
    fun deviceStorage(): DeviceStorage = deviceStorage

    @Single
    fun appearanceMirror(): AppearanceMirror = appearanceMirror

    @Single
    fun notificationPermission(): NotificationPermission = notificationPermission

    @Single
    fun motionPreferences(): MotionPreferences = motionPreferences

    /** Phase 13: the backend the student reads from. Injected, never compiled in (research D13),
     *  so a local stack is just a different [SupabaseConfig.projectUrl]. */
    @Single
    fun supabaseConfig(): SupabaseConfig = supabaseConfig
}

/**
 * Holds the started [Koin] instance for the app, accessible from `commonMain` on every target.
 *
 * Constitution Principle IV (shared-first) requires that DI resolution works identically on
 * Android and iOS from one source. Koin 4.x's `org.koin.core.context.GlobalContext` is not
 * resolvable on the Kotlin/Native target (the `context` package isn't exported there), so we
 * keep the started `Koin` instance in this common holder instead — a single source of truth
 * across targets, no `expect`/`actual` duplication, no JVM-only API. Initialized exactly once
 * by [initMatnKoin] before any composable resolves a dependency.
 */
object MatnKoinHolder {
    // Single-threaded app access: `initMatnKoin` runs once before any composable reads this.
    // `@Volatile` is intentionally avoided — it isn't resolvable on Kotlin/Native and there's
    // no cross-platform multiplatform equivalent without an expect/actual, which would violate
    // the common-single-source-of-truth goal. The holder is published before the UI starts.
    private var _koin: Koin? = null

    val koin: Koin
        get() = _koin ?: error("MatnKoinHolder not initialized — call initMatnKoin first")

    fun isInitialized(): Boolean = _koin != null

    fun initialize(koin: Koin) {
        _koin = koin
    }
}

/**
 * Boots the app-wide Koin instance with the platform-provided [driverFactory] plus the shared
 * [ContentModule]. The platform shell (Android `MainActivity`, iOS `MainViewController`) owns
 * the platform-specific construction of [DatabaseDriverFactory] and calls this exactly once
 * before any composable that resolves a use case (Constitution Principle I/III).
 *
 * Phase 13 removed two things from this function. The `deliveryEngine` parameter is gone: three
 * platform `ContentDeliveryEngine` implementations collapsed into one `RemoteContentDeliveryEngine`
 * in `commonMain`, which [ContentModule] can autowire (FR-041, research D3) — so it is no longer
 * something the platform shell hands in. And the bundled-sample seeding is gone with the content it
 * seeded (FR-038): the library now starts genuinely empty and fills only from what the teacher
 * published, so a first launch with no network is a connect-to-browse state rather than a
 * pre-populated one (constitution Principle VI, amended 2.0.0).
 */
fun initMatnKoin(
    driverFactory: DatabaseDriverFactory,
    audioEngine: AudioEngine,
    wakeLock: WakeLock,
    deviceStorage: DeviceStorage,
    appearanceMirror: AppearanceMirror,
    notificationPermission: NotificationPermission,
    motionPreferences: MotionPreferences,
    supabaseConfig: SupabaseConfig,
) {
    // Guard the whole body: a second call (e.g. Android `onCreate` after a rotation) must not
    // re-start Koin. Everything below runs exactly once.
    if (MatnKoinHolder.isInitialized()) return

    val app = koinApplication {
        modules(
            PlatformModule(
                driverFactory = driverFactory,
                audioEngine = audioEngine,
                wakeLock = wakeLock,
                deviceStorage = deviceStorage,
                appearanceMirror = appearanceMirror,
                notificationPermission = notificationPermission,
                motionPreferences = motionPreferences,
                supabaseConfig = supabaseConfig,
            ).module(),
            ContentModule().module(),
        )
    }
    MatnKoinHolder.initialize(app.koin)

    // T040 (FR-009): start persisting playback snapshots for the whole app lifetime. Resolving the
    // recorder here also materializes the PlaybackController singleton it observes — the same
    // instance every ViewModel gets.
    app.koin.get<SessionStateRecorder>().start()
    // Phase 7 (US2): start crediting the daily practice goal for the same PlaybackController.
    app.koin.get<PracticeSignalRecorder>().start()
}

/**
 * T039 (FR-009/FR-010): the one call each platform backgrounding hook makes — Android
 * `MainActivity.onStop`, the iOS scene-phase observer. All decision-making lives here in
 * `commonMain` (Principle IV); the hook bodies contain nothing but this call. Safe before
 * [initMatnKoin] (a cold start backgrounded before init has nothing to flush).
 */
fun flushSessionState() {
    if (!MatnKoinHolder.isInitialized()) return
    MatnKoinHolder.koin.get<SessionStateRecorder>().flushAsync()
}

