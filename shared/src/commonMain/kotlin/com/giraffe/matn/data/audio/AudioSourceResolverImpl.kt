package com.giraffe.matn.data.audio

import com.giraffe.matn.domain.audio.AudioSourceResolver
import com.giraffe.matn.domain.repository.DownloadedContentRepository

/**
 * Resolves a per-verse `fileRef` to a playable `file://` URI.
 *
 * Phase 13 collapsed this to a single rule. It previously had two other branches, both now deleted
 * (FR-038, FR-040):
 *
 * - the **starter** branch, which resolved a bundled matn through Compose resources — there is no
 *   bundled matn any more, and no matn is permanently present;
 * - the **Compose-resource fallback**, which fired when a pack root could not be resolved. Under a
 *   remote model that fallback is a lie: it would hand the player a URI for an asset that does not
 *   ship, turning "not downloaded" into an obscure playback error instead of the actionable
 *   download prompt FR-021 requires.
 *
 * Now every matn resolves the same way, beneath its download directory. A null root means the matn
 * is genuinely not downloaded, and `EnsureMatnPlayableUseCase` is the gate the caller must consult
 * — this class deliberately does not paper over it.
 *
 * The audio file lives under the download root by its **basename**: the published `fileRef` is a
 * full storage object path (`matns/{matnId}/verses/{...}.mp3`) and `ContentFileStore` strips that
 * prefix when staging, so the two must agree. `ContentFileStore.audioPath` owns the convention.
 */
@org.koin.core.annotation.Single(binds = [AudioSourceResolver::class])
class AudioSourceResolverImpl(
    private val contentRepository: DownloadedContentRepository,
) : AudioSourceResolver {

    override suspend fun resolve(matnId: String, fileRef: String): String {
        val root = contentRepository.contentRootFor(matnId) ?: return ""
        return "file://$root/audio/${fileRef.substringAfterLast('/')}"
    }
}
