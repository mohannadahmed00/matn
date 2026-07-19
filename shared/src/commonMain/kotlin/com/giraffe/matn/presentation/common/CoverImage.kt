package com.giraffe.matn.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.sp
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.cover_placeholder_desc
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width

/**
 * Renders a matn cover. Phase 1 has **no network image loader** (FR-018/SC-006). No cover image
 * assets are authored/bundled yet, so Phase 1 always renders the shared deterministic
 * **placeholder** — which is the spec-correct behavior for content with no cover set
 * (FR-002/FR-005/SC-008): the seeded content therefore carries `coverImageRef = null`.
 *
 * The [coverImageRef] parameter is retained so a later phase can render a real cover (via a
 * bundled-drawable lookup) when refs point at actual assets, **without changing any call site**.
 * Until then a non-null ref still shows the placeholder rather than a broken image slot.
 */
@Composable
fun CoverImage(coverImageRef: String?, modifier: Modifier = Modifier) {
    val placeholderDesc = stringResource(Res.string.cover_placeholder_desc)
    Surface(
        modifier = modifier.semantics { contentDescription = placeholderDesc },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "م",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 40.sp,
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