package com.giraffe.matn.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.presentation.theme.verseFontFamily
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.cover_placeholder_desc
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Renders a matn cover, or the shared **placeholder** — a framed parchment field carrying a gold
 * Amiri **«م»** monogram, an illuminated-initial nod to the manuscript identity.
 *
 * Phase 13 (FR-012) makes the cover real for the first time. Bytes arrive as [imageBytes], already
 * fetched and cached by `CoverImageCache` and handed down through the screen's UI state — this
 * composable performs **no IO of its own**, so it stays a pure function of state (Principle II) and
 * cannot smuggle a network call into a render pass.
 *
 * Every failure path lands on the placeholder: no cover published, not fetched yet, fetch failed,
 * or bytes that will not decode. FR-012 requires exactly that — an unfetchable cover must not block
 * or degrade any other part of the overview (User Story 1 scenario 6).
 */
@Composable
fun CoverImage(
    coverImageRef: String?,
    modifier: Modifier = Modifier,
    imageBytes: ByteArray? = null,
) {
    val placeholderDesc = stringResource(Res.string.cover_placeholder_desc)

    // `runCatching` rather than a decode-format check: any corrupt or unexpected payload must
    // degrade to the placeholder rather than crash the library grid.
    val bitmap = remember(imageBytes) {
        imageBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
            runCatching { bytes.decodeToImageBitmap() }.getOrNull()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(MatnShapes.lg),
        )
        return
    }

    Surface(
        modifier = modifier.semantics { contentDescription = placeholderDesc },
        shape = MatnShapes.lg,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "م",
                fontFamily = verseFontFamily(),
                color = MaterialTheme.colorScheme.secondary,
                // displayMedium (45sp in the default M3 scale) — the closest token-routed size to
                // the design's monogram, rather than an independent magic literal.
                fontSize = MaterialTheme.typography.displayMedium.fontSize,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview
@Composable
private fun CoverImagePlaceholderPreview() {
    MatnTheme {
        CoverImage(
            coverImageRef = null,
            modifier = Modifier.width(140.dp).aspectRatio(0.75f),
        )
    }
}
