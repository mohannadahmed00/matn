package com.giraffe.matn.presentation.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.domain.model.DeliveryPhase
import com.giraffe.matn.domain.model.DeliveryProgress
import com.giraffe.matn.presentation.theme.LocalReduceMotion
import com.giraffe.matn.presentation.theme.MatnMotion
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.content_waiting_confirmation
import matn.shared.generated.resources.content_waiting_network
import org.jetbrains.compose.resources.stringResource

/**
 * Determinate transfer progress (T043, data-model §2.2). A Play-parked transfer
 * (`WAITING_FOR_NETWORK_POLICY` / `REQUIRES_CONFIRMATION`, research D11) renders as a distinct,
 * explained wait rather than a frozen bar — SC-007 — instead of the bare `fraction` indicator.
 * Stateless and parameterized (Principle II); shared by [ContentAvailabilityBadge] and the matn
 * details header (Principle VIII, extract at second use).
 */
@Composable
fun InstallProgressIndicator(progress: DeliveryProgress, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val waitingLabel = when (progress.phase) {
        DeliveryPhase.WAITING_FOR_NETWORK_POLICY -> stringResource(Res.string.content_waiting_network)
        DeliveryPhase.REQUIRES_CONFIRMATION -> stringResource(Res.string.content_waiting_confirmation)
        DeliveryPhase.PENDING, DeliveryPhase.TRANSFERRING -> null
    }
    // T095 (US5, adaptive-motion-contract.md §B2.1/§B3): animates toward the true value at
    // durationShort, never past it (FR-034); reduce motion snaps immediately.
    val reduceMotion = LocalReduceMotion.current
    val animated by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(if (reduceMotion) 0 else MatnMotion.durationShort),
    )
    Column(modifier = modifier.fillMaxWidth()) {
        if (waitingLabel != null) {
            Text(
                text = waitingLabel,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = MatnSpacing.unit / 2),
            )
        }
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxWidth()
                .clip(MatnShapes.full),
            color = if (waitingLabel != null) scheme.tertiary else scheme.primary,
            trackColor = scheme.surfaceContainerHigh,
        )
        // T065 (US2, FR-016): the fill communicates progress by more than colour/length alone.
        Text(
            text = "${(progress.fraction * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MatnSpacing.unit / 4),
        )
    }
}

// --------------------------------------------------------------------------- Previews

@Preview
@Composable
private fun InstallProgressIndicatorTransferringPreview() {
    MatnTheme {
        InstallProgressIndicator(
            progress = DeliveryProgress(bytesTransferred = 1_200_000, totalBytes = 2_400_000, phase = DeliveryPhase.TRANSFERRING),
        )
    }
}

@Preview
@Composable
private fun InstallProgressIndicatorWaitingPreview() {
    MatnTheme {
        InstallProgressIndicator(
            progress = DeliveryProgress(bytesTransferred = 400_000, totalBytes = 2_400_000, phase = DeliveryPhase.WAITING_FOR_NETWORK_POLICY),
        )
    }
}

@Preview
@Composable
private fun InstallProgressIndicatorRequiresConfirmationPreview() {
    MatnTheme {
        InstallProgressIndicator(
            progress = DeliveryProgress(bytesTransferred = 0, totalBytes = 2_400_000, phase = DeliveryPhase.REQUIRES_CONFIRMATION),
        )
    }
}
