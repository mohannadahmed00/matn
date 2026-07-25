package com.giraffe.matn.preferences

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.giraffe.matn.domain.preferences.MotionPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android adapter for [MotionPreferences] (research D7, contract adaptive-motion §B2). Reads
 * `Settings.Global.ANIMATOR_DURATION_SCALE`: when set to `0f`, the "remove animations" toggle
 * is on. Re-emits via a [ContentObserver] so a mid-session change reaches the app live.
 *
 * Defer-and-never-crash: if the read returns `null` (some sandboxed contexts), reduce motion is
 * treated as `false` so the app keeps animating rather than erroring out.
 */
class AndroidMotionPreferences(
    context: Context,
) : MotionPreferences {

    private val resolver = context.contentResolver
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun observeReduceMotion(): Flow<Boolean> = callbackFlow {
        fun snapshot(): Boolean = currentReduceMotion()
        trySend(snapshot())

        val observer = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(snapshot())
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            /* notifyForDescendants = */ false,
            observer,
        )
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    private fun currentReduceMotion(): Boolean = try {
        val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        scale == 0f
    } catch (t: Throwable) {
        false
    }
}