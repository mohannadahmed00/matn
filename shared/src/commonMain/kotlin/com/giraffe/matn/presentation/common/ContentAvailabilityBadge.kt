package com.giraffe.matn.presentation.common

import androidx.compose.animation.core.tween
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
import com.giraffe.matn.presentation.theme.LocalReduceMotion
import com.giraffe.matn.presentation.theme.MatnMotion
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
    // T095 (US5, adaptive-motion-contract.md §B3): a state switch (not a continuous value) still
    // gets a smooth transition rather than an abrupt swap — keyed on the *kind*, not the whole
    // [availability] value, so Installing's own progress ticks recompose in place without
    // restarting the fade. Reduce motion snaps immediately (0ms).
    val reduceMotion = LocalReduceMotion.current
    val kind = when (availability) {
        is ContentAvailability.Downloaded -> 0
        is ContentAvailability.Downloading -> 1
        is ContentAvailability.NotDownloaded -> 2
        // Queued shares the not-downloaded visual: nothing has transferred, so promising progress
        // would be dishonest. The cancellable affordance lives in ContentActionButton (FR-015).
        ContentAvailability.Queued -> 2
    }
    androidx.compose.animation.Crossfade(
        targetState = kind,
        animationSpec = tween(if (reduceMotion) 0 else MatnMotion.durationShort),
        modifier = modifier,
    ) { currentKind ->
        when (currentKind) {
            0 -> DownloadedGlyph(
                color = scheme.primary,
                contentDescription = stringResource(Res.string.content_installed_desc),
            )
            1 -> InstallProgressIndicator(
                progress = (availability as ContentAvailability.Downloading).progress,
                modifier = Modifier.width(MatnSpacing.unit * 12),
            )
            else -> {
                if (declaredSizeBytes <= 0L) {
                    Text(
                        text = stringResource(Res.string.content_nothing_to_install),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    Row(
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
}

// --------------------------------------------------------------------------- Previews

@Preview
@Composable
private fun ContentAvailabilityBadgeNotInstalledPreview() {
    MatnTheme {
        ContentAvailabilityBadge(availability = ContentAvailability.NotDownloaded(), declaredSizeBytes = 2_400_000)
    }
}

@Preview
@Composable
private fun ContentAvailabilityBadgeInstallingPreview() {
    MatnTheme {
        ContentAvailabilityBadge(
            availability = ContentAvailability.Downloading(
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
        ContentAvailabilityBadge(availability = ContentAvailability.Downloaded(occupiedBytes = 2_400_000), declaredSizeBytes = 2_400_000)
    }
}

@Preview
@Composable
private fun ContentAvailabilityBadgeZeroAudioPreview() {
    MatnTheme {
        ContentAvailabilityBadge(availability = ContentAvailability.NotDownloaded(), declaredSizeBytes = 0L)
    }
}
