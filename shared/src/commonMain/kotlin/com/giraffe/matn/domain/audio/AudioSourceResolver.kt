package com.giraffe.matn.domain.audio

/**
 * Resolves a per-verse `fileRef` (e.g. `ajurrumiyya_verse_001.mp3`) to a playable platform URI
 * via Compose Multiplatform resources (D7). An interface so it is faked in tests (Principle V).
 */
interface AudioSourceResolver {
    suspend fun resolve(fileRef: String): String
}