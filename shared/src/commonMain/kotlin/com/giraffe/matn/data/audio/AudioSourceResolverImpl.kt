package com.giraffe.matn.data.audio

import com.giraffe.matn.domain.audio.AudioSourceResolver
import matn.shared.generated.resources.Res

/**
 * Resolves a per-verse `fileRef` to a playable platform URI via Compose Multiplatform resources
 * (D7) — `Res.getUri("files/audio/<fileRef>")` yields `file:///android_asset/...` on Android
 * (playable by ExoPlayer's `AssetDataSource`) and a bundle `file://` URL on iOS. The single
 * cross-platform bundling mechanism already in use for Amiri fonts, so audio needs no per-platform
 * copy step (Principle IV). Kept as an interface impl so it is faked in tests.
 */
class AudioSourceResolverImpl : AudioSourceResolver {
    @OptIn(org.jetbrains.compose.resources.InternalResourceApi::class)
    override suspend fun resolve(fileRef: String): String =
        Res.getUri("files/audio/$fileRef")
}