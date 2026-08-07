package com.giraffe.matn.presentation.common

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.DeliveryPhase
import com.giraffe.matn.domain.model.DeliveryProgress
import com.giraffe.matn.presentation.theme.LocalMatnSemantics
import com.giraffe.matn.presentation.theme.LocalReduceMotion
import com.giraffe.matn.presentation.theme.MatnMotion
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.content_action_install
import matn.shared.generated.resources.content_installed_desc
import matn.shared.generated.resources.content_installing_percent
import matn.shared.generated.resources.content_nothing_to_install
import matn.shared.generated.resources.content_state_queued
import org.jetbrains.compose.resources.stringResource

/**
 * Per-matn content-availability affordance — the state line at the foot of [MatnCard] (Matn Design
 * System §05, Library card: an uppercase status word carrying the state's own colour). Exactly the
 * [ContentAvailability] states render, no partial state (FR-001).
 *
 * The colour does the categorising, so the word does not have to. `Downloaded` uses **success**,
 * not `primary`: primary means "the thing you can act on", and a finished download is precisely the
 * thing you no longer act on. `Install` sits in `onSurfaceVariant` — it is available, not urgent —
 * and `Queued` in `outline`, quieter still, because nothing is happening yet.
 *
 * **Zero-audio edge case**: when [declaredSizeBytes] is `0` and the matn is not installed, this
 * renders "nothing to install" and offers no size (spec edge case "Matn with no audio at all").
 */
@Composable
fun ContentAvailabilityBadge(
    availability: ContentAvailability,
    declaredSizeBytes: Long,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val semantics = LocalMatnSemantics.current
    // A state switch (not a continuous value) still gets a smooth transition rather than an abrupt
    // swap — keyed on the *kind*, not the whole [availability] value, so Downloading's own progress
    // ticks recompose in place without restarting the fade. Reduce motion snaps immediately (0ms).
    val reduceMotion = LocalReduceMotion.current
    val kind = when (availability) {
        is ContentAvailability.Downloaded -> 0
        is ContentAvailability.Downloading -> 1
        // Queued shares nothing with Downloading on purpose: nothing has transferred, so promising
        // progress would be dishonest. The cancellable affordance lives in ContentActionButton.
        ContentAvailability.Queued -> 2
        is ContentAvailability.NotDownloaded -> 3
    }
    Crossfade(
        targetState = kind,
        animationSpec = tween(if (reduceMotion) 0 else MatnMotion.durationShort),
        modifier = modifier,
    ) { currentKind ->
        when (currentKind) {
            0 -> StateLine(stringResource(Res.string.content_installed_desc), semantics.success)

            1 -> Column(modifier = Modifier.fillMaxWidth()) {
                val progress = (availability as? ContentAvailability.Downloading)?.progress
                val percent = ((progress?.fraction ?: 0f) * 100).toInt()
                StateLine(
                    text = stringResource(
                        Res.string.content_installing_percent,
                        ltrIsolated("$percent%"),
                    ),
                    color = scheme.tertiary,
                )
                // The word says how far along; the bar says it is still moving. Both, because a
                // percentage that has not ticked in a while is indistinguishable from a stall.
                if (progress != null) {
                    InstallProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MatnSpacing.hairline),
                    )
                }
            }

            2 -> StateLine(stringResource(Res.string.content_state_queued), scheme.outline)

            else -> if (declaredSizeBytes <= 0L) {
                StateLine(stringResource(Res.string.content_nothing_to_install), scheme.outline)
            } else {
                // Size belongs on this line rather than in the card's totals: it is the cost of the
                // action the line is offering, and it stops being relevant the moment it is paid.
                StateLine(
                    text = autoIsolated(
                        stringResource(Res.string.content_action_install) + " · " + formatBytes(declaredSizeBytes),
                    ),
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StateLine(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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
                DeliveryProgress(bytesTransferred = 1_008_000, totalBytes = 2_400_000, phase = DeliveryPhase.TRANSFERRING),
            ),
            declaredSizeBytes = 2_400_000,
        )
    }
}

@Preview
@Composable
private fun ContentAvailabilityBadgeQueuedPreview() {
    MatnTheme {
        ContentAvailabilityBadge(availability = ContentAvailability.Queued, declaredSizeBytes = 2_400_000)
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
private fun ContentAvailabilityBadgeInstalledDarkPreview() {
    MatnTheme(themeMode = com.giraffe.matn.domain.model.ThemeMode.DARK) {
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
