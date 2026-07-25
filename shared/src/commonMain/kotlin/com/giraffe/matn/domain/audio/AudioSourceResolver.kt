package com.giraffe.matn.domain.audio

/**
 * Resolves a per-verse `fileRef` (e.g. `ajurrumiyya_verse_001.mp3`) to a playable platform URI
 * (D7 / research D6). The starter matn's audio ships in Compose Multiplatform resources; every
 * other matn's audio is delivered by the platform's on-demand mechanism ([ContentDeliveryEngine])
 * and resolved beneath its pack root.
 *
 * Takes the `matnId` so it can route to the right source without the caller knowing about pack ids
 * (FR-032 forward compatibility — only this impl and the platform adapters know what a pack is).
 * Faked in tests (Principle V).
 */
interface AudioSourceResolver {
    /** Returns a playable URI for [fileRef], routed through the starter matn's bundled resources
     *  or the on-demand pack root for [matnId]. */
    suspend fun resolve(matnId: String, fileRef: String): String
}