package com.giraffe.matn.data.remote

/**
 * Injected, not compiled in (research D13). When [emulatorHost] is set every base URL points at
 * the Firebase Local Emulator Suite instead of production (`contracts/rest-contract.md` §2).
 */
data class FirebaseConfig(
    val projectId: String,
    val apiKey: String,
    val storageBucket: String,
    val emulatorHost: String? = null,
) {
    val identityBaseUrl: String
        get() = emulatorHost?.let { "http://$it:9099/identitytoolkit.googleapis.com/v1" }
            ?: "https://identitytoolkit.googleapis.com/v1"

    val secureTokenBaseUrl: String
        get() = emulatorHost?.let { "http://$it:9099/securetoken.googleapis.com/v1" }
            ?: "https://securetoken.googleapis.com/v1"

    val firestoreBaseUrl: String
        get() = emulatorHost?.let { "http://$it:8080/v1" }
            ?: "https://firestore.googleapis.com/v1"

    val storageBaseUrl: String
        get() = emulatorHost?.let { "http://$it:9199/v0" }
            ?: "https://firebasestorage.googleapis.com/v0"
}
