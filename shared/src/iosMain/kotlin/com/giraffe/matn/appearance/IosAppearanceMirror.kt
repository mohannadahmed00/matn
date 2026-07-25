package com.giraffe.matn.appearance

import com.giraffe.matn.domain.appearance.AppearanceMirror
import com.giraffe.matn.domain.model.Appearance
import platform.Foundation.NSUserDefaults

/**
 * iOS adapter for [AppearanceMirror] (research D9, contract theming §1.1). Writes the
 * already-resolved [Appearance] name to `NSUserDefaults.standardUserDefaults` under
 * `"matn_appearance"` so the launch storyboard / first `ComposeUIViewController` can read it
 * synchronously before any Kotlin runs. **No business logic** — it stores what it receives.
 *
 * ⚠️ Like the other Apple-sysroot stubs (see `AvQueueAudioEngine`'s platform-toolchain note),
 * this compiles on Windows and the functionality is validated end-to-end on macOS/Xcode. The
 * write itself is a single `setObject:forKey:` call.
 */
class IosAppearanceMirror : AppearanceMirror {

    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults

    override fun write(appearance: Appearance) {
        defaults.setObject(appearance.name, forKey = KEY_APPEARANCE)
    }

    companion object {
        const val KEY_APPEARANCE = "matn_appearance"
    }
}