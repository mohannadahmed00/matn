package com.giraffe.matn.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.presentation.theme.verseFontFamily
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.cover_placeholder_desc
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Renders a matn cover. Phase 1 has **no network image loader** (FR-018/SC-006) and no cover
 * assets are authored yet, so it always renders the shared **placeholder** — a framed parchment
 * field carrying a gold Amiri **«م»** monogram, an illuminated-initial nod to the manuscript
 * identity. This is the spec-correct behavior for content with no cover set
 * (FR-002/FR-005/SC-008); seeded content therefore carries `coverImageRef = null`.
 *
 * The [coverImageRef] parameter is retained so a later phase can render a real cover (via a
 * bundled-drawable lookup) when refs point at actual assets, **without changing any call site**.
 */
@Composable
fun CoverImage(coverImageRef: String?, modifier: Modifier = Modifier) {
    val placeholderDesc = stringResource(Res.string.cover_placeholder_desc)
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
