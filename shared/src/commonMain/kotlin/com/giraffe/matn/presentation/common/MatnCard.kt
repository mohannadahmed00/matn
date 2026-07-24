package com.giraffe.matn.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.MatnSummary
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.verses_count
import org.jetbrains.compose.resources.stringResource

/**
 * One matn card in the library grid — extracted from `HomeScreen.kt` (specs/010-design-system-adoption
 * User Story 4, Constitution Principle VIII "second use, not third": the Home grid already
 * repeats this per item, which is itself the second use once a shared component exists to reuse).
 * Stateless and parameterized: no ViewModel, no navigation, no repository — driven entirely by
 * [summary] and [onClick].
 */
@Composable
fun MatnCard(summary: MatnSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(MatnSpacing.unit),
        horizontalAlignment = Alignment.Start,
    ) {
        CoverImage(
            coverImageRef = summary.matn.coverImageRef,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f),
        )
        Text(
            text = summary.matn.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = MatnSpacing.unit + 2.dp),
        )
        Text(
            text = summary.matn.author,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = MatnSpacing.unit / 2),
        )
        val totals = stringResource(Res.string.verses_count, summary.verseCount) +
            " · " + formatDuration(summary.totalDurationMs)
        Text(
            text = totals,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MatnSpacing.unit / 2),
        )
    }
}

// --------------------------------------------------------------------------- Previews

private fun previewSummary(id: String, title: String, count: Int, total: Long) = MatnSummary(
    matn = Matn(
        id = id,
        title = title,
        author = "ابن آجُرُّوم",
        description = "",
        coverImageRef = null,
        structureKind = StructureKind.SIMPLE,
    ),
    verseCount = count,
    totalDurationMs = total,
)

@Preview
@Composable
private fun MatnCardPreview() {
    MatnTheme {
        Box(modifier = Modifier.width(180.dp).padding(MatnSpacing.unit)) {
            MatnCard(summary = previewSummary("m1", "الأجرومية", 4, 31_300), onClick = {})
        }
    }
}
