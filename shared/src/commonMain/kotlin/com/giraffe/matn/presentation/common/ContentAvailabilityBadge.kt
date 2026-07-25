package com.giraffe.matn.presentation.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.DeliveryPhase
import com.giraffe.matn.domain.model.DeliveryProgress
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.content_installed_desc
import matn.shared.generated.resources.content_not_installed_desc
import matn.shared.generated.resources.content_nothing_to_install
import org.jetbrains.compose.resources.stringResource

/**
 * Per-matn content-availability affordance (T042, FR-002/FR-003) — the trailing-edge status-icon
 * slot on [MatnCard] and the matn details header (design-notes.md T042: reuses the fetched
 * `cloud_done`/`download` slot rather than inventing a new region). Exactly the three
 * [ContentAvailability] states render, no partial state (FR-001).
 *
 * **Zero-audio edge case**: when [declaredSizeBytes] is `0` and the matn is not installed, this
 * renders "nothing to install" instead of "0 B" and offers no install affordance (spec edge case
 * "Matn with no audio at all").
 */
@Composable
fun ContentAvailabilityBadge(
    availability: ContentAvailability,
    declaredSizeBytes: Long,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    when (availability) {
        is ContentAvailability.Installed -> {
            DownloadedGlyph(
                color = scheme.primary,
                modifier = modifier,
                contentDescription = stringResource(Res.string.content_installed_desc),
            )
        }
        is ContentAvailability.Installing -> {
            InstallProgressIndicator(
                progress = availability.progress,
                modifier = modifier.width(MatnSpacing.unit * 12),
            )
        }
        is ContentAvailability.NotInstalled -> {
            if (declaredSizeBytes <= 0L) {
                Text(
                    text = stringResource(Res.string.content_nothing_to_install),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = modifier,
                )
            } else {
                Row(
                    modifier = modifier,
                    horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit / 2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DownloadGlyph(
                        color = scheme.onSurfaceVariant,
                        contentDescription = stringResource(Res.string.content_not_installed_desc),
                    )
                    Text(
                        text = formatBytes(declaredSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------------- Previews

@Preview
@Composable
private fun ContentAvailabilityBadgeNotInstalledPreview() {
    MatnTheme {
        ContentAvailabilityBadge(availability = ContentAvailability.NotInstalled(), declaredSizeBytes = 2_400_000)
    }
}

@Preview
@Composable
private fun ContentAvailabilityBadgeInstallingPreview() {
    MatnTheme {
        ContentAvailabilityBadge(
            availability = ContentAvailability.Installing(
                DeliveryProgress(bytesTransferred = 1_200_000, totalBytes = 2_400_000, phase = DeliveryPhase.TRANSFERRING),
            ),
            declaredSizeBytes = 2_400_000,
        )
    }
}

@Preview
@Composable
private fun ContentAvailabilityBadgeInstalledPreview() {
    MatnTheme {
        ContentAvailabilityBadge(availability = ContentAvailability.Installed(occupiedBytes = 2_400_000), declaredSizeBytes = 2_400_000)
    }
}

@Preview
@Composable
private fun ContentAvailabilityBadgeZeroAudioPreview() {
    MatnTheme {
        ContentAvailabilityBadge(availability = ContentAvailability.NotInstalled(), declaredSizeBytes = 0L)
    }
}
