package com.giraffe.matn.remote

import org.junit.Assume.assumeTrue

/** `firebase emulators:exec` sets this; unset means no Firebase CLI on this machine (research D9). */
fun emulatorHostOrNull(): String? = System.getenv("FIREBASE_EMULATOR_HOST")

/** Skips the calling test (not a failure) when no emulator host is set, so `./gradlew test`
 * stays green on a machine with no Firebase CLI. */
fun requireEmulatorHost(): String {
    val host = emulatorHostOrNull()
    assumeTrue("FIREBASE_EMULATOR_HOST not set — skipping emulator-gated test", host != null)
    return requireNotNull(host)
}
