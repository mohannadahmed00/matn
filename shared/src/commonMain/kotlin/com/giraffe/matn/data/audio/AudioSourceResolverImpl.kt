package com.giraffe.matn.data.audio

import com.giraffe.matn.domain.audio.AudioSourceResolver
import com.giraffe.matn.domain.repository.ContentPackRepository
import matn.shared.generated.resources.Res

/**
 * Resolves a per-verse `fileRef` to a playable platform URI (D7 / research D6) — routed based on
 * which catalog row the [matnId] belongs to:
 *
 * - **Starter matn**: its audio ships in the app binary, so resolve via Compose Multiplatform
 *   resources — `Res.getUri("files/audio/<fileRef>")` yields `file:///android_asset/...` on Android
 *   (playable by ExoPlayer's `AssetDataSource`) and a bundle `file://` URL on iOS. The single
 *   cross-platform bundling mechanism already in use for Amifi fonts, so the starter's audio needs
 *   no per-platform copy step (Principle IV).
 *
 * - **On-demand matn**: resolve beneath the delivered pack root —
 *   `"file://" + packRoot + "/audio/" + fileRef`. The repo owns the `matnId ↔ packId` translation;
 *   this impl asks the repo for the resolved pack root (a path string, not a pack id — FR-032
 *   keeps the pack id itself private to the repo and the two platform adapters).
 *
 * The signature widening `resolve(fileRef) → resolve(matnId, fileRef)` (research D6) is the only
 * playback integration change Phase 8 makes; the gapless transition path stays untouched
 * (Constitution VII). This impl is faked in tests (`FakeAudioSourceResolver`) — the only other
 * audio-source implementation in the repo is the unit-test fake.
 */
class AudioSourceResolverImpl(
    private val contentPackRepository: ContentPackRepository,
) : AudioSourceResolver {

    @OptIn(org.jetbrains.compose.resources.InternalResourceApi::class)
    override suspend fun resolve(matnId: String, fileRef: String): String {
        if (contentPackRepository.isStarterMatn(matnId)) {
            return Res.getUri("files/audio/$fileRef")
        }
        val packRoot = contentPackRepository.packRootFor(matnId)
        if (packRoot != null) {
            return "file://$packRoot/audio/$fileRef"
        }
        // Defensive fallback only: the play-ability gate at `PlaybackController.startSession`
        // rejects non-installed matns before the queue builder reaches here, so a missing pack
        // root here means the pack was evicted between gate and queue-build — emit a Compose
        // resources URI for parity with the starter handling; the engine will surface a TrackError
        // downstream if the asset truly is missing.
        return Res.getUri("files/audio/$fileRef")
    }
}