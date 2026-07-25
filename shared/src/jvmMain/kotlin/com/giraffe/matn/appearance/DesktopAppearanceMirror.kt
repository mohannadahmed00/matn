package com.giraffe.matn.appearance

import com.giraffe.matn.domain.appearance.AppearanceMirror
import com.giraffe.matn.domain.model.Appearance
import java.util.prefs.Preferences

/**
 * Desktop adapter for [AppearanceMirror] (research D9, contract theming §1.1). There is no
 * pre-Kotlin splash window to match on desktop, so this has no cold-start reader the way
 * `AndroidAppearanceMirror`/`MainActivity` does — it exists only so [Appearance] resolution has
 * somewhere to persist across restarts, via [Preferences]. **No business logic** — it stores what
 * it receives.
 */
class DesktopAppearanceMirror : AppearanceMirror {
    private val prefs = Preferences.userNodeForPackage(DesktopAppearanceMirror::class.java)

    override fun write(appearance: Appearance) {
        prefs.put(KEY_APPEARANCE, appearance.name)
    }

    companion object {
        const val KEY_APPEARANCE = "appearance"
    }
}
