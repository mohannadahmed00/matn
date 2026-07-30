package com.giraffe.matn.data.remote

/**
 * Injected, not compiled in (research D13). The single backend config — Firebase's Identity
 * Toolkit, Secure Token, and Firestore were all replaced by one Supabase project, so the former
 * `FirebaseConfig` is gone and there is no second base URL family to keep in step.
 *
 * [anonKey] is Supabase's public "anon" API key. It goes on **every** request as the `apikey`
 * header — including sign-in, where there is no bearer token yet — and identifies the project, not
 * the caller; row-level security is what actually authorises, keyed off the `Authorization: Bearer
 * <accessToken>` when one is present.
 *
 * Pointing [projectUrl] at a local stack (`http://127.0.0.1:54321`) is the whole of what the
 * Firebase emulator's `emulatorHost` special-casing used to do.
 */
data class SupabaseConfig(
    val projectUrl: String,
    val anonKey: String,
    val bucket: String,
) {
    val authBaseUrl: String
        get() = "$projectUrl/auth/v1"

    val restBaseUrl: String
        get() = "$projectUrl/rest/v1"

    val storageBaseUrl: String
        get() = "$projectUrl/storage/v1"
}
