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
 * The install/cancel/remove action for one matn (T044, storage-ui-contract.md §3) — shared by the
 * matn details header and the Settings breakdown row (Principle VIII, extract at second use).
 * Stateless: driven entirely by [availability] and [isStarter]; renders nothing for the starter
 * (FR-027 — its audio ships in the app binary and is never installed or removed).
 */
@Composable
fun ContentActionButton(
    availability: ContentAvailability,
    isStarter: Boolean,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isStarter) return
    val scheme = MaterialTheme.colorScheme
    when (availability) {
        is ContentAvailability.NotInstalled -> {
            Button(
                onClick = onInstall,
                shape = MatnShapes.full,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
                modifier = modifier,
            ) {
                Text(stringResource(Res.string.content_action_install), style = MaterialTheme.typography.labelLarge)
            }
        }
        is ContentAvailability.Installing -> {
            IconButton(onClick = onCancel, modifier = modifier) {
                CancelInstallGlyph(color = scheme.onSurfaceVariant, contentDescription = stringResource(Res.string.content_action_cancel))
            }
        }
        is ContentAvailability.Installed -> {
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
            availability = ContentAvailability.NotInstalled(),
            isStarter = false,
            onInstall = {}, onCancel = {}, onRemove = {},
        )
    }
}

@Preview
@Composable
private fun ContentActionButtonInstallingPreview() {
    MatnTheme {
        ContentActionButton(
            availability = ContentAvailability.Installing(
                com.giraffe.matn.domain.model.DeliveryProgress(
                    bytesTransferred = 1_000_000,
                    totalBytes = 2_000_000,
                    phase = com.giraffe.matn.domain.model.DeliveryPhase.TRANSFERRING,
                ),
            ),
            isStarter = false,
            onInstall = {}, onCancel = {}, onRemove = {},
        )
    }
}

@Preview
@Composable
private fun ContentActionButtonInstalledPreview() {
    MatnTheme {
        ContentActionButton(
            availability = ContentAvailability.Installed(occupiedBytes = 2_000_000),
            isStarter = false,
            onInstall = {}, onCancel = {}, onRemove = {},
        )
    }
}

@Preview
@Composable
private fun ContentActionButtonStarterPreview() {
    MatnTheme {
        ContentActionButton(
            availability = ContentAvailability.Installed(occupiedBytes = 296_000),
            isStarter = true,
            onInstall = {}, onCancel = {}, onRemove = {},
        )
    }
}
