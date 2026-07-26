package com.giraffe.matn.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.MatnSummary
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.verses_count
import org.jetbrains.compose.resources.pluralStringResource

/**
 * One matn card in the library grid — extracted from `HomeScreen.kt` (specs/010-design-system-adoption
 * User Story 4, Constitution Principle VIII "second use, not third": the Home grid already
 * repeats this per item, which is itself the second use once a shared component exists to reuse).
 * Stateless and parameterized: no ViewModel, no navigation, no repository — driven entirely by
 * [summary] and [onClick].
 */
@Composable
fun MatnCard(
    summary: MatnSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progressFraction: Float? = null,
    /** Phase 8 (FR-002/FR-003): content-delivery availability rendered in the card's existing
     *  trailing status-icon slot (design-notes.md T046). `null` renders nothing — a matn whose
     *  availability hasn't resolved yet stays silent rather than showing a wrong state. */
    availability: ContentAvailability? = null,
    declaredSizeBytes: Long = 0L,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // T059 (US2): a content row — the title is already merged into the accessible name
            // from its descendant Text, so onClickLabel only needs to name what a tap does.
            .clickable(onClickLabel = summary.matn.title, onClick = onClick)
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
        val totals = pluralStringResource(Res.plurals.verses_count, summary.verseCount, summary.verseCount) +
            " · " + formatDuration(summary.totalDurationMs)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = MatnSpacing.unit / 2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = totals,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // design-notes.md T046: reuses the fetched card's trailing status-icon slot.
            if (availability != null) {
                ContentAvailabilityBadge(availability = availability, declaredSizeBytes = declaredSizeBytes)
            }
        }
        // Phase 7 (FR-006): a compact progress affordance — same visual language as
        // MatnProgressBar, condensed for card width (design-notes.md gap #1).
        if (progressFraction != null) {
            com.giraffe.matn.presentation.common.MatnProgressBar(
                fraction = progressFraction,
                modifier = Modifier.padding(top = MatnSpacing.unit / 2),
            )
        }
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

@Preview
@Composable
private fun MatnCardWithProgressPreview() {
    MatnTheme {
        Box(modifier = Modifier.width(180.dp).padding(MatnSpacing.unit)) {
            MatnCard(
                summary = previewSummary("m1", "الأجرومية", 4, 31_300),
                onClick = {},
                progressFraction = 0.6f,
            )
        }
    }
}

@Preview
@Composable
private fun MatnCardNotInstalledPreview() {
    MatnTheme {
        Box(modifier = Modifier.width(180.dp).padding(MatnSpacing.unit)) {
            MatnCard(
                summary = previewSummary("m2", "متن الآجرومية مبوب", 5, 39_900),
                onClick = {},
                availability = com.giraffe.matn.domain.model.ContentAvailability.NotInstalled(),
                declaredSizeBytes = 2_400_000,
            )
        }
    }
}
