package com.giraffe.matn.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.MatnSummary
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.presentation.theme.verseFontFamily
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.verses_count
import org.jetbrains.compose.resources.pluralStringResource

/**
 * One matn card in the library grid (Matn Design System §05 — Library card).
 *
 * A contained card on `surfaceContainerLow` with **no elevation**, per the design: depth in this
 * product is carried by containment, not by shadow. The cover fills the card's head with the matn's
 * Arabic title set over it in Amiri, which is the manuscript-frontispiece identity the product is
 * built around — and it is also the only name the domain has, so putting it anywhere else would
 * mean either duplicating it below or inventing a transliteration the catalog does not carry.
 *
 * A scrim sits under that title. The design assumes flat-colour covers, but [coverBytes] can be a
 * real published image with anything at all along its bottom edge, and a title that is legible only
 * against some covers is not legible.
 *
 * Stateless and parameterized: no ViewModel, no navigation, no repository — driven entirely by
 * [summary] and [onClick].
 */
@Composable
fun MatnCard(
    summary: MatnSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progressFraction: Float? = null,
    /** Phase 8 (FR-002/FR-003): content-delivery availability, rendered as the card's state line.
     *  `null` renders nothing — a matn whose availability hasn't resolved yet stays silent rather
     *  than showing a wrong state. */
    availability: ContentAvailability? = null,
    declaredSizeBytes: Long = 0L,
    /** Phase 13 (FR-012): cached cover bytes, or null for the placeholder. Fetched by the
     *  ViewModel on the browse path — this composable does no IO (Principle II). */
    coverBytes: ByteArray? = null,
) {
    Surface(
        shape = MatnShapes.xl,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            // T059 (US2): a content row — the title is already merged into the accessible name
            // from its descendant Text, so onClickLabel only needs to name what a tap does.
            .clickable(onClickLabel = summary.matn.title, onClick = onClick),
    ) {
        Column {
            CoverPlate(
                summary = summary,
                coverBytes = coverBytes,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                modifier = Modifier.padding(
                    start = MatnSpacing.snug,
                    end = MatnSpacing.snug,
                    top = MatnSpacing.unit + 2.dp,
                    bottom = MatnSpacing.snug,
                ),
            ) {
                Text(
                    text = summary.matn.author,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Phase 13: an undownloaded matn has no local duration — the catalog overview
                // carries no verses (FR-003), so it is genuinely unknown rather than zero. Omit it
                // instead of printing a fabricated "0:00"; the verse count still says what the
                // student is getting, and the download size sits on the state line below.
                val verses = pluralStringResource(Res.plurals.verses_count, summary.verseCount, summary.verseCount)
                // Isolated as a whole (BidiText.kt § Composites): the " · " separator is neutral and
                // sits between a localized count and an LTR duration, so without this the paragraph
                // direction decides their order — which is what rendered "4 verses · 0:31" as
                // "verses · 0:314".
                val totals = if (summary.totalDurationMs > 0L) {
                    autoIsolated(verses + " · " + formatDuration(summary.totalDurationMs))
                } else {
                    autoIsolated(verses)
                }
                Text(
                    text = totals,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = MatnSpacing.hairline / 2),
                )
                if (availability != null) {
                    ContentAvailabilityBadge(
                        availability = availability,
                        declaredSizeBytes = declaredSizeBytes,
                        modifier = Modifier.padding(top = MatnSpacing.unit),
                    )
                }
                // Phase 7 (FR-006): a compact memorization affordance — same visual language as
                // MatnProgressBar, condensed for card width. Distinct from the delivery state above:
                // one is how much of the matn is on the device, the other how much is in the
                // student's memory, and they are routinely different numbers.
                if (progressFraction != null) {
                    MatnProgressBar(
                        fraction = progressFraction,
                        modifier = Modifier.padding(top = MatnSpacing.unit),
                    )
                }
            }
        }
    }
}

/** The cover, with the Arabic title set over its foot behind a legibility scrim. */
@Composable
private fun CoverPlate(summary: MatnSummary, coverBytes: ByteArray?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.aspectRatio(1.35f).clip(MatnShapes.xl)) {
        CoverImage(
            coverImageRef = summary.matn.coverImageRef,
            imageBytes = coverBytes,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.62f),
                    ),
                ),
        )
        Text(
            text = summary.matn.title,
            fontFamily = verseFontFamily(),
            style = MaterialTheme.typography.titleMedium,
            // Fixed against the scrim rather than routed through the scheme: this text sits on a
            // dark overlay in both themes, so an onSurface that flips with the theme would go
            // invisible in one of them.
            color = Color(0xFFFCF9F8),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(MatnSpacing.unit + 2.dp),
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

@Preview
@Composable
private fun MatnCardDarkPreview() {
    MatnTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.width(180.dp).padding(MatnSpacing.unit)) {
            MatnCard(
                summary = previewSummary("m1", "الأجرومية", 4, 31_300),
                onClick = {},
                availability = ContentAvailability.Downloaded(occupiedBytes = 2_400_000),
                progressFraction = 0.6f,
            )
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
                availability = ContentAvailability.Downloaded(occupiedBytes = 2_400_000),
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
                summary = previewSummary("m2", "متن الآجرومية مبوب", 5, 0),
                onClick = {},
                availability = ContentAvailability.NotDownloaded(),
                declaredSizeBytes = 2_400_000,
            )
        }
    }
}

@Preview
@Composable
private fun MatnCardDownloadingPreview() {
    MatnTheme {
        Box(modifier = Modifier.width(180.dp).padding(MatnSpacing.unit)) {
            MatnCard(
                summary = previewSummary("m2", "متن الآجرومية مبوب", 5, 0),
                onClick = {},
                availability = ContentAvailability.Downloading(
                    com.giraffe.matn.domain.model.DeliveryProgress(
                        bytesTransferred = 1_008_000,
                        totalBytes = 2_400_000,
                        phase = com.giraffe.matn.domain.model.DeliveryPhase.TRANSFERRING,
                    ),
                ),
                declaredSizeBytes = 2_400_000,
            )
        }
    }
}

/** Largest reachable font scale, narrowest card (accessibility-contract.md §5/§7). */
@Preview(fontScale = 2.0f)
@Composable
private fun MatnCardMaxScalePreview() {
    MatnTheme {
        Box(modifier = Modifier.width(150.dp).padding(MatnSpacing.unit)) {
            MatnCard(
                summary = previewSummary("m1", "الأجرومية", 4, 31_300),
                onClick = {},
                availability = ContentAvailability.Downloaded(occupiedBytes = 2_400_000),
            )
        }
    }
}
