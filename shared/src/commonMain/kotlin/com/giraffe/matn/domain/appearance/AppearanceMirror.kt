package com.giraffe.matn.domain.appearance

import com.giraffe.matn.domain.model.Appearance

/**
 * Platform seam that records the already-resolved [Appearance] where the platform launch window
 * can read it synchronously, before any Kotlin runs (research D9, contract theming §1.1).
 *
 * Android → `SharedPreferences`; iOS → `NSUserDefaults`. Contains **no** business logic: it
 * receives an already-resolved `Appearance` and stores it. Faked in tests.
 */
interface AppearanceMirror {
    /** Records the resolved appearance so the next cold-start launch window can match it. */
    fun write(appearance: Appearance)
}