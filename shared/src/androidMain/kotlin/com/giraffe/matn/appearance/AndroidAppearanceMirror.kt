package com.giraffe.matn.appearance

import android.content.Context
import com.giraffe.matn.domain.appearance.AppearanceMirror
import com.giraffe.matn.domain.model.Appearance

/**
 * Android adapter for [AppearanceMirror] (research D9, contract theming §1.1). Writes the
 * already-resolved [Appearance] name to a `SharedPreferences` file the launch window can read
 * synchronously before any Kotlin runs. **No business logic** — it stores what it receives.
 *
 * A stale or missing mirror degrades to the *system* appearance in `MainActivity`, never to an
 * error (research D9).
 */
class AndroidAppearanceMirror(
    context: Context,
) : AppearanceMirror {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun write(appearance: Appearance) {
        prefs.edit().putString(KEY_APPEARANCE, appearance.name).apply()
    }

    companion object {
        const val PREFS_NAME = "matn_appearance"
        const val KEY_APPEARANCE = "appearance"
    }
}