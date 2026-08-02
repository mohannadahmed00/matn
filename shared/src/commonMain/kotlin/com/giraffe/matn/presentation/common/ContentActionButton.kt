package com.giraffe.matn.presentation.common

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.content_action_cancel
import matn.shared.generated.resources.content_action_install
import matn.shared.generated.resources.content_action_remove
import org.jetbrains.compose.resources.stringResource

/**
 * The download/cancel/remove action for one matn (delivery-contract.md §3) — shared by the matn
 * details header and the Settings breakdown row (Principle VIII, extract at second use).
 *
 * Stateless: driven entirely by [availability]. Phase 13 removed the `isStarter` parameter and the
 * early return it guarded — **every** matn now renders an action, because FR-031 forbids any item
 * being exempt from removal.
 */
@Composable
fun ContentActionButton(
    availability: ContentAvailability,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    when (availability) {
        // FR-015 / Clarification 4: queued renders as a cancellable wait with NO progress bar.
        // A bar sitting at 0% would promise movement that has not started — the whole point of
        // the state is that the transfer slot is still occupied by another matn.
        ContentAvailability.Queued -> {
            IconButton(onClick = onCancel, modifier = modifier) {
                CancelInstallGlyph(
                    color = scheme.onSurfaceVariant,
                    contentDescription = stringResource(Res.string.content_action_cancel),
                )
            }
        }
        is ContentAvailability.NotDownloaded -> {
            Button(
                onClick = onInstall,
                shape = MatnShapes.full,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
                modifier = modifier,
            ) {
                Text(stringResource(Res.string.content_action_install), style = MaterialTheme.typography.labelLarge)
            }
        }
        is ContentAvailability.Downloading -> {
            IconButton(onClick = onCancel, modifier = modifier) {
                CancelInstallGlyph(color = scheme.onSurfaceVariant, contentDescription = stringResource(Res.string.content_action_cancel))
            }
        }
        is ContentAvailability.Downloaded -> {
            IconButton(onClick = onRemove, modifier = modifier) {
                RemoveContentGlyph(color = scheme.error, contentDescription = stringResource(Res.string.content_action_remove))
            }
        }
    }
}

// --------------------------------------------------------------------------- Previews

@Preview
@Composable
private fun ContentActionButtonNotInstalledPreview() {
    MatnTheme {
        ContentActionButton(
            availability = ContentAvailability.NotDownloaded(),
            onInstall = {}, onCancel = {}, onRemove = {},
        )
    }
}

@Preview
@Composable
private fun ContentActionButtonInstallingPreview() {
    MatnTheme {
        ContentActionButton(
            availability = ContentAvailability.Downloading(
                com.giraffe.matn.domain.model.DeliveryProgress(
                    bytesTransferred = 1_000_000,
                    totalBytes = 2_000_000,
                    phase = com.giraffe.matn.domain.model.DeliveryPhase.TRANSFERRING,
                ),
            ),
            onInstall = {}, onCancel = {}, onRemove = {},
        )
    }
}

@Preview
@Composable
private fun ContentActionButtonInstalledPreview() {
    MatnTheme {
        ContentActionButton(
            availability = ContentAvailability.Downloaded(occupiedBytes = 2_000_000),
            onInstall = {}, onCancel = {}, onRemove = {},
        )
    }
}

/** Phase 13: replaces the old starter preview. Queued must be visually distinct from Downloading —
 *  cancellable, but with no progress bar (FR-015). */
@Preview
@Composable
private fun ContentActionButtonQueuedPreview() {
    MatnTheme {
        ContentActionButton(
            availability = ContentAvailability.Queued,
            onInstall = {}, onCancel = {}, onRemove = {},
        )
    }
}
