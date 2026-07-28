package com.giraffe.matn.data.remote

/**
 * Injected, not compiled in — same shape as [FirebaseConfig] (research D13). Backs
 * [com.giraffe.matn.data.remote.storage.StorageRestClient]: cover images today, Phase 12's
 * per-verse audio later, both under one bucket (`design-notes.md`).
 *
 * [anonKey] is Supabase's public "anon" API key — required as the `apikey` header alongside the
 * `Authorization: Bearer <firebaseIdToken>` on every request; Supabase's third-party-auth trust of
 * the Firebase project is what lets it verify that bearer token directly, so no second sign-in
 * flow or token type is introduced.
 */
data class SupabaseConfig(
    val projectUrl: String,
    val anonKey: String,
    val bucket: String,
) {
    val storageBaseUrl: String
        get() = "$projectUrl/storage/v1"
}
